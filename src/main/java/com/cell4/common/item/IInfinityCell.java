package com.cell4.common.item;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import com.cell4.common.util.Cell4Util;
import com.cell4.common.util.NBTKeys;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * Interface for all Infinity Cell types.
 * Provides default implementations for common behavior shared across all cell types:
 * - Custom name get/set via NBT
 * - Blacklist tooltip rendering
 * - FuzzyMode (always IGNORE_ALL)
 * - Display name logic (custom name or translation key)
 * Subclasses only need to implement getCellType() and translationKey().
 */
public interface IInfinityCell {

    enum CellType {
        ITEM, TAG, MODID
    }

    /**
     * Get the type of this infinity cell.
     */
    CellType getCellType();

    /**
     * Get the translation key for this cell's default display name.
     */
    String translationKey();

    /**
     * Get which fields are editable in the configurator.
     */
    default boolean canEditItem() { return false; }
    default boolean canEditTag() { return false; }
    default boolean canEditModId() { return false; }
    default boolean canEditBlacklist() { return true; }
    default boolean canEditName() { return true; }

    // === Common name logic ===

    default String getCustomName(ItemStack stack) {
        var tag = Cell4Util.getCustomTag(stack);
        return tag.getStringOr(NBTKeys.NAME, "");
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

    /**
     * Get the display name: custom name if set, otherwise the translation key.
     */
    default Component getDisplayName(ItemStack stack) {
        String customName = getCustomName(stack);
        if (!customName.isEmpty()) return Component.literal(customName);
        return Component.translatable(translationKey());
    }

    // === Common blacklist tooltip ===

    /**
     * Append blacklist tooltip lines. Shared by all cell types.
     */
    default void appendBlacklistTooltip(ItemStack stack, Consumer<Component> tooltipAdder) {
        Cell4Util.BlacklistData blacklist = Cell4Util.getBlacklistData(stack);
        for (AEKey key : blacklist.getItemKeys()) {
            tooltipAdder.accept(Component.translatable("tooltip.cell4.blacklist_item", key.getDisplayName()).withStyle(ChatFormatting.RED));
        }
        for (String tagName : blacklist.getTagNames()) {
            tooltipAdder.accept(Component.translatable("tooltip.cell4.blacklist_tag", tagName).withStyle(ChatFormatting.RED));
        }
        for (String modId : blacklist.getModIds()) {
            tooltipAdder.accept(Component.translatable("tooltip.cell4.blacklist_modid", modId).withStyle(ChatFormatting.RED));
        }
    }

    // === Common FuzzyMode ===

    default FuzzyMode getFuzzyMode(ItemStack itemStack) { return FuzzyMode.IGNORE_ALL; }
    default void setFuzzyMode(ItemStack itemStack, FuzzyMode fuzzyMode) { /* NO-OP */ }

    // === Common utility ===

    /**
     * Get the infinite amount for a given AEKey type.
     */
    static long getAsIntMax(AEKey key) {
        if (key instanceof AEFluidKey) return (long) Integer.MAX_VALUE * AEFluidKey.AMOUNT_BUCKET;
        return Integer.MAX_VALUE;
    }
}
