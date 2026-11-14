package com.cell4.common.util;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.cell4.Cell4;
import com.cell4.common.integration.MekanismIntegration;
import com.cell4.common.integration.ThirdPartyIntegration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Glob / regex support for cell filters.
 * <p>
 * Supported syntax:
 * <ul>
 *   <li>{@code minecraft:*} / {@code *diamond*} / {@code minecraft:*_ore} — glob (also {@code glob:} prefix)</li>
 *   <li>{@code regex:.*diamond.*} — Java regular expression</li>
 *   <li>{@code mekfluid:*} — glob limited to fluids</li>
 *   <li>{@code mekchemical:*} — glob limited to Mekanism chemicals</li>
 *   <li>{@code energy:*} — glob limited to the supported energy keys</li>
 * </ul>
 * Patterns are matched against the AEKey id ({@code namespace:path}).
 */
public final class Cell4Patterns {

    private Cell4Patterns() {}

    private static final String REGEX_PREFIX = "regex:";
    private static final String GLOB_PREFIX = "glob:";
    private static final String FLUID_PREFIX = "mekfluid:";
    private static final String CHEMICAL_PREFIX = "mekchemical:";
    private static final String ENERGY_PREFIX = "energy:";

    /** Maximum number of keys a single pattern may expand to. */
    private static final int MAX_EXPANSION = 4096;

    private static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, List<AEKey>> EXPANSION_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, List<AEKey>> RESOLVE_CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_ENTRIES = 128;
    /** 已提示过的无法解析条目（摘要），避免反复解析时刷屏。 */
    private static final Set<String> WARNED_ENTRIES = ConcurrentHashMap.newKeySet();
    private static final int MAX_WARNED_ENTRIES = 512;

    // === Detection ===

    /** True if the entry contains glob/regex syntax (with or without an explicit prefix). */
    public static boolean isPattern(String raw) {
        if (raw == null || raw.isEmpty()) return false;
        // Full item NBT compounds ({id:...,Count:...,tag:{...}}) are exact entries, not patterns.
        if (raw.startsWith("{")) return false;
        String body = stripDomainPrefix(raw);
        if (body.startsWith(REGEX_PREFIX) || body.startsWith(GLOB_PREFIX)) return true;
        return body.indexOf('*') >= 0 || body.indexOf('?') >= 0 || body.indexOf('[') >= 0;
    }

