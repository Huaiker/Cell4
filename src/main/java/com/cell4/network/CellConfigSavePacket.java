package com.cell4.network;

import com.cell4.Cell4;
import com.cell4.common.item.IInfinityCell;
import com.cell4.common.item.InfinityItemCell;
import com.cell4.common.item.InfinityModIdCell;
import com.cell4.common.item.InfinityTagCell;
import com.cell4.common.menu.CellConfiguratorMenu;
import com.cell4.common.util.Cell4Util;
import com.cell4.common.util.NBTKeys;
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

import java.util.*;

/**
 * C2S packet for saving cell configuration.
 * 2.1: Now includes server-side validation.
 * 4.4: Now sends a response packet back to client.
 * 4.8: Increased max length from 1024 to 4096.
 */
public record CellConfigSavePacket(String cell4item, String cell4tag, String cell4modid, String blacklist, String cell4name) implements CustomPacketPayload {

    // 4.8: Increased from 1024 to 4096
    private static final int MAX_LENGTH = 4096;

    public static final Type<CellConfigSavePacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Cell4.MODID, "cell_config_save")
    );

    public static final StreamCodec<FriendlyByteBuf, CellConfigSavePacket> STREAM_CODEC = StreamCodec.of(
        CellConfigSavePacket::encode,
        CellConfigSavePacket::decode
    );

    private static void encode(FriendlyByteBuf buf, CellConfigSavePacket pkt) {
        buf.writeUtf(pkt.cell4item, MAX_LENGTH);
        buf.writeUtf(pkt.cell4tag, MAX_LENGTH);
        buf.writeUtf(pkt.cell4modid, MAX_LENGTH);
        buf.writeUtf(pkt.blacklist, MAX_LENGTH);
        buf.writeUtf(pkt.cell4name, 256);
    }

    private static CellConfigSavePacket decode(FriendlyByteBuf buf) {
        return new CellConfigSavePacket(
            buf.readUtf(MAX_LENGTH), buf.readUtf(MAX_LENGTH),
            buf.readUtf(MAX_LENGTH), buf.readUtf(MAX_LENGTH),
            buf.readUtf(256)
        );
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
            String validationError = validate(cell, items, tags, modIds, blacklist);
            if (validationError != null) {
                sendResponse(context, false, validationError);
                return;
            }

            // Apply changes
            if (cell.getItem() instanceof InfinityItemCell) {
                if (!items.isEmpty()) InfinityItemCell.setIdentifiers(cell, items);
                else { CompoundTag tag = Cell4Util.getCustomTag(cell); tag.remove(NBTKeys.ITEM); Cell4Util.setCustomTag(cell, tag); }
            }
            if (cell.getItem() instanceof InfinityTagCell) {
                if (!tags.isEmpty()) InfinityTagCell.setTagNames(cell, tags);
                else { CompoundTag tag = Cell4Util.getCustomTag(cell); tag.remove(NBTKeys.TAG); Cell4Util.setCustomTag(cell, tag); }
                if (!modIds.isEmpty()) InfinityTagCell.setModIds(cell, modIds);
                else { CompoundTag tag = Cell4Util.getCustomTag(cell); tag.remove(NBTKeys.MODID); Cell4Util.setCustomTag(cell, tag); }
            }
            if (cell.getItem() instanceof InfinityModIdCell) {
                if (!modIds.isEmpty()) InfinityModIdCell.setModIds(cell, modIds);
                else { CompoundTag tag = Cell4Util.getCustomTag(cell); tag.remove(NBTKeys.MODID); Cell4Util.setCustomTag(cell, tag); }
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
    private static String validate(ItemStack cell, List<String> items, List<String> tags, List<String> modIds, List<String> blacklist) {
        // Validate item identifiers (support mekfluid:/mekchemical: prefixes)
        for (String id : items) {
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

    private static List<String> parseCommaList(String value) {
        if (value == null || value.isBlank()) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }
}
