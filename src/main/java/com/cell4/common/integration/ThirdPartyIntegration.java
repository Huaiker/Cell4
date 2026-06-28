package com.cell4.common.integration;

import appeng.api.stacks.AEKey;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reflection-based integration for third-party AE2 addon keys (1.21.1 NeoForge).
 * <p>
 * - ArsEnergistique Source: {@code energy:ars_source} → SourceKey.KEY
 * - AppBot Mana: {@code energy:botania_mana} → ManaKey.KEY (1.20.1 only, always null here)
 * - AppFlux FE: {@code energy:fe} → FluxKey.of(EnergyType.FE)
 */
public final class ThirdPartyIntegration {

    private static final boolean ARS_ENG_LOADED = isModLoaded("arseng");
    private static final boolean APPFLUX_LOADED = isModLoaded("appflux");

    private static AEKey sourceKey;
    private static boolean sourceTried = false;

    private static AEKey feKey;
    private static boolean feTried = false;

    private ThirdPartyIntegration() {}

    private static boolean isModLoaded(String modId) {
        try { return ModList.get().isLoaded(modId); } catch (Throwable t) { return false; }
    }

    public static boolean isArsEngLoaded() { return ARS_ENG_LOADED; }
    public static boolean isAppFluxLoaded() { return APPFLUX_LOADED; }

    public static AEKey getSourceKey() {
        if (!ARS_ENG_LOADED) return null;
        if (sourceTried) return sourceKey;
        sourceTried = true;
        try {
            Class<?> clazz = Class.forName("gripe._90.arseng.me.key.SourceKey");
            Field field = clazz.getField("KEY");
            Object obj = field.get(null);
            if (obj instanceof AEKey) sourceKey = (AEKey) obj;
        } catch (Throwable ignored) {}
        return sourceKey;
    }

    public static AEKey getManaKey() {
        // AppBot is 1.20.1 only — always null on 1.21.1
        return null;
    }

    public static AEKey getFEKey() {
        if (!APPFLUX_LOADED) return null;
        if (feTried) return feKey;
        feTried = true;
        try {
            Class<?> energyTypeClass = Class.forName("com.glodblock.github.appflux.common.me.key.type.EnergyType");
            Object feEnum = Enum.valueOf((Class<Enum>) energyTypeClass, "FE");
            Class<?> fluxKeyClass = Class.forName("com.glodblock.github.appflux.common.me.key.FluxKey");
            Method ofMethod = fluxKeyClass.getMethod("of", energyTypeClass);
            Object obj = ofMethod.invoke(null, feEnum);
            if (obj instanceof AEKey) feKey = (AEKey) obj;
        } catch (Throwable ignored) {}
        return feKey;
    }
}
