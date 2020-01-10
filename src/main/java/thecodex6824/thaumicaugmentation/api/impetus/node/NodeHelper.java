/**
 *  Thaumic Augmentation
 *  Copyright (c) 2019 TheCodex6824.
 *
 *  This file is part of Thaumic Augmentation.
 *
 *  Thaumic Augmentation is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Thaumic Augmentation is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with Thaumic Augmentation.  If not, see <https://www.gnu.org/licenses/>.
 */

package thecodex6824.thaumicaugmentation.api.impetus.node;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk.EnumCreateEntityType;
import thecodex6824.thaumicaugmentation.api.internal.TAInternals;
import thecodex6824.thaumicaugmentation.api.item.CapabilityImpetusLinker;
import thecodex6824.thaumicaugmentation.api.item.IImpetusLinker;
import thecodex6824.thaumicaugmentation.api.util.DimensionalBlockPos;

public final class NodeHelper {

    private NodeHelper() {}
    
    @SuppressWarnings("null")
    public static boolean handleLinkInteract(TileEntity provider, World world, ItemStack stack, EntityPlayer player, BlockPos pos, 
            EnumFacing face, EnumHand hand) {
        
        IImpetusLinker linker = stack.getCapability(CapabilityImpetusLinker.IMPETUS_LINKER, null);
        if (!world.isRemote && linker != null) {
            DimensionalBlockPos origin = linker.getOrigin();
            if (player.isSneaking()) {
                if (!origin.isInvalid() && origin.getPos().getX() == pos.getX() && origin.getPos().getY() == pos.getY() &&
                            origin.getPos().getZ() == pos.getZ() && origin.getDimension() == world.provider.getDimension()) {
                        
                    linker.setOrigin(DimensionalBlockPos.INVALID);
                    return true;
                }
                
                linker.setOrigin(new DimensionalBlockPos(pos.getX(), pos.getY(), pos.getZ(), world.provider.getDimension()));
                return true;
            }
            else if (!origin.isInvalid()) {
                IImpetusNode node = provider.getCapability(CapabilityImpetusNode.IMPETUS_NODE, null);
                if (!origin.equals(node.getLocation()) && world.isBlockLoaded(origin.getPos())) {
                    TileEntity te = world.getChunk(origin.getPos()).getTileEntity(origin.getPos(), EnumCreateEntityType.CHECK);
                    if (te != null) {
                        IImpetusNode otherNode = te.getCapability(CapabilityImpetusNode.IMPETUS_NODE, null);
                        if (otherNode != null) {
                            if (node.getInputLocations().contains(origin)) {
                                if (node.canRemoveNodeAsInput(otherNode) && otherNode.canRemoveNodeAsOutput(node)) {
                                    node.removeInput(otherNode);
                                    provider.markDirty();
                                    te.markDirty();
                                    syncRemovedImpetusNodeInput(node, origin);
                                }
                            }
                            else {
                                if (otherNode.getNumOutputs() >= otherNode.getMaxOutputs())
                                    player.sendStatusMessage(new TextComponentTranslation("thaumicaugmentation.text.impetus_link_limit_out"), true);
                                else if (node.getNumInputs() >= node.getMaxInputs())
                                    player.sendStatusMessage(new TextComponentTranslation("thaumicaugmentation.text.impetus_link_limit_in"), true);
                                else if (node.canConnectNodeAsInput(otherNode) && otherNode.canConnectNodeAsOutput(node)) {
                                    double dist = node.getLocation().getPos().distanceSq(otherNode.getLocation().getPos());
                                    double nodeMax = node.getMaxConnectDistance(otherNode);
                                    double otherNodeMax = otherNode.getMaxConnectDistance(node);
                                    if (dist > Math.min(nodeMax * nodeMax, otherNodeMax * otherNodeMax))
                                        player.sendStatusMessage(new TextComponentTranslation("thaumicaugmentation.text.impetus_link_too_far"), true);
                                    else if (!nodesPassDefaultCollisionCheck(world, node, otherNode))
                                        player.sendStatusMessage(new TextComponentTranslation("thaumicaugmentation.text.impetus_link_blocked"), true);
                                    else {
                                        node.addInput(otherNode);
                                        provider.markDirty();
                                        te.markDirty();
                                        syncAddedImpetusNodeInput(node, origin);
                                    }
                                }
                            }
                        }
                    }
                }
                else
                    player.sendStatusMessage(new TextComponentTranslation("thaumicaugmentation.text.impetus_link_same_pos"), true);
                    
                return true;
            }
        }
        else if (world.isRemote)
            return true;
        
        return false;
    }
    
