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
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
 * Infinity ModID Cell - can infinitely extract any item/fluid from specified mods.
 * <p>
 * Binding is done purely via NBT. Supports both single-value and list formats:
 * /give @p cell4:infinity_modid_cell{cell4modid:"mekanism"}
 * /give @p cell4:infinity_modid_cell{cell4modid:["mekanism","thermal"]}
 * </p>
 */
public class InfinityModIdCell extends AEBaseItem implements ICellWorkbenchItem {

    private static final String NBT_KEY = "cell4modid";

    public InfinityModIdCell() {
        super(new Item.Properties().stacksTo(1));
    }

    /**
     * Get mod ID strings from NBT.
     * Supports both single string (legacy) and list format.
     */
    @NotNull
    public static List<String> getModIds(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_KEY)) {
            return Collections.emptyList();
        }

        // List format: {cell4modid:["mekanism","thermal"]}
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

        // Legacy single string format: {cell4modid:"mekanism"}
        String single = tag.getString(NBT_KEY);
        if (!single.isEmpty()) {
            return Collections.singletonList(single);
        }

        return Collections.emptyList();
    }

    public static void setModIds(ItemStack stack, List<String> modIds) {
        var tag = stack.getOrCreateTag();
        ListTag listTag = new ListTag();
        for (String id : modIds) {
            listTag.add(StringTag.valueOf(id));
        }
        tag.put(NBT_KEY, listTag);
    }

    public static void setModId(ItemStack stack, String modId) {
        var tag = stack.getOrCreateTag();
        tag.putString(NBT_KEY, modId);
    }

    public static boolean hasModIds(ItemStack stack) {
        return !getModIds(stack).isEmpty();
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack is) {
        return Component.translatable("item.cell4.infinity_modid_cell");
    }

    @Override
    public void appendHoverText(@NotNull ItemStack is, Level world, @NotNull List<Component> lines, @NotNull TooltipFlag adv) {
        lines.add(Component.translatable("tooltip.cell4.infinity").withStyle(ChatFormatting.GREEN));
        List<String> modIds = getModIds(is);
        for (String id : modIds) {
            lines.add(Component.translatable("tooltip.cell4.modid_filter", id).withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        // Show blacklist entries with all types
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
        List<String> modIds = getModIds(stack);
        if (modIds.isEmpty()) return Optional.empty();

        // Collect matching items (max 18 for 2 rows)
        List<AEItemKey> previewItems = new ArrayList<>();
        Cell4Util.BlacklistData blacklist = Cell4Util.getBlacklistData(stack);
        Set<String> modIdSet = Set.copyOf(modIds);

        for (var item : BuiltInRegistries.ITEM) {
            if (previewItems.size() >= 18) break;
            ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
            if (rl != null && modIdSet.contains(rl.getNamespace())) {
                var key = AEItemKey.of(item);
                if (key != null && !blacklist.isBlacklisted(key)) {
                    previewItems.add(key);
                }
            }
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
