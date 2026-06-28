package com.cell4.common.item;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.items.AEBaseItem;
import appeng.items.storage.StorageCellTooltipComponent;
import com.cell4.common.integration.MekanismIntegration;
import com.cell4.common.util.Cell4Util;
import com.cell4.common.util.NBTKeys;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
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
 * <p>
 * 5.1: Pure ModID mode - when only cell4modid is set (no cell4tag), the cell
 * behaves like a ModID cell, matching items by their namespace.
 * </p>
 */
public class InfinityTagCell extends AEBaseItem implements ICellWorkbenchItem, IInfinityCell {

    public InfinityTagCell() {
        super(new Item.Properties().stacksTo(1));
    }

    // IInfinityCell implementation
    @Override
    public CellType getCellType() { return CellType.TAG; }

    @Override
    public String translationKey() { return "item.cell4.infinity_tag_cell"; }

    @Override
    public boolean canEditTag() { return true; }

    @Override
    public boolean canEditModId() { return true; }

    @Override
    public String getCustomName(ItemStack stack) {
        return IInfinityCell.super.getCustomName(stack);
    }

    @Override
    public void setCustomName(ItemStack stack, String name) {
        IInfinityCell.super.setCustomName(stack, name);
    }

    /**
     * Get tag name strings from NBT.
     * Supports both single string (legacy) and list format.
     */
    @NotNull
    public static List<String> getTagNames(ItemStack stack) {
        return Cell4Util.getStringList(stack, NBTKeys.TAG);
    }

    public static void setTagNames(ItemStack stack, List<String> tagNames) {
        Cell4Util.setStringList(stack, NBTKeys.TAG, tagNames);
    }

    public static void setTagName(ItemStack stack, String tagName) {
        Cell4Util.setStringValue(stack, NBTKeys.TAG, tagName);
    }

    public static boolean hasTagNames(ItemStack stack) {
        return !getTagNames(stack).isEmpty();
    }

    /**
     * Get mod ID strings from NBT for intersection filtering.
     */
    @NotNull
    public static List<String> getModIds(ItemStack stack) {
        return Cell4Util.getStringList(stack, NBTKeys.MODID);
    }

    /**
     * Set mod IDs using list format on an existing cell ItemStack.
     */
    public static void setModIds(ItemStack stack, List<String> modIds) {
        Cell4Util.setStringList(stack, NBTKeys.MODID, modIds);
    }

    /**
     * Set a single mod ID (legacy format) on an existing cell ItemStack.
     */
    public static void setModId(ItemStack stack, String modId) {
        Cell4Util.setStringValue(stack, NBTKeys.MODID, modId);
    }

    /**
     * Check if the cell has mod ID filters configured.
     */
    public static boolean hasModIds(ItemStack stack) {
        return !getModIds(stack).isEmpty();
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack is) {
        return IInfinityCell.super.getDisplayName(is);
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

        // Show blacklist entries
        appendBlacklistTooltip(is, lines);
    }

    @NotNull
    @Override
    public Optional<TooltipComponent> getTooltipImage(@NotNull ItemStack stack) {
        List<String> tagNames = getTagNames(stack);
        List<String> modIds = getModIds(stack);
        boolean hasTags = !tagNames.isEmpty();
        boolean hasModIds = !modIds.isEmpty();
        Set<String> modIdSet = Set.copyOf(modIds);

        // 5.1: Allow pure ModID mode
        if (!hasTags && !hasModIds) return Optional.empty();

        List<AEKey> previewKeys = new ArrayList<>();
        int totalMatchCount = 0;
        Cell4Util.BlacklistData blacklist = Cell4Util.getBlacklistData(stack);

        if (hasTags) {
            for (String tagName : tagNames) {
                ResourceLocation tagRL = ResourceLocation.tryParse(tagName);
                if (tagRL == null) continue;

                // Items matching tag
                TagKey<net.minecraft.world.item.Item> itemTag = TagKey.create(BuiltInRegistries.ITEM.key(), tagRL);
                for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(itemTag)) {
                    var key = AEItemKey.of(holder.value());
                    if (key == null || blacklist.isBlacklisted(key)) continue;
                    if (hasModIds && !Cell4Util.belongsToMod(key, modIdSet)) continue;
                    totalMatchCount++;
                    if (previewKeys.size() < 18) previewKeys.add(key);
                }

                // 6.2: Also include fluids matching the tag (skip flowing_ variants)
                TagKey<Fluid> fluidTag = TagKey.create(BuiltInRegistries.FLUID.key(), tagRL);
                for (var holder : BuiltInRegistries.FLUID.getTagOrEmpty(fluidTag)) {
                    Fluid fluid = holder.value();
                    if (fluid == Fluids.EMPTY) continue;
                    ResourceLocation fluidRl = BuiltInRegistries.FLUID.getKey(fluid);
                    if (fluidRl != null && fluidRl.getPath().startsWith("flowing_")) continue;
                    var key = AEFluidKey.of(fluid);
                    if (key == null || blacklist.isBlacklisted(key)) continue;
                    if (hasModIds && !Cell4Util.belongsToMod(key, modIdSet)) continue;
                    totalMatchCount++;
                    if (previewKeys.size() < 18) previewKeys.add(key);
                }

                // Mekanism chemicals matching the tag
                for (AEKey chemicalKey : MekanismIntegration.getChemicalKeysByTag(tagName)) {
                    if (hasModIds && !Cell4Util.belongsToMod(chemicalKey, modIdSet)) continue;
                    if (blacklist.isBlacklisted(chemicalKey)) continue;
                    totalMatchCount++;
                    if (previewKeys.size() < 18) previewKeys.add(chemicalKey);
                }
            }
        } else if (hasModIds) {
            // 5.1: Pure ModID mode for Tag cell
            for (var item : BuiltInRegistries.ITEM) {
                if (Cell4Util.belongsToMod(AEItemKey.of(item), modIdSet)) {
                    var key = AEItemKey.of(item);
                    if (key != null && !blacklist.isBlacklisted(key)) {
                        totalMatchCount++;
                        if (previewKeys.size() < 18) previewKeys.add(key);
                    }
                }
            }
            for (var fluid : BuiltInRegistries.FLUID) {
                if (fluid == Fluids.EMPTY) continue;
                ResourceLocation rl = BuiltInRegistries.FLUID.getKey(fluid);
                if (rl != null && rl.getPath().startsWith("flowing_")) continue;
                var key = AEFluidKey.of(fluid);
                if (key != null && Cell4Util.belongsToMod(key, modIdSet) && !blacklist.isBlacklisted(key)) {
                    totalMatchCount++;
                    if (previewKeys.size() < 18) previewKeys.add(key);
                }
            }
            for (AEKey chemicalKey : MekanismIntegration.getAllChemicalKeys()) {
                if (Cell4Util.belongsToMod(chemicalKey, modIdSet) && !blacklist.isBlacklisted(chemicalKey)) {
                    totalMatchCount++;
                    if (previewKeys.size() < 18) previewKeys.add(chemicalKey);
                }
            }
        }

