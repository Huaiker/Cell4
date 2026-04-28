package com.cell4.common.registration;

import com.cell4.Cell4;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class Cell4CreativeTab {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Cell4.MODID);

    public static final RegistryObject<CreativeModeTab> CELL4_TAB =
            CREATIVE_TABS.register("cell4_tab", () ->
                    CreativeModeTab.builder()
                            .icon(() -> new ItemStack(Cell4Items.INFINITY_ITEM_CELL.get()))
                            .title(Component.translatable("itemGroup.cell4"))
                            .displayItems((parameters, output) -> {
                                output.accept(Cell4Items.INFINITY_ITEM_CELL.get());
                                output.accept(Cell4Items.INFINITY_TAG_CELL.get());
                                output.accept(Cell4Items.INFINITY_MODID_CELL.get());
                            })
                            .build()
            );
}
