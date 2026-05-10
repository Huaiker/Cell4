package com.cell4.common.item;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.items.AEBaseItem;
import appeng.items.storage.StorageCellTooltipComponent;
import com.cell4.common.util.Cell4Util;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Infinity Tag Cell - can infinitely extract any item/fluid matching specified tags.
 * <p>
 * Binding is done purely via NBT. Supports both single-value and list formats:
 * /give @p cell4:infinity_tag_cell{cell4tag:"minecraft:logs"}
 * /give @p cell4:infinity_tag_cell{cell4tag:["minecraft:logs","forge:ingots/iron"]}
 * </p>
 * <p>
 * Also supports mod ID filtering via cell4modid for intersection:
 * /give @p cell4:infinity_tag_cell{cell4tag:["minecraft:logs"],cell4modid:["minecraft"]}
 * When both cell4tag AND cell4modid are present, items must match tag AND belong to the specified mod.
 * </p>
 */
public class InfinityTagCell extends AEBaseItem implements ICellWorkbenchItem {

    private static final String NBT_KEY = "cell4tag";
    public static final String MODID_NBT_KEY = "cell4modid";

    public InfinityTagCell() {
        super(new Item.Properties().stacksTo(1));
    }

    /**
     * Get tag name strings from NBT.
     * Supports both single string (legacy) and list format.
     */
    @NotNull
    public static List<String> getTagNames(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_KEY)) {
            return Collections.emptyList();
        }

        // List format: {cell4tag:["minecraft:logs","forge:ingots"]}
        if (tag.get(NBT_KEY) instanceof ListTag listTag) {
            List<String> result = new ArrayList<>(listTag.size());
            for (int i = 0; i < listTag.size(); i++) {
                String str = listTag.getString(i);
                if (!str.isEmpty()) {
                    result.add(str);
                }
            }
            return result;
        }

        // Legacy single string format: {cell4tag:"minecraft:logs"}
        String single = tag.getString(NBT_KEY);
        if (!single.isEmpty()) {
            return Collections.singletonList(single);
        }

        return Collections.emptyList();
    }

    public static void setTagNames(ItemStack stack, List<String> tagNames) {
        var tag = stack.getOrCreateTag();
        ListTag listTag = new ListTag();
        for (String name : tagNames) {
            listTag.add(StringTag.valueOf(name));
        }
        tag.put(NBT_KEY, listTag);
    }

    public static void setTagName(ItemStack stack, String tagName) {
        var tag = stack.getOrCreateTag();
        tag.putString(NBT_KEY, tagName);
    }

    public static boolean hasTagNames(ItemStack stack) {
        return !getTagNames(stack).isEmpty();
    }

    /**
     * Get mod ID strings from NBT for intersection filtering.
     */
    @NotNull
    public static List<String> getModIds(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null || !tag.contains(MODID_NBT_KEY)) {
            return Collections.emptyList();
        }
        return Cell4Util.parseStringList(tag, MODID_NBT_KEY);
    }

    /**
     * Set mod IDs using list format on an existing cell ItemStack.
     */
    public static void setModIds(ItemStack stack, List<String> modIds) {
        var tag = stack.getOrCreateTag();
        ListTag listTag = new ListTag();
        for (String id : modIds) {
            listTag.add(StringTag.valueOf(id));
        }
        tag.put(MODID_NBT_KEY, listTag);
    }

    /**
     * Set a single mod ID (legacy format) on an existing cell ItemStack.
     */
    public static void setModId(ItemStack stack, String modId) {
        var tag = stack.getOrCreateTag();
        tag.putString(MODID_NBT_KEY, modId);
    }

    /**
     * Check if the cell has mod ID filters configured.
     */
    public static boolean hasModIds(ItemStack stack) {
        return !getModIds(stack).isEmpty();
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack is) {
        return Component.translatable("item.cell4.infinity_tag_cell");
    }

    @Override
    public void appendHoverText(@NotNull ItemStack is, Level world, @NotNull List<Component> lines, @NotNull TooltipFlag adv) {
        lines.add(Component.translatable("tooltip.cell4.infinity").withStyle(ChatFormatting.GREEN));

        List<String> tags = getTagNames(is);
        for (String tag : tags) {
            lines.add(Component.translatable("tooltip.cell4.tag_filter", tag).withStyle(ChatFormatting.AQUA));
        }

        // Show modid filters
        List<String> modIds = getModIds(is);
        for (String id : modIds) {
            lines.add(Component.translatable("tooltip.cell4.modid_filter", id).withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        // Show blacklist entries with new types
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
        List<String> tagNames = getTagNames(stack);
        if (tagNames.isEmpty()) return Optional.empty();

        // Collect matching items (max 18 for 2 rows)
        List<AEItemKey> previewItems = new ArrayList<>();
        Cell4Util.BlacklistData blacklist = Cell4Util.getBlacklistData(stack);
        List<String> modIds = getModIds(stack);
        boolean hasModIdsFilter = !modIds.isEmpty();
        Set<String> modIdSet = Set.copyOf(modIds);

        for (String tagName : tagNames) {
            ResourceLocation tagRL = ResourceLocation.tryParse(tagName);
            if (tagRL == null) continue;
            TagKey<net.minecraft.world.item.Item> itemTag = TagKey.create(BuiltInRegistries.ITEM.key(), tagRL);
            for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(itemTag)) {
                if (previewItems.size() >= 18) break;
                var key = AEItemKey.of(holder.value());
                if (key == null || blacklist.isBlacklisted(key)) continue;
                if (hasModIdsFilter) {
                    ResourceLocation rl = BuiltInRegistries.ITEM.getKey(key.getItem());
                    if (rl == null || !modIdSet.contains(rl.getNamespace())) continue;
                }
                previewItems.add(key);
            }
            if (previewItems.size() >= 18) break;
        }

        if (previewItems.isEmpty()) return Optional.empty();

        List<GenericStack> content = new ArrayList<>(previewItems.size());
        for (AEItemKey key : previewItems) {
            content.add(new GenericStack(key, Integer.MAX_VALUE));
        }
        return Optional.of(new StorageCellTooltipComponent(List.of(), content, false, true));
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