        if (previewKeys.isEmpty()) return Optional.empty();

        List<GenericStack> content = new ArrayList<>(previewKeys.size());
        for (AEKey key : previewKeys) {
            content.add(new GenericStack(key, IInfinityCell.getAsIntMax(key)));
        }

        return Optional.of(new StorageCellTooltipComponent(List.of(), content, totalMatchCount > previewKeys.size(), true));
    }

    /**
     * Return the TRUE total count of matching keys (not capped to 18).
     */
    public static int getPreviewTotalCount(ItemStack stack) {
        List<String> tagNames = getTagNames(stack);
        List<String> modIds = getModIds(stack);
        boolean hasTags = !tagNames.isEmpty();
        boolean hasModIds = !modIds.isEmpty();
        Set<String> modIdSet = Set.copyOf(modIds);
        Cell4Util.BlacklistData blacklist = Cell4Util.getBlacklistData(stack);
        int total = 0;

        if (hasTags) {
            for (String tagName : tagNames) {
                ResourceLocation tagRL = ResourceLocation.tryParse(tagName);
                if (tagRL == null) continue;

                TagKey<net.minecraft.world.item.Item> itemTag = TagKey.create(BuiltInRegistries.ITEM.key(), tagRL);
                for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(itemTag)) {
                    var key = AEItemKey.of(holder.value());
                    if (key == null || blacklist.isBlacklisted(key)) continue;
                    if (hasModIds && !Cell4Util.belongsToMod(key, modIdSet)) continue;
                    total++;
                }

                TagKey<Fluid> fluidTag = TagKey.create(BuiltInRegistries.FLUID.key(), tagRL);
                for (var holder : BuiltInRegistries.FLUID.getTagOrEmpty(fluidTag)) {
                    Fluid fluid = holder.value();
                    if (fluid == Fluids.EMPTY) continue;
                    ResourceLocation fluidRl = BuiltInRegistries.FLUID.getKey(fluid);
                    if (fluidRl != null && fluidRl.getPath().startsWith("flowing_")) continue;
                    var key = AEFluidKey.of(fluid);
                    if (key == null || blacklist.isBlacklisted(key)) continue;
                    if (hasModIds && !Cell4Util.belongsToMod(key, modIdSet)) continue;
                    total++;
                }

                for (AEKey chemicalKey : MekanismIntegration.getChemicalKeysByTag(tagName)) {
                    if (hasModIds && !Cell4Util.belongsToMod(chemicalKey, modIdSet)) continue;
                    if (!blacklist.isBlacklisted(chemicalKey)) total++;
                }
            }
        } else if (hasModIds) {
            for (var item : BuiltInRegistries.ITEM) {
                var key = AEItemKey.of(item);
                if (key != null && Cell4Util.belongsToMod(key, modIdSet) && !blacklist.isBlacklisted(key)) total++;
            }
            for (var fluid : BuiltInRegistries.FLUID) {
                if (fluid == Fluids.EMPTY) continue;
                ResourceLocation rl = BuiltInRegistries.FLUID.getKey(fluid);
                if (rl != null && rl.getPath().startsWith("flowing_")) continue;
                var key = AEFluidKey.of(fluid);
                if (key != null && Cell4Util.belongsToMod(key, modIdSet) && !blacklist.isBlacklisted(key)) total++;
            }
            for (AEKey chemicalKey : MekanismIntegration.getAllChemicalKeys()) {
                if (Cell4Util.belongsToMod(chemicalKey, modIdSet) && !blacklist.isBlacklisted(chemicalKey)) total++;
            }
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
