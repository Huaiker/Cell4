package com.cell4.network;

import com.cell4.Cell4;
import com.cell4.common.item.IInfinityCell;
import com.cell4.common.item.InfinityItemCell;
import com.cell4.common.item.InfinityModIdCell;
import com.cell4.common.item.InfinityTagCell;
import com.cell4.common.menu.CellConfiguratorMenu;
import com.cell4.common.util.Cell4Patterns;
import com.cell4.common.util.Cell4Util;
import com.cell4.common.util.NBTKeys;
import com.cell4.common.util.PortableCellPower;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

/**
 * C2S packet for saving cell configuration.
 * 2.1: Now includes server-side validation.
 * 4.4: Now sends a response packet back to client.
 * 4.8: Increased max length from 1024 to 4096.
 * 6.5: The four entry lists are encoded as "varint count + one utf per entry"
 * instead of one comma-joined string. A single over-long list used to make
 * {@code writeUtf(32767)} throw, which aborted the whole save and left the cell
 * empty (the AE terminal then showed nothing at all).
 * 7.0: Limits raised and the budget is now measured in UTF-8 bytes instead of
 * characters: {@link #MAX_ENTRIES} = 16384, {@link #MAX_TOTAL_BYTES} =
 * 1_900_000 and {@link #MAX_ENTRY_BYTES} = 1_000_000. Ordinary entries (a few
 * tens of KB of AE2 portable-cell NBT) are never truncated.
 * <p>
 * NOTE: Minecraft/Netty limits a single packet frame to roughly 2 MiB. This
 * packet's 1.9 MB payload budget therefore sits very close to that hard limit;
 * anything larger must be split across multiple packets (fragmentation), which
 * is deliberately not implemented here.
 */
public class CellConfigSavePacket {
    /**
     * 单条条目传给 {@code writeUtf}/{@code readUtf} 的上限。
     * 1.20.1 语义：该参数是字符数上限，同时隐含 UTF-8 字节上限 = 3 倍字符数。
     * 实际单条字节数由 {@link #MAX_ENTRY_BYTES} 约束（远小于 3 倍值，故不会因
     * 多字节 UTF-8 触发 readUtf 的越界检查）。
     */
    private static final int MAX_ENTRY_CHARS = 1_048_576;
    /** 防爆：单条条目允许的最大 UTF-8 字节数。 */
    private static final int MAX_ENTRY_BYTES = 1_000_000;
    /** 防爆：单个列表最多允许的条目数。 */
    private static final int MAX_ENTRIES = 16_384;
    /** 防爆：整个配置包最多允许的 UTF-8 字节数（四个列表共用）。 */
    private static final int MAX_TOTAL_BYTES = 1_900_000;

    private final List<String> cell4item;
    private final List<String> cell4tag;
    private final List<String> cell4modid;
    private final List<String> blacklist;
    private final String cell4name;
    private final String powerMax;
    private final String powerCost;
    private final String powerCharge;
    private final String texture;

    public CellConfigSavePacket(String cell4item, String cell4tag, String cell4modid, String blacklist,
                                String cell4name, String powerMax, String powerCost, String powerCharge,
                                String texture) {
        // 编码前拆分，保留原有的括号/引号感知拆分逻辑。
        this(parseCommaList(cell4item), parseCommaList(cell4tag), parseCommaList(cell4modid),
                parseCommaList(blacklist), cell4name, powerMax, powerCost, powerCharge, texture);
    }

    private CellConfigSavePacket(List<String> cell4item, List<String> cell4tag, List<String> cell4modid,
                                 List<String> blacklist, String cell4name, String powerMax, String powerCost,
                                 String powerCharge, String texture) {
        this.cell4item = cell4item;
        this.cell4tag = cell4tag;
        this.cell4modid = cell4modid;
        this.blacklist = blacklist;
        this.cell4name = cell4name;
        this.powerMax = powerMax;
        this.powerCost = powerCost;
        this.powerCharge = powerCharge;
        this.texture = texture;
    }

