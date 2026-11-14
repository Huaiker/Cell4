package com.cell4.common.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Energy helpers for portable infinity cells (data-component implementation).
 */
public final class PortableCellPower {

    /** 区分“NBT 没有该值”与真实值，避免每次都急切读取配置默认值。 */
    private static final long UNSPECIFIED_LONG = Long.MIN_VALUE;
    private static final double UNSPECIFIED_DOUBLE = Double.NaN;

    private PortableCellPower() {}

    public static double getEnergy(ItemStack stack) {
        CompoundTag tag = Cell4Util.getCustomTag(stack);
        return Math.max(0.0, tag.getDoubleOr(NBTKeys.POWER, 0.0));
    }

    public static void setEnergy(ItemStack stack, double energy) {
        long max = getMaxPower(stack);
        double value = max <= 0L ? 0.0 : Math.min(Math.max(0.0, energy), (double) max);
        CompoundTag tag = Cell4Util.getCustomTag(stack);
        tag.putDouble(NBTKeys.POWER, value);
        Cell4Util.setCustomTag(stack, tag);
    }

    /** 0 means the cell does not need any energy at all. */
    public static long getMaxPower(ItemStack stack) {
        CompoundTag tag = Cell4Util.getCustomTag(stack);
        long value = tag.getLongOr(NBTKeys.POWER_MAX, UNSPECIFIED_LONG);
        if (value == UNSPECIFIED_LONG) {
            value = tag.getLongOr(NBTKeys.POWER_MAX_ALIAS, UNSPECIFIED_LONG);
        }
        if (value == UNSPECIFIED_LONG) {
            return Math.max(0L, Cell4Config.get().portable.defaultMaxPower);
        }
        return Math.max(0L, value);
    }

    public static long getCostPerItem(ItemStack stack) {
        CompoundTag tag = Cell4Util.getCustomTag(stack);
        long value = tag.getLongOr(NBTKeys.POWER_COST, UNSPECIFIED_LONG);
        if (value == UNSPECIFIED_LONG) {
            value = tag.getLongOr(NBTKeys.POWER_COST_ALIAS, UNSPECIFIED_LONG);
        }
        if (value == UNSPECIFIED_LONG) {
            return Math.max(0L, Cell4Config.get().portable.defaultCostPerItem);
        }
        return Math.max(0L, value);
    }

    /** Cost actually charged when extracting; max=0 disables energy entirely. */
    public static long getEffectiveCostPerItem(ItemStack stack) {
        return getMaxPower(stack) <= 0L ? 0L : getCostPerItem(stack);
    }

    public static double getConfiguredChargeRate(ItemStack stack) {
        CompoundTag tag = Cell4Util.getCustomTag(stack);
        double value = tag.getDoubleOr(NBTKeys.POWER_CHARGE, UNSPECIFIED_DOUBLE);
        if (Double.isNaN(value)) {
            value = tag.getDoubleOr(NBTKeys.POWER_CHARGE_ALIAS, UNSPECIFIED_DOUBLE);
        }
        if (Double.isNaN(value)) {
            return Math.max(0.0, Cell4Config.get().portable.chargeRate);
        }
        return Math.max(0.0, value);
    }

    public static double getChargeRate(ItemStack stack) {
        if (getMaxPower(stack) <= 0L) return 0.0;
        return getConfiguredChargeRate(stack);
    }

    public static void setOverrides(ItemStack stack, String max, String cost, String charge) {
        CompoundTag tag = Cell4Util.getCustomTag(stack);
        tag.remove(NBTKeys.POWER_MAX_ALIAS);
        tag.remove(NBTKeys.POWER_COST_ALIAS);
        tag.remove(NBTKeys.POWER_CHARGE_ALIAS);
        putOrRemoveLong(tag, NBTKeys.POWER_MAX, max);
        putOrRemoveLong(tag, NBTKeys.POWER_COST, cost);
        putOrRemoveDouble(tag, NBTKeys.POWER_CHARGE, charge);
        Cell4Util.setCustomTag(stack, tag);
        if (getMaxPower(stack) <= 0L) setEnergy(stack, 0.0);
    }

    private static void putOrRemoveLong(CompoundTag tag, String key, String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            tag.remove(key);
            return;
        }
        try {
            tag.putLong(key, Math.max(0L, Long.parseLong(raw.trim())));
        } catch (NumberFormatException ignored) {
            tag.remove(key);
        }
    }

    private static void putOrRemoveDouble(CompoundTag tag, String key, String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            tag.remove(key);
            return;
        }
        try {
            tag.putDouble(key, Math.max(0.0, Double.parseDouble(raw.trim())));
        } catch (NumberFormatException ignored) {
            tag.remove(key);
        }
    }

    public static boolean consume(ItemStack stack, double amount) {
        if (getMaxPower(stack) <= 0L) return true;
        double current = getEnergy(stack);
        if (current < amount) return false;
        setEnergy(stack, current - amount);
        return true;
    }

    public static String format(double value) {
        if (value >= 1_000_000_000.0) return String.format("%.1fG", value / 1_000_000_000.0);
        if (value >= 1_000_000.0) return String.format("%.1fM", value / 1_000_000.0);
        if (value >= 1_000.0) return String.format("%.1fk", value / 1_000.0);
        return Long.toString((long) value);
    }
}
