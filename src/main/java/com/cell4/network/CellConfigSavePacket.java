package com.cell4.network;

import com.cell4.Cell4;
import com.cell4.common.item.InfinityItemCell;
import com.cell4.common.item.InfinityModIdCell;
import com.cell4.common.item.InfinityTagCell;
import com.cell4.common.menu.CellConfiguratorMenu;
import com.cell4.common.util.Cell4Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

public record CellConfigSavePacket(String cell4item, String cell4tag, String cell4modid, String blacklist) implements CustomPacketPayload {

    public static final Type<CellConfigSavePacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Cell4.MODID, "cell_config_save")
    );

    public static final StreamCodec<FriendlyByteBuf, CellConfigSavePacket> STREAM_CODEC = StreamCodec.of(
        CellConfigSavePacket::encode,
        CellConfigSavePacket::decode
    );

    private static void encode(FriendlyByteBuf buf, CellConfigSavePacket pkt) {
        buf.writeUtf(pkt.cell4item, 1024);
        buf.writeUtf(pkt.cell4tag, 1024);
        buf.writeUtf(pkt.cell4modid, 1024);
        buf.writeUtf(pkt.blacklist, 1024);
    }

    private static CellConfigSavePacket decode(FriendlyByteBuf buf) {
        return new CellConfigSavePacket(buf.readUtf(1024), buf.readUtf(1024), buf.readUtf(1024), buf.readUtf(1024));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CellConfigSavePacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof CellConfiguratorMenu menu) {
                ItemStack cell = menu.getCellInSlot();
                if (cell.isEmpty()) return;

                List<String> items = parseCommaList(pkt.cell4item);
                List<String> tags = parseCommaList(pkt.cell4tag);
                List<String> modIds = parseCommaList(pkt.cell4modid);
                List<String> blacklist = parseCommaList(pkt.blacklist);

                // Apply changes on server side (using NeoForge DataComponents-compatible NBT methods)
                if (cell.getItem() instanceof InfinityItemCell) {
                    if (!items.isEmpty()) InfinityItemCell.setIdentifiers(cell, items);
                    else {
                        CompoundTag tag = Cell4Util.getCustomTag(cell);
                        tag.remove("cell4item");
                        Cell4Util.setCustomTag(cell, tag);
                    }
                }
                if (cell.getItem() instanceof InfinityTagCell) {
                    if (!tags.isEmpty()) InfinityTagCell.setTagNames(cell, tags);
                    else {
                        CompoundTag tag = Cell4Util.getCustomTag(cell);
                        tag.remove("cell4tag");
                        Cell4Util.setCustomTag(cell, tag);
                    }
                    if (!modIds.isEmpty()) InfinityTagCell.setModIds(cell, modIds);
                    else {
                        CompoundTag tag = Cell4Util.getCustomTag(cell);
                        tag.remove("cell4modid");
                        Cell4Util.setCustomTag(cell, tag);
                    }
                }
                if (cell.getItem() instanceof InfinityModIdCell) {
                    if (!modIds.isEmpty()) InfinityModIdCell.setModIds(cell, modIds);
                    else {
                        CompoundTag tag = Cell4Util.getCustomTag(cell);
                        tag.remove("cell4modid");
                        Cell4Util.setCustomTag(cell, tag);
                    }
                }

                if (!blacklist.isEmpty()) {
                    CompoundTag tag = Cell4Util.getCustomTag(cell);
                    ListTag listTag = new ListTag();
                    for (String entry : blacklist) {
                        listTag.add(StringTag.valueOf(entry));
                    }
                    tag.put(Cell4Util.BLACKLIST_KEY, listTag);
                    Cell4Util.setCustomTag(cell, tag);
                } else {
                    CompoundTag tag = Cell4Util.getCustomTag(cell);
                    tag.remove(Cell4Util.BLACKLIST_KEY);
                    Cell4Util.setCustomTag(cell, tag);
                }

                menu.getSlot(0).setChanged();
            }
        });
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
