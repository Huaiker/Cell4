package com.cell4.network;

import com.cell4.common.item.InfinityItemCell;
import com.cell4.common.item.InfinityModIdCell;
import com.cell4.common.item.InfinityTagCell;
import com.cell4.common.menu.CellConfiguratorMenu;
import com.cell4.common.util.Cell4Util;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

public class CellConfigSavePacket {
    private final String cell4item;
    private final String cell4tag;
    private final String cell4modid;
    private final String blacklist;

    public CellConfigSavePacket(String cell4item, String cell4tag, String cell4modid, String blacklist) {
        this.cell4item = cell4item;
        this.cell4tag = cell4tag;
        this.cell4modid = cell4modid;
        this.blacklist = blacklist;
    }

    public static void encode(CellConfigSavePacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.cell4item, 1024);
        buf.writeUtf(pkt.cell4tag, 1024);
        buf.writeUtf(pkt.cell4modid, 1024);
        buf.writeUtf(pkt.blacklist, 1024);
    }

    public static CellConfigSavePacket decode(FriendlyByteBuf buf) {
        return new CellConfigSavePacket(buf.readUtf(1024), buf.readUtf(1024), buf.readUtf(1024), buf.readUtf(1024));
    }

    public static void handle(CellConfigSavePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.containerMenu instanceof CellConfiguratorMenu menu) {
                ItemStack cell = menu.getCellInSlot();
                if (cell.isEmpty()) return;

                List<String> items = parseCommaList(pkt.cell4item);
                List<String> tags = parseCommaList(pkt.cell4tag);
                List<String> modIds = parseCommaList(pkt.cell4modid);
                List<String> blacklist = parseCommaList(pkt.blacklist);

                // Apply changes on server side
                if (cell.getItem() instanceof InfinityItemCell) {
                    if (!items.isEmpty()) InfinityItemCell.setIdentifiers(cell, items);
                    else cell.removeTagKey("cell4item");
                }
                if (cell.getItem() instanceof InfinityTagCell) {
                    if (!tags.isEmpty()) InfinityTagCell.setTagNames(cell, tags);
                    else cell.removeTagKey("cell4tag");
                    if (!modIds.isEmpty()) InfinityTagCell.setModIds(cell, modIds);
                    else cell.removeTagKey("cell4modid");
                }
                if (cell.getItem() instanceof InfinityModIdCell) {
                    if (!modIds.isEmpty()) InfinityModIdCell.setModIds(cell, modIds);
                    else cell.removeTagKey("cell4modid");
                }

                if (!blacklist.isEmpty()) {
                    var tag = cell.getOrCreateTag();
                    ListTag listTag = new ListTag();
                    for (String entry : blacklist) {
                        listTag.add(StringTag.valueOf(entry));
                    }
                    tag.put(Cell4Util.BLACKLIST_KEY, listTag);
                } else {
                    cell.removeTagKey(Cell4Util.BLACKLIST_KEY);
                }

                menu.getSlot(0).setChanged();
            }
        });
        ctx.get().setPacketHandled(true);
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
