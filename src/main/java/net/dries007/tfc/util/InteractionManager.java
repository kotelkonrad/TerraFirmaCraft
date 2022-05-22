/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */

package net.dries007.tfc.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

import net.dries007.tfc.client.TFCSounds;
import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blockentities.TFCBlockEntities;
import net.dries007.tfc.common.blocks.*;
import net.dries007.tfc.common.blocks.devices.IngotPileBlock;
import net.dries007.tfc.common.blocks.devices.SheetPileBlock;
import net.dries007.tfc.common.container.ItemStackContainerProvider;
import net.dries007.tfc.common.container.TFCContainerProviders;
import net.dries007.tfc.common.recipes.ScrapingRecipe;
import net.dries007.tfc.common.recipes.inventory.ItemStackInventory;
import net.dries007.tfc.util.collections.IndirectHashCollection;
import net.dries007.tfc.util.events.StartFireEvent;

/**
 * This exists due to problems in handling right click events
 * Forge provides a right click block event. This works for intercepting would-be calls to {@link BlockState#use(Level, Player, InteractionHand, BlockHitResult)}
 * However, this cannot be used (maintaining vanilla behavior) for item usages, or calls to {@link ItemStack#onItemUse(UseOnContext, Function)}, as the priority of those two behaviors are very different (blocks take priority, cancelling the event with an item behavior forces the item to take priority
 *
 * This is in lieu of a system such as https://github.com/MinecraftForge/MinecraftForge/pull/6615
 */
