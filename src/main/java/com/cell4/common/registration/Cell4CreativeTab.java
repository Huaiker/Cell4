package com.cell4.common.registration;

import com.cell4.Cell4;
import com.cell4.common.item.IInfinityCell;
import com.cell4.common.item.InfinityItemCell;
import com.cell4.common.item.InfinityModIdCell;
import com.cell4.common.item.InfinityTagCell;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

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

                                // Cell Configurator
                                output.accept(Cell4Items.CELL_CONFIGURATOR.get());

                                // Preset: minecraft:logs tag cell
                                ItemStack logsTagCell = new ItemStack(Cell4Items.INFINITY_TAG_CELL.get());
                                InfinityTagCell.setTagNames(logsTagCell, List.of("minecraft:logs"));
                                ((IInfinityCell) Cell4Items.INFINITY_TAG_CELL.get()).setCustomName(logsTagCell, "All Logs");
                                output.accept(logsTagCell);

                                // Preset: minecraft modid cell
                                ItemStack minecraftModidCell = new ItemStack(Cell4Items.INFINITY_MODID_CELL.get());
                                InfinityModIdCell.setModIds(minecraftModidCell, List.of("minecraft"));
                                ((IInfinityCell) Cell4Items.INFINITY_MODID_CELL.get()).setCustomName(minecraftModidCell, "All Vanilla");
                                output.accept(minecraftModidCell);

                                // 6.1: Fix: minecraft:water is not a tag name, it's a fluid registry name.
                                // Use an Infinity Item Cell instead for water.
                                ItemStack waterCell = new ItemStack(Cell4Items.INFINITY_ITEM_CELL.get());
                                InfinityItemCell.setIdentifier(waterCell, "minecraft:water");
                                ((IInfinityCell) Cell4Items.INFINITY_ITEM_CELL.get()).setCustomName(waterCell, "Water");
                                output.accept(waterCell);
                            })
                            .build()
            );
}
