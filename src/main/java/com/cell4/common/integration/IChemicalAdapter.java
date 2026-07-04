package com.cell4.common.integration;

import appeng.api.stacks.AEKey;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;

/**
 * Adapter interface for Mekanism chemical AEKey integration.
 * <p>
 * IMPORTANT: This interface MUST NOT reference any Mekanism API class.
 * All method signatures use only AE2 / Minecraft types so that this
 * interface (and any class that holds a reference to it) can be safely
 * loaded by the JVM even when Mekanism is not installed.
 * </p>
 * <p>
 * The actual Mekanism-dependent implementation lives in
 * {@code MekanismChemicalAdapter}, which is loaded via {@code Class.forName}
 * only when both Mekanism and AppMek are present (see
 * {@link ChemicalAdapterFactory}).
 * </p>
 */
public interface IChemicalAdapter {

    /**
     * @return {@code true} if Mekanism + AppMek are loaded and the adapter
     *         can actually produce chemical AEKeys.
     */
    boolean isAvailable();

    /**
     * Parse a ResourceLocation into a Mekanism chemical AEKey.
     *
     * @param rl the chemical id (e.g. {@code mekanism:oxygen})
     * @return the AEKey, or {@code null} if Mekanism is not available
     *         or the chemical does not exist
     */
    AEKey parseChemicalKey(ResourceLocation rl);

    /**
     * Get the namespace (mod id) of a chemical AEKey.
     *
     * @return the namespace, or {@code null} if the key is not a Mekanism key
     *         or Mekanism is not available
     */
    String getNamespace(AEKey key);

    /**
     * Check whether a chemical AEKey belongs to any of the given mod ids.
     *
     * @return {@code false} if Mekanism is not available or the key is not
     *         a Mekanism key
     */
    boolean belongsToMod(AEKey key, Set<String> modIdSet);

    /**
     * Check whether a chemical AEKey matches the given tag name.
     *
     * @return {@code false} if Mekanism is not available or the key is not
     *         a Mekanism key
     */
    boolean matchesTag(AEKey key, String tagName);

    /**
     * Enumerate ALL registered chemical AEKeys.
     *
     * @return an empty list if Mekanism is not available
     */
    List<AEKey> getAllChemicalKeys();

    /**
     * Enumerate all chemical AEKeys matching the given tag name.
     *
     * @return an empty list if Mekanism is not available
     */
    List<AEKey> getChemicalKeysByTag(String tagName);
}