    public static void encode(CellConfigSavePacket pkt, FriendlyByteBuf buf) {
        // 四个列表共用同一份 UTF-8 字节预算，避免整包逼近 Netty 的单帧 2 MiB 硬限。
        int[] byteBudget = {MAX_TOTAL_BYTES};
        writeEntries(buf, pkt.cell4item, "cell4item", byteBudget);
        writeEntries(buf, pkt.cell4tag, "cell4tag", byteBudget);
        writeEntries(buf, pkt.cell4modid, "cell4modid", byteBudget);
        writeEntries(buf, pkt.blacklist, "cell4blacklist", byteBudget);
        buf.writeUtf(pkt.cell4name == null ? "" : pkt.cell4name, 256);
        buf.writeUtf(pkt.powerMax == null ? "" : pkt.powerMax, 64);
        buf.writeUtf(pkt.powerCost == null ? "" : pkt.powerCost, 64);
        buf.writeUtf(pkt.powerCharge == null ? "" : pkt.powerCharge, 64);
        buf.writeUtf(pkt.texture == null ? "" : pkt.texture, 128);
    }

    public static CellConfigSavePacket decode(FriendlyByteBuf buf) {
        int[] byteBudget = {MAX_TOTAL_BYTES};
        return new CellConfigSavePacket(
            readEntries(buf, "cell4item", byteBudget), readEntries(buf, "cell4tag", byteBudget),
            readEntries(buf, "cell4modid", byteBudget), readEntries(buf, "cell4blacklist", byteBudget),
            buf.readUtf(256), buf.readUtf(64), buf.readUtf(64), buf.readUtf(64),
            buf.readUtf(128)
        );
    }

    /**
     * 防爆：发送前限制条目数与总 UTF-8 字节数，并丢弃超长的单条条目
     * （截断后的 SNBT 无法解析，丢弃比保存半截数据更安全）。
     */
    private static void writeEntries(FriendlyByteBuf buf, List<String> entries, String field, int[] byteBudget) {
        List<String> safe = limitEntries(entries, field, byteBudget);
        buf.writeVarInt(safe.size());
        for (String entry : safe) {
            buf.writeUtf(entry, MAX_ENTRY_CHARS);
        }
    }

