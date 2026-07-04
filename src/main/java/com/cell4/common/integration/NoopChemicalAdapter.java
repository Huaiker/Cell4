package com.cell4.common.integration;

import appeng.api.stacks.AEKey;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * No-op implementation of {@link IChemicalAdapter}.
 * <p>
 * Returned by {@link ChemicalAdapterFactory} when Mekanism or AppMek is
 * not installed. Every method returns {@code null}, {@code false}, or an
 * empty list, which is exactly what the original {@code MekanismIntegration}
 * returned in the "not available" branch.
 * </p>
 * <p>
 * This class MUST NOT reference any Mekanism API class so that it can be
 * safely loaded without Mekanism on the classpath.
 * </p>
 */
public final class NoopChemicalAdapter implements IChemicalAdapter {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public AEKey parseChemicalKey(ResourceLocation rl) {
        return null;
    }

    @Override
    public String getNamespace(AEKey key) {
        return null;
    }

    @Override
    public boolean belongsToMod(AEKey key, Set<String> modIdSet) {
        return false;
    }

    @Override
    public boolean matchesTag(AEKey key, String tagName) {
        return false;
    }

    @Override
    public List<AEKey> getAllChemicalKeys() {
        return Collections.emptyList();
    }

    @Override
    public List<AEKey> getChemicalKeysByTag(String tagName) {
        return Collections.emptyList();
    }
}
