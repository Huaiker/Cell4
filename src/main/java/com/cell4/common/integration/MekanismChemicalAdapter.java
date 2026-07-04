package com.cell4.common.integration;

import appeng.api.stacks.AEKey;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.ChemicalTags;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.infuse.InfuseType;
import mekanism.api.chemical.infuse.InfusionStack;
import mekanism.api.chemical.pigment.Pigment;
import mekanism.api.chemical.pigment.PigmentStack;
import mekanism.api.chemical.slurry.Slurry;
import mekanism.api.chemical.slurry.SlurryStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Mekanism-dependent implementation of {@link IChemicalAdapter} for
 * 1.20.1 Forge.
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

    private AEKey createKey(ChemicalStack<?> stack) {
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

    private AEKey createKey(Gas gas) {
        return createKey(new GasStack(gas, 1));
    }

    private AEKey createKey(InfuseType inf) {
        return createKey(new InfusionStack(inf, 1));
    }

    private AEKey createKey(Pigment pig) {
        return createKey(new PigmentStack(pig, 1));
    }

    private AEKey createKey(Slurry slu) {
        return createKey(new SlurryStack(slu, 1));
    }

    @Override
    public AEKey parseChemicalKey(ResourceLocation rl) {
        if (rl == null) return null;
        try {
            Gas gas = MekanismAPI.gasRegistry().getValue(rl);
            if (gas != null && !gas.isEmptyType()) return createKey(gas);
            InfuseType inf = MekanismAPI.infuseTypeRegistry().getValue(rl);
            if (inf != null && !inf.isEmptyType()) return createKey(inf);
            Pigment pig = MekanismAPI.pigmentRegistry().getValue(rl);
            if (pig != null && !pig.isEmptyType()) return createKey(pig);
            Slurry slu = MekanismAPI.slurryRegistry().getValue(rl);
            if (slu != null && !slu.isEmptyType()) return createKey(slu);
        } catch (Throwable t) {
            // fall through
        }
        return null;
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
            if (key.isTagged(ChemicalTags.GAS.tag(tagRL))) return true;
            if (key.isTagged(ChemicalTags.INFUSE_TYPE.tag(tagRL))) return true;
            if (key.isTagged(ChemicalTags.PIGMENT.tag(tagRL))) return true;
            if (key.isTagged(ChemicalTags.SLURRY.tag(tagRL))) return true;
        } catch (Throwable t) {
            // fall through
        }
        return false;
    }

    @Override
    public List<AEKey> getAllChemicalKeys() {
        List<AEKey> result = new ArrayList<>();
        try {
            for (Gas gas : MekanismAPI.gasRegistry()) {
                if (gas == null || gas.isEmptyType()) continue;
                try { result.add(createKey(gas)); } catch (Throwable ignored) {}
            }
            for (InfuseType inf : MekanismAPI.infuseTypeRegistry()) {
                if (inf == null || inf.isEmptyType()) continue;
                try { result.add(createKey(inf)); } catch (Throwable ignored) {}
            }
            for (Pigment pig : MekanismAPI.pigmentRegistry()) {
                if (pig == null || pig.isEmptyType()) continue;
                try { result.add(createKey(pig)); } catch (Throwable ignored) {}
            }
            for (Slurry slu : MekanismAPI.slurryRegistry()) {
                if (slu == null || slu.isEmptyType()) continue;
                try { result.add(createKey(slu)); } catch (Throwable ignored) {}
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

            TagKey<Gas> gasTag = ChemicalTags.GAS.tag(tagRL);
            for (Gas gas : MekanismAPI.gasRegistry()) {
                if (gas == null || gas.isEmptyType()) continue;
                if (gas.is(gasTag)) {
                    try { result.add(createKey(gas)); } catch (Throwable ignored) {}
                }
            }
            TagKey<InfuseType> infTag = ChemicalTags.INFUSE_TYPE.tag(tagRL);
            for (InfuseType inf : MekanismAPI.infuseTypeRegistry()) {
                if (inf == null || inf.isEmptyType()) continue;
                if (inf.is(infTag)) {
                    try { result.add(createKey(inf)); } catch (Throwable ignored) {}
                }
            }
            TagKey<Pigment> pigTag = ChemicalTags.PIGMENT.tag(tagRL);
            for (Pigment pig : MekanismAPI.pigmentRegistry()) {
                if (pig == null || pig.isEmptyType()) continue;
                if (pig.is(pigTag)) {
                    try { result.add(createKey(pig)); } catch (Throwable ignored) {}
                }
            }
            TagKey<Slurry> sluTag = ChemicalTags.SLURRY.tag(tagRL);
            for (Slurry slu : MekanismAPI.slurryRegistry()) {
                if (slu == null || slu.isEmptyType()) continue;
                if (slu.is(sluTag)) {
                    try { result.add(createKey(slu)); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            // fall through with whatever we collected
        }
        return result;
    }
}
