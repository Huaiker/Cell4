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
import com.cell4.common.util.Cell4Patterns;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
public class InfinityItemCell extends AEBaseItem implements ICellWorkbenchItem, IInfinityCell {

    public InfinityItemCell() {
        super(new Item.Properties().stacksTo(1));
    }

    // IInfinityCell implementation
    @Override
    public CellType getCellType() { return CellType.ITEM; }

    @Override
    public String translationKey() { return "item.cell4.infinity_item_cell"; }

    @Override
    public boolean canEditItem() { return true; }

    @Override
    public String getCustomName(ItemStack stack) {
        return IInfinityCell.super.getCustomName(stack);
    }

    @Override
    public void setCustomName(ItemStack stack, String name) {
        IInfinityCell.super.setCustomName(stack, name);
    }

    /**
     * Get item/fluid identifier strings from NBT.
     * Supports both single string (legacy) and list format.
     */
    @NotNull
    public static List<String> getIdentifiers(ItemStack stack) {
        return Cell4Util.getStringList(stack, NBTKeys.ITEM);
    }

    /**
     * Parse identifier strings into AEKeys.
     * Tries item registry first, then fluid registry for each identifier.
     * <p>
     * 不再截断条目数：终端/预览需要显示全部有效条目（预览面板自身的 6 项上限除外）。
     * 无法解析或 key 类型未注册的条目会输出 warn，而不是静默消失。
     */
    @NotNull
    public static List<AEKey> getRecords(ItemStack stack) {
        List<String> ids = getIdentifiers(stack);
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }

        List<AEKey> keys = new ArrayList<>();
        for (AEKey key : Cell4Patterns.resolveEntries(ids)) {
            if (key == null) continue;
            boolean registered;
            try {
                registered = isKeyTypeRegistered(key);
            } catch (Throwable t) {
                com.cell4.Cell4.LOGGER.warn("Cell4: cannot check key type of '{}' on {}",
                        key, stack.getItem(), t);
                continue;
            }
            if (!registered) {
                com.cell4.Cell4.LOGGER.warn("Cell4: skipping key with unregistered type '{}' on {}",
                        key, stack.getItem());
                continue;
            }
            keys.add(key);
        }
        return keys;
    }

    /** Check if the AEKeyType for this key is registered (avoid crashes with uninstalled mods). */
    private static boolean isKeyTypeRegistered(AEKey key) {
        try {
            appeng.api.stacks.AEKeyTypes.get(key.getType().getId());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Set identifiers using list format on an existing cell ItemStack.
     */
    public static void setIdentifiers(ItemStack stack, List<String> ids) {
        Cell4Util.setStringList(stack, NBTKeys.ITEM, ids);
    }

    /**
     * Set a single identifier (legacy format) on an existing cell ItemStack.
     */
    public static void setIdentifier(ItemStack stack, String id) {
        Cell4Util.setStringValue(stack, NBTKeys.ITEM, id);
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
        return IInfinityCell.super.getDisplayName(is);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack is, Level world, @NotNull List<Component> lines, @NotNull TooltipFlag adv) {
        lines.add(Component.translatable("tooltip.cell4.infinity").withStyle(ChatFormatting.GREEN));
        List<String> ids = getIdentifiers(is);
        // 仅影响显示：重复的绑定条目只计一次。
        int uniqueCount = new java.util.LinkedHashSet<>(ids).size();
        if (uniqueCount > 1) {
            lines.add(Component.translatable("tooltip.cell4.item_count", uniqueCount).withStyle(ChatFormatting.AQUA));
        }

        // Show blacklist entries
        appendBlacklistTooltip(is, lines);
    }

    @NotNull
    @Override
    public Optional<TooltipComponent> getTooltipImage(@NotNull ItemStack stack) {
        return buildTooltipImage(stack);
    }

    @NotNull
    public static Optional<TooltipComponent> buildTooltipImage(ItemStack stack) {
        List<AEKey> records = getRecords(stack);
        Cell4Util.BlacklistData blacklist = Cell4Util.getBlacklistData(stack);
        if (records.isEmpty()) {
            return Optional.empty();
        }
        List<GenericStack> content = new ArrayList<>(records.size());
        for (AEKey key : records) {
            if (!blacklist.isBlacklisted(key)) {
                content.add(new GenericStack(key, IInfinityCell.getAsIntMax(key)));
            }
        }
        if (content.isEmpty()) {
            return Optional.empty();
        }
        // 与标签/模组元件一致：最多显示 18 个，其余用“...”表示
        boolean hasMore = content.size() > 18;
        if (hasMore) {
            content = new ArrayList<>(content.subList(0, 18));
        }
        return Optional.of(new StorageCellTooltipComponent(List.of(), content, hasMore, true));
    }

    /**
     * Return the TRUE total count of bound keys (not capped).
     */
    public static int getPreviewTotalCount(ItemStack stack) {
        List<AEKey> records = Cell4Patterns.resolveEntries(getIdentifiers(stack));
        Cell4Util.BlacklistData blacklist = Cell4Util.getBlacklistData(stack);
        int total = 0;
        for (AEKey key : records) {
            if (!blacklist.isBlacklisted(key)) total++;
        }
        return total;
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack itemStack) {
        return IInfinityCell.super.getFuzzyMode(itemStack);
    }

    @Override
    public void setFuzzyMode(ItemStack itemStack, FuzzyMode fuzzyMode) {
        IInfinityCell.super.setFuzzyMode(itemStack, fuzzyMode);
    }
}
