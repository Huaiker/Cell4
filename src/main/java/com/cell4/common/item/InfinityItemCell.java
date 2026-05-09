package com.cell4.common.item;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.items.AEBaseItem;
import appeng.items.storage.StorageCellTooltipComponent;
import com.cell4.common.registration.Cell4Items;
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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Infinity Item Cell - can infinitely extract specific items, fluids, or other AE2 key types.
 * <p>
 * Binding is done purely via NBT. Supports both single-value and list formats:
 * /give @p cell4:infinity_item_cell{cell4item:"minecraft:diamond"}
 * /give @p cell4:infinity_item_cell{cell4item:["minecraft:diamond","minecraft:oak_log"]}}
 * </p>
 * <p>
 * This class references the design of ExtendAE's InfinityCell
 * (com.glodblock.github.extendedae.common.items.InfinityCell).
 * The getAsIntMax method follows the same pattern.
 * </p>
 */
public class InfinityItemCell extends AEBaseItem implements ICellWorkbenchItem {

    private static final String NBT_KEY = "cell4item";

    public InfinityItemCell() {
        super(new Item.Properties().stacksTo(1));
    }

    /**
     * Get item/fluid identifier strings from NBT.
     * Supports both single string (legacy) and list format.
     */
    @NotNull
    public static List<String> getIdentifiers(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_KEY)) {
            return Collections.emptyList();
        }

        // List format: {cell4item:["minecraft:diamond","minecraft:oak_log"]}
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

        // Legacy single string format: {cell4item:"minecraft:diamond"}
        String single = tag.getString(NBT_KEY);
        if (!single.isEmpty()) {
            return Collections.singletonList(single);
        }

        return Collections.emptyList();
    }

    /**
     * Parse identifier strings into AEKeys.
     * Tries item registry first, then fluid registry for each identifier.
     */
    @NotNull
    public static List<AEKey> getRecords(ItemStack stack) {
        List<String> ids = getIdentifiers(stack);
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }

        List<AEKey> keys = new ArrayList<>(ids.size());
        for (String id : ids) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null) {
                // Try as item
                var item = BuiltInRegistries.ITEM.getOptional(rl);
                if (item.isPresent()) {
                    keys.add(AEItemKey.of(item.get()));
                    continue;
                }
                // Try as fluid
                var fluid = BuiltInRegistries.FLUID.getOptional(rl);
                if (fluid.isPresent() && fluid.get() != Fluids.EMPTY) {
                    keys.add(AEFluidKey.of(fluid.get()));
                }
            }
        }
        return keys;
    }

    /**
     * Set identifiers using list format on an existing cell ItemStack.
     */
    public static void setIdentifiers(ItemStack stack, List<String> ids) {
        var tag = stack.getOrCreateTag();
        ListTag listTag = new ListTag();
        for (String id : ids) {
            listTag.add(StringTag.valueOf(id));
        }
        tag.put(NBT_KEY, listTag);
    }

    /**
     * Set a single identifier (legacy format) on an existing cell ItemStack.
     */
    public static void setIdentifier(ItemStack stack, String id) {
        var tag = stack.getOrCreateTag();
        tag.putString(NBT_KEY, id);
    }

    /**
     * Create a new Infinity Item Cell bound to the specified registry names.
     */
    public ItemStack createStack(List<String> identifiers) {
        var stack = new ItemStack(Cell4Items.INFINITY_ITEM_CELL.get());
        setIdentifiers(stack, identifiers);
        return stack;
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack is) {
        return Component.translatable("item.cell4.infinity_item_cell");
    }

    @Override
    public void appendHoverText(@NotNull ItemStack is, Level world, @NotNull List<Component> lines, @NotNull TooltipFlag adv) {
        lines.add(Component.translatable("tooltip.cell4.infinity").withStyle(ChatFormatting.GREEN));
        List<String> ids = getIdentifiers(is);
        if (ids.size() > 1) {
            lines.add(Component.translatable("tooltip.cell4.item_count", ids.size()).withStyle(ChatFormatting.AQUA));
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
        List<AEKey> records = getRecords(stack);
        Cell4Util.BlacklistData blacklist = Cell4Util.getBlacklistData(stack);
        if (records.isEmpty()) {
            return Optional.empty();
        }
        List<GenericStack> content = new ArrayList<>(records.size());
        for (AEKey key : records) {
            if (!blacklist.isBlacklisted(key)) {
                content.add(new GenericStack(key, getAsIntMax(key)));
            }
        }
        if (content.isEmpty()) {
            return Optional.empty();
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

    /**
     * Get the maximum display amount for a given AEKey type.
     * Items use Integer.MAX_VALUE, fluids use Integer.MAX_VALUE * AMOUNT_BUCKET.
     * This follows the same pattern as ExtendAE's InfinityCell.getAsIntMax().
     */
    public static long getAsIntMax(AEKey key) {
        if (key instanceof AEFluidKey) {
            return (long) Integer.MAX_VALUE * AEFluidKey.AMOUNT_BUCKET;
        }
        return Integer.MAX_VALUE;
    }
}
