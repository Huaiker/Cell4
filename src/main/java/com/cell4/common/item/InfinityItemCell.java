package com.cell4.common.item;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.items.AEBaseItem;
import appeng.items.storage.StorageCellTooltipComponent;
import com.cell4.common.util.Cell4Util;
import com.cell4.common.util.NBTKeys;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class InfinityItemCell extends AEBaseItem implements ICellWorkbenchItem, IInfinityCell {

    public InfinityItemCell(Properties properties) {
        super(properties);
    }

    // IInfinityCell implementation
    @Override
    public CellType getCellType() { return CellType.ITEM; }

    @Override
    public String translationKey() { return "item.cell4.infinity_item_cell"; }

    @Override
    public boolean canEditItem() { return true; }

    // Name delegates to IInfinityCell default methods
    @Override
    public String getCustomName(ItemStack stack) { return IInfinityCell.super.getCustomName(stack); }
    @Override
    public void setCustomName(ItemStack stack, String name) { IInfinityCell.super.setCustomName(stack, name); }

    @NotNull
    public static List<String> getIdentifiers(ItemStack stack) {
        return Cell4Util.getStringList(stack, NBTKeys.ITEM);
    }

    @NotNull
    public static List<AEKey> getRecords(ItemStack stack) {
        List<String> ids = getIdentifiers(stack);
        if (ids.isEmpty()) return Collections.emptyList();
        List<AEKey> keys = new ArrayList<>(ids.size());
        for (String id : ids) {
            Identifier rl = Identifier.tryParse(id);
            if (rl != null) {
                var item = BuiltInRegistries.ITEM.getOptional(rl);
                if (item.isPresent()) { keys.add(AEItemKey.of(item.get())); continue; }
                var fluid = BuiltInRegistries.FLUID.getOptional(rl);
                if (fluid.isPresent() && fluid.get() != Fluids.EMPTY) {
                    keys.add(AEFluidKey.of(fluid.get()));
                }
            }
        }
        return keys;
    }

    public static void setIdentifiers(ItemStack stack, List<String> ids) {
        Cell4Util.setStringList(stack, NBTKeys.ITEM, ids);
    }

    public static void setIdentifier(ItemStack stack, String id) {
        Cell4Util.setStringValue(stack, NBTKeys.ITEM, id);
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack is) {
        return IInfinityCell.super.getDisplayName(is);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack is, @NotNull Item.TooltipContext context, @NotNull TooltipDisplay display, @NotNull Consumer<Component> tooltipAdder, @NotNull TooltipFlag adv) {
        tooltipAdder.accept(Component.translatable("tooltip.cell4.infinity").withStyle(ChatFormatting.GREEN));
        List<String> ids = getIdentifiers(is);
        if (ids.size() > 1) {
            tooltipAdder.accept(Component.translatable("tooltip.cell4.item_count", ids.size()).withStyle(ChatFormatting.AQUA));
        }
        appendBlacklistTooltip(is, tooltipAdder);
    }

    @NotNull
    @Override
    public Optional<TooltipComponent> getTooltipImage(@NotNull ItemStack stack) {
        List<AEKey> records = getRecords(stack);
        Cell4Util.BlacklistData blacklist = Cell4Util.getBlacklistData(stack);
        if (records.isEmpty()) return Optional.empty();
        List<GenericStack> content = new ArrayList<>(records.size());
        for (AEKey key : records) {
            if (!blacklist.isBlacklisted(key)) {
                content.add(new GenericStack(key, IInfinityCell.getAsIntMax(key)));
            }
        }
        if (content.isEmpty()) return Optional.empty();
        return Optional.of(new StorageCellTooltipComponent(List.of(), content, false, true));
    }

    // FuzzyMode delegates to IInfinityCell default methods
    @Override
    public appeng.api.config.FuzzyMode getFuzzyMode(ItemStack itemStack) { return IInfinityCell.super.getFuzzyMode(itemStack); }
    @Override
    public void setFuzzyMode(ItemStack itemStack, appeng.api.config.FuzzyMode fuzzyMode) { IInfinityCell.super.setFuzzyMode(itemStack, fuzzyMode); }
}
