package com.cell4.common.menu;

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

    private static final int PLAYER_INV_X = 47;
    private static final int PLAYER_INV_Y = 173;

    private final Container cellContainer;
    private final Inventory playerInventory;

    public CellConfiguratorMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null);
    }

    public CellConfiguratorMenu(int containerId, Inventory inventory, net.minecraft.network.FriendlyByteBuf buf) {
        super(Cell4MenuTypes.CELL_CONFIGURATOR.get(), containerId);

        this.playerInventory = inventory;

        this.cellContainer = new SimpleContainer(1) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                return isValidCell(stack);
            }
        };

        // Cell input slot (20x20 visual, item at x=8, y=22 in GUI space)
        this.addSlot(new Slot(this.cellContainer, 0, 8, 22) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isValidCell(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        // Player main inventory (3 rows of 9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }

        // Player hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, PLAYER_INV_X + col * 18, PLAYER_INV_Y + 58));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            result = slotStack.copy();

            if (index == 0) {
                // From cell slot to player inventory
                if (!this.moveItemStackTo(slotStack, 1, 37, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // From player inventory to cell slot
                if (isValidCell(slotStack)) {
                    if (!this.moveItemStackTo(slotStack, 0, 1, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }

            if (slotStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) {
            ItemStack cellStack = this.cellContainer.getItem(0);
            if (!cellStack.isEmpty()) {
                player.getInventory().add(cellStack);
                this.cellContainer.setItem(0, ItemStack.EMPTY);
            }
        }
    }

    public ItemStack getCellInSlot() {
        return this.cellContainer.getItem(0);
    }

    public Container getCellContainer() {
        return this.cellContainer;
    }

    public static boolean canEditCell4Item(ItemStack stack) {
        return stack.getItem() instanceof InfinityItemCell;
    }

    public static boolean canEditCell4Tag(ItemStack stack) {
        return stack.getItem() instanceof InfinityTagCell;
    }

    public static boolean canEditCell4ModId(ItemStack stack) {
        return stack.getItem() instanceof InfinityTagCell || stack.getItem() instanceof InfinityModIdCell;
    }

    public static boolean canEditBlacklist(ItemStack stack) {
        return stack.getItem() instanceof InfinityItemCell
            || stack.getItem() instanceof InfinityTagCell
            || stack.getItem() instanceof InfinityModIdCell;
    }

    public static boolean isValidCell(ItemStack stack) {
        return stack.getItem() instanceof InfinityItemCell
            || stack.getItem() instanceof InfinityTagCell
            || stack.getItem() instanceof InfinityModIdCell;
    }
}
