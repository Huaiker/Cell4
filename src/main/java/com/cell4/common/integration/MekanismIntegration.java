package com.cell4.common.integration;

import appeng.api.stacks.AEKey;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;

/**
 * Facade over {@link IChemicalAdapter} for backward compatibility with
 * existing call sites that use the {@code MekanismIntegration} static API.
 * <p>
 * <b>CRITICAL:</b> This class MUST NOT import any Mekanism API class.
 * All Mekanism-dependent logic lives inside {@code MekanismChemicalAdapter},
 * which is only loaded via {@link ChemicalAdapterFactory} when Mekanism is
 * present. This is the fix for the original crash where Mekanism's hard
 * imports caused {@code NoClassDefFoundError} when Mekanism was not
 * installed.
 * </p>
 * <p>
 * Every method delegates to the adapter resolved by
 * {@link ChemicalAdapterFactory#get()}:
 * <ul>
 *   <li>If Mekanism + AppMek are loaded, the call goes to
 *       {@code MekanismChemicalAdapter} (real behavior).</li>
 *   <li>Otherwise, it goes to {@link NoopChemicalAdapter} (returns
 *       {@code null} / {@code false} / empty list, which callers already
 *       handle).</li>
 * </ul>
 * </p>
 */
public final class MekanismIntegration {

    private MekanismIntegration() {}

    private static IChemicalAdapter adapter() {
        return ChemicalAdapterFactory.get();
    }

    public static boolean isMekanismLoaded() {
        return adapter().isAvailable();
    }

    public static boolean isAvailable() {
        return adapter().isAvailable();
    }

    public static AEKey parseChemicalKey(ResourceLocation rl) {
        return adapter().parseChemicalKey(rl);
    }

    public static String getNamespace(AEKey key) {
        return adapter().getNamespace(key);
    }

    public static boolean belongsToMod(AEKey key, Set<String> modIdSet) {
        return adapter().belongsToMod(key, modIdSet);
    }

    public static boolean matchesTag(AEKey key, String tagName) {
        return adapter().matchesTag(key, tagName);
    }

    public static List<AEKey> getAllChemicalKeys() {
        return adapter().getAllChemicalKeys();
    }

    public static List<AEKey> getChemicalKeysByTag(String tagName) {
        return adapter().getChemicalKeysByTag(tagName);
    }
}
