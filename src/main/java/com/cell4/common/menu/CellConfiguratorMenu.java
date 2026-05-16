package com.cell4.common.menu;

import com.cell4.common.item.IInfinityCell;
import com.cell4.common.item.InfinityItemCell;
import com.cell4.common.item.InfinityModIdCell;
import com.cell4.common.item.InfinityTagCell;
import com.cell4.common.registration.Cell4MenuTypes;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class CellConfiguratorMenu extends AbstractContainerMenu {

    private static final int PLAYER_INV_X = 64;
    private static final int PLAYER_INV_Y = 192;

    private final Container cellContainer;
    private final Inventory playerInventory;

    public CellConfiguratorMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null);
    }

    public CellConfiguratorMenu(int containerId, Inventory inventory, net.minecraft.network.RegistryFriendlyByteBuf buf) {
        super(Cell4MenuTypes.CELL_CONFIGURATOR.get(), containerId);
        this.playerInventory = inventory;

        this.cellContainer = new SimpleContainer(1) {
            @Override
            public int getMaxStackSize() { return 1; }

            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                return isValidCell(stack);
            }
        };

        this.addSlot(new Slot(this.cellContainer, 0, 9, 23) {
            @Override
            public boolean mayPlace(ItemStack stack) { return isValidCell(stack); }
            @Override
            public int getMaxStackSize() { return 1; }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, PLAYER_INV_X + col * 18, PLAYER_INV_Y + 58));
        }
    }

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            result = slotStack.copy();
            if (index == 0) {
                if (!this.moveItemStackTo(slotStack, 1, 37, true)) return ItemStack.EMPTY;
            } else {
                if (isValidCell(slotStack)) {
                    if (!this.moveItemStackTo(slotStack, 0, 1, false)) return ItemStack.EMPTY;
                } else {
                    return ItemStack.EMPTY; // Not a valid cell - prevent infinite loop
                }
            }
            if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return result;
    }

    // 6.4: Fix item loss when player inventory is full
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) {
            ItemStack cellStack = this.cellContainer.getItem(0);
            if (!cellStack.isEmpty()) {
                // Try to add to player inventory first
                if (!player.getInventory().add(cellStack)) {
                    // If inventory is full, drop the item at player's location
                    player.drop(cellStack, false);
                }
                this.cellContainer.setItem(0, ItemStack.EMPTY);
            }
        }
    }

    public ItemStack getCellInSlot() { return this.cellContainer.getItem(0); }
    public Container getCellContainer() { return this.cellContainer; }

    // 3.2: Use IInfinityCell interface methods instead of instanceof
    public static boolean canEditCell4Item(ItemStack stack) {
        if (stack.getItem() instanceof IInfinityCell cell) return cell.canEditItem();
        return false;
    }

    public static boolean canEditCell4Tag(ItemStack stack) {
        if (stack.getItem() instanceof IInfinityCell cell) return cell.canEditTag();
        return false;
    }

    public static boolean canEditCell4ModId(ItemStack stack) {
        if (stack.getItem() instanceof IInfinityCell cell) return cell.canEditModId();
        return false;
    }

    public static boolean canEditBlacklist(ItemStack stack) {
        if (stack.getItem() instanceof IInfinityCell cell) return cell.canEditBlacklist();
        return false;
    }

    public static boolean canEditName(ItemStack stack) {
        if (stack.getItem() instanceof IInfinityCell cell) return cell.canEditName();
        return false;
    }

    public static boolean isValidCell(ItemStack stack) {
        return stack.getItem() instanceof IInfinityCell;
    }
}
