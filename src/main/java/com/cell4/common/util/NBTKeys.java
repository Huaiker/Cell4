package com.cell4.common.util;

/**
 * Centralized NBT key constants for Cell4.
 * All NBT key names used across the mod are defined here
 * to avoid duplication and ensure consistency.
 */
public final class NBTKeys {
    private NBTKeys() {} // Prevent instantiation

    public static final String ITEM = "cell4item";
    public static final String TAG = "cell4tag";
    public static final String MODID = "cell4modid";
    public static final String BLACKLIST = "cell4blacklist";
    public static final String NAME = "cell4name";

    /** Custom item texture set name (folder under config/Cell4/texture). */
    public static final String TEXTURE = "cell4texture";
    /** Accepted alias (common typo) so existing items keep working. */
    public static final String TEXTURE_ALIAS = "cell4textrue";

    /** Portable cell energy (current/max/cost/charge rate). */
    public static final String POWER = "cell4power";
    public static final String POWER_MAX = "cell4power_max";
    public static final String POWER_COST = "cell4power_cost";
    public static final String POWER_CHARGE = "cell4power_charge";

    // Short aliases accepted for convenience (the config UI always writes cell4power_*).
    public static final String POWER_MAX_ALIAS = "power_max";
    public static final String POWER_COST_ALIAS = "power_cost";
    public static final String POWER_CHARGE_ALIAS = "power_charge";
}
