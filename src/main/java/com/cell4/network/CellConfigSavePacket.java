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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * C2S packet for saving cell configuration.
 * 2.1: Now includes server-side validation.
 * 4.4: Now sends a response packet back to client.
 * 4.8: Increased max length from 1024 to 4096.
 *
 * <p>注意：MC/Netty 单个数据包帧上限约 2MB（2,097,152 字节未压缩数据），超过该硬限必须自行分片
 * （fragmentation）传输。当前预算（1.9MB / 16384 条）已接近该硬限，若继续提高需要改为多包分片。</p>
 */
public record CellConfigSavePacket(String cell4item, String cell4tag, String cell4modid, String blacklist,
                                   String cell4name, String powerMax, String powerCost, String powerCharge,
                                   String texture)
        implements CustomPacketPayload {

    // 防爆上限：整个包最多 16384 条条目。
    private static final int MAX_ENTRIES = 16384;
    // 预算按 UTF-8 字节计算：MC/Netty 单包帧上限约 2MB（2,097,152 字节），
    // 留出约 190KB 余量给条目长度前缀、固定字段与压缩/协议开销。
    private static final int MAX_TOTAL_BYTES = 1_900_000;
    // 单条上限：1,000,000 字节。AE2 便携元件几十 KB 的 NBT 远低于此值，正常条目不会被截断。
    private static final int MAX_ENTRY_BYTES = 1_000_000;
    // writeUtf/readUtf 的单条字符上限：配合 MAX_ENTRY_BYTES 的字节上限，
    // 保证多字节 UTF-8 内容读写都不会越界。
    private static final int MAX_ENTRY_CHARS = 1_048_576;

    public static final Type<CellConfigSavePacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Cell4.MODID, "cell_config_save")
    );

    public static final StreamCodec<FriendlyByteBuf, CellConfigSavePacket> STREAM_CODEC = StreamCodec.of(
        CellConfigSavePacket::encode,
        CellConfigSavePacket::decode
    );

    private static void encode(FriendlyByteBuf buf, CellConfigSavePacket pkt) {
        // 逐条写入：按 UTF-8 字节预算累计，避免整表 join 后超出 Utf8String / 单包 2MB 上限直接抛异常。
        Budget budget = new Budget();
        writeList(buf, "cell4item", parseCommaListLimited(pkt.cell4item), budget);
        writeList(buf, "cell4tag", parseCommaListLimited(pkt.cell4tag), budget);
        writeList(buf, "cell4modid", parseCommaListLimited(pkt.cell4modid), budget);
        writeList(buf, "blacklist", parseCommaListLimited(pkt.blacklist), budget);
        buf.writeUtf(clamp(pkt.cell4name, 256));
        buf.writeUtf(clamp(pkt.powerMax, 64));
        buf.writeUtf(clamp(pkt.powerCost, 64));
        buf.writeUtf(clamp(pkt.powerCharge, 64));
        buf.writeUtf(clamp(pkt.texture, 128));
    }

    private static CellConfigSavePacket decode(FriendlyByteBuf buf) {
        Budget budget = new Budget();
        return new CellConfigSavePacket(
            readList(buf, "cell4item", budget), readList(buf, "cell4tag", budget),
            readList(buf, "cell4modid", budget), readList(buf, "blacklist", budget),
            buf.readUtf(256), buf.readUtf(64), buf.readUtf(64), buf.readUtf(64),
            buf.readUtf(128)
        );
    }

    /** 整个包共享的条目/UTF-8 字节预算，避免多个列表各自达到上限后总量失控。 */
    private static final class Budget {
        int entries;
        int bytes;
    }

    /** 先按 UTF-8 字节数截断原文再解析，避免恶意超长字符串在 parseCommaList 阶段就占用大量内存。 */
    private static List<String> parseCommaListLimited(String value) {
        if (value == null) return Collections.emptyList();
        int bytes = utf8Length(value);
        if (bytes <= MAX_TOTAL_BYTES) return parseCommaList(value);
        Cell4.LOGGER.warn("Cell4: CellConfigSavePacket field truncated from {} to at most {} UTF-8 bytes before encoding",
                bytes, MAX_TOTAL_BYTES);
        return parseCommaList(value.substring(0, Math.min(value.length(), MAX_TOTAL_BYTES)));
    }

    /** 写条目数量 + 逐条字符串（空列表写 0）；仅在超出条目/字节预算时丢弃并警告。 */
    private static void writeList(FriendlyByteBuf buf, String field, List<String> values, Budget budget) {
        int allowed = Math.max(0, MAX_ENTRIES - budget.entries);
        int count = Math.min(values.size(), allowed);
        if (values.size() > allowed) {
            Cell4.LOGGER.warn("Cell4: CellConfigSavePacket '{}' truncated from {} to {} entries (max {})",
                    field, values.size(), count, MAX_ENTRIES);
        }
        List<String> kept = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String value = values.get(i);
            int bytes = utf8Length(value);
            // 单条超过 1MB：整条丢弃（截断后的 SNBT/NBT 已无意义），正常大小条目不受影响。
            if (bytes > MAX_ENTRY_BYTES) {
                Cell4.LOGGER.warn("Cell4: CellConfigSavePacket '{}' dropped an entry of {} bytes (max {} bytes)",
                        field, bytes, MAX_ENTRY_BYTES);
                continue;
            }
            if (budget.bytes + bytes > MAX_TOTAL_BYTES) {
                Cell4.LOGGER.warn("Cell4: CellConfigSavePacket '{}' truncated to {} entries at {} bytes (max {} bytes)",
                        field, kept.size(), budget.bytes, MAX_TOTAL_BYTES);
                break;
            }
            kept.add(value);
            budget.entries++;
            budget.bytes += bytes;
        }
        buf.writeVarInt(kept.size());
        for (String value : kept) {
            buf.writeUtf(value, MAX_ENTRY_CHARS);
        }
    }

    /**
     * 读取一条条目。声明的字节长度超过单条上限时，读完并丢弃（保持流对齐），返回 null，
     * 而不是让 readUtf 抛 DecoderException 直接断开连接。
     */
    private static String readEntry(FriendlyByteBuf buf, String field) {
        int start = buf.readerIndex();
        int byteLength = buf.readVarInt();
        if (byteLength > MAX_ENTRY_BYTES) {
            Cell4.LOGGER.warn("Cell4: CellConfigSavePacket '{}' dropped an entry of {} bytes (max {} bytes)",
                    field, byteLength, MAX_ENTRY_BYTES);
            buf.skipBytes(byteLength);
            return null;
        }
        buf.readerIndex(start);
        // readUtf(MAX_ENTRY_CHARS) 经 Utf8String 校验：编码字节上限为 MAX_ENTRY_CHARS * 3，
        // 单条已限制在 MAX_ENTRY_BYTES 字节内，且 UTF-8 解码后字符数不超过字节数，多字节内容不会越界。
        return buf.readUtf(MAX_ENTRY_CHARS);
    }

    /** 读取逐条字符串并重新拼成 handle() 使用的逗号分隔原文；超出预算的条目读取后丢弃。 */
    private static String readList(FriendlyByteBuf buf, String field, Budget budget) {
        int size = buf.readVarInt();
        if (size < 0) size = 0;
        int allowed = Math.max(0, MAX_ENTRIES - budget.entries);
        int keep = Math.min(size, allowed);
        if (size > allowed) {
            Cell4.LOGGER.warn("Cell4: CellConfigSavePacket '{}' claims {} entries, keeping at most {} (max {})",
                    field, size, keep, MAX_ENTRIES);
        }
        StringBuilder joined = new StringBuilder(Math.min(MAX_TOTAL_BYTES, keep * 16));
        int keptCount = 0;
        boolean bytesCapped = false;
        for (int i = 0; i < size; i++) {
            // 每一条都必须完整读取，超限条目读完丢弃以保证流对齐。
            String value = readEntry(buf, field);
            if (value == null || i >= keep) continue;
            int bytes = utf8Length(value);
            if (budget.bytes + bytes > MAX_TOTAL_BYTES) {
                bytesCapped = true;
                continue;
            }
            if (keptCount > 0) joined.append(',');
            joined.append(value);
            keptCount++;
            budget.entries++;
            budget.bytes += bytes;
        }
        if (bytesCapped) {
            Cell4.LOGGER.warn("Cell4: CellConfigSavePacket '{}' truncated at {} bytes (max {} bytes)",
                    field, budget.bytes, MAX_TOTAL_BYTES);
        }
        return joined.toString();
    }

    /** UTF-8 编码后的字节数，用于包级预算。 */
    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    /** 按字符数截断；null 视为空串，保证 writeUtf 永不因单条超长抛异常。 */
    private static String clamp(String value, int maxLength) {
        if (value == null) return "";
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CellConfigSavePacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!(player.containerMenu instanceof CellConfiguratorMenu menu)) return;

            ItemStack cell = menu.getCellInSlot();
            if (cell.isEmpty()) {
                sendResponse(context, false, "gui.cell4.save_fail_no_cell");
                return;
            }

            List<String> items = parseCommaList(pkt.cell4item);
            List<String> tags = parseCommaList(pkt.cell4tag);
            List<String> modIds = parseCommaList(pkt.cell4modid);
            List<String> blacklist = parseCommaList(pkt.blacklist);
            String customName = pkt.cell4name != null ? pkt.cell4name.trim() : "";

            // 2.1: Server-side validation
            String validationError = validate(cell, items, tags, modIds, blacklist, pkt.powerMax, pkt.powerCost, pkt.powerCharge);
            if (validationError != null) {
                sendResponse(context, false, validationError);
                return;
            }

            // Apply changes based on the logical cell type (works for normal and portable cells).
            if (cell.getItem() instanceof IInfinityCell infinityCell) {
                switch (infinityCell.getCellType()) {
                    case ITEM -> {
                        if (!items.isEmpty()) InfinityItemCell.setIdentifiers(cell, items);
                        else {
                            CompoundTag tag = Cell4Util.getCustomTag(cell);
                            tag.remove(NBTKeys.ITEM);
                            Cell4Util.setCustomTag(cell, tag);
                        }
                    }
                    case TAG -> {
                        if (!tags.isEmpty()) InfinityTagCell.setTagNames(cell, tags);
                        else {
                            CompoundTag tag = Cell4Util.getCustomTag(cell);
                            tag.remove(NBTKeys.TAG);
                            Cell4Util.setCustomTag(cell, tag);
                        }
                        if (!modIds.isEmpty()) InfinityTagCell.setModIds(cell, modIds);
                        else {
                            CompoundTag tag = Cell4Util.getCustomTag(cell);
                            tag.remove(NBTKeys.MODID);
                            Cell4Util.setCustomTag(cell, tag);
                        }
                    }
                    case MODID -> {
                        if (!modIds.isEmpty()) InfinityModIdCell.setModIds(cell, modIds);
                        else {
                            CompoundTag tag = Cell4Util.getCustomTag(cell);
                            tag.remove(NBTKeys.MODID);
                            Cell4Util.setCustomTag(cell, tag);
                        }
                    }
                }
            }

            if (!blacklist.isEmpty()) {
                CompoundTag tag = Cell4Util.getCustomTag(cell);
                ListTag listTag = new ListTag();
                for (String entry : blacklist) listTag.add(StringTag.valueOf(entry));
                tag.put(NBTKeys.BLACKLIST, listTag);
                Cell4Util.setCustomTag(cell, tag);
            } else {
                CompoundTag tag = Cell4Util.getCustomTag(cell);
                tag.remove(NBTKeys.BLACKLIST);
                Cell4Util.setCustomTag(cell, tag);
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
            sendResponse(context, true, "gui.cell4.save_success");
        });
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
            if (id.indexOf('{') >= 0 || id.indexOf('[') >= 0) {
                // Vanilla item argument: id[...] components, id{...} SNBT or a full {id:...,count:...} compound
                if (Cell4Util.parseItemStackEntry(id).isEmpty()) {
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
                if (entry.indexOf('{') >= 0 || entry.indexOf('[') >= 0) {
                    // Vanilla item argument: id[...] components, id{...} SNBT or a full {id:...,count:...} compound
                    if (Cell4Util.parseItemStackEntry(entry).isEmpty()) {
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

    private static void sendResponse(IPayloadContext context, boolean success, String message) {
        if (context.player() instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new CellConfigResponsePacket(success, message));
        }
    }

    /**
     * Split a comma-separated entry list, ignoring commas inside SNBT compounds
     * (e.g. {id:"minecraft:stone",count:1,components:{...}}) or quoted strings.
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
