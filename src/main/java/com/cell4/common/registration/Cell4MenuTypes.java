package com.cell4.common.registration;

import com.cell4.Cell4;
import com.cell4.common.menu.CellConfiguratorMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class Cell4MenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, Cell4.MODID);

    public static final RegistryObject<MenuType<CellConfiguratorMenu>> CELL_CONFIGURATOR =
            MENU_TYPES.register("cell_configurator", () ->
                IForgeMenuType.create(CellConfiguratorMenu::new));
}
