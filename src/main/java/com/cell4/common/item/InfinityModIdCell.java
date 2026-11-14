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
import net.minecraft.nbt.CompoundTag;
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
        List<String> modIds = new ArrayList<>(new LinkedHashSet<>(getModIds(is)));
        for (String id : modIds) {
            lines.add(Component.translatable("tooltip.cell4.modid_filter", id).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        int total = getPreviewTotalCount(is);
        if (total > 18) {
            lines.add(Component.translatable("tooltip.cell4.more_items", total - 18).withStyle(ChatFormatting.GRAY));
        }
        appendBlacklistTooltip(is, lines);
    }

    @NotNull
    @Override
    public Optional<TooltipComponent> getTooltipImage(@NotNull ItemStack stack) {
        return buildTooltipImage(stack);
    }

    /** 预览图标与真实总数，二者共用一次注册表扫描结果。 */
    private record PreviewResult(List<AEItemKey> keys, int total) {}

    private static final int MAX_PREVIEW_CACHE = 64;
    private static final Map<String, PreviewResult> PREVIEW_CACHE =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, PreviewResult> eldest) {
                    return size() > MAX_PREVIEW_CACHE;
                }
            };

    /**
     * 按元件配置列表（模组 + 黑名单）缓存预览结果。
     * 配置变化会产生新的 key，旧结果由 LRU 淘汰。
     */
    private static PreviewResult getPreview(ItemStack stack) {
        CompoundTag tag = Cell4Util.getCustomTag(stack);
        List<String> modIds = new ArrayList<>(new LinkedHashSet<>(
                Cell4Util.parseStringList(tag, NBTKeys.MODID)));
        List<String> blacklistIds = Cell4Util.parseStringList(tag, NBTKeys.BLACKLIST);
        String cacheKey = String.join("\u0001", modIds) + '\u0002'
                + String.join("\u0001", blacklistIds);

        synchronized (PREVIEW_CACHE) {
            PreviewResult cached = PREVIEW_CACHE.get(cacheKey);
            if (cached != null) return cached;
        }

        PreviewResult computed = computePreview(modIds, blacklistIds);
        synchronized (PREVIEW_CACHE) {
            PREVIEW_CACHE.put(cacheKey, computed);
        }
        return computed;
    }

    private static PreviewResult computePreview(List<String> modIds, List<String> blacklistIds) {
        if (modIds.isEmpty()) return new PreviewResult(List.of(), 0);
        List<AEItemKey> previewItems = new ArrayList<>();
        int totalMatchCount = 0;
        Cell4Util.BlacklistData blacklist = Cell4Util.getBlacklistData(blacklistIds);
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

        return new PreviewResult(List.copyOf(previewItems), totalMatchCount);
    }

    @NotNull
    public static Optional<TooltipComponent> buildTooltipImage(ItemStack stack) {
        PreviewResult preview = getPreview(stack);
        if (preview.keys().isEmpty()) return Optional.empty();

        List<GenericStack> content = new ArrayList<>(preview.keys().size());
        for (AEItemKey key : preview.keys()) {
            content.add(new GenericStack(key, IInfinityCell.getAsIntMax(key)));
        }
        return Optional.of(new StorageCellTooltipComponent(List.of(), content,
                preview.total() > preview.keys().size(), true));
    }

    /**
     * Return the TRUE total count of matching items (not capped to 18).
     */
    public static int getPreviewTotalCount(ItemStack stack) {
        return getPreview(stack).total();
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack itemStack) { return IInfinityCell.super.getFuzzyMode(itemStack); }
    @Override
    public void setFuzzyMode(ItemStack itemStack, FuzzyMode fuzzyMode) { IInfinityCell.super.setFuzzyMode(itemStack, fuzzyMode); }
}
