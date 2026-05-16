package com.cell4.network;

import com.cell4.Cell4;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class Cell4Network {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(Cell4.MODID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    private static int nextId = 0;

    public static void register() {
        CHANNEL.registerMessage(nextId++, CellConfigSavePacket.class,
            CellConfigSavePacket::encode,
            CellConfigSavePacket::decode,
            CellConfigSavePacket::handle);
        // 4.4: Register S2C response packet
        CHANNEL.registerMessage(nextId++, CellConfigResponsePacket.class,
            CellConfigResponsePacket::encode,
            CellConfigResponsePacket::decode,
            CellConfigResponsePacket::handle);
    }
}
