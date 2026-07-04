package com.cell4.common.integration;

import appeng.api.stacks.AEKey;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Mekanism-dependent implementation of {@link IChemicalAdapter} for
 * 1.21.1 NeoForge.
 * <p>
 * <b>CRITICAL:</b> This class imports Mekanism API classes. It MUST NOT be
 * loaded by the JVM when Mekanism is not installed, otherwise the JVM's
 * class verifier will throw {@code NoClassDefFoundError} while resolving
 * the imports.
 * </p>
 * <p>
 * To guarantee this, the class is never referenced via a normal {@code import}
 * or {@code new} statement anywhere in the codebase. It is only loaded via
 * {@code Class.forName} inside {@link ChemicalAdapterFactory}, AFTER the
 * factory has confirmed that Mekanism is loaded.
 * </p>
 * <p>
 * All Mekanism references are confined to method bodies (local variables
 * and method-internal calls). No field has a Mekanism type, and the class
 * only implements the Mekanism-free {@link IChemicalAdapter} interface.
 * This ensures that loading the class itself does not require any Mekanism
 * class to be resolvable until a method is actually invoked.
 * </p>
 * <p>
 * On 1.21.1 Mekanism exposes a single unified {@code Chemical} registry
 * ({@link MekanismAPI#CHEMICAL_REGISTRY}) covering gas / infuse type /
 * pigment / slurry, so the implementation is simpler than the 1.20.1
 * version which had four separate registries.
 * </p>
 * <p>
 * AppMek's {@code MekanismKey} is loaded via reflection because AppMek is
 * also an optional dependency.
 * </p>
 */
public final class MekanismChemicalAdapter implements IChemicalAdapter {

    // AppMek's MekanismKey reflection handles (resolved lazily on first use)
    private Class<?> mekanismKeyClass;
    private Method mekanismKeyOfMethod;
    private boolean reflectionTried = false;

    @Override
    public boolean isAvailable() {
        // We are only constructed when both mods are loaded (see ChemicalAdapterFactory),
        // so this is always true. Kept for interface completeness.
        return true;
    }

    private synchronized boolean initReflection() {
        if (reflectionTried) return mekanismKeyClass != null;
        reflectionTried = true;
        try {
            mekanismKeyClass = Class.forName("me.ramidzkh.mekae2.ae2.MekanismKey");
            mekanismKeyOfMethod = mekanismKeyClass.getMethod("of", ChemicalStack.class);
        } catch (Throwable t) {
            mekanismKeyClass = null;
        }
        return mekanismKeyClass != null;
    }

    private AEKey createKey(ChemicalStack stack) {
        if (!initReflection()) return null;
        try {
            return (AEKey) mekanismKeyOfMethod.invoke(null, stack);
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean isMekanismKey(AEKey key) {
        if (!initReflection()) return false;
        try {
            return mekanismKeyClass.isInstance(key);
        } catch (Throwable t) {
            return false;
        }
    }

    private AEKey createKey(Chemical chemical) {
        return createKey(new ChemicalStack(chemical, 1));
    }

    @Override
    public AEKey parseChemicalKey(ResourceLocation rl) {
        if (rl == null) return null;
        try {
            Chemical chemical = MekanismAPI.CHEMICAL_REGISTRY.get(rl);
            if (chemical == null || chemical == MekanismAPI.EMPTY_CHEMICAL) return null;
            return createKey(chemical);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public String getNamespace(AEKey key) {
        if (!isMekanismKey(key)) return null;
        try {
            ResourceLocation id = key.getId();
            return id != null ? id.getNamespace() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public boolean belongsToMod(AEKey key, Set<String> modIdSet) {
        if (!isMekanismKey(key)) return false;
        String ns = getNamespace(key);
        return ns != null && modIdSet.contains(ns);
    }

    @Override
    public boolean matchesTag(AEKey key, String tagName) {
        if (!isMekanismKey(key)) return false;
        try {
            ResourceLocation tagRL = ResourceLocation.tryParse(tagName);
            if (tagRL == null) return false;
            TagKey<Chemical> chemicalTag = TagKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, tagRL);
            return key.isTagged(chemicalTag);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public List<AEKey> getAllChemicalKeys() {
        List<AEKey> result = new ArrayList<>();
        try {
            for (Chemical chemical : MekanismAPI.CHEMICAL_REGISTRY) {
                if (chemical == null || chemical == MekanismAPI.EMPTY_CHEMICAL) continue;
                try {
                    AEKey k = createKey(chemical);
                    if (k != null) result.add(k);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            // fall through with whatever we collected
        }
        return result;
    }

    @Override
    public List<AEKey> getChemicalKeysByTag(String tagName) {
        List<AEKey> result = new ArrayList<>();
        try {
            ResourceLocation tagRL = ResourceLocation.tryParse(tagName);
            if (tagRL == null) return result;
            TagKey<Chemical> chemicalTag = TagKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, tagRL);
            for (Chemical chemical : MekanismAPI.CHEMICAL_REGISTRY) {
                if (chemical == null || chemical == MekanismAPI.EMPTY_CHEMICAL) continue;
                if (chemical.is(chemicalTag)) {
                    try {
                        AEKey k = createKey(chemical);
                        if (k != null) result.add(k);
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            // fall through with whatever we collected
        }
        return result;
    }
}
