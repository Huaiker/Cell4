package com.cell4.common.item;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEKey;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.items.AEBaseItem;
import com.cell4.common.util.Cell4Util;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Infinity Tag Cell - can infinitely extract any item/fluid matching specified tags
 * or from specified mods.
 * <p>
 * Binding is done purely via NBT (stored in custom data component in 1.21.1).
 * Supports both single-value and list formats:
 * /give @p cell4:infinity_tag_cell[custom_data={cell4tag:"minecraft:logs"}]
 * /give @p cell4:infinity_tag_cell[custom_data={cell4tag:["minecraft:logs","c:ingots/iron"]}]
 * /give @p cell4:infinity_tag_cell[custom_data={cell4tag:"minecraft:logs",cell4modid:"mekanism"}]
 * /give @p cell4:infinity_tag_cell[custom_data={cell4tag:["minecraft:logs"],cell4modid:["mekanism","thermal"]}]
 * </p>
 */
public class InfinityTagCell extends AEBaseItem implements ICellWorkbenchItem {

    private static final String TAG_NBT_KEY = "cell4tag";
    private static final String MODID_NBT_KEY = "cell4modid";

    public InfinityTagCell(Properties properties) {
        super(properties.stacksTo(1));
    }

    /**
     * Get tag name strings from NBT.
     * Supports both single string (legacy) and list format.
     */
    @NotNull
    public static List<String> getTagNames(ItemStack stack) {
        CompoundTag tag = Cell4Util.getCustomTag(stack);
        if (!tag.contains(TAG_NBT_KEY)) {
            return Collections.emptyList();
        }
        return Cell4Util.parseStringList(tag, TAG_NBT_KEY);
    }

    public static void setTagNames(ItemStack stack, List<String> tagNames) {
        CompoundTag tag = Cell4Util.getCustomTag(stack);
        ListTag listTag = new ListTag();
        for (String name : tagNames) {
            listTag.add(StringTag.valueOf(name));
        }
        tag.put(TAG_NBT_KEY, listTag);
        Cell4Util.setCustomTag(stack, tag);
    }

    public static void setTagName(ItemStack stack, String tagName) {
        CompoundTag tag = Cell4Util.getCustomTag(stack);
        tag.putString(TAG_NBT_KEY, tagName);
        Cell4Util.setCustomTag(stack, tag);
    }

    public static boolean hasTagNames(ItemStack stack) {
        return !getTagNames(stack).isEmpty();
    }

    /**
     * Get mod ID strings from NBT.
     * Supports both single string (legacy) and list format.
     */
    @NotNull
    public static List<String> getModIds(ItemStack stack) {
        CompoundTag tag = Cell4Util.getCustomTag(stack);
        if (!tag.contains(MODID_NBT_KEY)) {
            return Collections.emptyList();
        }
        return Cell4Util.parseStringList(tag, MODID_NBT_KEY);
    }

    public static void setModIds(ItemStack stack, List<String> modIds) {
        CompoundTag tag = Cell4Util.getCustomTag(stack);
        ListTag listTag = new ListTag();
        for (String id : modIds) {
            listTag.add(StringTag.valueOf(id));
        }
        tag.put(MODID_NBT_KEY, listTag);
        Cell4Util.setCustomTag(stack, tag);
    }

    public static void setModId(ItemStack stack, String modId) {
        CompoundTag tag = Cell4Util.getCustomTag(stack);
        tag.putString(MODID_NBT_KEY, modId);
        Cell4Util.setCustomTag(stack, tag);
    }

    public static boolean hasModIds(ItemStack stack) {
        return !getModIds(stack).isEmpty();
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack is) {
        return Component.translatable("item.cell4.infinity_tag_cell");
    }

    @Override
    public void appendHoverText(@NotNull ItemStack is, @NotNull Item.TooltipContext context, @NotNull List<Component> lines, @NotNull TooltipFlag adv) {
        lines.add(Component.translatable("tooltip.cell4.infinity").withStyle(ChatFormatting.GREEN));
        List<String> tags = getTagNames(is);
        for (String tag : tags) {
            lines.add(Component.translatable("tooltip.cell4.tag_filter", tag).withStyle(ChatFormatting.AQUA));
        }
        List<String> modIds = getModIds(is);
        for (String id : modIds) {
            lines.add(Component.translatable("tooltip.cell4.modid_filter", id).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        Cell4Util.BlacklistData blacklist = Cell4Util.getBlacklistData(is);
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

    @NotNull
    @Override
    public Optional<TooltipComponent> getTooltipImage(@NotNull ItemStack stack) {
        return Optional.empty();
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack itemStack) {
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(ItemStack itemStack, FuzzyMode fuzzyMode) {
        // NO-OP
    }
}
