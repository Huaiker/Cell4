package com.cell4.common.registration;

import com.cell4.Cell4;
import com.cell4.common.menu.CellConfiguratorMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class Cell4MenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.MENU, Cell4.MODID);
    
    public static final DeferredHolder<MenuType<?>, MenuType<CellConfiguratorMenu>> CELL_CONFIGURATOR =
            MENU_TYPES.register("cell_configurator", () -> 
                IMenuTypeExtension.create(CellConfiguratorMenu::new));
}
