package com.cell4.common.util;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.cell4.Cell4;
import com.cell4.client.Cell4ClientHooks;
import com.mojang.brigadier.StringReader;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.*;

public class Cell4Util {

    // Keep backward compatibility alias
    public static final String BLACKLIST_KEY = NBTKeys.BLACKLIST;

    /** 解析结果缓存：每个绑定条目每帧都会被 GUI 取用，避免重复构造 ItemParser。 */
    private static final int MAX_PARSED_ENTRIES = 512;
    private static final Map<String, ItemStack> PARSED_STACK_CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ItemStack> eldest) {
            return size() > MAX_PARSED_ENTRIES;
        }
    };
    private static HolderLookup.Provider parsedCacheRegistry;

    /**
     * Parse an exact identifier (namespace:path) into an AEKey, trying items
     * first and then fluids. The explicit {@code mekfluid:} prefix forces a
     * fluid lookup. Patterns are handled by {@link Cell4Patterns}.
     */
    public static AEKey parseIdentifier(String id) {
        if (id == null || id.isEmpty()) return null;
        if (id.startsWith("mekfluid:")) {
            Identifier rl = Identifier.tryParse(id.substring("mekfluid:".length()));
            if (rl == null) return null;
            var fluid = BuiltInRegistries.FLUID.getOptional(rl);
            if (fluid.isPresent() && fluid.get() != Fluids.EMPTY) {
                return AEFluidKey.of(fluid.get());
            }
            return null;
        }

        List<String> failures = new ArrayList<>(2);
        HolderLookup.Provider registries = getRegistryLookup();
        ItemStack stack = parseItemStackEntryCached(id, registries, failures);
        if (stack != null) {
            AEItemKey itemKey = AEItemKey.of(stack);
            if (itemKey != null) return itemKey;
        }

        // Legacy: fluid fallback (plain namespace:path may be a fluid id)
        Identifier rl = Identifier.tryParse(id);
        if (rl != null) {
            var fluid = BuiltInRegistries.FLUID.getOptional(rl);
            if (fluid.isPresent() && fluid.get() != Fluids.EMPTY) {
                return AEFluidKey.of(fluid.get());
            }
        }
        if (rl != null) {
            warnParseFailure(id, failures);
        }
        return null;
    }

    /**
     * Parse a cell4item entry into an ItemStack, supporting the full vanilla
     * {@code /give} item argument syntax:
     * <ul>
     *   <li>{@code minecraft:diamond_sword[minecraft:enchantments={levels:{"minecraft:sharpness":5}}]}</li>
     *   <li>{@code {id:"minecraft:diamond_sword",count:1,components:{...}}}</li>
     *   <li>{@code minecraft:diamond_sword{custom_data:{...}}} (legacy, written into custom_data)</li>
     *   <li>{@code minecraft:diamond_sword}</li>
     * </ul>
     *
     * @return the parsed stack, or null if the entry is not a resolvable item
     */
    public static ItemStack parseItemStackEntry(String entry) {
        if (entry == null) return null;
        String value = entry.trim();
        if (value.isEmpty()) return null;

        List<String> failures = new ArrayList<>(2);
        ItemStack stack = parseItemStackEntryCached(value, getRegistryLookup(), failures);
        if (stack == null) warnParseFailure(value, failures);
        return stack;
    }

    /** 与 {@link #parseItemStackEntry(String)} 共用缓存，但把失败原因交给调用方决定是否记录。 */
    private static ItemStack parseItemStackEntryCached(String value, HolderLookup.Provider registries,
                                                       List<String> failures) {
        synchronized (PARSED_STACK_CACHE) {
            if (registries != parsedCacheRegistry) {
                PARSED_STACK_CACHE.clear();
                parsedCacheRegistry = registries;
            }
            ItemStack cached = PARSED_STACK_CACHE.get(value);
            if (cached != null) {
                if (cached.isEmpty()) {
                    failures.add("previously rejected (cached)");
                    return null;
                }
                return cached.copy();
            }
        }

        ItemStack stack = parseItemStackEntryUncached(value, registries, failures);
        synchronized (PARSED_STACK_CACHE) {
            PARSED_STACK_CACHE.put(value, stack == null ? ItemStack.EMPTY : stack);
        }
        return stack == null ? null : stack.copy();
    }

    /**
     * 条目是否为物品/组件写法（{@code {...}}、{@code id{...}}、{@code id[...]}）。
     * 用于在 glob/regex pattern 判定前排除物品条目（组件里可能含 [] 等字符）。
     */
    public static boolean isItemStackEntry(String entry) {
        if (entry == null) return false;
        String value = entry.trim();
        if (value.isEmpty()) return false;
        return value.startsWith("{") || value.indexOf('{') > 0
                || isComponentSyntax(value) || isRegisteredItemComponentSyntax(value);
    }

    /** 条目是否含组件语法字符（{@code {} 或 []}），需要走 {@link #parseItemStackEntry} 而不是纯 Identifier 校验。 */
    public static boolean hasItemStackSyntax(String entry) {
        if (entry == null) return false;
        String value = entry.trim();
        return value.indexOf('{') >= 0 || value.indexOf('[') >= 0;
    }

    /** {@code namespace:path[component=value,...]} 形式（原版 /give 的物品参数）。 */
    private static boolean isComponentSyntax(String value) {
        if (!value.endsWith("]")) return false;
        int bracket = value.indexOf('[');
        if (bracket <= 0 || Identifier.tryParse(value.substring(0, bracket)) == null) return false;
        String body = value.substring(bracket + 1, value.length() - 1).trim();
        return body.isEmpty() || body.indexOf(':') >= 0;
    }

    /**
     * 组件体不含 {@code ':'} 的组件写法，例如 {@code netherite_axe[damage=100,max_stack_size=1]}。
     * 只有前缀是已注册物品且组件体形如 {@code key=value} / {@code !key} 时才判定为物品条目，
     * 避免把 glob 的字符类（如 {@code [a-z]}）当成组件。
     */
    private static boolean isRegisteredItemComponentSyntax(String value) {
        if (!value.endsWith("]")) return false;
        int bracket = value.indexOf('[');
        if (bracket <= 0) return false;
        Identifier id = Identifier.tryParse(value.substring(0, bracket).trim());
        if (id == null || BuiltInRegistries.ITEM.getOptional(id).isEmpty()) return false;
        String body = value.substring(bracket + 1, value.length() - 1).trim();
        return body.isEmpty() || body.charAt(0) == '!' || body.indexOf('=') >= 0;
    }

    private static ItemStack parseItemStackEntryUncached(String value, HolderLookup.Provider registries,
                                                         List<String> failures) {
        // 旧版组件值包装（26.1.2 已改为直接的值）先做兼容归一化。
        String parsedValue = normalizeLegacyComponentValues(value);

        // 完整物品 compound：{id:"...",count:1,components:{...}}
        if (parsedValue.startsWith("{")) {
            ItemStack stack = parseItemStackCompound(parsedValue, registries, failures);
            if (stack != null) return stack;
            return null;
        }

        // 原版 /give 物品参数：minecraft:diamond_sword[...]（组件写法）
        if (isComponentSyntax(parsedValue) || isRegisteredItemComponentSyntax(parsedValue)) {
            ItemStack stack = parseItemStackWithCommandParser(parsedValue, registries, failures);
            if (stack != null) return stack;
            return null;
        }

        // 花括号写法：minecraft:stone{custom_data:{...}}
        int brace = parsedValue.indexOf('{');
        if (brace > 0 && parsedValue.endsWith("}")) {
            String rawId = parsedValue.substring(0, brace).trim();
            String body = parsedValue.substring(brace + 1, parsedValue.length() - 1);
            Identifier snbtId = Identifier.tryParse(rawId);
            if (snbtId != null) {
                // 26.1.2：花括号内容等同于组件赋值，先按 /give 的 id[...] 解析
                ItemStack componentStack = parseItemStackWithCommandParser(
                        rawId + "[" + body + "]", registries, failures);
                if (componentStack != null) return componentStack;

                // 兼容旧写法：整段 NBT 写入 custom_data
                var snbtItem = BuiltInRegistries.ITEM.getOptional(snbtId);
                if (snbtItem.isPresent()) {
                    try {
                        CompoundTag nbt = net.minecraft.nbt.TagParser.parseCompoundFully(
                                parsedValue.substring(brace));
                        ItemStack stack = new ItemStack(snbtItem.get());
                        setCustomTag(stack, nbt);
                        return stack;
                    } catch (Exception e) {
                        failures.add("legacy NBT: " + describe(e));
                    }
                }
            }
        }

        // 纯 ID
        Identifier rl = Identifier.tryParse(parsedValue);
        if (rl == null) {
            failures.add("invalid identifier");
            return null;
        }
        var item = BuiltInRegistries.ITEM.getOptional(rl);
        if (item.isPresent()) return new ItemStack(item.get());
        failures.add("unknown item " + rl);
        return null;
    }

    /** 用原版命令解析器解析 {@code id[...]}，与 /give 行为一致。 */
    private static ItemStack parseItemStackWithCommandParser(String value, HolderLookup.Provider registries,
                                                             List<String> failures) {
        if (registries == null) {
            failures.add("component syntax: no registry access");
            return null;
        }
        try {
            StringReader reader = new StringReader(value);
            ItemInput input = new ItemParser(registries).parse(reader);
            if (reader.canRead()) {
                failures.add("component syntax: trailing data");
                return null;
            }
            ItemStack stack = input.createItemStack(1);
            if (stack.isEmpty()) {
                failures.add("component syntax: empty stack");
                return null;
            }
            return stack;
        } catch (Exception e) {
            failures.add("component syntax: " + describe(e));
            return null;
        }
    }

    /** 用 ItemStack.CODEC 解析完整的 {@code {id,count,components}} compound。 */
    private static ItemStack parseItemStackCompound(String value, HolderLookup.Provider registries,
                                                    List<String> failures) {
        if (registries == null) {
            failures.add("compound syntax: no registry access");
            return null;
        }
        try {
            CompoundTag tag = net.minecraft.nbt.TagParser.parseCompoundFully(value);
            RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
            ItemStack stack = ItemStack.CODEC.parse(ops, tag).result().orElse(null);
            if (stack == null) {
                failures.add("compound syntax: codec rejected");
                return null;
            }
            if (stack.isEmpty()) {
                failures.add("compound syntax: empty stack");
                return null;
            }
            return stack;
        } catch (Exception e) {
            failures.add("compound syntax: " + describe(e));
            return null;
        }
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        return message == null || message.isEmpty()
                ? t.getClass().getSimpleName()
                : t.getClass().getSimpleName() + ": " + message;
    }

    // === 解析失败日志（去重，避免每帧刷屏） ===

    private static final Set<String> WARNED_PARSE_FAILURES = new java.util.HashSet<>();
    private static final int MAX_WARNED_PARSE_FAILURES = 256;

    private static void warnParseFailure(String entry, List<String> failures) {
        String summary = entry.length() > 160 ? entry.substring(0, 160) + "..." : entry;
        synchronized (WARNED_PARSE_FAILURES) {
            if (WARNED_PARSE_FAILURES.size() >= MAX_WARNED_PARSE_FAILURES
                    || !WARNED_PARSE_FAILURES.add(entry)) {
                return;
            }
        }
        Cell4.LOGGER.warn("Cell4: failed to parse item entry ({} chars) '{}': {}",
                entry.length(), summary,
                failures.isEmpty() ? "unresolved item/fluid" : String.join(" | ", failures));
    }

    /**
     * 26.1.2 把若干组件的“单键包装”简化成直接的值，这里把旧写法降级成新写法，
     * 使旧配置/教程里的条目仍能解析：
     * <ul>
     *   <li>{@code enchantments={levels:{...}}} / {@code stored_enchantments={levels:{...}}} → {@code ={...}}</li>
     *   <li>{@code attribute_modifiers={modifiers:[...]}} → {@code =[...]}</li>
     *   <li>{@code dyed_color={rgb:16711680}} → {@code =16711680}</li>
     * </ul>
     */
    private static String normalizeLegacyComponentValues(String value) {
        String result = unwrapLegacyComponentValue(value, "enchantments", "levels");
        result = unwrapLegacyComponentValue(result, "attribute_modifiers", "modifiers");
        return unwrapLegacyComponentValue(result, "dyed_color", "rgb");
    }

    /**
     * 把 {@code component={key:value}} 形式的旧写法展开为 {@code component=value}。
     * 仅当包装里的单键正好覆盖整个组件值时才会改写。
     */
    private static String unwrapLegacyComponentValue(String value, String component, String wrapperKey) {
        if (value.indexOf(wrapperKey) < 0) return value;
        String result = value;
        int search = 0;
        while (search < result.length()) {
            int wrapperOpen = findComponentValueOpen(result, component, search);
            if (wrapperOpen < 0) return result;
            int wrapperClose = findMatching(result, wrapperOpen);
            if (wrapperClose < 0) return result;
            int valueOpen = findWrapperKeyValueStart(result, wrapperOpen, wrapperKey);
            if (valueOpen < 0) {
                search = wrapperOpen + 1;
                continue;
            }
            int valueEnd;
            char first = result.charAt(valueOpen);
            if (first == '{' || first == '[') {
                int close = findMatching(result, valueOpen);
                if (close < 0) return result;
                valueEnd = close + 1;
            } else {
                valueEnd = findScalarValueEnd(result, valueOpen);
            }
            // 只处理包装里的单键正好覆盖整个组件值的情况
            if (skipSpaces(result, valueEnd) != wrapperClose) {
                search = wrapperOpen + 1;
                continue;
            }
            String inner = result.substring(valueOpen, valueEnd).trim();
            result = result.substring(0, wrapperOpen) + inner + result.substring(wrapperClose + 1);
            search = wrapperOpen + inner.length();
        }
        return result;
    }

    /** 返回 {@code ...component= {} } 中左花括号的位置。 */
    private static int findComponentValueOpen(String value, String component, int from) {
        int idx = value.indexOf(component, from);
        while (idx >= 0) {
            int i = idx + component.length();
            if (i < value.length() && (value.charAt(i) == '\'' || value.charAt(i) == '"')) i++;
            i = skipSpaces(value, i);
            if (i < value.length() && (value.charAt(i) == '=' || value.charAt(i) == ':')) {
                i = skipSpaces(value, i + 1);
                if (i < value.length() && value.charAt(i) == '{') return i;
            }
            idx = value.indexOf(component, idx + 1);
        }
        return -1;
    }

    /** 返回 {@code {key: <值起点>}} 中值起点（{}、[] 或标量的第一个字符）。 */
    private static int findWrapperKeyValueStart(String value, int wrapperOpen, String key) {
        int i = skipSpaces(value, wrapperOpen + 1);
        if (i < value.length() && (value.charAt(i) == '\'' || value.charAt(i) == '"')) i++;
        if (!value.startsWith(key, i)) return -1;
        i = skipSpaces(value, i + key.length());
        if (i < value.length() && (value.charAt(i) == '\'' || value.charAt(i) == '"')) i++;
        i = skipSpaces(value, i);
        if (i >= value.length() || value.charAt(i) != ':') return -1;
        i = skipSpaces(value, i + 1);
        return i < value.length() ? i : -1;
    }

    /** 标量值的结束位置（顶层逗号或右花括号处，忽略字符串内的分隔符）。 */
    private static int findScalarValueEnd(String value, int from) {
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = from; i < value.length(); i++) {
            char c = value.charAt(i);
            if (inSingle) {
                if (c == '\\') i++;
                else if (c == '\'') inSingle = false;
                continue;
            }
            if (inDouble) {
                if (c == '\\') i++;
                else if (c == '"') inDouble = false;
                continue;
            }
            if (c == '\'') inSingle = true;
            else if (c == '"') inDouble = true;
            else if (c == ',' || c == '}') return i;
        }
        return value.length();
    }

    private static int skipSpaces(String value, int from) {
        int i = from;
        while (i < value.length() && Character.isWhitespace(value.charAt(i))) i++;
        return i;
    }

    /** 返回与 {@code openIndex} 处 '{' / '[' 匹配的右括号下标（忽略 SNBT 字符串内的括号）。 */
    private static int findMatching(String value, int openIndex) {
        char open = value.charAt(openIndex);
        char close = open == '{' ? '}' : ']';
        int depth = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = openIndex; i < value.length(); i++) {
            char c = value.charAt(i);
            if (inSingle) {
                if (c == '\\') i++;
                else if (c == '\'') inSingle = false;
                continue;
            }
            if (inDouble) {
                if (c == '\\') i++;
                else if (c == '"') inDouble = false;
                continue;
            }
            if (c == '\'') {
                inSingle = true;
            } else if (c == '"') {
                inDouble = true;
            } else if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /** 组件解析需要的注册表：优先服务端，其次客户端当前世界。都没有时返回 null。 */
    private static HolderLookup.Provider getRegistryLookup() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) return server.registryAccess();
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            return Cell4ClientHooks.getRegistryAccess();
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
                Identifier rl = null;
                if (key instanceof AEItemKey itemKey) {
                    rl = BuiltInRegistries.ITEM.getKey(itemKey.getItem());
                } else if (key instanceof AEFluidKey fluidKey) {
                    rl = BuiltInRegistries.FLUID.getKey(fluidKey.getFluid());
                }
                if (rl != null && modIds.contains(rl.getNamespace())) return true;
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

        List<String> ids = parseStringList(tag, NBTKeys.BLACKLIST);
        if (ids.isEmpty()) {
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
            } else if (Cell4Patterns.isPattern(id)) {
                patterns.add(id);
            } else {
                AEKey key = parseIdentifier(id);
                if (key != null) itemKeys.add(key);
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
                String str = listTag.getStringOr(i, "");
                if (!str.isEmpty()) result.add(str);
            }
            return result;
        }
        String single = tag.getStringOr(key, "");
        if (!single.isEmpty()) return Collections.singletonList(single);
        return Collections.emptyList();
    }

    private static boolean matchesTagBlacklist(AEKey key, String tagName) {
        Identifier tagRL = Identifier.tryParse(tagName);
        if (tagRL == null) return false;
        if (key instanceof AEItemKey itemKey) {
            TagKey<net.minecraft.world.item.Item> itemTag = TagKey.create(BuiltInRegistries.ITEM.key(), tagRL);
            return itemKey.getItem().builtInRegistryHolder().is(itemTag);
        } else if (key instanceof AEFluidKey fluidKey) {
            TagKey<net.minecraft.world.level.material.Fluid> fluidTag = TagKey.create(BuiltInRegistries.FLUID.key(), tagRL);
            return fluidKey.getFluid().builtInRegistryHolder().is(fluidTag);
        }
        return false;
    }

    // === Shared NBT list get/set for cell data fields ===

    /**
     * Get a string list from the cell's custom NBT data.
     */
    public static List<String> getStringList(ItemStack stack, String nbtKey) {
        CompoundTag tag = getCustomTag(stack);
        if (!tag.contains(nbtKey)) return Collections.emptyList();
        return parseStringList(tag, nbtKey);
    }

    /**
     * Set a string list in the cell's custom NBT data.
     */
    public static void setStringList(ItemStack stack, String nbtKey, List<String> values) {
        CompoundTag tag = getCustomTag(stack);
        ListTag listTag = new ListTag();
        for (String val : values) listTag.add(StringTag.valueOf(val));
        tag.put(nbtKey, listTag);
        setCustomTag(stack, tag);
    }

    /**
     * Set a single string value in the cell's custom NBT data.
     */
    public static void setStringValue(ItemStack stack, String nbtKey, String value) {
        CompoundTag tag = getCustomTag(stack);
        tag.putString(nbtKey, value);
        setCustomTag(stack, tag);
    }

    /**
     * Check if a key belongs to any of the specified mod IDs (namespaces).
     * Shared by InfinityModIdStorage and InfinityTagStorage.
     */
    public static boolean belongsToMod(AEKey key, Set<String> modIdSet) {
        if (key instanceof AEItemKey itemKey) {
            Identifier rl = BuiltInRegistries.ITEM.getKey(itemKey.getItem());
            return rl != null && modIdSet.contains(rl.getNamespace());
        } else if (key instanceof AEFluidKey fluidKey) {
            Identifier rl = BuiltInRegistries.FLUID.getKey(fluidKey.getFluid());
            return rl != null && modIdSet.contains(rl.getNamespace());
        }
        return false;
    }
}
