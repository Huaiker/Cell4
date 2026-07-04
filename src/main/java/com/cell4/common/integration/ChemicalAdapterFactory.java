package com.cell4.common.integration;

import net.minecraftforge.fml.ModList;

/**
 * Factory that resolves the active {@link IChemicalAdapter} at first use.
 * <p>
 * Resolution logic:
 * <ol>
 *   <li>If both {@code mekanism} and {@code appmek} are loaded, instantiate
 *       {@code MekanismChemicalAdapter} via {@code Class.forName}.</li>
 *   <li>Otherwise, return a singleton {@link NoopChemicalAdapter}.</li>
 * </ol>
 * </p>
 * <p>
 * <b>CRITICAL:</b> {@code MekanismChemicalAdapter} is loaded via reflection
 * ({@code Class.forName}) and NEVER via a direct {@code import} or {@code new}.
 * This guarantees that the JVM will not try to verify that class — and thus
 * will not try to resolve its Mekanism API imports — unless Mekanism is
 * actually present on the classpath.
 * </p>
 * <p>
 * The factory's static {@code INSTANCE} field is initialized lazily on the
 * first call to {@link #get()}. By that time the game is fully booted and
 * {@link ModList#get()} is reliable.
 * </p>
 */
public final class ChemicalAdapterFactory {

    private static final IChemicalAdapter INSTANCE = resolve();

    private ChemicalAdapterFactory() {}

    public static IChemicalAdapter get() {
        return INSTANCE;
    }

    private static IChemicalAdapter resolve() {
        try {
            boolean mekLoaded = isModLoaded("mekanism");
            boolean appmekLoaded = isModLoaded("appmek");
            if (mekLoaded && appmekLoaded) {
                // Load the adapter class via reflection. This is the ONLY place
                // in the entire codebase that mentions MekanismChemicalAdapter
                // by name — every other site only sees the IChemicalAdapter
                // interface, so they never trigger class loading of the impl.
                Class<?> clazz = Class.forName(
                        "com.cell4.common.integration.MekanismChemicalAdapter");
                return (IChemicalAdapter) clazz.getDeclaredConstructor().newInstance();
            }
        } catch (Throwable t) {
            // MekanismChemicalAdapter failed to load (should not happen if both
            // mods are present, but be defensive). Fall back to no-op.
        }
        return new NoopChemicalAdapter();
    }

    private static boolean isModLoaded(String modId) {
        try {
            return ModList.get().isLoaded(modId);
        } catch (Throwable t) {
            return false;
        }
    }
}
