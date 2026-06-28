package com.cell4.common.integration;

import appeng.api.stacks.AEKey;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * AppMek-only integration for Mekanism chemicals (1.21.1 NeoForge).
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

    private static AEKey createKey(ChemicalStack stack) {
        if (!initReflection()) return null;
        try { return (AEKey) mekanismKeyOfMethod.invoke(null, stack); }
        catch (Throwable t) { return null; }
    }

    private static boolean isMekanismKey(AEKey key) {
        if (!initReflection()) return false;
        try { return mekanismKeyClass.isInstance(key); } catch (Throwable t) { return false; }
    }

    private static AEKey createKey(Chemical chemical) {
        if (!isAvailable()) return null;
        try { return createKey(new ChemicalStack(chemical, 1)); } catch (Throwable t) { return null; }
    }

    public static AEKey parseChemicalKey(ResourceLocation rl) {
        if (!isAvailable() || rl == null) return null;
        try {
            Chemical chemical = MekanismAPI.CHEMICAL_REGISTRY.get(rl);
            if (chemical == null || chemical == MekanismAPI.EMPTY_CHEMICAL) return null;
            return createKey(chemical);
        } catch (Throwable t) { return null; }
    }

    public static String getNamespace(AEKey key) {
        if (!isAvailable() || !isMekanismKey(key)) return null;
        try { ResourceLocation id = key.getId(); return id != null ? id.getNamespace() : null; }
        catch (Throwable t) { return null; }
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
            TagKey<Chemical> chemicalTag = TagKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, tagRL);
            return key.isTagged(chemicalTag);
        } catch (Throwable t) { return false; }
    }

    public static List<AEKey> getAllChemicalKeys() {
        List<AEKey> result = new ArrayList<>();
        if (!isAvailable()) return result;
        try {
            for (Chemical chemical : MekanismAPI.CHEMICAL_REGISTRY) {
                if (chemical == null || chemical == MekanismAPI.EMPTY_CHEMICAL) continue;
                try { AEKey k = createKey(chemical); if (k != null) result.add(k); } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {}
        return result;
    }

    public static List<AEKey> getChemicalKeysByTag(String tagName) {
        List<AEKey> result = new ArrayList<>();
        if (!isAvailable()) return result;
        try {
            ResourceLocation tagRL = ResourceLocation.tryParse(tagName);
            if (tagRL == null) return result;
            TagKey<Chemical> chemicalTag = TagKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, tagRL);
            for (Chemical chemical : MekanismAPI.CHEMICAL_REGISTRY) {
                if (chemical == null || chemical == MekanismAPI.EMPTY_CHEMICAL) continue;
                if (chemical.is(chemicalTag)) {
                    try { AEKey k = createKey(chemical); if (k != null) result.add(k); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {}
        return result;
    }
}
