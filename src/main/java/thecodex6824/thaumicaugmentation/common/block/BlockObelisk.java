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

package thecodex6824.thaumicaugmentation.common.block;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import thecodex6824.thaumicaugmentation.api.TABlocks;
import thecodex6824.thaumicaugmentation.api.TAItems;
import thecodex6824.thaumicaugmentation.api.block.property.IDirectionalBlock;
import thecodex6824.thaumicaugmentation.api.block.property.IObeliskPart;
import thecodex6824.thaumicaugmentation.api.block.property.IObeliskType;
import thecodex6824.thaumicaugmentation.common.block.prefab.BlockTABase;
import thecodex6824.thaumicaugmentation.common.tile.TileObelisk;
import thecodex6824.thaumicaugmentation.common.util.BitUtil;

public class BlockObelisk extends BlockTABase implements IObeliskType, IObeliskPart {

    protected static final AxisAlignedBB CAP_AABB_UP = new AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 0.8, 1.0);
    protected static final AxisAlignedBB CAP_AABB_DOWN = new AxisAlignedBB(0.0, 0.2, 0.0, 1.0, 1.0, 1.0);
    
    public BlockObelisk() {
        super(Material.ROCK);
        setHardness(-1.0F);
        setResistance(6000000.0F);
        setDefaultState(getDefaultState().withProperty(IObeliskType.OBELISK_TYPE, ObeliskType.ELDRITCH).withProperty(
                IObeliskPart.OBELISK_PART, ObeliskPart.MIDDLE));
    }
    
    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, IObeliskType.OBELISK_TYPE, IObeliskPart.OBELISK_PART, IDirectionalBlock.DIRECTION);
    }
    
    @Override
    public int getMetaFromState(IBlockState state) {
        int meta = state.getValue(IObeliskPart.OBELISK_PART).getMeta();
        meta |= (state.getValue(IObeliskType.OBELISK_TYPE).getMeta() << 2);
        return meta;
    }
    
    @Override
    public IBlockState getStateFromMeta(int meta) {
        IBlockState state = getDefaultState().withProperty(IObeliskPart.OBELISK_PART, ObeliskPart.fromMeta(BitUtil.getBits(meta, 0, 2)));
        return state.withProperty(IObeliskType.OBELISK_TYPE, ObeliskType.fromMeta(BitUtil.getBits(meta, 2, 4)));
    }
    
    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (state.getValue(IObeliskPart.OBELISK_PART) == ObeliskPart.CAP) {
            IBlockState down = world.getBlockState(pos.down());
            if (down.getPropertyKeys().contains(IObeliskPart.OBELISK_PART) && down.getValue(IObeliskPart.OBELISK_PART) == ObeliskPart.INNER)
                return state.withProperty(IDirectionalBlock.DIRECTION, EnumFacing.UP);
            else
                return state.withProperty(IDirectionalBlock.DIRECTION, EnumFacing.DOWN);
        }
        else if (state.getValue(IObeliskPart.OBELISK_PART) == ObeliskPart.INNER) {
            IBlockState down = world.getBlockState(pos.down());
            if (down.getPropertyKeys().contains(IObeliskPart.OBELISK_PART) && down.getValue(IObeliskPart.OBELISK_PART) == ObeliskPart.MIDDLE)
                return state.withProperty(IDirectionalBlock.DIRECTION, EnumFacing.DOWN);
            else
                return state.withProperty(IDirectionalBlock.DIRECTION, EnumFacing.UP);
        }
        
        return state;
    }
    
    @Override
    public ItemStack getItem(World world, BlockPos pos, IBlockState state) {
        return new ItemStack(TAItems.OBELISK_PLACER, 1, state.getValue(IObeliskType.OBELISK_TYPE).getMeta());
    }
    
    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos fromPos) {
        IBlockState d = world.getBlockState(pos.down());
        IBlockState u = world.getBlockState(pos.up());
        ObeliskPart down = d.getBlock() == TABlocks.OBELISK ? d.getValue(IObeliskPart.OBELISK_PART) : null;
        ObeliskPart up = u.getBlock() == TABlocks.OBELISK ? u.getValue(IObeliskPart.OBELISK_PART) : null;
        ObeliskPart check = state.getValue(IObeliskPart.OBELISK_PART);
        if (check == ObeliskPart.CAP) {
            if (down != ObeliskPart.INNER && up != ObeliskPart.INNER)
                world.destroyBlock(pos, false);
        }
        else if (check == ObeliskPart.INNER) {
            if ((down != ObeliskPart.CAP || up != ObeliskPart.MIDDLE) && (down != ObeliskPart.MIDDLE || up != ObeliskPart.CAP))
                world.destroyBlock(pos, false);
        }
        else {
            if (down != ObeliskPart.INNER || up != ObeliskPart.INNER)
                world.destroyBlock(pos, false);
        }
    }
    
    @Override
    public int getLightOpacity(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.getValue(IObeliskPart.OBELISK_PART) == ObeliskPart.CAP ? 0 : super.getLightOpacity(state, world, pos);
    }
    
    @Override
    @SuppressWarnings("deprecation")
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        if (state.getValue(IObeliskPart.OBELISK_PART) == ObeliskPart.CAP) {
            IBlockState up = source.getBlockState(pos.up());
            if (up.getBlock() == TABlocks.OBELISK && up.getValue(IObeliskPart.OBELISK_PART) == ObeliskPart.INNER)
                return CAP_AABB_DOWN;
            else
                return CAP_AABB_UP;
        }
        else
            return super.getBoundingBox(state, source, pos);
    }
    
    @Override
    public boolean canEntityDestroy(IBlockState state, IBlockAccess world, BlockPos pos, Entity entity) {
        return false;
    }
    
    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        if (state.getValue(IObeliskPart.OBELISK_PART) == ObeliskPart.MIDDLE ||
                state.getValue(IObeliskPart.OBELISK_PART) == ObeliskPart.INNER)
            return BlockFaceShape.SOLID;
        else
            return BlockFaceShape.UNDEFINED;
    }
    
    @Override
    public boolean isNormalCube(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.getValue(IObeliskPart.OBELISK_PART) != ObeliskPart.CAP;
    }
    
    @Override
    public boolean isBlockNormalCube(IBlockState state) {
        return state.getValue(IObeliskPart.OBELISK_PART) != ObeliskPart.CAP;
    }
    
    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return state.getValue(IObeliskPart.OBELISK_PART) != ObeliskPart.CAP;
    }
    
    @Override
    public boolean isFullBlock(IBlockState state) {
        return state.getValue(IObeliskPart.OBELISK_PART) != ObeliskPart.CAP;
    }
    
    @Override
    public boolean isFullCube(IBlockState state) {
        return state.getValue(IObeliskPart.OBELISK_PART) != ObeliskPart.CAP;
    }
    
    @Override
    public boolean isTranslucent(IBlockState state) {
        return true;
    }
    
    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.IGNORE;
    }
    
    @Override
    public boolean hasTileEntity(IBlockState state) {
        return state.getValue(IObeliskPart.OBELISK_PART) == ObeliskPart.MIDDLE;
    }
    
    @Override
    @Nullable
    public TileEntity createTileEntity(World world, IBlockState state) {
        if (state.getValue(IObeliskPart.OBELISK_PART) == ObeliskPart.MIDDLE)
            return new TileObelisk();
        
        return null;
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.TRANSLUCENT;
    }
    
}