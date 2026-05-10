package com.cell4.common.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

public class CellConfiguratorMenuProvider implements MenuProvider {
    @Override
    public Component getDisplayName() {
        return Component.translatable("item.cell4.cell_configurator");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new com.cell4.common.menu.CellConfiguratorMenu(containerId, inventory);
    }
}