    /** True if this is a pattern and it compiles without errors. */
    public static boolean isValidPattern(String raw) {
        if (!isPattern(raw)) return false;
        try {
            return compile(stripDomainPrefix(raw)) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String stripDomainPrefix(String raw) {
        if (raw.startsWith(FLUID_PREFIX)) return raw.substring(FLUID_PREFIX.length());
        if (raw.startsWith(CHEMICAL_PREFIX)) return raw.substring(CHEMICAL_PREFIX.length());
        if (raw.startsWith(ENERGY_PREFIX)) return raw.substring(ENERGY_PREFIX.length());
        return raw;
    }

    // === Matching ===

    public static boolean matches(String raw, AEKey key) {
        if (raw == null || key == null) return false;
        String id;
        try {
            id = key.getId().toString();
        } catch (Throwable t) {
            return false;
        }
        return matchesId(raw, id, key);
    }

    private static boolean matchesId(String raw, String id, AEKey key) {
        if (raw.startsWith(FLUID_PREFIX)) {
            if (!(key instanceof AEFluidKey)) return false;
            return matchesBody(raw.substring(FLUID_PREFIX.length()), id);
        }
        if (raw.startsWith(CHEMICAL_PREFIX)) {
            if (key instanceof AEItemKey || key instanceof AEFluidKey) return false;
            if (MekanismIntegration.getNamespace(key) == null) return false;
            return matchesBody(raw.substring(CHEMICAL_PREFIX.length()), id);
        }
        if (raw.startsWith(ENERGY_PREFIX)) {
            return matchesEnergy(raw.substring(ENERGY_PREFIX.length()), key);
        }
        return matchesBody(raw, id);
    }

    private static boolean matchesBody(String body, String id) {
        Pattern pattern = compile(body);
        return pattern != null && pattern.matcher(id).matches();
    }

    private static boolean matchesEnergy(String body, AEKey key) {
        AEKey source = ThirdPartyIntegration.getSourceKey();
        AEKey mana = ThirdPartyIntegration.getManaKey();
        AEKey fe = ThirdPartyIntegration.getFEKey();
        if (key == source && matchesBody(body, "ars_source")) return true;
        if (key == mana && matchesBody(body, "botania_mana")) return true;
        if (key == fe && matchesBody(body, "fe")) return true;
        return false;
    }

    // === Expansion (pattern -> concrete AEKeys) ===

    /**
     * Expand a pattern against the game registries. Exact identifiers should not
     * be passed here — use {@link Cell4Util#parseIdentifier(String)} for those.
     */
    public static List<AEKey> expand(String raw) {
        if (!isPattern(raw)) return Collections.emptyList();
        List<AEKey> cached = EXPANSION_CACHE.get(raw);
        if (cached != null) return cached;

        List<AEKey> result = new ArrayList<>();
        try {
            if (raw.startsWith(FLUID_PREFIX)) {
                expandFluids(result, raw.substring(FLUID_PREFIX.length()));
            } else if (raw.startsWith(CHEMICAL_PREFIX)) {
                expandChemicals(result, raw.substring(CHEMICAL_PREFIX.length()));
            } else if (raw.startsWith(ENERGY_PREFIX)) {
                expandEnergy(result, raw.substring(ENERGY_PREFIX.length()));
            } else {
                expandItems(result, raw);
                expandFluids(result, raw);
            }
        } catch (Throwable ignored) {
            // Keep whatever was collected before the failure.
        }

        if (EXPANSION_CACHE.size() > MAX_CACHE_ENTRIES) EXPANSION_CACHE.clear();
        EXPANSION_CACHE.put(raw, result);
        return result;
    }

    private static void expandItems(List<AEKey> out, String body) {
        Pattern pattern = compile(body);
        if (pattern == null) return;
        for (var item : BuiltInRegistries.ITEM) {
            if (out.size() >= MAX_EXPANSION) return;
            ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
            if (rl == null) continue;
            if (pattern.matcher(rl.toString()).matches()) {
                out.add(AEItemKey.of(item));
            }
        }
    }

    private static void expandFluids(List<AEKey> out, String body) {
        Pattern pattern = compile(body);
        if (pattern == null) return;
        for (var fluid : BuiltInRegistries.FLUID) {
            if (out.size() >= MAX_EXPANSION) return;
            if (fluid == Fluids.EMPTY) continue;
            ResourceLocation rl = BuiltInRegistries.FLUID.getKey(fluid);
            if (rl == null) continue;
            // Skip the internal flowing_ duplicates so the terminal does not show both.
            if (rl.getPath().startsWith("flowing_")) continue;
            if (pattern.matcher(rl.toString()).matches()) {
                out.add(AEFluidKey.of(fluid));
            }
        }
    }

    private static void expandChemicals(List<AEKey> out, String body) {
        Pattern pattern = compile(body);
        if (pattern == null) return;
        for (AEKey key : MekanismIntegration.getAllChemicalKeys()) {
            if (out.size() >= MAX_EXPANSION) return;
            try {
                if (pattern.matcher(key.getId().toString()).matches()) out.add(key);
            } catch (Throwable ignored) {}
        }
    }

    private static void expandEnergy(List<AEKey> out, String body) {
        AEKey source = ThirdPartyIntegration.getSourceKey();
        AEKey mana = ThirdPartyIntegration.getManaKey();
        AEKey fe = ThirdPartyIntegration.getFEKey();
        if (source != null && matchesBody(body, "ars_source")) out.add(source);
        if (mana != null && matchesBody(body, "botania_mana")) out.add(mana);
        if (fe != null && matchesBody(body, "fe")) out.add(fe);
    }

    // === Bulk resolution ===

    /**
     * Resolve a mixed list of exact identifiers and patterns into concrete AEKeys.
     * Duplicates are removed. Order is stable (exact entries first).
     * <p>
     * 变体区分：结果按“完整条目字符串”去重，而不是按 {@link AEKey} 去重。
     * 同 ID 不同 NBT 的条目必须各自保留一个 key，否则终端里只会剩下一格。
     * 解析失败的条目不再静默丢弃，而是输出带摘要与异常的 warn 日志。
     */
    public static List<AEKey> resolveEntries(List<String> entries) {
        if (entries == null || entries.isEmpty()) return Collections.emptyList();
        String cacheKey = String.join("\u0001", entries);
        List<AEKey> cached = RESOLVE_CACHE.get(cacheKey);
        if (cached != null) return cached;

        Map<String, AEKey> result = new LinkedHashMap<>();
        for (String entry : entries) {
            if (entry == null || entry.isEmpty()) continue;
            if (entry.indexOf('{') < 0 && isPattern(entry)) {
                for (AEKey key : expand(entry)) {
                    if (key != null) result.putIfAbsent(patternVariantKey(key), key);
                }
            } else {
                AEKey key = parseExactEntry(entry);
                if (key != null) {
                    // 完整条目字符串（含 SNBT）就是变体身份。
                    result.putIfAbsent(entry, key);
                }
            }
        }
        List<AEKey> resolved = new ArrayList<>(result.values());
        if (RESOLVE_CACHE.size() > MAX_CACHE_ENTRIES) RESOLVE_CACHE.clear();
        RESOLVE_CACHE.put(cacheKey, resolved);
        return resolved;
    }

    /**
     * 解析单个“精确条目”（裸 ID、{@code id{SNBT}} 或完整 compound）。
     * 解析失败时输出 warn（含条目摘要与异常），返回 null。
     */
    public static AEKey parseExactEntry(String entry) {
        if (entry == null || entry.isEmpty()) return null;
        AEKey key;
        try {
            key = Cell4Util.parseIdentifier(entry);
        } catch (Throwable t) {
            warnUnresolved(entry, t);
            return null;
        }
        if (key == null) warnUnresolved(entry, null);
        return key;
    }

    /** 模式展开得到的 key 没有 NBT 变体，用类型 + ID 作为去重身份。 */
    private static String patternVariantKey(AEKey key) {
        try {
            return key.getType().getId() + "|" + key.getId();
        } catch (Throwable t) {
            return "key|" + key;
        }
    }

    /** 每个失败条目只提示一次，避免注册表/终端反复查询时刷屏。 */
    private static void warnUnresolved(String entry, Throwable error) {
        String summary = abbreviate(entry);
        if (WARNED_ENTRIES.size() >= MAX_WARNED_ENTRIES) WARNED_ENTRIES.clear();
        if (!WARNED_ENTRIES.add(summary) && error == null) return;
        if (error == null) {
            Cell4.LOGGER.warn("Cell4: unresolved bound entry '{}' (cannot be parsed as item/fluid/chemical)", summary);
        } else {
            Cell4.LOGGER.warn("Cell4: failed to parse bound entry '{}'", summary, error);
        }
    }

    public static String abbreviate(String entry) {
        if (entry == null) return "null";
        return entry.length() <= 160 ? entry : entry.substring(0, 160) + "...(" + entry.length() + " chars)";
    }

    /** Count concrete keys produced by a mixed list of exact entries and patterns. */
    public static int countEntries(List<String> entries) {
        return resolveEntries(entries).size();
    }

    // === Compilation ===

    private static Pattern compile(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        Pattern cached = PATTERN_CACHE.get(raw);
        if (cached != null) return cached;

        String regex;
        try {
            if (raw.startsWith(REGEX_PREFIX)) {
                regex = raw.substring(REGEX_PREFIX.length());
            } else {
                String glob = raw.startsWith(GLOB_PREFIX) ? raw.substring(GLOB_PREFIX.length()) : raw;
                regex = globToRegex(glob);
            }
            Pattern pattern = Pattern.compile(regex);
            if (PATTERN_CACHE.size() > MAX_CACHE_ENTRIES) PATTERN_CACHE.clear();
            PATTERN_CACHE.put(raw, pattern);
            return pattern;
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

    private static String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder(glob.length() + 8);
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append('.');
                case '[' -> {
                    // Support the common glob negated character class: [!abc] -> [^abc]
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '!') {
                        sb.append("[^");
                        i++;
                    } else {
                        sb.append('[');
                    }
                }
                case ']' -> sb.append(']');
                case '\\', '.', '+', '(', ')', '{', '}', '^', '$', '|' -> sb.append('\\').append(c);
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
