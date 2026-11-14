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
import net.minecraft.resources.Identifier;
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
 * 4.9: Limits are now counted in UTF-8 bytes (not chars), so large NBT payloads
 *      (e.g. AE2 portable cell contents, tens of KB each) are no longer truncated.
 * <p>
 * 注意：单个网络包 2MB 是 MC/Netty 的帧限制（{@code CompressionDecoder.MAXIMUM_COMPRESSED_LENGTH}
 * 以及 Varint21 三字节长度前缀），超过该限制必须分片发送。本包的 UTF-8 总预算已接近这一硬限，
 * 若日后仍需要更大的配置，请改为分片传输，而不是继续提高这里的常量。
 */
public record CellConfigSavePacket(List<String> cell4item, List<String> cell4tag, List<String> cell4modid,
                                   List<String> blacklist,
                                   String cell4name, String powerMax, String powerCost, String powerCharge,
                                   String texture)
        implements CustomPacketPayload {

    // 26.1.2 的 writeUtf/readUtf 参数是字符数（Utf8String.write 校验 value.length()，
    // Utf8String.read 校验解码后的字符数），对应字节上限是该参数的 3 倍，故 1_048_576 字符
    // 足以容纳 MAX_ENTRY_BYTES = 1_000_000 字节的单条数据（纯 ASCII 时也是 1_000_000 字符）。
    private static final int MAX_LENGTH = 1_048_576;
    // 单个网络包 2MB 是 MC/Netty 帧限制，1_900_000 给帧头、条目计数和 varint 长度前缀留出余量。
    private static final long MAX_TOTAL_BYTES = 1_900_000L;
    private static final int MAX_ENTRY_BYTES = 1_000_000;
    // 发送/接收预算：防止恶意或误操作发送数万条合法 id 撑爆槽位同步包。
    private static final int MAX_ENTRIES = 16384;

    public static final Type<CellConfigSavePacket> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(Cell4.MODID, "cell_config_save")
    );

    public static final StreamCodec<FriendlyByteBuf, CellConfigSavePacket> STREAM_CODEC = StreamCodec.of(
        CellConfigSavePacket::encode,
        CellConfigSavePacket::decode
    );

    private static void encode(FriendlyByteBuf buf, CellConfigSavePacket pkt) {
        Limits limits = new Limits();
        writeList(buf, pkt.cell4item, "cell4item", limits);
        writeList(buf, pkt.cell4tag, "cell4tag", limits);
        writeList(buf, pkt.cell4modid, "cell4modid", limits);
        writeList(buf, pkt.blacklist, "blacklist", limits);
        buf.writeUtf(pkt.cell4name, 256);
        buf.writeUtf(pkt.powerMax == null ? "" : pkt.powerMax, 64);
        buf.writeUtf(pkt.powerCost == null ? "" : pkt.powerCost, 64);
        buf.writeUtf(pkt.powerCharge == null ? "" : pkt.powerCharge, 64);
        buf.writeUtf(pkt.texture == null ? "" : pkt.texture, 128);
    }

    /** 整个包共享的发送/接收预算（按 UTF-8 字节计），避免四个列表各自放大到上限。 */
    private static final class Limits {
        int entries;
        long bytes;
    }

    /**
     * 写列表元素数量 + 逐条 writeUtf，避免把整表拼接后单字段超过 writeUtf 上限。
     * 空列表写 0。单条超过 MAX_ENTRY_BYTES 的条目丢弃，总条目/总字节超出预算时截断，均记录警告；
     * 正常条目（AE2 便携元件几十 KB 的 NBT）远低于预算，不会被截断。
     */
    private static void writeList(FriendlyByteBuf buf, List<String> values, String field, Limits limits) {
        List<String> list = values == null ? List.of() : values;
        List<String> accepted = new ArrayList<>(Math.min(list.size(), 64));
        int dropped = 0;
        for (String value : list) {
            if (limits.entries >= MAX_ENTRIES || limits.bytes >= MAX_TOTAL_BYTES) break;
            String entry = value == null ? "" : value;
            int bytes = entry.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > MAX_ENTRY_BYTES) {
                dropped++;
                Cell4.LOGGER.warn("Cell4: dropped oversized {} entry ({} UTF-8 bytes > {})",
                        field, bytes, MAX_ENTRY_BYTES);
                continue;
            }
            if (limits.bytes + bytes > MAX_TOTAL_BYTES) break;
            limits.entries++;
            limits.bytes += bytes;
            accepted.add(entry);
        }
        if (accepted.size() < list.size()) {
            Cell4.LOGGER.warn("Cell4: truncated {} list from {} to {} entries (total budget: {} entries / {} UTF-8 bytes, dropped {} oversized)",
                    field, list.size(), accepted.size(), MAX_ENTRIES, MAX_TOTAL_BYTES, dropped);
        }
        buf.writeVarInt(accepted.size());
        for (String value : accepted) {
            buf.writeUtf(value, MAX_LENGTH);
        }
    }

    /**
     * 读取列表：先读条目数，再逐条读取。条目数或总字节数超限时仍读完整个列表
     * （保持流对齐），只保留预算内的条目并记录警告，绝不因数量异常直接断开连接。
     */
    private static List<String> readList(FriendlyByteBuf buf, String field, Limits limits) {
        int size = buf.readVarInt();
        if (size <= 0) return List.of();
        List<String> list = new ArrayList<>(Math.min(size, 64));
        boolean truncated = false;
        for (int i = 0; i < size; i++) {
            String value = buf.readUtf(MAX_LENGTH);
            int bytes = value.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > MAX_ENTRY_BYTES || limits.entries >= MAX_ENTRIES || limits.bytes + bytes > MAX_TOTAL_BYTES) {
                truncated = true;
                continue;
            }
            limits.entries++;
            limits.bytes += bytes;
            list.add(value);
        }
        if (truncated || list.size() < size) {
            Cell4.LOGGER.warn("Cell4: truncated received {} list from {} to {} entries (limits: {} entries / {} UTF-8 bytes)",
                    field, size, list.size(), MAX_ENTRIES, MAX_TOTAL_BYTES);
        }
        return list;
    }

    private static CellConfigSavePacket decode(FriendlyByteBuf buf) {
        try {
            Limits limits = new Limits();
            return new CellConfigSavePacket(
                readList(buf, "cell4item", limits),
                readList(buf, "cell4tag", limits),
                readList(buf, "cell4modid", limits),
                readList(buf, "blacklist", limits),
                buf.readUtf(256), buf.readUtf(64), buf.readUtf(64), buf.readUtf(64),
                buf.readUtf(128)
            );
        } catch (Exception e) {
            // 畸形负载直接当作空配置拒绝，避免解码异常导致玩家断线。
            Cell4.LOGGER.warn("Cell4: rejected malformed cell config payload", e);
            return new CellConfigSavePacket(List.of(), List.of(), List.of(), List.of(),
                    "", "", "", "", "");
        }
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

            List<String> items = pkt.cell4item != null ? pkt.cell4item : List.of();
            List<String> tags = pkt.cell4tag != null ? pkt.cell4tag : List.of();
            List<String> modIds = pkt.cell4modid != null ? pkt.cell4modid : List.of();
            List<String> blacklist = pkt.blacklist != null ? pkt.blacklist : List.of();
            String customName = pkt.cell4name != null ? pkt.cell4name.trim() : "";

            // 2.1: Server-side validation
            String validationError = validate(cell, items, tags, modIds, blacklist, pkt.powerMax, pkt.powerCost, pkt.powerCharge);
            if (validationError != null) {
                sendResponse(context, false, validationError);
                return;
            }

            // Apply changes (works for normal and portable cells).
            if (cell.getItem() instanceof IInfinityCell infinityCell) {
                switch (infinityCell.getCellType()) {
                    case ITEM -> {
                        if (!items.isEmpty()) InfinityItemCell.setIdentifiers(cell, items);
                        else { CompoundTag tag = Cell4Util.getCustomTag(cell); tag.remove(NBTKeys.ITEM); Cell4Util.setCustomTag(cell, tag); }
                    }
                    case TAG -> {
                        if (!tags.isEmpty()) InfinityTagCell.setTagNames(cell, tags);
                        else { CompoundTag tag = Cell4Util.getCustomTag(cell); tag.remove(NBTKeys.TAG); Cell4Util.setCustomTag(cell, tag); }
                        if (!modIds.isEmpty()) InfinityTagCell.setModIds(cell, modIds);
                        else { CompoundTag tag = Cell4Util.getCustomTag(cell); tag.remove(NBTKeys.MODID); Cell4Util.setCustomTag(cell, tag); }
                    }
                    case MODID -> {
                        if (!modIds.isEmpty()) InfinityModIdCell.setModIds(cell, modIds);
                        else { CompoundTag tag = Cell4Util.getCustomTag(cell); tag.remove(NBTKeys.MODID); Cell4Util.setCustomTag(cell, tag); }
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
        // Validate item identifiers (exact ids, full item syntax or glob/regex)
        for (String id : items) {
            String error = validateItemEntry(id);
            if (error != null) return error;
        }
        // Validate tag names
        for (String tag : tags) {
            if (Identifier.tryParse(tag) == null) {
                return "gui.cell4.save_fail_invalid_tag";
            }
        }
        // Validate mod IDs (should be lowercase alphanumeric with possible underscores/dots)
        for (String modId : modIds) {
            if (!modId.matches("[a-z0-9_.]+")) {
                return "gui.cell4.save_fail_invalid_modid";
            }
        }
        // Validate blacklist entries
        for (String entry : blacklist) {
            if (entry.startsWith("#")) {
                String tagName = entry.substring(1);
                if (tagName.isEmpty() || Identifier.tryParse(tagName) == null) {
                    return "gui.cell4.save_fail_invalid_blacklist_tag";
                }
            } else if (entry.startsWith("@")) {
                String modId = entry.substring(1);
                if (modId.isEmpty() || !modId.matches("[a-z0-9_.]+")) {
                    return "gui.cell4.save_fail_invalid_blacklist_modid";
                }
            } else {
                String error = validateItemEntry(entry);
                if (error != null) {
                    return "gui.cell4.save_fail_invalid_item".equals(error)
                            ? "gui.cell4.save_fail_invalid_blacklist_item" : error;
                }
            }
        }
        return null;
    }

    /**
     * 校验单条物品/黑名单条目：物品语法（{@code {}}/{@code []}）走与
     * {@link Cell4Util#parseItemStackEntry} 相同的解析，纯 ID 才用 Identifier 校验。
     */
    private static String validateItemEntry(String entry) {
        if (Cell4Util.isItemStackEntry(entry)) {
            return Cell4Util.parseItemStackEntry(entry) == null ? "gui.cell4.save_fail_invalid_item" : null;
        }
        if (Cell4Patterns.isPattern(entry)) {
            return Cell4Patterns.isValidPattern(entry) ? null : "gui.cell4.save_fail_invalid_pattern";
        }
        // 含组件的条目即使未被 isItemStackEntry 识别，也不能退回 Identifier 校验（会误判为非法 ID）
        if (Cell4Util.hasItemStackSyntax(entry)) {
            return Cell4Util.parseItemStackEntry(entry) == null ? "gui.cell4.save_fail_invalid_item" : null;
        }
        return Identifier.tryParse(entry) == null ? "gui.cell4.save_fail_invalid_item" : null;
    }

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

    private static void sendResponse(IPayloadContext context, boolean success, String message) {
        if (context.player() instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new CellConfigResponsePacket(success, message));
        }
    }
}
