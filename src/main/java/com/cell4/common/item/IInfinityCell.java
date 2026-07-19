package com.cell4.common.item;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import com.cell4.common.util.Cell4Util;
import com.cell4.common.util.NBTKeys;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public interface IInfinityCell {

    enum CellType { ITEM, TAG, MODID }

    CellType getCellType();
    String translationKey();

    default boolean canEditItem() { return false; }
    default boolean canEditTag() { return false; }
    default boolean canEditModId() { return false; }
    default boolean canEditBlacklist() { return true; }
    default boolean canEditName() { return true; }

    default String getCustomName(ItemStack stack) {
        var tag = Cell4Util.getCustomTag(stack);
        return tag.contains(NBTKeys.NAME) ? tag.getString(NBTKeys.NAME) : "";
    }

    default void setCustomName(ItemStack stack, String name) {
        var tag = Cell4Util.getCustomTag(stack);
        if (name == null || name.isEmpty()) {
            tag.remove(NBTKeys.NAME);
        } else {
            tag.putString(NBTKeys.NAME, name);
        }
        Cell4Util.setCustomTag(stack, tag);
    }

    default Component getDisplayName(ItemStack stack) {
        String customName = getCustomName(stack);
        if (!customName.isEmpty()) return Component.literal(customName);
        return Component.translatable(translationKey());
    }

    default void appendBlacklistTooltip(ItemStack stack, List<Component> lines) {
        Cell4Util.BlacklistData blacklist = Cell4Util.getBlacklistData(stack);
        for (AEKey key : blacklist.getItemKeys()) {
            lines.add(Component.translatable("tooltip.cell4.blacklist_item", key.getDisplayName()).withStyle(ChatFormatting.RED));
        }
        for (String tagName : blacklist.getTagNames()) {
            lines.add(Component.translatable("tooltip.cell4.blacklist_tag", tagName).withStyle(ChatFormatting.RED));
        }
        for (String modId : blacklist.getModIds()) {
            lines.add(Component.translatable("tooltip.cell4.blacklist_modid", modId).withStyle(ChatFormatting.RED));
        }
    }

    default FuzzyMode getFuzzyMode(ItemStack itemStack) { return FuzzyMode.IGNORE_ALL; }
    default void setFuzzyMode(ItemStack itemStack, FuzzyMode fuzzyMode) { /* NO-OP */ }

    static long getAsIntMax(AEKey key) {
        // Long.MAX_VALUE — 无限元件报告 Long.MAX_VALUE 作为实际上限。
        // 在 AE2 终端显示为 "9.2E"（4 字符格式）。
        //
        // 存储上限架构 (v1.0.5)：
        // - AE2 的 KeyCounter 内部使用 long 算术。
        // - KeyCounterMixin 拦截 add/get/remove/set，将溢出路由到
        //   Cell4BigStorage，后者在全局 map 中跟踪真实的 BigInteger 数值。
        // - KeyCounter 中的 long 字段被 clamp 到 Long.MAX_VALUE（永不溢出，
        //   永不变负），同时精确的 BigInteger 值被保留。
        // - 实际上，存储上限被提升到 BigInteger，而报告给 AE2 基于 long API
        //   的实际上限保持为 Long.MAX_VALUE。
        //
        // 这修复了两个 bug：
        // 1. ae2wtlib restock overlay 崩溃（ReadableNumberConverter 拒绝负数）
        // 2. 自动合成"缺东西"（负数可用量被当作"无物品"）
        return Long.MAX_VALUE;
    }
}
