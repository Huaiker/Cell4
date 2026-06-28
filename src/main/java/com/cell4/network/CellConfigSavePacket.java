package com.cell4.network;

import com.cell4.common.item.IInfinityCell;
import com.cell4.common.item.InfinityItemCell;
import com.cell4.common.item.InfinityModIdCell;
import com.cell4.common.item.InfinityTagCell;
import com.cell4.common.menu.CellConfiguratorMenu;
import com.cell4.common.util.Cell4Util;
import com.cell4.common.util.NBTKeys;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;
import java.util.function.Supplier;

/**
 * C2S packet for saving cell configuration.
 * 2.1: Now includes server-side validation.
 * 4.4: Now sends a response packet back to client.
 * 4.8: Increased max length from 1024 to 4096.
 */
public class CellConfigSavePacket {
    private final String cell4item;
    private final String cell4tag;
    private final String cell4modid;
    private final String blacklist;
    private final String cell4name;

    // 4.8: Increased from 1024 to 4096
    private static final int MAX_LENGTH = 4096;

    public CellConfigSavePacket(String cell4item, String cell4tag, String cell4modid, String blacklist, String cell4name) {
        this.cell4item = cell4item;
        this.cell4tag = cell4tag;
        this.cell4modid = cell4modid;
        this.blacklist = blacklist;
        this.cell4name = cell4name;
    }

    public static void encode(CellConfigSavePacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.cell4item, MAX_LENGTH);
        buf.writeUtf(pkt.cell4tag, MAX_LENGTH);
        buf.writeUtf(pkt.cell4modid, MAX_LENGTH);
        buf.writeUtf(pkt.blacklist, MAX_LENGTH);
        buf.writeUtf(pkt.cell4name, 256);
    }

    public static CellConfigSavePacket decode(FriendlyByteBuf buf) {
        return new CellConfigSavePacket(
            buf.readUtf(MAX_LENGTH), buf.readUtf(MAX_LENGTH),
            buf.readUtf(MAX_LENGTH), buf.readUtf(MAX_LENGTH),
            buf.readUtf(256)
        );
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

            List<String> items = parseCommaList(pkt.cell4item);
            List<String> tags = parseCommaList(pkt.cell4tag);
            List<String> modIds = parseCommaList(pkt.cell4modid);
            List<String> blacklist = parseCommaList(pkt.blacklist);
            String customName = pkt.cell4name != null ? pkt.cell4name.trim() : "";

            // 2.1: Server-side validation
            String validationError = validate(cell, items, tags, modIds, blacklist);
            if (validationError != null) {
                sendResponse(player, false, validationError);
                return;
            }

            // Apply changes on server side
            if (cell.getItem() instanceof InfinityItemCell) {
                if (!items.isEmpty()) InfinityItemCell.setIdentifiers(cell, items);
                else cell.removeTagKey(NBTKeys.ITEM);
            }
            if (cell.getItem() instanceof InfinityTagCell) {
                if (!tags.isEmpty()) InfinityTagCell.setTagNames(cell, tags);
                else cell.removeTagKey(NBTKeys.TAG);
                if (!modIds.isEmpty()) InfinityTagCell.setModIds(cell, modIds);
                else cell.removeTagKey(NBTKeys.MODID);
            }
            if (cell.getItem() instanceof InfinityModIdCell) {
                if (!modIds.isEmpty()) InfinityModIdCell.setModIds(cell, modIds);
                else cell.removeTagKey(NBTKeys.MODID);
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

    /**
     * 4.4: Send a response packet back to the client.
     */
    private static void sendResponse(ServerPlayer player, boolean success, String message) {
        Cell4Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CellConfigResponsePacket(success, message));
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
