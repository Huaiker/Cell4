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
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Infinity ModID Cell - can infinitely extract any item/fluid from specified mods.
 * <p>
 * Binding is done purely via NBT (stored in custom data component in 1.21.1).
 * Supports both single-value and list formats:
 * /give @p cell4:infinity_modid_cell{custom_data:{cell4modid:"mekanism"}}
 * /give @p cell4:infinity_modid_cell{custom_data:{cell4modid:["mekanism","thermal"]}}
 * </p>
 */
public class InfinityModIdCell extends AEBaseItem implements ICellWorkbenchItem {

    private static final String NBT_KEY = "cell4modid";

    public InfinityModIdCell(Properties properties) {
        super(properties.stacksTo(1));
    }

    /**
     * Get mod ID strings from NBT.
     * Supports both single string (legacy) and list format.
     */
    @NotNull
    public static List<String> getModIds(ItemStack stack) {
        CompoundTag tag = Cell4Util.getCustomTag(stack);
        if (!tag.contains(NBT_KEY)) {
            return Collections.emptyList();
        }
        return Cell4Util.parseStringList(tag, NBT_KEY);
    }

    public static void setModIds(ItemStack stack, List<String> modIds) {
        CompoundTag tag = Cell4Util.getCustomTag(stack);
        ListTag listTag = new ListTag();
        for (String id : modIds) {
            listTag.add(StringTag.valueOf(id));
        }
        tag.put(NBT_KEY, listTag);
        Cell4Util.setCustomTag(stack, tag);
    }

    public static void setModId(ItemStack stack, String modId) {
        CompoundTag tag = Cell4Util.getCustomTag(stack);
        tag.putString(NBT_KEY, modId);
        Cell4Util.setCustomTag(stack, tag);
    }

    public static boolean hasModIds(ItemStack stack) {
        return !getModIds(stack).isEmpty();
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack is) {
        return Component.translatable("item.cell4.infinity_modid_cell");
    }

    @Override
    public void appendHoverText(@NotNull ItemStack is, @NotNull Item.TooltipContext context, @NotNull List<Component> lines, @NotNull TooltipFlag adv) {
        lines.add(Component.translatable("tooltip.cell4.infinity").withStyle(ChatFormatting.GREEN));
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
        List<String> modIds = getModIds(stack);
        if (modIds.isEmpty()) return Optional.empty();

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
