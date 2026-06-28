package com.cell4.common.item;

import appeng.api.config.FuzzyMode;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class InfinityModIdCell extends AEBaseItem implements ICellWorkbenchItem, IInfinityCell {

    public InfinityModIdCell(Properties properties) {
        super(properties.stacksTo(1));
    }

    // IInfinityCell implementation
    @Override
    public CellType getCellType() { return CellType.MODID; }

    @Override
    public String translationKey() { return "item.cell4.infinity_modid_cell"; }

    @Override
    public boolean canEditModId() { return true; }

    @Override
    public String getCustomName(ItemStack stack) { return IInfinityCell.super.getCustomName(stack); }

    @Override
    public void setCustomName(ItemStack stack, String name) { IInfinityCell.super.setCustomName(stack, name); }

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
    public void appendHoverText(@NotNull ItemStack is, @NotNull Item.TooltipContext context, @NotNull List<Component> lines, @NotNull TooltipFlag adv) {
        lines.add(Component.translatable("tooltip.cell4.infinity").withStyle(ChatFormatting.GREEN));
        List<String> modIds = getModIds(is);
        for (String id : modIds) {
            lines.add(Component.translatable("tooltip.cell4.modid_filter", id).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        int total = 0;
        Set<String> modIdSet = Set.copyOf(modIds);
        Cell4Util.BlacklistData blacklist = Cell4Util.getBlacklistData(is);
        for (var item : BuiltInRegistries.ITEM) {
            ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
            if (rl != null && modIdSet.contains(rl.getNamespace())) {
                var key = AEItemKey.of(item);
                if (key != null && !blacklist.isBlacklisted(key)) total++;
            }
        }
        if (total > 18) {
            lines.add(Component.translatable("tooltip.cell4.more_items", total - 18).withStyle(ChatFormatting.GRAY));
        }
        appendBlacklistTooltip(is, lines);
    }

    @NotNull
    @Override
    public Optional<TooltipComponent> getTooltipImage(@NotNull ItemStack stack) {
        List<String> modIds = getModIds(stack);
        if (modIds.isEmpty()) return Optional.empty();

        List<AEItemKey> previewItems = new ArrayList<>();
        int totalMatchCount = 0;
        Cell4Util.BlacklistData blacklist = Cell4Util.getBlacklistData(stack);
        Set<String> modIdSet = Set.copyOf(modIds);

        for (var item : BuiltInRegistries.ITEM) {
            ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
            if (rl != null && modIdSet.contains(rl.getNamespace())) {
                var key = AEItemKey.of(item);
                if (key != null && !blacklist.isBlacklisted(key)) {
                    totalMatchCount++;
                    if (previewItems.size() < 18) previewItems.add(key);
                }
            }
        }

        if (previewItems.isEmpty()) return Optional.empty();

        List<GenericStack> content = new ArrayList<>(previewItems.size());
        for (AEItemKey key : previewItems) {
            content.add(new GenericStack(key, IInfinityCell.getAsIntMax(key)));
        }
        return Optional.of(new StorageCellTooltipComponent(List.of(), content, totalMatchCount > previewItems.size(), true));
    }

    /**
     * Return the TRUE total count of matching items (not capped to 18).
     */
    public static int getPreviewTotalCount(ItemStack stack) {
        List<String> modIds = getModIds(stack);
        if (modIds.isEmpty()) return 0;
        Cell4Util.BlacklistData blacklist = Cell4Util.getBlacklistData(stack);
        Set<String> modIdSet = Set.copyOf(modIds);
        int total = 0;
        for (var item : BuiltInRegistries.ITEM) {
            ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
            if (rl != null && modIdSet.contains(rl.getNamespace())) {
                var key = AEItemKey.of(item);
                if (key != null && !blacklist.isBlacklisted(key)) total++;
            }
        }
        return total;
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack itemStack) { return IInfinityCell.super.getFuzzyMode(itemStack); }
    @Override
    public void setFuzzyMode(ItemStack itemStack, FuzzyMode fuzzyMode) { IInfinityCell.super.setFuzzyMode(itemStack, fuzzyMode); }
}
