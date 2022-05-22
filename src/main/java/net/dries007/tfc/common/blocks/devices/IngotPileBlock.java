/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */

package net.dries007.tfc.common.blocks.devices;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.dries007.tfc.common.blocks.ExtendedBlock;
import net.dries007.tfc.common.blocks.ExtendedProperties;
import net.dries007.tfc.common.blocks.TFCBlockStateProperties;

public class IngotPileBlock extends ExtendedBlock
{
    public static final IntegerProperty COUNT = TFCBlockStateProperties.COUNT_1_64;

    private static final VoxelShape[] SHAPES = {
        box(0, 0, 0, 1, 0.125, 1),
        box(0, 0, 0, 1, 0.25, 1),
        box(0, 0, 0, 1, 0.375, 1),
        box(0, 0, 0, 1, 0.5, 1),
        box(0, 0, 0, 1, 0.625, 1),
        box(0, 0, 0, 1, 0.75, 1),
        box(0, 0, 0, 1, 0.875, 1),
        Shapes.block()
    };

    public IngotPileBlock(ExtendedProperties properties)
    {
        super(properties);

        registerDefaultState(getStateDefinition().any().setValue(COUNT, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        super.createBlockStateDefinition(builder.add(COUNT));
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPES[state.getValue(COUNT) / 8];
    }
}
