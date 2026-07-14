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
        var tag = stack.getTag();
        if (tag != null && tag.contains(NBTKeys.NAME)) {
            return tag.getString(NBTKeys.NAME);
        }
        return "";
    }

    default void setCustomName(ItemStack stack, String name) {
        var tag = stack.getOrCreateTag();
        if (name == null || name.isEmpty()) {
            tag.remove(NBTKeys.NAME);
            if (tag.isEmpty()) {
                stack.setTag(null);
            }
        } else {
            tag.putString(NBTKeys.NAME, name);
        }
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
        // Long.MAX_VALUE — the infinite cell reports true maximum amount.
        // Displays as "9.2E" in AE2's terminal (4-character format).
        // Overflow protection is handled in each Storage class's getAvailableStacks()
        // by using set() instead of add(), so that the counter never accumulates
        // beyond Long.MAX_VALUE.
        return Long.MAX_VALUE;
    }
}