    public static ConsumeResult consumeImpetusFromConnectedProviders(long amount, IImpetusConsumer dest, boolean simulate) {
        if (amount <= 0)
            return new ConsumeResult(0, Collections.emptyList());
        
        ArrayList<IImpetusProvider> providers = new ArrayList<>(dest.getGraph().findDirectProviders(dest));
        if (!providers.isEmpty()) {
            providers.sort((p1, p2) -> Long.compare(p2.provide(Long.MAX_VALUE, true), p1.provide(Long.MAX_VALUE, true)));
            if (amount < providers.size()) {
                int remove = providers.size() - (int) amount;
                for (int i = 0; i < remove; ++i)
                    providers.remove(providers.size() - 1 - i);
            }
            
            ArrayList<Deque<IImpetusNode>> paths = new ArrayList<>(providers.size());
            for (IImpetusProvider p : providers) {
                Deque<IImpetusNode> path = dest.getGraph().findPath(p, dest);
                if (path != null)
                    paths.add(path);
            }
            
            long drawn = 0;
            long step = amount / providers.size();
            long remain = amount % providers.size();
            ArrayList<Deque<IImpetusNode>> usedPaths = new ArrayList<>();
            for (int i = 0; i < providers.size(); ++i) {
                IImpetusProvider p = providers.get(i);
                long actuallyDrawn = p.provide(Math.min(step + (remain > 0 ? 1 : 0), amount - drawn), true);
                if (actuallyDrawn > 0) {
                    Deque<IImpetusNode> nodes = paths.get(i);
                    for (IImpetusNode n : nodes) {
                        actuallyDrawn = n.onTransaction(dest, nodes, actuallyDrawn, simulate);
                        if (actuallyDrawn <= 0)
                            break;
                    }
                    
                    if (actuallyDrawn > 0) {
                        actuallyDrawn = p.provide(actuallyDrawn, false);
                        usedPaths.add(nodes);
                        drawn += actuallyDrawn;
                        if (actuallyDrawn < step && i < providers.size() - 1) {
                            step = (amount - drawn) / (providers.size() - (i + 1));
                            remain = (amount - drawn) % (providers.size() - (i + 1));
                        }
                        else
                            --remain;
                    }
                    else if (i < providers.size() - 1) {
                        step = (amount - drawn) / (providers.size() - (i + 1));
                        remain = (amount - drawn) % (providers.size() - (i + 1));
                    }
                }
                else if (i < providers.size() - 1) {
                    step = (amount - drawn) / (providers.size() - (i + 1));
                    remain = (amount - drawn) % (providers.size() - (i + 1));
                }
            }
            
            return new ConsumeResult(drawn, usedPaths);
        }
        
        return new ConsumeResult(0, Collections.emptyList());
    }
    
    public static boolean nodesPassDefaultCollisionCheck(World sharedWorld, IImpetusNode node1, IImpetusNode node2) {
        Vec3d start = node1.getBeamEndpoint();
        Vec3d target = node2.getBeamEndpoint();
        boolean clear = true;
        while (!start.equals(target)) {
            RayTraceResult r = sharedWorld.rayTraceBlocks(start, target, false, true, false);
            if (r == null || node2.getLocation().getPos().equals(r.getBlockPos()))
                break;
            else if (r.getBlockPos() != null) {
                IBlockState state = sharedWorld.getBlockState(r.getBlockPos());
                if (state.isOpaqueCube() || state.getLightOpacity(sharedWorld, r.getBlockPos()) > 0) {
                    clear = false;
                    break;
                }
                else {
                    double dX = target.x - r.getBlockPos().getX();
                    double dY = target.y - r.getBlockPos().getY();
                    double dZ = target.z - r.getBlockPos().getZ();
                    start = new Vec3d(r.getBlockPos().add(dX, dY, dZ));
                }
            }
        }
        
        return clear;
    }
    
    public static void validateOutputs(World sharedWorld, IImpetusNode node) {
        HashSet<IImpetusNode> changed = new HashSet<>();
        for (IImpetusNode output : node.getOutputs()) {
            if (sharedWorld.provider.getDimension() == node.getLocation().getDimension() &&
                    sharedWorld.provider.getDimension() == output.getLocation().getDimension()) {
                
                double dist = node.getLocation().getPos().distanceSq(output.getLocation().getPos());
                if (dist > node.getMaxConnectDistance(output) * node.getMaxConnectDistance(output) ||
                        dist > output.getMaxConnectDistance(node) * output.getMaxConnectDistance(node) ||
                        !NodeHelper.nodesPassDefaultCollisionCheck(sharedWorld, node, output)) {
                
                    node.removeOutput(output);
                    changed.add(node);
                    changed.add(output);
                    NodeHelper.syncRemovedImpetusNodeOutput(node, output.getLocation());
                    NodeHelper.syncRemovedImpetusNodeInput(output, node.getLocation());
                }
            }
        }
        
        for (IImpetusNode n : changed) {
            if (n.getLocation().getDimension() == sharedWorld.provider.getDimension()) {
                TileEntity tile = sharedWorld.getTileEntity(n.getLocation().getPos());
                if (tile != null)
                    tile.markDirty();
            }
        }
    }
    
    public static void syncImpetusTransaction(Deque<IImpetusNode> path) {
        TAInternals.syncImpetusTransaction(path);
    }
    
    public static void syncAllImpetusTransactions(Collection<Deque<IImpetusNode>> paths) {
        for (Deque<IImpetusNode> path : paths)
            TAInternals.syncImpetusTransaction(path);
    }
    
    public static void syncImpetusNodeFully(IImpetusNode node) {
        TAInternals.fullySyncImpetusNode(node);
    }
    
    public static void syncAddedImpetusNodeInput(IImpetusNode node, DimensionalBlockPos input) {
        TAInternals.updateImpetusNode(node, input, false, false);
    }
    
    public static void syncAddedImpetusNodeOutput(IImpetusNode node, DimensionalBlockPos output) {
        TAInternals.updateImpetusNode(node, output, true, false);
    }
    
    public static void syncRemovedImpetusNodeInput(IImpetusNode node, DimensionalBlockPos input) {
        TAInternals.updateImpetusNode(node, input, false, true);
    }
    
    public static void syncRemovedImpetusNodeOutput(IImpetusNode node, DimensionalBlockPos output) {
        TAInternals.updateImpetusNode(node, output, true, true);
    }
    
}