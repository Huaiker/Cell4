package com.cell4.common.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Energy helpers for portable infinity cells (1.20.1 NBT implementation).
 */
public final class PortableCellPower {

    /** 配置默认值的快照；只有配置实例变化时才重新读取，避免每次取电量都访问配置。 */
    private static Cell4Config defaultsSource;
    private static long defaultMaxPower = 100000L;
    private static long defaultCostPerItem = 20L;
    private static double defaultChargeRate = 8.0;

    private PortableCellPower() {}

    private static void refreshDefaults() {
        Cell4Config config = Cell4Config.get();
        if (config == defaultsSource) return;
        defaultsSource = config;
        Cell4Config.Portable portable = config.portable != null ? config.portable : new Cell4Config.Portable();
        defaultMaxPower = Math.max(0L, portable.defaultMaxPower);
        defaultCostPerItem = Math.max(0L, portable.defaultCostPerItem);
        defaultChargeRate = Math.max(0.0, portable.chargeRate);
    }

    public static double getEnergy(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(NBTKeys.POWER) ? tag.getDouble(NBTKeys.POWER) : 0.0;
    }

    public static void setEnergy(ItemStack stack, double energy) {
        long max = getMaxPower(stack);
        double value = max <= 0L ? 0.0 : Math.min(Math.max(0.0, energy), (double) max);
        stack.getOrCreateTag().putDouble(NBTKeys.POWER, value);
    }

    /** 0 means the cell does not need any energy at all. */
    public static long getMaxPower(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(NBTKeys.POWER_MAX)) return Math.max(0L, tag.getLong(NBTKeys.POWER_MAX));
        if (tag != null && tag.contains(NBTKeys.POWER_MAX_ALIAS)) return Math.max(0L, tag.getLong(NBTKeys.POWER_MAX_ALIAS));
        refreshDefaults();
        return defaultMaxPower;
    }

    public static long getCostPerItem(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(NBTKeys.POWER_COST)) return Math.max(0L, tag.getLong(NBTKeys.POWER_COST));
        if (tag != null && tag.contains(NBTKeys.POWER_COST_ALIAS)) return Math.max(0L, tag.getLong(NBTKeys.POWER_COST_ALIAS));
        refreshDefaults();
        return defaultCostPerItem;
    }

    /** Cost actually charged when extracting; max=0 disables energy entirely. */
    public static long getEffectiveCostPerItem(ItemStack stack) {
        return getMaxPower(stack) <= 0L ? 0L : getCostPerItem(stack);
    }

    public static double getConfiguredChargeRate(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(NBTKeys.POWER_CHARGE)) return Math.max(0.0, tag.getDouble(NBTKeys.POWER_CHARGE));
        if (tag != null && tag.contains(NBTKeys.POWER_CHARGE_ALIAS)) return Math.max(0.0, tag.getDouble(NBTKeys.POWER_CHARGE_ALIAS));
        refreshDefaults();
        return defaultChargeRate;
    }

    public static double getChargeRate(ItemStack stack) {
        if (getMaxPower(stack) <= 0L) return 0.0;
        return getConfiguredChargeRate(stack);
    }

    public static void setOverrides(ItemStack stack, String max, String cost, String charge) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.remove(NBTKeys.POWER_MAX_ALIAS);
        tag.remove(NBTKeys.POWER_COST_ALIAS);
        tag.remove(NBTKeys.POWER_CHARGE_ALIAS);
        putOrRemoveLong(tag, NBTKeys.POWER_MAX, max);
        putOrRemoveLong(tag, NBTKeys.POWER_COST, cost);
        putOrRemoveDouble(tag, NBTKeys.POWER_CHARGE, charge);
        if (tag.isEmpty()) stack.setTag(null);
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
