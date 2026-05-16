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
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

public class InfinityTagCell extends AEBaseItem implements ICellWorkbenchItem, IInfinityCell {

    public InfinityTagCell(Properties properties) {
        super(properties);
    }

    // IInfinityCell implementation
    @Override
    public CellType getCellType() { return CellType.TAG; }

    @Override
    public String translationKey() { return "item.cell4.infinity_tag_cell"; }

    @Override
    public boolean canEditTag() { return true; }

    @Override
    public boolean canEditModId() { return true; }

    // Name delegates to IInfinityCell default methods
    @Override
    public String getCustomName(ItemStack stack) { return IInfinityCell.super.getCustomName(stack); }
    @Override
    public void setCustomName(ItemStack stack, String name) { IInfinityCell.super.setCustomName(stack, name); }

    // Tag names - delegates to Cell4Util shared methods
    @NotNull
    public static List<String> getTagNames(ItemStack stack) {
        return Cell4Util.getStringList(stack, NBTKeys.TAG);
    }

    public static void setTagNames(ItemStack stack, List<String> tagNames) {
        Cell4Util.setStringList(stack, NBTKeys.TAG, tagNames);
    }

    public static void setTagName(ItemStack stack, String tagName) {
        Cell4Util.setStringValue(stack, NBTKeys.TAG, tagName);
    }

    public static boolean hasTagNames(ItemStack stack) { return !getTagNames(stack).isEmpty(); }

    // Mod IDs - delegates to Cell4Util shared methods (same as InfinityModIdCell)
    @NotNull
    public static List<String> getModIds(ItemStack stack) {
        return Cell4Util.getStringList(stack, NBTKeys.MODID);
    }

    public static void setModIds(ItemStack stack, List<String> modIds) {
        Cell4Util.setStringList(stack, NBTKeys.MODID, modIds);
    }

    public static void setModId(ItemStack stack, String modId) {
        Cell4Util.setStringValue(stack, NBTKeys.MODID, modId);
    }

    public static boolean hasModIds(ItemStack stack) { return !getModIds(stack).isEmpty(); }

    @Override
    public @NotNull Component getName(@NotNull ItemStack is) {
        return IInfinityCell.super.getDisplayName(is);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack is, @NotNull Item.TooltipContext context, @NotNull TooltipDisplay display, @NotNull Consumer<Component> tooltipAdder, @NotNull TooltipFlag adv) {
        tooltipAdder.accept(Component.translatable("tooltip.cell4.infinity").withStyle(ChatFormatting.GREEN));
        for (String tag : getTagNames(is)) {
            tooltipAdder.accept(Component.translatable("tooltip.cell4.tag_filter", tag).withStyle(ChatFormatting.AQUA));
        }
        for (String id : getModIds(is)) {
            tooltipAdder.accept(Component.translatable("tooltip.cell4.modid_filter", id).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        appendBlacklistTooltip(is, tooltipAdder);
    }

    @NotNull
    @Override
    public Optional<TooltipComponent> getTooltipImage(@NotNull ItemStack stack) {
        List<String> tagNames = getTagNames(stack);
        List<String> modIds = getModIds(stack);
        boolean hasTags = !tagNames.isEmpty();
        boolean hasModIds = !modIds.isEmpty();
        Set<String> modIdSet = Set.copyOf(modIds);

        if (!hasTags && !hasModIds) return Optional.empty();

        List<AEKey> previewKeys = new ArrayList<>();
        Cell4Util.BlacklistData blacklist = Cell4Util.getBlacklistData(stack);

        if (hasTags) {
            for (String tagName : tagNames) {
                Identifier tagRL = Identifier.tryParse(tagName);
                if (tagRL == null) continue;

                TagKey<net.minecraft.world.item.Item> itemTag = TagKey.create(BuiltInRegistries.ITEM.key(), tagRL);
                for (Holder<net.minecraft.world.item.Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(itemTag)) {
                    var key = AEItemKey.of(holder.value());
                    if (key == null || blacklist.isBlacklisted(key)) continue;
                    if (hasModIds && !Cell4Util.belongsToMod(key, modIdSet)) continue;
                    if (previewKeys.size() < 18) previewKeys.add(key);
                }

                TagKey<Fluid> fluidTag = TagKey.create(BuiltInRegistries.FLUID.key(), tagRL);
                for (Holder<Fluid> holder : BuiltInRegistries.FLUID.getTagOrEmpty(fluidTag)) {
                    Fluid fluid = holder.value();
                    if (fluid == Fluids.EMPTY) continue;
                    var key = AEFluidKey.of(fluid);
                    if (key == null || blacklist.isBlacklisted(key)) continue;
                    if (hasModIds && !Cell4Util.belongsToMod(key, modIdSet)) continue;
                    if (previewKeys.size() < 18) previewKeys.add(key);
                }
            }
        } else if (hasModIds) {
            for (var item : BuiltInRegistries.ITEM) {
                Identifier rl = BuiltInRegistries.ITEM.getKey(item);
                if (rl != null && modIdSet.contains(rl.getNamespace())) {
                    var key = AEItemKey.of(item);
                    if (key != null && !blacklist.isBlacklisted(key)) {
                        if (previewKeys.size() < 18) previewKeys.add(key);
                    }
                }
            }
        }

        if (previewKeys.isEmpty()) return Optional.empty();

        List<GenericStack> content = new ArrayList<>(previewKeys.size());
        for (AEKey key : previewKeys) {
            content.add(new GenericStack(key, IInfinityCell.getAsIntMax(key)));
        }
        return Optional.of(new StorageCellTooltipComponent(List.of(), content, false, true));
    }

    // FuzzyMode delegates to IInfinityCell default methods
    @Override
    public appeng.api.config.FuzzyMode getFuzzyMode(ItemStack itemStack) { return IInfinityCell.super.getFuzzyMode(itemStack); }
    @Override
    public void setFuzzyMode(ItemStack itemStack, appeng.api.config.FuzzyMode fuzzyMode) { IInfinityCell.super.setFuzzyMode(itemStack, fuzzyMode); }
}
