package com.cell4.common.item;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.FuzzyMode;
import appeng.items.tools.powered.AbstractPortableCell;
import appeng.items.contents.PortableCellMenuHost;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.me.common.MEStorageMenu;
import com.cell4.Cell4;
import com.cell4.common.util.PortableCellPower;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Portable infinity cell (26.1.2): 直接复用 AE2 的 AbstractPortableCell，
 * 右键打开 AE2 原版便携物品元件终端（MEStorageMenu），只替换能量与无限抽取逻辑。
 */
public class PortableInfinityCell extends AbstractPortableCell implements IInfinityCell {

    private final CellType cellType;

    public PortableInfinityCell(Properties properties, CellType cellType) {
        super(MEStorageMenu.PORTABLE_ITEM_CELL_TYPE, properties.stacksTo(1), 0x80caff);
        this.cellType = cellType;
    }

    public CellType getPortableCellType() { return cellType; }

    @Override
    public boolean isPortable() { return true; }

    @Override
    public CellType getCellType() { return cellType; }

    @Override
    public String translationKey() {
        return switch (cellType) {
            case ITEM -> "item.cell4.portable_infinity_item_cell";
            case TAG -> "item.cell4.portable_infinity_tag_cell";
            case MODID -> "item.cell4.portable_infinity_modid_cell";
        };
    }

    @Override
    public boolean canEditItem() { return cellType == CellType.ITEM; }
    @Override
    public boolean canEditTag() { return cellType == CellType.TAG; }
    @Override
    public boolean canEditModId() { return cellType == CellType.TAG || cellType == CellType.MODID; }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        return IInfinityCell.super.getDisplayName(stack);
    }

    @Override
    public Identifier getRecipeId() {
        return Identifier.fromNamespaceAndPath(Cell4.MODID, "portable_infinity_cell");
    }

    @Override
    public PortableCellMenuHost<?> getMenuHost(Player player, ItemMenuHostLocator locator,
                                               @Nullable BlockHitResult hitResult) {
        // 复用 AE2 的宿主，但关闭“终端待机耗电”；Cell4 的能量只按抽取数量计费。
        return new PortableCellMenuHost<>(this, player, locator,
                (p, sm) -> openFromInventory(p, locator, true)) {
            @Override
            protected double getPowerDrainPerTick() {
                return 0.0;
            }

            // AE2 默认的 SIMULATE 会按“当前能量”截断，导致 cost=0 / max=0 时也被判定没电。
            @Override
            public double extractAEPower(double amt, Actionable mode, PowerMultiplier usePowerMultiplier) {
                double converted = usePowerMultiplier.multiply(amt);
                return usePowerMultiplier.divide(
                        PortableInfinityCell.this.extractAEPower(getItemStack(), converted, mode));
            }
        };
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull Item.TooltipContext context,
                                 @NotNull TooltipDisplay display, @NotNull Consumer<Component> tooltipAdder,
                                 @NotNull TooltipFlag flag) {
        tooltipAdder.accept(Component.translatable("tooltip.cell4.infinity").withStyle(ChatFormatting.GREEN));
        switch (cellType) {
            case ITEM -> {
                List<String> ids = InfinityItemCell.getIdentifiers(stack);
                int uniqueCount = new java.util.LinkedHashSet<>(ids).size();
                if (uniqueCount > 1) {
                    tooltipAdder.accept(Component.translatable("tooltip.cell4.item_count", uniqueCount)
                            .withStyle(ChatFormatting.AQUA));
                }
            }
            case TAG -> {
                for (String tag : new java.util.LinkedHashSet<>(InfinityTagCell.getTagNames(stack))) {
                    tooltipAdder.accept(Component.translatable("tooltip.cell4.tag_filter", tag)
                            .withStyle(ChatFormatting.AQUA));
                }
                for (String id : new java.util.LinkedHashSet<>(InfinityTagCell.getModIds(stack))) {
                    tooltipAdder.accept(Component.translatable("tooltip.cell4.modid_filter", id)
                            .withStyle(ChatFormatting.LIGHT_PURPLE));
                }
            }
            case MODID -> {
                for (String id : new java.util.LinkedHashSet<>(InfinityModIdCell.getModIds(stack))) {
                    tooltipAdder.accept(Component.translatable("tooltip.cell4.modid_filter", id)
                            .withStyle(ChatFormatting.LIGHT_PURPLE));
                }
            }
        }
        if (PortableCellPower.getMaxPower(stack) <= 0L) {
            tooltipAdder.accept(Component.translatable("tooltip.cell4.portable_no_energy")
                    .withStyle(ChatFormatting.GOLD));
        } else {
            tooltipAdder.accept(Component.translatable("tooltip.cell4.portable_energy",
                            PortableCellPower.format(getAECurrentPower(stack)),
                            PortableCellPower.format(getAEMaxPower(stack)))
                    .withStyle(ChatFormatting.GOLD));
            long cost = PortableCellPower.getEffectiveCostPerItem(stack);
            tooltipAdder.accept(cost <= 0L
                    ? Component.translatable("tooltip.cell4.portable_cost_free").withStyle(ChatFormatting.GRAY)
                    : Component.translatable("tooltip.cell4.portable_cost", cost).withStyle(ChatFormatting.GRAY));
        }
        appendBlacklistTooltip(stack, tooltipAdder);
    }

    @NotNull
    @Override
    public Optional<TooltipComponent> getTooltipImage(@NotNull ItemStack stack) {
        return switch (cellType) {
            case ITEM -> InfinityItemCell.buildTooltipImage(stack);
            case TAG -> InfinityTagCell.buildTooltipImage(stack);
            case MODID -> InfinityModIdCell.buildTooltipImage(stack);
        };
    }

    // === Cell4 custom power (AE2 terminal 会把“抽取数量”传给 extractAEPower) ===

    @Override
    public double injectAEPower(ItemStack stack, double amount, Actionable mode) {
        double max = getAEMaxPower(stack);
        double current = getEnergy(stack);
        double accepted = Math.min(amount, Math.max(0.0, max - current));
        if (mode == Actionable.MODULATE && accepted > 0.0) {
            PortableCellPower.setEnergy(stack, current + accepted);
        }
        return amount - accepted;
    }

    @Override
    public double extractAEPower(ItemStack stack, double amount, Actionable mode) {
        long max = PortableCellPower.getMaxPower(stack);
        long cost = PortableCellPower.getEffectiveCostPerItem(stack);
        if (max <= 0L || cost <= 0L) {
            return amount;
        }
        double current = getEnergy(stack);
        double affordable = Math.min(amount, current / cost);
        if (mode == Actionable.MODULATE && affordable > 0.0) {
            PortableCellPower.setEnergy(stack, current - affordable * cost);
        }
        return affordable;
    }

    @Override
    public double getAEMaxPower(ItemStack stack) {
        return Math.max(0.0, (double) PortableCellPower.getMaxPower(stack));
    }

    @Override
    public double getAECurrentPower(ItemStack stack) {
        return PortableCellPower.getEnergy(stack);
    }

    @Override
    public AccessRestriction getPowerFlow(ItemStack stack) {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public double getChargeRate(ItemStack stack) {
        return PortableCellPower.getChargeRate(stack);
    }

    private double getEnergy(ItemStack stack) {
        return PortableCellPower.getEnergy(stack);
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack itemStack) { return IInfinityCell.super.getFuzzyMode(itemStack); }
    @Override
    public void setFuzzyMode(ItemStack itemStack, FuzzyMode fuzzyMode) { IInfinityCell.super.setFuzzyMode(itemStack, fuzzyMode); }
}