public final class InteractionManager
{
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);
    private static final List<Entry> ACTIONS = new ArrayList<>();
    private static final IndirectHashCollection<Item, Entry> CACHE = IndirectHashCollection.create(wrapper -> wrapper.keyExtractor.get(), () -> ACTIONS);

    public static void registerDefaultInteractions()
    {
        register(TFCTags.Items.THATCH_BED_HIDES, false, (stack, context) -> {
            final Level level = context.getLevel();
            final Player player = context.getPlayer();
            if (!level.isClientSide() && player != null)
            {
                final BlockPos basePos = context.getClickedPos();
                final BlockState baseState = level.getBlockState(basePos);
                final Direction facing = context.getHorizontalDirection();
                final BlockState bed = TFCBlocks.THATCH_BED.get().defaultBlockState();
                for (Direction direction : new Direction[] {facing, facing.getClockWise(), facing.getOpposite(), facing.getCounterClockWise()})
                {
                    final BlockPos headPos = basePos.relative(direction, 1);
                    final BlockState headState = level.getBlockState(headPos);
                    if (Helpers.isBlock(baseState, TFCTags.Blocks.THATCH_BED_THATCH) && Helpers.isBlock(headState, TFCTags.Blocks.THATCH_BED_THATCH))
                    {
                        final BlockPos playerPos = player.blockPosition();
                        if (playerPos != headPos && playerPos != basePos)
                        {
                            level.setBlock(basePos, bed.setValue(ThatchBedBlock.PART, BedPart.FOOT).setValue(ThatchBedBlock.FACING, direction), 18);
                            level.setBlock(headPos, bed.setValue(ThatchBedBlock.PART, BedPart.HEAD).setValue(ThatchBedBlock.FACING, direction.getOpposite()), 18);
                            level.getBlockEntity(headPos, TFCBlockEntities.THATCH_BED.get()).ifPresent(entity -> entity.setBed(headState, baseState, stack.split(1)));
                            return InteractionResult.SUCCESS;
                        }

                    }
                }
            }
            return InteractionResult.FAIL;
        });

        register(TFCTags.Items.STARTS_FIRES_WITH_DURABILITY, false, (stack, context) -> {
            final Player player = context.getPlayer();
            final Level level = context.getLevel();
            final BlockPos pos = context.getClickedPos();
            if (player != null && StartFireEvent.startFire(level, pos, level.getBlockState(pos), context.getClickedFace(), player, stack))
            {
                level.playSound(player, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.4F + 0.8F);
                if (!player.isCreative())
                {
                    stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(context.getHand()));
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });

        register(TFCTags.Items.STARTS_FIRES_WITH_ITEMS, false, (stack, context) -> {
            final Player playerEntity = context.getPlayer();
            if (playerEntity instanceof final ServerPlayer player)
            {
                final Level level = context.getLevel();
                final BlockPos pos = context.getClickedPos();
                if (!player.isCreative())
                    stack.shrink(1);
                if (StartFireEvent.startFire(level, pos, level.getBlockState(pos), context.getClickedFace(), player, stack))
                    return InteractionResult.SUCCESS;
            }
            return InteractionResult.FAIL;
        });

        register(Items.SNOW, false, (stack, context) -> {
            Player player = context.getPlayer();
            if (player != null && !player.getAbilities().mayBuild)
            {
                return InteractionResult.PASS;
            }
            else
            {
                final BlockPlaceContext blockContext = new BlockPlaceContext(context);
                final Level level = blockContext.getLevel();
                final BlockPos pos = blockContext.getClickedPos();
                final BlockState stateAt = level.getBlockState(blockContext.getClickedPos());
                if (SnowPileBlock.canPlaceSnowPile(level, pos, stateAt))
                {
                    SnowPileBlock.placeSnowPile(level, pos, stateAt, true);
                    final BlockState placedState = level.getBlockState(pos);
                    final SoundType placementSound = placedState.getSoundType(level, pos, player);
                    level.playSound(player, pos, placedState.getSoundType(level, pos, player).getPlaceSound(), SoundSource.BLOCKS, (placementSound.getVolume() + 1.0F) / 2.0F, placementSound.getPitch() * 0.8F);
                    if (player == null || !player.getAbilities().instabuild)
                    {
                        stack.shrink(1);
                    }

                    InteractionResult result = InteractionResult.sidedSuccess(level.isClientSide);
                    if (player != null && result.consumesAction())
                    {
                        player.awardStat(Stats.ITEM_USED.get(Items.SNOW));
                    }
                    return result;
                }

                // Default behavior
                // Handles layering behavior of both snow piles and snow layers via the blocks replacement / getStateForPlacement
                if (Items.SNOW instanceof BlockItem blockItem)
                {
                    return blockItem.place(blockContext);
                }
                return InteractionResult.FAIL;
            }
        });

        register(Items.CHARCOAL, false, (stack, context) -> {
            Player player = context.getPlayer();
            if (player != null && !player.getAbilities().mayBuild)
            {
                return InteractionResult.PASS;
            }
            else
            {
                final Level level = context.getLevel();
                final BlockPos pos = context.getClickedPos();
                final BlockState stateAt = level.getBlockState(pos);
                final Block pile = TFCBlocks.CHARCOAL_PILE.get();
                if (player != null && (player.blockPosition().equals(pos) || (player.blockPosition().equals(pos.above()) && Helpers.isBlock(stateAt, pile) && stateAt.getValue(CharcoalPileBlock.LAYERS) == 8)))
                {
                    return InteractionResult.FAIL;
                }
                if (Helpers.isBlock(stateAt, pile))
                {
                    int layers = stateAt.getValue(CharcoalPileBlock.LAYERS);
                    if (layers != 8)
                    {
                        stack.shrink(1);
                        level.setBlockAndUpdate(pos, stateAt.setValue(CharcoalPileBlock.LAYERS, layers + 1));
                        Helpers.playSound(level, pos, TFCSounds.CHARCOAL_PILE_PLACE.get());
                        return InteractionResult.SUCCESS;
                    }
                }
                if (level.isEmptyBlock(pos.above()) && stateAt.isFaceSturdy(level, pos, Direction.UP))
                {
                    stack.shrink(1);
                    level.setBlockAndUpdate(pos.above(), pile.defaultBlockState());
                    Helpers.playSound(level, pos, TFCSounds.CHARCOAL_PILE_PLACE.get());
                    return InteractionResult.SUCCESS;
                }
                return InteractionResult.FAIL;
            }
        });

        // Log pile creation and insertion.
        // Note: sneaking will always bypass the log pile block onUse method - that is why we have to handle some insertion here.
        // - holding log, targeting block, shift click = place log pile
        // - holding log, targeting log pile, shift click = insert all
        // - holding log, targeting log pile, click normally = insert one
        final BlockItemPlacement logPilePlacement = new BlockItemPlacement(() -> Items.AIR, TFCBlocks.LOG_PILE);
        register(TFCTags.Items.LOG_PILE_LOGS, false, (stack, context) -> {
            final Player player = context.getPlayer();
            if (player != null && player.isShiftKeyDown())
            {
                final Level level = context.getLevel();
                final Direction direction = context.getClickedFace();
                final BlockPos posClicked = context.getClickedPos();
                final BlockState stateClicked = level.getBlockState(posClicked);
                final BlockPos relativePos = posClicked.relative(direction);

                // If we're targeting a log pile, we can do one of two insertion operations
                if (Helpers.isBlock(stateClicked, TFCBlocks.LOG_PILE.get()))
                {
                    return level.getBlockEntity(posClicked, TFCBlockEntities.LOG_PILE.get())
                        .flatMap(entity -> entity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY).map(t -> t))
                        .map(cap -> {
                            ItemStack insertStack = stack.copy();
                            insertStack = Helpers.insertAllSlots(cap, insertStack);
                            if (insertStack.getCount() < stack.getCount()) // Some logs were inserted
                            {
                                if (!level.isClientSide())
                                {
                                    Helpers.playSound(level, relativePos, SoundEvents.WOOD_PLACE);
                                    stack.setCount(insertStack.getCount());
                                }
                                return InteractionResult.SUCCESS;
                            }

                            // if we placed instead, insert logs at the RELATIVE position using the mutated stack
                            final InteractionResult result = logPilePlacement.onItemUse(stack, context);
                            if (result.consumesAction())
                            {
                                // shrinking is handled by the item placement
                                Helpers.insertOne(level, relativePos, TFCBlockEntities.LOG_PILE.get(), insertStack);
                            }
                            return result;
                        }).orElse(InteractionResult.PASS);
                }
                else if (!level.getBlockState(relativePos.below()).isAir())
                {
                    // when placing against a non-pile block
                    final InteractionResult result = logPilePlacement.onItemUse(stack, context);
                    if (result.consumesAction())
                    {
                        // shrinking is handled by the item placement
                        Helpers.insertOne(level, relativePos, TFCBlockEntities.LOG_PILE.get(), stack);
                    }
                    return result;
                }
            }
            return InteractionResult.PASS;
        });

        register(TFCTags.Items.SCRAPABLE, false, (stack, context) -> {
            Level level = context.getLevel();
            ScrapingRecipe recipe = ScrapingRecipe.getRecipe(level, new ItemStackInventory(stack));
            if (recipe != null)
            {
                final BlockPos pos = context.getClickedPos();
                final BlockPos abovePos = pos.above();
                Player player = context.getPlayer();
                if (player != null && context.getClickedFace() == Direction.UP && Helpers.isBlock(level.getBlockState(pos), TFCTags.Blocks.SCRAPING_SURFACE) && level.getBlockState(abovePos).isAir())
                {
                    level.setBlockAndUpdate(abovePos, TFCBlocks.SCRAPING.get().defaultBlockState());
                    level.getBlockEntity(abovePos, TFCBlockEntities.SCRAPING.get())
                        .map(entity -> entity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY).map(cap -> {
                            if (!level.isClientSide)
                            {
                                ItemStack insertStack = stack.split(1);
                                stack.setCount(stack.getCount() + cap.insertItem(0, insertStack, false).getCount());
                                entity.setCachedItem(recipe.getResultItem().copy());
                            }
                            return InteractionResult.SUCCESS;
                        }).orElse(InteractionResult.PASS));
                }
            }
            return InteractionResult.PASS;
        });

        // BlockItem mechanics for vanilla items that match groundcover types
        for (GroundcoverBlockType type : GroundcoverBlockType.values())
        {
            if (type.getVanillaItem() != null)
            {
                register(new BlockItemPlacement(type.getVanillaItem(), TFCBlocks.GROUNDCOVER.get(type)));
            }
        }

        // Knapping
        register(TFCTags.Items.CLAY_KNAPPING, true, createKnappingInteraction((stack, player) -> stack.getCount() >= 5, TFCContainerProviders.CLAY_KNAPPING));
        register(TFCTags.Items.FIRE_CLAY_KNAPPING, true, createKnappingInteraction((stack, player) -> stack.getCount() >= 5, TFCContainerProviders.FIRE_CLAY_KNAPPING));
        register(TFCTags.Items.LEATHER_KNAPPING, true, createKnappingInteraction((stack, player) -> player.getInventory().contains(TFCTags.Items.KNIVES), TFCContainerProviders.LEATHER_KNAPPING));
        register(TFCTags.Items.ROCK_KNAPPING, true, createKnappingInteraction((stack, player) -> stack.getCount() >= 2, TFCContainerProviders.ROCK_KNAPPING));

        // Piles (Ingots + Sheets)
        // Shift + Click = Add to pile (either on the targeted pile, or create a new one)
        // Removal (Non-Shift Click) is handled by the respective pile block
        final BlockItemPlacement ingotPilePlacement = new BlockItemPlacement(() -> Items.AIR, TFCBlocks.INGOT_PILE);
        register(TFCTags.Items.PILEABLE_INGOTS, false, (stack, context) -> {
            final Player player = context.getPlayer();
            if (player != null && player.isShiftKeyDown())
            {
                final Level level = context.getLevel();
                final Direction direction = context.getClickedFace();
                final BlockPos posClicked = context.getClickedPos();
                final BlockState stateClicked = level.getBlockState(posClicked);
                final BlockPos relativePos = posClicked.relative(direction);

                if (Helpers.isBlock(stateClicked, TFCBlocks.INGOT_PILE.get()))
                {
                    // We clicked on an ingot pile, so attempt to add to the pile
                    final int currentIngots = stateClicked.getValue(IngotPileBlock.COUNT);
                    if (currentIngots < 64)
                    {
                        final ItemStack insertStack = stack.split(1);

                        level.setBlock(posClicked, stateClicked.setValue(IngotPileBlock.COUNT, currentIngots + 1), Block.UPDATE_CLIENTS);
                        level.getBlockEntity(posClicked, TFCBlockEntities.INGOT_PILE.get()).ifPresent(pile -> pile.addIngot(insertStack));
                        return InteractionResult.SUCCESS;
                    }
                    else
                    {
                        // todo: Handle ingot piles adding to the top of the stack
                        return InteractionResult.FAIL;
                    }
                }
                else
                {
                    // We clicked on a non-ingot pile, so we want to try and place an ingot pile at the current location.
                    final ItemStack stackBefore = stack.copy();
                    final InteractionResult result = ingotPilePlacement.onItemUse(stack, context);
                    if (result.consumesAction())
                    {
                        // Shrinking is already handled by the placement onItemUse() call, we just need to insert the stack
                        stackBefore.setCount(1);
                        level.getBlockEntity(relativePos, TFCBlockEntities.INGOT_PILE.get()).ifPresent(pile -> pile.addIngot(stackBefore));
                    }
                    return result;
                }
            }
            return InteractionResult.PASS;
        });

        final BlockItemPlacement sheetPilePlacement = new BlockItemPlacement(() -> Items.AIR, TFCBlocks.SHEET_PILE);
        register(TFCTags.Items.PILEABLE_SHEETS, false, (stack, context) -> {
            final Player player = context.getPlayer();
            if (player != null && player.isShiftKeyDown())
            {
                final Level level = context.getLevel();
                final Direction clickedFace = context.getClickedFace(); // i.e. click on UP
                final Direction sheetFace = clickedFace.getOpposite(); // i.e. place on DOWN
                final BlockPos relativePos = context.getClickedPos().relative(clickedFace);
                final BlockState relativeState = level.getBlockState(relativePos);

                final BlockPlaceContext blockContext = new BlockPlaceContext(context);
                final BooleanProperty property = DirectionPropertyBlock.getProperty(sheetFace);

                if (blockContext.replacingClickedOnBlock())
                {
                    // todo: this causes weird behavior, how to handle properly?
                    return InteractionResult.FAIL;
                }

                // Sheets behave differently than ingots, because we need to check the targeted face if it's empty or not
                // We assume immediately that we want to target the relative pos and state
                if (Helpers.isBlock(relativeState, TFCBlocks.SHEET_PILE.get()))
                {
                    // We targeted a existing sheet pile, so we need to check if there's an empty space for it
                    if (!relativeState.getValue(property))
                    {
                        // Add to an existing sheet pile
                        final ItemStack insertStack = stack.split(1);
                        SheetPileBlock.addSheet(level, relativePos, relativeState, sheetFace, insertStack);
                        return InteractionResult.SUCCESS;
                    }
                    else
                    {
                        // No space
                        return InteractionResult.FAIL;
                    }
                }
                else
                {
                    // Want to place a new sheet at the above location
                    final BlockState placingState = TFCBlocks.SHEET_PILE.get().defaultBlockState().setValue(property, true);
                    if (BlockItemPlacement.canPlace(blockContext, placingState))
                    {
                        final ItemStack insertStack = stack.split(1);
                        SheetPileBlock.addSheet(level, relativePos, placingState, sheetFace, insertStack);
                        return InteractionResult.SUCCESS;
                    }
                }
            }
            return InteractionResult.PASS;
        });
    }

    /**
     * Register an interaction. This method is safe to call during parallel mod loading.
     */
    public static void register(BlockItemPlacement wrapper)
    {
        register(new Entry(wrapper, stack -> stack.getItem() == wrapper.getItem(), () -> Collections.singleton(wrapper.getItem()), false));
    }

    /**
     * Register an interaction. This method is safe to call during parallel mod loading.
     */
    public static void register(Item item, boolean targetAir, OnItemUseAction action)
    {
        register(new Entry(action, stack -> stack.getItem() == item, () -> Collections.singleton(item), targetAir));
    }

    /**
     * Register an interaction. This method is safe to call during parallel mod loading.
     */
    public static void register(TagKey<Item> tag, boolean targetAir, OnItemUseAction action)
    {
        register(new Entry(action, stack -> Helpers.isItem(stack.getItem(), tag), () -> Helpers.getAllTagValues(tag, ForgeRegistries.ITEMS), targetAir));
    }

    public static OnItemUseAction createKnappingInteraction(BiPredicate<ItemStack, Player> condition, ItemStackContainerProvider container)
    {
        return (stack, context) -> {
            final Player player = context.getPlayer();
            if (player != null && condition.test(stack, player))
            {
                if (player instanceof ServerPlayer serverPlayer)
                {
                    NetworkHooks.openGui(serverPlayer, container.of(stack, context.getHand()), ItemStackContainerProvider.write(context.getHand()));
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        };
    }

    public static Optional<InteractionResult> onItemUse(ItemStack stack, UseOnContext context, boolean isTargetingAir)
    {
        if (!ACTIVE.get())
        {
            for (Entry entry : CACHE.getAll(stack.getItem()))
            {
                if ((entry.targetAir() || !isTargetingAir) && entry.test().test(stack))
                {
                    InteractionResult result;
                    ACTIVE.set(true);
                    try
                    {
                        result = entry.action().onItemUse(stack, context);
                    }
                    finally
                    {
                        ACTIVE.set(false);
                    }
                    return result == InteractionResult.PASS ? Optional.empty() : Optional.of(result);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Register an interaction. This method is safe to call during parallel mod loading.
     */
    private static synchronized void register(Entry entry)
    {
        ACTIONS.add(entry);
    }

    /**
     * Return {@link InteractionResult#PASS} to allow normal right click handling
     */
    @FunctionalInterface
    public interface OnItemUseAction
    {
        InteractionResult onItemUse(ItemStack stack, UseOnContext context);
    }

    private record Entry(OnItemUseAction action, Predicate<ItemStack> test, Supplier<Iterable<Item>> keyExtractor, boolean targetAir) {}
}
