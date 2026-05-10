package com.cell4.common.registration;

import com.cell4.Cell4;
import com.cell4.common.item.InfinityModIdCell;
import com.cell4.common.item.InfinityTagCell;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public class Cell4CreativeTab {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Cell4.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CELL4_TAB =
            CREATIVE_TABS.register("cell4_tab", () ->
                    CreativeModeTab.builder()
                            .icon(() -> new ItemStack(Cell4Items.INFINITY_ITEM_CELL.get()))
                            .title(Component.translatable("itemGroup.cell4"))
                            .displayItems((parameters, output) -> {
                                output.accept(Cell4Items.INFINITY_ITEM_CELL.get());
                                output.accept(Cell4Items.INFINITY_TAG_CELL.get());
                                output.accept(Cell4Items.INFINITY_MODID_CELL.get());
                                output.accept(Cell4Items.CELL_CONFIGURATOR.get());
                                
                                // Preset: minecraft:logs tag cell
                                ItemStack logsTagCell = new ItemStack(Cell4Items.INFINITY_TAG_CELL.get());
                                InfinityTagCell.setTagNames(logsTagCell, List.of("minecraft:logs"));
                                output.accept(logsTagCell);
                                
                                // Preset: minecraft modid cell
                                ItemStack minecraftModidCell = new ItemStack(Cell4Items.INFINITY_MODID_CELL.get());
                                InfinityModIdCell.setModIds(minecraftModidCell, List.of("minecraft"));
                                output.accept(minecraftModidCell);
                                
                                // Preset: fluid tag cell (water)
                                ItemStack fluidTagCell = new ItemStack(Cell4Items.INFINITY_TAG_CELL.get());
                                InfinityTagCell.setTagNames(fluidTagCell, List.of("minecraft:water"));
                                output.accept(fluidTagCell);
                            })
                            .build()
            );
}