    private static List<String> limitEntries(List<String> entries, String field, int[] byteBudget) {
        if (entries == null || entries.isEmpty()) return Collections.emptyList();
        List<String> result = new ArrayList<>(Math.min(entries.size(), MAX_ENTRIES));
        int droppedLong = 0;
        for (String entry : entries) {
            if (entry == null || entry.isEmpty()) continue;
            if (result.size() >= MAX_ENTRIES) {
                Cell4.LOGGER.warn("Cell4: {} has more than {} entries, truncating", field, MAX_ENTRIES);
                break;
            }
            int bytes = entry.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > MAX_ENTRY_BYTES) {
                droppedLong++;
                continue;
            }
            if (bytes > byteBudget[0]) {
                Cell4.LOGGER.warn("Cell4: cell configuration exceeds {} UTF-8 bytes, truncating", MAX_TOTAL_BYTES);
                break;
            }
            result.add(entry);
            byteBudget[0] -= bytes;
        }
        if (droppedLong > 0) {
            Cell4.LOGGER.warn("Cell4: {} dropped {} entry/entries longer than {} UTF-8 bytes",
                    field, droppedLong, MAX_ENTRY_BYTES);
        }
        return result;
    }

    /**
     * 防爆：条目数与字节预算在 decode 时同样做上限校验并截断，绝不因超长而抛异常断线。
     * 仍按包内条目数读完数据以保持流同步。
     */
    private static List<String> readEntries(FriendlyByteBuf buf, String field, int[] byteBudget) {
        int count = buf.readVarInt();
        if (count <= 0) return Collections.emptyList();
        List<String> result = new ArrayList<>(Math.min(count, MAX_ENTRIES));
        boolean truncated = false;
        for (int i = 0; i < count; i++) {
            if (buf.readableBytes() <= 0) {
                truncated = true;
                break;
            }
            String entry = buf.readUtf(MAX_ENTRY_CHARS);
            int bytes = entry.getBytes(StandardCharsets.UTF_8).length;
            if (i >= MAX_ENTRIES || bytes > MAX_ENTRY_BYTES || bytes > byteBudget[0]) {
                truncated = true;
                continue;
            }
            if (!entry.isEmpty()) {
                result.add(entry);
                byteBudget[0] -= bytes;
            }
        }
        if (truncated) {
            Cell4.LOGGER.warn("Cell4: {} from client exceeded entry/byte limits, truncated", field);
        }
        return result;
    }

    public static void handle(CellConfigSavePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!(player.containerMenu instanceof CellConfiguratorMenu menu)) return;

            ItemStack cell = menu.getCellInSlot();
            if (cell.isEmpty()) {
                sendResponse(player, false, "gui.cell4.save_fail_no_cell");
                return;
            }

            List<String> items = pkt.cell4item;
            List<String> tags = pkt.cell4tag;
            List<String> modIds = pkt.cell4modid;
            List<String> blacklist = pkt.blacklist;
            String customName = pkt.cell4name != null ? pkt.cell4name.trim() : "";

            // 2.1: Server-side validation
            String validationError = validate(cell, items, tags, modIds, blacklist, pkt.powerMax, pkt.powerCost, pkt.powerCharge);
            if (validationError != null) {
                sendResponse(player, false, validationError);
                return;
            }

            // Apply changes on server side (works for normal and portable cells).
            if (cell.getItem() instanceof IInfinityCell infinityCell) {
                switch (infinityCell.getCellType()) {
                    case ITEM -> {
                        if (!items.isEmpty()) InfinityItemCell.setIdentifiers(cell, items);
                        else cell.removeTagKey(NBTKeys.ITEM);
                    }
                    case TAG -> {
                        if (!tags.isEmpty()) InfinityTagCell.setTagNames(cell, tags);
                        else cell.removeTagKey(NBTKeys.TAG);
                        if (!modIds.isEmpty()) InfinityTagCell.setModIds(cell, modIds);
                        else cell.removeTagKey(NBTKeys.MODID);
                    }
                    case MODID -> {
                        if (!modIds.isEmpty()) InfinityModIdCell.setModIds(cell, modIds);
                        else cell.removeTagKey(NBTKeys.MODID);
                    }
                }
            }

            if (!blacklist.isEmpty()) {
                var tag = cell.getOrCreateTag();
                ListTag listTag = new ListTag();
                for (String entry : blacklist) {
                    listTag.add(StringTag.valueOf(entry));
                }
                tag.put(NBTKeys.BLACKLIST, listTag);
            } else {
                cell.removeTagKey(NBTKeys.BLACKLIST);
            }

            // 5.3: Apply custom name via IInfinityCell
            if (cell.getItem() instanceof IInfinityCell infinityCell) {
                infinityCell.setCustomName(cell, customName);
                infinityCell.setTextureName(cell, pkt.texture != null ? pkt.texture.trim() : "");
                if (infinityCell.isPortable()) {
                    PortableCellPower.setOverrides(cell, pkt.powerMax, pkt.powerCost, pkt.powerCharge);
                }
            }

            menu.getSlot(0).setChanged();

            // 4.4: Send success response
            sendResponse(player, true, "gui.cell4.save_success");
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * 2.1: Validate input data on server side.
     * Returns null if valid, or an error translation key if invalid.
     */
    private static String validate(ItemStack cell, List<String> items, List<String> tags, List<String> modIds,
                                   List<String> blacklist, String powerMax, String powerCost, String powerCharge) {
        if (!validLong(powerMax) || !validLong(powerCost) || !validDouble(powerCharge)) {
            return "gui.cell4.save_fail_invalid_power";
        }
        // Validate item identifiers (support SNBT entries, mekfluid:/mekchemical: prefixes and glob/regex)
        for (String id : items) {
            if (id.indexOf('{') >= 0) {
                // Vanilla item NBT entry: id{...} or a full {id:...,Count:...,tag:{...}} compound
                if (Cell4Util.parseItemStackEntry(id) == null) {
                    return "gui.cell4.save_fail_invalid_item";
                }
                continue;
            }
            if (Cell4Patterns.isPattern(id)) {
                if (!Cell4Patterns.isValidPattern(id)) {
                    return "gui.cell4.save_fail_invalid_pattern";
                }
                continue;
            }
            if (stripPrefix(id) == null || ResourceLocation.tryParse(stripPrefix(id)) == null) {
                return "gui.cell4.save_fail_invalid_item";
            }
        }
        // Validate tag names
        for (String tag : tags) {
            if (ResourceLocation.tryParse(tag) == null) {
                return "gui.cell4.save_fail_invalid_tag";
            }
        }
        // Validate mod IDs (should be lowercase alphanumeric with possible underscores/dots)
        for (String modId : modIds) {
            if (!modId.matches("[a-z0-9_.]+")) {
                return "gui.cell4.save_fail_invalid_modid";
            }
        }
        // Validate blacklist entries (support mekfluid:/mekchemical: prefixes for plain entries)
        for (String entry : blacklist) {
            if (entry.startsWith("#")) {
                String tagName = entry.substring(1);
                if (tagName.isEmpty() || ResourceLocation.tryParse(tagName) == null) {
                    return "gui.cell4.save_fail_invalid_blacklist_tag";
                }
            } else if (entry.startsWith("@")) {
                String modId = entry.substring(1);
                if (modId.isEmpty() || !modId.matches("[a-z0-9_.]+")) {
                    return "gui.cell4.save_fail_invalid_blacklist_modid";
                }
            } else {
                if (entry.indexOf('{') >= 0) {
                    // Vanilla item NBT entry: id{...} or a full {id:...,Count:...,tag:{...}} compound
                    if (Cell4Util.parseItemStackEntry(entry) == null) {
                        return "gui.cell4.save_fail_invalid_blacklist_item";
                    }
                    continue;
                }
                if (Cell4Patterns.isPattern(entry)) {
                    if (!Cell4Patterns.isValidPattern(entry)) {
                        return "gui.cell4.save_fail_invalid_pattern";
                    }
                    continue;
                }
                String stripped = stripPrefix(entry);
                if (stripped == null || ResourceLocation.tryParse(stripped) == null) {
                    return "gui.cell4.save_fail_invalid_blacklist_item";
                }
            }
        }
        return null;
    }

    /**
     * Strip the mekfluid:/mekchemical: prefix if present, returning the plain
     * namespace:path portion for ResourceLocation validation. Returns null if
     * the input is null/empty.
     */
    private static boolean validLong(String value) {
        if (value == null || value.trim().isEmpty()) return true;
        try {
            return Long.parseLong(value.trim()) >= 0L;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean validDouble(String value) {
        if (value == null || value.trim().isEmpty()) return true;
        try {
            return Double.parseDouble(value.trim()) >= 0.0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String stripPrefix(String id) {
        if (id == null || id.isEmpty()) return null;
        if (id.startsWith("mekfluid:")) return id.substring("mekfluid:".length());
        if (id.startsWith("mekchemical:")) return id.substring("mekchemical:".length());
        if (id.startsWith("energy:")) return id.substring("energy:".length());
        return id;
    }

    /**
     * 4.4: Send a response packet back to the client.
     */
    private static void sendResponse(ServerPlayer player, boolean success, String message) {
        Cell4Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CellConfigResponsePacket(success, message));
    }

    /**
     * Split a comma-separated entry list, ignoring commas inside SNBT compounds
     * (e.g. {id:"minecraft:stone",Count:1,tag:{...}}) or quoted strings.
     */
    private static List<String> parseCommaList(String value) {
        if (value == null || value.isBlank()) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (quote != 0) {
                current.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                current.append(c);
                continue;
            }
            if (c == '{' || c == '[') {
                depth++;
            } else if ((c == '}' || c == ']') && depth > 0) {
                depth--;
            }
            if (c == ',' && depth == 0) {
                String entry = current.toString().trim();
                if (!entry.isEmpty()) result.add(entry);
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        String entry = current.toString().trim();
        if (!entry.isEmpty()) result.add(entry);
        return result;
    }
}
