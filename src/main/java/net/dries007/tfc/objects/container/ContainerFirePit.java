/*
 * Work under Copyright. Licensed under the EUPL.
 * See the project README.md and LICENSE.txt for more information.
 */

package net.dries007.tfc.objects.container;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import net.dries007.tfc.objects.inventory.slot.SlotTEInput;
import net.dries007.tfc.objects.te.TEFirePit;

import static net.dries007.tfc.objects.te.TEFirePit.*;

public class ContainerFirePit extends ContainerTE<TEFirePit>
{
    public ContainerFirePit(InventoryPlayer playerInv, TEFirePit te)
    {
        super(playerInv, te, true);
    }

    @Nonnull
    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index)
    {
        // Slot that was clicked
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack())
            return ItemStack.EMPTY;

        ItemStack stack = slot.getStack();
        ItemStack stackCopy = stack.copy();

        // Transfer out of the container
        int containerSlots = inventorySlots.size() - player.inventory.mainInventory.size();
        if (index < containerSlots)
        {
            if (!this.mergeItemStack(stack, containerSlots, inventorySlots.size(), true))
            {
                return ItemStack.EMPTY;
            }
            tile.setAndUpdateSlots(index);
        }
        // Transfer into the container
        else
        {
            // Merge into fuel slot -> item slot
            if (!this.mergeItemStack(stack, TEFirePit.SLOT_FUEL_INPUT, SLOT_ITEM_INPUT + 1, false))
            {
                return ItemStack.EMPTY;
            }
        }

        if (stack.getCount() == 0)
        {
            slot.putStack(ItemStack.EMPTY);
        }
        else
        {
            slot.onSlotChanged();
        }
        if (stack.getCount() == stackCopy.getCount())
        {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return stackCopy;
    }

    @Override
    protected void addContainerSlots()
    {
        IItemHandler inventory = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (inventory != null)
        {
            // fuel slots
            for (int i = 0; i < 4; i++)
                addSlotToContainer(new SlotTEInput(inventory, i, 8, 62 - 18 * i, tile));
            // input slot
            addSlotToContainer(new SlotTEInput(inventory, SLOT_ITEM_INPUT, 80, 20, tile));
            // output slots
            addSlotToContainer(new SlotTEInput(inventory, SLOT_OUTPUT_1, 71, 48, tile));
            addSlotToContainer(new SlotTEInput(inventory, SLOT_OUTPUT_2, 89, 48, tile));
        }
    }
}
