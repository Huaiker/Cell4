package com.cell4.common.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Energy helpers for portable infinity cells.
 * <p>
 * Values can be overridden per item through custom data:
 * <ul>
 *   <li>{@code cell4power} — current AE energy (double)</li>
 *   <li>{@code cell4power_max} — capacity in AE (long)</li>
 *   <li>{@code cell4power_cost} — AE cost per extracted item (long)</li>
 *   <li>{@code cell4power_charge} — AE per charger tick (double, optional)</li>
 * </ul>
 * Defaults come from config/Cell4/config.json.
 */
public final class PortableCellPower {

    private PortableCellPower() {}

    public static double getEnergy(ItemStack stack) {
        CompoundTag tag = Cell4Util.getCustomTag(stack);
        return tag.contains(NBTKeys.POWER) ? tag.getDouble(NBTKeys.POWER) : 0.0;
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
        if (tag.contains(NBTKeys.POWER_MAX)) return Math.max(0L, tag.getLong(NBTKeys.POWER_MAX));
        if (tag.contains(NBTKeys.POWER_MAX_ALIAS)) return Math.max(0L, tag.getLong(NBTKeys.POWER_MAX_ALIAS));
        return Math.max(0L, Cell4Config.get().portable.defaultMaxPower);
    }

    public static long getCostPerItem(ItemStack stack) {
        CompoundTag tag = Cell4Util.getCustomTag(stack);
        if (tag.contains(NBTKeys.POWER_COST)) return Math.max(0L, tag.getLong(NBTKeys.POWER_COST));
        if (tag.contains(NBTKeys.POWER_COST_ALIAS)) return Math.max(0L, tag.getLong(NBTKeys.POWER_COST_ALIAS));
        return Math.max(0L, Cell4Config.get().portable.defaultCostPerItem);
    }

    /** Cost actually charged when extracting; max=0 disables energy entirely. */
    public static long getEffectiveCostPerItem(ItemStack stack) {
        return getMaxPower(stack) <= 0L ? 0L : getCostPerItem(stack);
    }

    /** Stored/configured charge rate, independent of whether max is currently 0. */
    public static double getConfiguredChargeRate(ItemStack stack) {
        CompoundTag tag = Cell4Util.getCustomTag(stack);
        if (tag.contains(NBTKeys.POWER_CHARGE)) return Math.max(0.0, tag.getDouble(NBTKeys.POWER_CHARGE));
        if (tag.contains(NBTKeys.POWER_CHARGE_ALIAS)) return Math.max(0.0, tag.getDouble(NBTKeys.POWER_CHARGE_ALIAS));
        return Math.max(0.0, Cell4Config.get().portable.chargeRate);
    }

    public static double getChargeRate(ItemStack stack) {
        if (getMaxPower(stack) <= 0L) return 0.0;
        return getConfiguredChargeRate(stack);
    }

    /**
     * Write the three UI values. Empty strings remove the override so the
     * config default applies again.
     */
    public static void setOverrides(ItemStack stack, String max, String cost, String charge) {
        CompoundTag tag = Cell4Util.getCustomTag(stack);
        tag.remove(NBTKeys.POWER_MAX_ALIAS);
        tag.remove(NBTKeys.POWER_COST_ALIAS);
        tag.remove(NBTKeys.POWER_CHARGE_ALIAS);
        putOrRemoveLong(tag, NBTKeys.POWER_MAX, max);
        putOrRemoveLong(tag, NBTKeys.POWER_COST, cost);
        putOrRemoveDouble(tag, NBTKeys.POWER_CHARGE, charge);
        Cell4Util.setCustomTag(stack, tag);
        // Setting max to 0 also drops any stored energy.
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
