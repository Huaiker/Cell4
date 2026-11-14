package com.cell4.common.util;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Glob / regex support for 26.1.2. Chemical/energy prefixes are not available
 * in this version because the optional AE2 addon integrations are not present.
 */
public final class Cell4Patterns {

    private Cell4Patterns() {}

    private static final String REGEX_PREFIX = "regex:";
    private static final String GLOB_PREFIX = "glob:";
    private static final String FLUID_PREFIX = "mekfluid:";
    private static final int MAX_EXPANSION = 4096;

    private static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, List<AEKey>> EXPANSION_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, List<AEKey>> RESOLVE_CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_ENTRIES = 128;

    public static boolean isPattern(String raw) {
        if (raw == null || raw.isEmpty()) return false;
        String value = raw.trim();
        if (value.isEmpty()) return false;
        // 物品条目（完整 compound 或组件写法）是精确条目，其中的 ?/*/[ 不是 glob
        if (value.startsWith("{") || value.startsWith("[") || Cell4Util.isItemStackEntry(value)) return false;
        String body = value.startsWith(FLUID_PREFIX) ? value.substring(FLUID_PREFIX.length()) : value;
        if (body.startsWith(REGEX_PREFIX) || body.startsWith(GLOB_PREFIX)) return true;
        return body.indexOf('*') >= 0 || body.indexOf('?') >= 0 || body.indexOf('[') >= 0;
    }

    public static boolean isValidPattern(String raw) {
        if (!isPattern(raw)) return false;
        try {
            return compile(stripDomainPrefix(raw)) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String stripDomainPrefix(String raw) {
        return raw.startsWith(FLUID_PREFIX) ? raw.substring(FLUID_PREFIX.length()) : raw;
    }

    public static boolean matches(String raw, AEKey key) {
        if (raw == null || key == null) return false;
        String id;
        try {
            id = key.getId().toString();
        } catch (Throwable t) {
            return false;
        }
        if (raw.startsWith(FLUID_PREFIX)) {
            if (!(key instanceof AEFluidKey)) return false;
            return matchesBody(raw.substring(FLUID_PREFIX.length()), id);
        }
        return matchesBody(raw, id);
    }

    private static boolean matchesBody(String body, String id) {
        Pattern pattern = compile(body);
        return pattern != null && pattern.matcher(id).matches();
    }

    public static List<AEKey> expand(String raw) {
        if (!isPattern(raw)) return Collections.emptyList();
        List<AEKey> cached = EXPANSION_CACHE.get(raw);
        if (cached != null) return cached;

        List<AEKey> result = new ArrayList<>();
        try {
            if (raw.startsWith(FLUID_PREFIX)) {
                expandFluids(result, raw.substring(FLUID_PREFIX.length()));
            } else {
                expandItems(result, raw);
                expandFluids(result, raw);
            }
        } catch (Throwable ignored) {}

        if (EXPANSION_CACHE.size() > MAX_CACHE_ENTRIES) EXPANSION_CACHE.clear();
        EXPANSION_CACHE.put(raw, result);
        return result;
    }

    private static void expandItems(List<AEKey> out, String body) {
        Pattern pattern = compile(body);
        if (pattern == null) return;
        for (var item : BuiltInRegistries.ITEM) {
            if (out.size() >= MAX_EXPANSION) return;
            Identifier rl = BuiltInRegistries.ITEM.getKey(item);
            if (rl != null && pattern.matcher(rl.toString()).matches()) {
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
            Identifier rl = BuiltInRegistries.FLUID.getKey(fluid);
            if (rl == null || rl.getPath().startsWith("flowing_")) continue;
            if (pattern.matcher(rl.toString()).matches()) {
                out.add(AEFluidKey.of(fluid));
            }
        }
    }

    public static List<AEKey> resolveEntries(List<String> entries) {
        if (entries == null || entries.isEmpty()) return Collections.emptyList();
        String cacheKey = String.join("\u0001", entries);
        List<AEKey> cached = RESOLVE_CACHE.get(cacheKey);
        if (cached != null) return cached;

        List<AEKey> resolved = new ArrayList<>();
        // 以完整条目字符串去重：同一 ID 的不同组件/NBT 变体都会保留；
        // 完全相同的 AEKey（真正重复的条目）只保留一次。
        Set<String> seenEntries = new HashSet<>();
        Set<AEKey> seenKeys = new HashSet<>();
        for (String entry : entries) {
            if (entry == null) continue;
            String value = entry.trim();
            if (value.isEmpty()) continue;
            if (value.indexOf('{') < 0 && isPattern(value)) {
                for (AEKey key : expand(value)) {
                    if (key != null && seenKeys.add(key)) resolved.add(key);
                }
            } else {
                if (!seenEntries.add(value)) continue;
                AEKey key = Cell4Util.parseIdentifier(value);
                if (key != null && seenKeys.add(key)) resolved.add(key);
            }
        }
        if (RESOLVE_CACHE.size() > MAX_CACHE_ENTRIES) RESOLVE_CACHE.clear();
        RESOLVE_CACHE.put(cacheKey, resolved);
        return resolved;
    }

    public static int countEntries(List<String> entries) {
        return resolveEntries(entries).size();
    }

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
