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
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * AppMek-only integration for Mekanism chemicals (1.20.1 Forge).
 * Uses AppMek's MekanismKey via reflection. No Cell4 chemical key type.
 */
public final class MekanismIntegration {

    private static final boolean MEKANISM_LOADED = checkMod("mekanism");
    private static final boolean APPMEK_LOADED = checkMod("appmek");

    private static Class<?> mekanismKeyClass;
    private static Method mekanismKeyOfMethod;
    private static boolean reflectionTried = false;

    private MekanismIntegration() {}

    private static boolean checkMod(String modId) {
        try { return ModList.get().isLoaded(modId); } catch (Throwable t) { return false; }
    }

    public static boolean isMekanismLoaded() { return MEKANISM_LOADED; }
    public static boolean isAvailable() { return MEKANISM_LOADED && APPMEK_LOADED; }

    private static synchronized boolean initReflection() {
        if (reflectionTried) return mekanismKeyClass != null;
        reflectionTried = true;
        if (!APPMEK_LOADED) return false;
        try {
            mekanismKeyClass = Class.forName("me.ramidzkh.mekae2.ae2.MekanismKey");
            mekanismKeyOfMethod = mekanismKeyClass.getMethod("of", ChemicalStack.class);
        } catch (Throwable t) { mekanismKeyClass = null; }
        return mekanismKeyClass != null;
    }

    private static AEKey createKey(ChemicalStack<?> stack) {
        if (!initReflection()) return null;
        try { return (AEKey) mekanismKeyOfMethod.invoke(null, stack); } catch (Throwable t) { return null; }
    }

    private static boolean isMekanismKey(AEKey key) {
        if (!initReflection()) return false;
        try { return mekanismKeyClass.isInstance(key); } catch (Throwable t) { return false; }
    }

    private static AEKey createKey(Gas gas) { if (!isAvailable()) return null; return createKey(new GasStack(gas, 1)); }
    private static AEKey createKey(InfuseType inf) { if (!isAvailable()) return null; return createKey(new InfusionStack(inf, 1)); }
    private static AEKey createKey(Pigment pig) { if (!isAvailable()) return null; return createKey(new PigmentStack(pig, 1)); }
    private static AEKey createKey(Slurry slu) { if (!isAvailable()) return null; return createKey(new SlurryStack(slu, 1)); }

    public static AEKey parseChemicalKey(ResourceLocation rl) {
        if (!isAvailable() || rl == null) return null;
        try {
            Gas gas = MekanismAPI.gasRegistry().getValue(rl);
            if (gas != null && !gas.isEmptyType()) return createKey(gas);
            InfuseType inf = MekanismAPI.infuseTypeRegistry().getValue(rl);
            if (inf != null && !inf.isEmptyType()) return createKey(inf);
            Pigment pig = MekanismAPI.pigmentRegistry().getValue(rl);
            if (pig != null && !pig.isEmptyType()) return createKey(pig);
            Slurry slu = MekanismAPI.slurryRegistry().getValue(rl);
            if (slu != null && !slu.isEmptyType()) return createKey(slu);
        } catch (Throwable t) {}
        return null;
    }

    public static String getNamespace(AEKey key) {
        if (!isAvailable() || !isMekanismKey(key)) return null;
        try { ResourceLocation id = key.getId(); return id != null ? id.getNamespace() : null; } catch (Throwable t) { return null; }
    }

    public static boolean belongsToMod(AEKey key, java.util.Set<String> modIdSet) {
        if (!isAvailable() || !isMekanismKey(key)) return false;
        String ns = getNamespace(key); return ns != null && modIdSet.contains(ns);
    }

    public static boolean matchesTag(AEKey key, String tagName) {
        if (!isAvailable() || !isMekanismKey(key)) return false;
        try {
            ResourceLocation tagRL = ResourceLocation.tryParse(tagName);
            if (tagRL == null) return false;
            if (key.isTagged(ChemicalTags.GAS.tag(tagRL))) return true;
            if (key.isTagged(ChemicalTags.INFUSE_TYPE.tag(tagRL))) return true;
            if (key.isTagged(ChemicalTags.PIGMENT.tag(tagRL))) return true;
            if (key.isTagged(ChemicalTags.SLURRY.tag(tagRL))) return true;
        } catch (Throwable t) {}
        return false;
    }

    public static List<AEKey> getAllChemicalKeys() {
        List<AEKey> result = new ArrayList<>();
        if (!isAvailable()) return result;
        try {
            for (Gas gas : MekanismAPI.gasRegistry()) { if (gas == null || gas.isEmptyType()) continue; try { result.add(createKey(gas)); } catch (Throwable ignored) {} }
            for (InfuseType inf : MekanismAPI.infuseTypeRegistry()) { if (inf == null || inf.isEmptyType()) continue; try { result.add(createKey(inf)); } catch (Throwable ignored) {} }
            for (Pigment pig : MekanismAPI.pigmentRegistry()) { if (pig == null || pig.isEmptyType()) continue; try { result.add(createKey(pig)); } catch (Throwable ignored) {} }
            for (Slurry slu : MekanismAPI.slurryRegistry()) { if (slu == null || slu.isEmptyType()) continue; try { result.add(createKey(slu)); } catch (Throwable ignored) {} }
        } catch (Throwable t) {}
        return result;
    }

    public static List<AEKey> getChemicalKeysByTag(String tagName) {
        List<AEKey> result = new ArrayList<>();
        if (!isAvailable()) return result;
        try {
            ResourceLocation tagRL = ResourceLocation.tryParse(tagName);
            if (tagRL == null) return result;
            TagKey<Gas> gasTag = ChemicalTags.GAS.tag(tagRL);
            for (Gas gas : MekanismAPI.gasRegistry()) { if (gas == null || gas.isEmptyType()) continue; if (gas.is(gasTag)) { try { result.add(createKey(gas)); } catch (Throwable ignored) {} } }
            TagKey<InfuseType> infTag = ChemicalTags.INFUSE_TYPE.tag(tagRL);
            for (InfuseType inf : MekanismAPI.infuseTypeRegistry()) { if (inf == null || inf.isEmptyType()) continue; if (inf.is(infTag)) { try { result.add(createKey(inf)); } catch (Throwable ignored) {} } }
            TagKey<Pigment> pigTag = ChemicalTags.PIGMENT.tag(tagRL);
            for (Pigment pig : MekanismAPI.pigmentRegistry()) { if (pig == null || pig.isEmptyType()) continue; if (pig.is(pigTag)) { try { result.add(createKey(pig)); } catch (Throwable ignored) {} } }
            TagKey<Slurry> sluTag = ChemicalTags.SLURRY.tag(tagRL);
            for (Slurry slu : MekanismAPI.slurryRegistry()) { if (slu == null || slu.isEmptyType()) continue; if (slu.is(sluTag)) { try { result.add(createKey(slu)); } catch (Throwable ignored) {} } }
        } catch (Throwable t) {}
        return result;
    }
}
