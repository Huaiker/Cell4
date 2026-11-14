package com.cell4.common.util;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.cell4.Cell4;
import com.cell4.common.integration.MekanismIntegration;
import com.google.gson.JsonElement;
import com.mojang.brigadier.StringReader;
import com.mojang.serialization.JsonOps;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Cell4Util {

    // Keep backward compatibility alias
    public static final String BLACKLIST_KEY = NBTKeys.BLACKLIST;

    /**
     * 解析结果缓存：UI2 网格每帧都会取用同一批条目，避免重复构造 ItemParser
     * 与反复反射访问注册表。键是条目原文，注册表变化或配置变化时整体失效。
     */
    private static final int MAX_PARSED_ENTRIES = 256;
    private static final Map<String, ItemStack> PARSED_STACK_CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ItemStack> eldest) {
            return size() > MAX_PARSED_ENTRIES;
        }
    };
    private static HolderLookup.Provider parsedCacheProvider;
    private static ItemParser cachedItemParser;
    private static HolderLookup.Provider cachedItemParserProvider;

    /** 已提示过的无法解析条目（摘要），避免终端/预览每帧解析时反复刷屏。 */
    private static final Set<String> WARNED_ENTRIES = ConcurrentHashMap.newKeySet();
    private static final int MAX_WARNED_ENTRIES = 512;

    // 客户端注册表访问的反射成员只解析一次。
    private static boolean clientLookupResolved;
    private static Class<?> minecraftClass;
    private static Method minecraftGetInstance;
    private static Field minecraftLevelField;

    /** 配置或注册表变化后清空条目解析缓存。 */
    public static void invalidateParseCache() {
        synchronized (PARSED_STACK_CACHE) {
            PARSED_STACK_CACHE.clear();
            parsedCacheProvider = null;
            cachedItemParser = null;
            cachedItemParserProvider = null;
        }
    }

    // === Identifier prefix parsing ===
    // Supported formats:
    //   mekfluid:namespace:path    → AEFluidKey (explicit Mekanism fluid)
    //   mekchemical:namespace:path → AppMek MekanismKey (via reflection, requires AppMek)
    //   energy:ars_source          → ArsE SourceKey (via reflection, requires ArsEnergistique)
    //   namespace:path             → item first, then fluid (legacy behavior)

    /**
     * Parse a user-entered identifier string into an AEKey.
     * <p>
     * Recognized prefixes:
     * <ul>
     *   <li>{@code mekfluid:ns:path} — explicit Mekanism fluid</li>
     *   <li>{@code mekchemical:ns:path} — Mekanism chemical (requires Mekanism)</li>
     *   <li>{@code energy:ars_source} — ArsE Source (requires ArsEnergistique)</li>
     *   <li>{@code energy:botania_mana} — AppBot Mana (requires Applied Botanics, 1.20.1 only)</li>
     *   <li>{@code ns:path} (no prefix) — try item first, then fluid (legacy)</li>
     * </ul>
     * Returns null if the identifier cannot be resolved.
     */
    public static AEKey parseIdentifier(String id) {
        if (id == null || id.isEmpty()) return null;

        // Explicit Mekanism fluid prefix
        if (id.startsWith("mekfluid:")) {
            String rest = id.substring("mekfluid:".length());
            ResourceLocation rl = ResourceLocation.tryParse(rest);
            if (rl == null) return null;
            var fluid = BuiltInRegistries.FLUID.getOptional(rl);
            if (fluid.isPresent() && fluid.get() != Fluids.EMPTY) {
                return AEFluidKey.of(fluid.get());
            }
            return null;
        }

        // Explicit Mekanism chemical prefix
        if (id.startsWith("mekchemical:")) {
            String rest = id.substring("mekchemical:".length());
            ResourceLocation rl = ResourceLocation.tryParse(rest);
            if (rl == null) return null;
            return MekanismIntegration.parseChemicalKey(rl);
        }

        // Energy identifiers: energy:ars_source energy:botania_mana energy:fe
        if (id.startsWith("energy:")) {
            String rest = id.substring("energy:".length());
            if (rest.equals("ars_source")) return com.cell4.common.integration.ThirdPartyIntegration.getSourceKey();
            if (rest.equals("botania_mana")) return com.cell4.common.integration.ThirdPartyIntegration.getManaKey();
            if (rest.equals("fe")) return com.cell4.common.integration.ThirdPartyIntegration.getFEKey();
            return null;
        }

        // 物品条目：原版 /give 物品参数语法（纯 ID、id[...] 组件、id{...} SNBT、完整 compound）
        ItemStack stack = parseItemStackEntry(id);
        if (!stack.isEmpty()) return AEItemKey.of(stack);

        // Legacy: try fluid
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return null;
        var fluid = BuiltInRegistries.FLUID.getOptional(rl);
        if (fluid.isPresent() && fluid.get() != Fluids.EMPTY) {
            return AEFluidKey.of(fluid.get());
        }
        return null;
    }

    /**
     * 解析单条元件绑定项为带组件的 ItemStack，支持原版 {@code /give} 的全部物品参数写法：
     * <ul>
     *   <li>{@code minecraft:diamond_sword} — 纯物品 ID</li>
     *   <li>{@code minecraft:diamond_sword[minecraft:enchantments={...},minecraft:custom_name='{"text":"Test"}']}
     *       — 组件语法（原版 {@link ItemParser}）</li>
     *   <li>{@code minecraft:diamond_sword{custom_data:{...}}} — 旧版 SNBT，写入 custom_data 组件</li>
     *   <li>{@code {id:"minecraft:diamond_sword",count:1,components:{...}}} — 完整物品 compound</li>
     * </ul>
     * 无法解析时返回 {@link ItemStack#EMPTY}。
     */
    public static ItemStack parseItemStackEntry(String entry) {
        if (entry == null) return ItemStack.EMPTY;
        String value = entry.trim();
        if (value.isEmpty()) return ItemStack.EMPTY;

        HolderLookup.Provider provider = registryProvider();
        synchronized (PARSED_STACK_CACHE) {
            if (provider != parsedCacheProvider) {
                PARSED_STACK_CACHE.clear();
                parsedCacheProvider = provider;
            }
            ItemStack cached = PARSED_STACK_CACHE.get(value);
            if (cached != null) {
                return cached.isEmpty() ? ItemStack.EMPTY : cached.copy();
            }
        }

        ItemStack stack = parseItemStackEntryUncached(value, provider);
        synchronized (PARSED_STACK_CACHE) {
            PARSED_STACK_CACHE.put(value, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        return stack;
    }

    private static ItemStack parseItemStackEntryUncached(String value, HolderLookup.Provider provider) {
        // 完整物品 compound：{id:"...",count:1,components:{...}}
        if (value.startsWith("{")) {
            return parseItemStackCompound(value, provider);
        }

        // 模式/其它非物品前缀不按物品解析
        String idPart = itemIdPart(value);
        if (idPart.isEmpty() || isNonItemEntry(idPart)) return ItemStack.EMPTY;

        Throwable failure = null;
        // 原版命令解析器：id[...] 组件语法
        if (provider != null) {
            Throwable[] parserFailure = new Throwable[1];
            // ItemParser 的构造会构建整套命令参数树，按注册表实例缓存复用。
            synchronized (PARSED_STACK_CACHE) {
                if (cachedItemParser == null || cachedItemParserProvider != provider) {
                    cachedItemParser = new ItemParser(provider);
                    cachedItemParserProvider = provider;
                }
                ItemStack parsed = parseWithItemParser(cachedItemParser, value, parserFailure);
                if (parsed != null) return parsed;
                // 1.21.1 的文本组件只接受 JSON 字符串（SNBT compound 写法从 1.21.5 才有），
                // 这里把 custom_name={text:'...'} 一类的值改写成 JSON 字符串后再试一次。
                if (value.indexOf('[') >= 0) {
                    String rewritten = coerceComponentSyntax(value);
                    if (!rewritten.equals(value)) {
                        parsed = parseWithItemParser(cachedItemParser, rewritten, parserFailure);
                        if (parsed != null) return parsed;
                    }
                }
            }
            failure = parserFailure[0];
        }

        // 简化 SNBT：minecraft:diamond_sword{...}（写入 custom_data 组件）
        int brace = value.indexOf('{');
        if (brace > 0 && value.endsWith("}")) {
            ResourceLocation snbtId = ResourceLocation.tryParse(idPart);
            if (snbtId == null) {
                warnUnresolvedEntry(value, failure);
                return ItemStack.EMPTY;
            }
            var snbtItem = BuiltInRegistries.ITEM.getOptional(snbtId);
            if (snbtItem.isEmpty()) {
                warnUnresolvedEntry(value, failure);
                return ItemStack.EMPTY;
            }
            try {
                CompoundTag nbt = TagParser.parseTag(value.substring(brace));
                ItemStack stack = new ItemStack(snbtItem.get());
                // 兼容 1.21 写法 {custom_data:{...}} / {tag:{...}}，避免重复嵌套一层。
                setCustomTag(stack, unwrapCustomData(nbt));
                return stack;
            } catch (Throwable t) {
                warnUnresolvedEntry(value, t);
                return ItemStack.EMPTY;
            }
        }

        // 纯 ID
        ResourceLocation rl = ResourceLocation.tryParse(value);
        if (rl == null) {
            if (failure != null) warnUnresolvedEntry(value, failure);
            return ItemStack.EMPTY;
        }
        var item = BuiltInRegistries.ITEM.getOptional(rl);
        if (item.isPresent()) return new ItemStack(item.get());
        // 纯流体 id 由 parseIdentifier 的 legacy 分支处理，这里不算解析失败。
        if (!isFluidIdentifier(rl)) warnUnresolvedEntry(value, failure);
        return ItemStack.EMPTY;
    }

    /** 用 {@link ItemParser} 解析；未消费完输入视为失败（形如 {@code id{SNBT}} 会留下花括号部分）。 */
    private static ItemStack parseWithItemParser(ItemParser parser, String value, Throwable[] failure) {
        try {
            StringReader reader = new StringReader(value);
            ItemParser.ItemResult result = parser.parse(reader);
            if (reader.canRead()) {
                failure[0] = new IllegalArgumentException(
                        "unconsumed input at " + reader.getCursor() + ": " + value.substring(reader.getCursor()));
                return null;
            }
            return new ItemStack(result.item(), 1, result.components());
        } catch (Throwable t) {
            failure[0] = t;
            return null;
        }
    }

    /** 完整物品 compound → ItemStack（依赖世界/服务端的注册表上下文）。 */
    private static ItemStack parseItemStackCompound(String value, HolderLookup.Provider provider) {
        if (provider == null) return ItemStack.EMPTY;
        try {
            CompoundTag tag = TagParser.parseTag(value);
            // 1.21.1 的文本组件要求 JSON 字符串，先把 SNBT compound 形式的文本组件改写掉。
            coerceTextComponents(tag);
            ItemStack stack = ItemStack.CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, provider), tag)
                    .result().orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) warnUnresolvedEntry(value, null);
            return stack;
        } catch (Throwable t) {
            warnUnresolvedEntry(value, t);
            return ItemStack.EMPTY;
        }
    }

    /**
     * 把 {@code id[custom_name={text:'X'}]} 里的文本组件改写为 JSON 字符串形式的 SNBT 值，
     * 返回改写后的条目；无需改写时原样返回。
     */
    private static String coerceComponentSyntax(String value) {
        int open = value.indexOf('[');
        if (open < 0 || !value.endsWith("]")) return value;
        List<String> parts = splitTopLevel(value.substring(open + 1, value.length() - 1));
        StringBuilder sb = new StringBuilder(value.length() + 32);
        sb.append(value, 0, open + 1);
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(',');
            String part = parts.get(i);
            int eq = part.indexOf('=');
            if (eq <= 0) {
                sb.append(part);
                continue;
            }
            String rawValue = part.substring(eq + 1).trim();
            String coerced = coerceTextComponentValue(rawValue);
            sb.append(part, 0, eq + 1).append(coerced == null ? rawValue : coerced);
        }
        return sb.append(']').toString();
    }

    /** 单个组件值：若是 SNBT 文本组件则返回 JSON 字符串字面量，否则返回 null。 */
    private static String coerceTextComponentValue(String raw) {
        if (raw.length() < 2 || raw.charAt(0) != '{' || raw.charAt(raw.length() - 1) != '}') return null;
        try {
            CompoundTag nbt = TagParser.parseTag(raw);
            if (!looksLikeTextComponent(nbt)) return null;
            String json = toJsonString(nbt);
            if (json == null) return null;
            return "'" + json.replace("\\", "\\\\").replace("'", "\\'") + "'";
        } catch (Throwable t) {
            return null;
        }
    }

    /** 完整 compound 的 components 里，把 SNBT 文本组件值改写为 JSON 字符串。 */
    private static void coerceTextComponents(CompoundTag root) {
        if (!(root.get("components") instanceof CompoundTag components)) return;
        for (String key : new ArrayList<>(components.getAllKeys())) {
            Tag v = components.get(key);
            if (v instanceof CompoundTag compound && looksLikeTextComponent(compound)) {
                String json = toJsonString(compound);
                if (json != null) components.putString(key, json);
            } else if (v instanceof ListTag list) {
                ListTag converted = new ListTag();
                boolean changed = false;
                for (Tag element : list) {
                    if (element instanceof CompoundTag compound && looksLikeTextComponent(compound)) {
                        String json = toJsonString(compound);
                        if (json != null) {
                            converted.add(StringTag.valueOf(json));
                            changed = true;
                            continue;
                        }
                    }
                    converted.add(element);
                }
                if (changed) components.put(key, converted);
            }
        }
    }

    /** 文本组件（text/translate/...）才需要 JSON 字符串，其它组件保持原样。 */
    private static boolean looksLikeTextComponent(CompoundTag tag) {
        return tag.contains("text") || tag.contains("translate") || tag.contains("keybind")
                || tag.contains("score") || tag.contains("selector") || tag.contains("nbt")
                || tag.contains("extra") || tag.contains("with");
    }

    private static String toJsonString(CompoundTag tag) {
        try {
            JsonElement json = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, tag);
            return json == null ? null : json.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    /** 按顶层逗号切分组件列表（忽略 {} / [] / 引号内部的逗号）。 */
    private static List<String> splitTopLevel(String body) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        char quote = 0;
        int start = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (quote != 0) {
                if (c == '\\') i++;
                else if (c == quote) quote = 0;
                continue;
            }
            switch (c) {
                case '\'', '"' -> quote = c;
                case '{', '[' -> depth++;
                case '}', ']' -> depth--;
                case ',' -> {
                    if (depth == 0) {
                        parts.add(body.substring(start, i));
                        start = i + 1;
                    }
                }
                default -> { }
            }
        }
        if (start <= body.length()) parts.add(body.substring(start));
        return parts;
    }

    /** 兼容 {@code {custom_data:{...}}} / {@code {tag:{...}}} 写法，取出真正的自定义数据。 */
    private static CompoundTag unwrapCustomData(CompoundTag tag) {
        if (tag.size() != 1) return tag;
        for (String key : new String[]{"custom_data", "tag"}) {
            if (tag.get(key) instanceof CompoundTag inner) return inner;
        }
        return tag;
    }

    private static boolean isFluidIdentifier(ResourceLocation rl) {
        try {
            var fluid = BuiltInRegistries.FLUID.getOptional(rl);
            return fluid.isPresent() && fluid.get() != Fluids.EMPTY;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 解析失败的条目只提示一次（有上限），包含条目摘要、长度与异常。 */
    public static void warnUnresolvedEntry(String entry, Throwable error) {
        String summary = abbreviateEntry(entry);
        int length = entry == null ? 0 : entry.length();
        synchronized (WARNED_ENTRIES) {
            if (WARNED_ENTRIES.size() >= MAX_WARNED_ENTRIES) WARNED_ENTRIES.clear();
            if (!WARNED_ENTRIES.add(summary)) return;
        }
        if (error == null) {
            Cell4.LOGGER.warn("Cell4: unresolved bound entry '{}' (length {}, cannot be parsed as item/fluid/chemical)",
                    summary, length);
        } else {
            Cell4.LOGGER.warn("Cell4: failed to parse bound entry '{}' (length {})", summary, length, error);
        }
    }

    /** 条目摘要：过长时截断，日志里同时给出完整长度。 */
    public static String abbreviateEntry(String entry) {
        if (entry == null) return "null";
        return entry.length() <= 160 ? entry : entry.substring(0, 160) + "...";
    }

    /** 取条目中物品 ID 部分（遇到组件 {@code [} 或 SNBT 花括号时截止）。 */
    private static String itemIdPart(String value) {
        int end = value.length();
        int brace = value.indexOf('{');
        int bracket = value.indexOf('[');
        if (brace > 0) end = Math.min(end, brace);
        if (bracket > 0) end = Math.min(end, bracket);
        return value.substring(0, end);
    }

    /** 通配符 / 正则 / 流体 / 化学品 / 能量等非物品条目前缀。 */
    private static boolean isNonItemEntry(String idPart) {
        return idPart.indexOf('*') >= 0 || idPart.indexOf('?') >= 0
                || idPart.startsWith("regex:") || idPart.startsWith("glob:")
                || idPart.startsWith("mekfluid:") || idPart.startsWith("mekchemical:")
                || idPart.startsWith("energy:");
    }

    /** 用于解析组件/注册表引用的注册表上下文：服务端 > 客户端 > null。 */
    private static HolderLookup.Provider registryProvider() {
        try {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) return server.registryAccess();
        } catch (Throwable ignored) {
        }
        try {
            // 反射访问客户端，避免专用服务器链接客户端类；反射成员只解析一次。
            if (!clientLookupResolved) {
                clientLookupResolved = true;
                try {
                    minecraftClass = Class.forName("net.minecraft.client.Minecraft");
                    minecraftGetInstance = minecraftClass.getMethod("getInstance");
                    minecraftLevelField = minecraftClass.getField("level");
                } catch (Throwable ignored) {
                    minecraftClass = null;
                }
            }
            if (minecraftClass != null) {
                Object minecraft = minecraftGetInstance.invoke(null);
                if (minecraft != null) {
                    Object level = minecraftLevelField.get(minecraft);
                    if (level instanceof Level clientLevel) return clientLevel.registryAccess();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }


    public static CompoundTag getCustomTag(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null ? customData.copyTag() : new CompoundTag();
    }

    public static void setCustomTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /**
     * BlacklistData holds three types of blacklist entries with tag caching (2.3).
     */
    public static class BlacklistData {
        private final Set<AEKey> itemKeys;
        private final List<String> tagNames;
        private final Set<String> modIds;
        private final List<String> patterns;
        // 2.3: Cache for tag blacklist matching results
        private Map<String, Boolean> tagMatchCache = new HashMap<>();

        public BlacklistData(Set<AEKey> itemKeys, List<String> tagNames, Set<String> modIds) {
            this(itemKeys, tagNames, modIds, Collections.emptyList());
        }

        public BlacklistData(Set<AEKey> itemKeys, List<String> tagNames, Set<String> modIds, List<String> patterns) {
            this.itemKeys = itemKeys;
            this.tagNames = tagNames;
            this.modIds = modIds;
            this.patterns = patterns;
        }

        public Set<AEKey> getItemKeys() { return itemKeys; }
        public List<String> getTagNames() { return tagNames; }
        public Set<String> getModIds() { return modIds; }
        public List<String> getPatterns() { return patterns; }

        public boolean isBlacklisted(AEKey key) {
            if (itemKeys.contains(key)) return true;

            // 2.3: Use cached tag matching
            for (String tagName : tagNames) {
                String cacheKey = key.toString() + "|" + tagName;
                Boolean cached = tagMatchCache.get(cacheKey);
                if (cached != null) {
                    if (cached) return true;
                } else {
                    boolean matches = matchesTagBlacklist(key, tagName);
                    tagMatchCache.put(cacheKey, matches);
                    if (matches) return true;
                }
            }

            if (!modIds.isEmpty()) {
                String ns = getNamespace(key);
                if (ns != null && modIds.contains(ns)) return true;
            }

            if (!patterns.isEmpty()) {
                String keyId;
                try {
                    keyId = key.getId().toString();
                } catch (Throwable t) {
                    keyId = null;
                }
                if (keyId != null) {
                    String cacheKey = keyId + "|pattern";
                    Boolean cached = tagMatchCache.get(cacheKey);
                    if (cached != null) {
                        if (cached) return true;
                    } else {
                        boolean matches = false;
                        for (String pattern : patterns) {
                            if (Cell4Patterns.matches(pattern, key)) {
                                matches = true;
                                break;
                            }
                        }
                        tagMatchCache.put(cacheKey, matches);
                        if (matches) return true;
                    }
                }
            }

            return false;
        }

        public boolean isEmpty() {
            return itemKeys.isEmpty() && tagNames.isEmpty() && modIds.isEmpty() && patterns.isEmpty();
        }
    }

    public static BlacklistData getBlacklistData(ItemStack stack) {
        CompoundTag tag = getCustomTag(stack);
        if (!tag.contains(NBTKeys.BLACKLIST)) {
            return new BlacklistData(Collections.emptySet(), Collections.emptyList(), Collections.emptySet());
        }
        return getBlacklistData(parseStringList(tag, NBTKeys.BLACKLIST));
    }

    /** 由黑名单原始条目构建，供已有快照的调用方复用（避免重复读取 NBT）。 */
    public static BlacklistData getBlacklistData(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return new BlacklistData(Collections.emptySet(), Collections.emptyList(), Collections.emptySet());
        }

        Set<AEKey> itemKeys = new HashSet<>();
        List<String> tagNames = new ArrayList<>();
        Set<String> modIds = new HashSet<>();
        List<String> patterns = new ArrayList<>();

        for (String id : ids) {
            if (id.startsWith("#")) {
                String tagName = id.substring(1);
                if (!tagName.isEmpty()) tagNames.add(tagName);
            } else if (id.startsWith("@")) {
                String modId = id.substring(1);
                if (!modId.isEmpty()) modIds.add(modId);
            } else {
                // 先按精确条目解析：id[...] 组件写法、id{...} SNBT、完整 compound 含有
                // 通配符字符但必须按物品处理，只有解析失败才考虑 glob/regex。
                AEKey key = parseIdentifier(id);
                if (key != null) {
                    itemKeys.add(key);
                } else if (Cell4Patterns.isPattern(id)) {
                    patterns.add(id);
                }
            }
        }

        return new BlacklistData(itemKeys, tagNames, modIds, patterns);
    }

    public static Set<AEKey> getBlacklistKeys(ItemStack stack) {
        return getBlacklistData(stack).getItemKeys();
    }

    public static List<String> getBlacklistIds(ItemStack stack) {
        CompoundTag tag = getCustomTag(stack);
        if (!tag.contains(NBTKeys.BLACKLIST)) return Collections.emptyList();
        return parseStringList(tag, NBTKeys.BLACKLIST);
    }

    public static List<String> parseStringList(CompoundTag tag, String key) {
        if (!tag.contains(key)) return Collections.emptyList();
        if (tag.get(key) instanceof ListTag listTag) {
            List<String> result = new ArrayList<>(listTag.size());
            for (int i = 0; i < listTag.size(); i++) {
                String str = listTag.getString(i);
                if (!str.isEmpty()) result.add(str);
            }
            return result;
        }
        String single = tag.getString(key);
        if (!single.isEmpty()) return Collections.singletonList(single);
        return Collections.emptyList();
    }

    // === Shared NBT list get/set for cell data fields ===

    public static List<String> getStringList(ItemStack stack, String nbtKey) {
        CompoundTag tag = getCustomTag(stack);
        if (!tag.contains(nbtKey)) return Collections.emptyList();
        return parseStringList(tag, nbtKey);
    }

    public static void setStringList(ItemStack stack, String nbtKey, List<String> values) {
        CompoundTag tag = getCustomTag(stack);
        ListTag listTag = new ListTag();
        for (String val : values) listTag.add(StringTag.valueOf(val));
        tag.put(nbtKey, listTag);
        setCustomTag(stack, tag);
    }

    public static void setStringValue(ItemStack stack, String nbtKey, String value) {
        CompoundTag tag = getCustomTag(stack);
        tag.putString(nbtKey, value);
        setCustomTag(stack, tag);
    }

    public static boolean belongsToMod(AEKey key, Set<String> modIdSet) {
        if (key instanceof AEItemKey itemKey) {
            ResourceLocation rl = BuiltInRegistries.ITEM.getKey(itemKey.getItem());
            return rl != null && modIdSet.contains(rl.getNamespace());
        } else if (key instanceof AEFluidKey fluidKey) {
            ResourceLocation rl = BuiltInRegistries.FLUID.getKey(fluidKey.getFluid());
            return rl != null && modIdSet.contains(rl.getNamespace());
        }
        // Fall back to Mekanism chemicals (returns false if Mekanism not installed)
        return MekanismIntegration.belongsToMod(key, modIdSet);
    }

    /**
     * Get the namespace (mod id) of an AEKey. Supports items, fluids, and Mekanism chemicals.
     */
    public static String getNamespace(AEKey key) {
        if (key instanceof AEItemKey itemKey) {
            ResourceLocation rl = BuiltInRegistries.ITEM.getKey(itemKey.getItem());
            return rl != null ? rl.getNamespace() : null;
        } else if (key instanceof AEFluidKey fluidKey) {
            ResourceLocation rl = BuiltInRegistries.FLUID.getKey(fluidKey.getFluid());
            return rl != null ? rl.getNamespace() : null;
        }
        return MekanismIntegration.getNamespace(key);
    }

    private static boolean matchesTagBlacklist(AEKey key, String tagName) {
        ResourceLocation tagRL = ResourceLocation.tryParse(tagName);
        if (tagRL == null) return false;
        if (key instanceof AEItemKey itemKey) {
            TagKey<net.minecraft.world.item.Item> itemTag = TagKey.create(BuiltInRegistries.ITEM.key(), tagRL);
            return itemKey.getItem().builtInRegistryHolder().is(itemTag);
        } else if (key instanceof AEFluidKey fluidKey) {
            TagKey<net.minecraft.world.level.material.Fluid> fluidTag = TagKey.create(BuiltInRegistries.FLUID.key(), tagRL);
            return fluidKey.getFluid().builtInRegistryHolder().is(fluidTag);
        }
        // Mekanism chemical tag matching (no-op if Mekanism not installed)
        return MekanismIntegration.matchesTag(key, tagName);
    }
}
