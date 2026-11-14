package com.cell4.network;

import com.cell4.client.Cell4ClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C：让客户端打开自定义材质文件夹。
 */
public class OpenTextureFolderPacket {

    public static void encode(OpenTextureFolderPacket pkt, FriendlyByteBuf buf) {
    }

    public static OpenTextureFolderPacket decode(FriendlyByteBuf buf) {
        return new OpenTextureFolderPacket();
    }

    public static void handle(OpenTextureFolderPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(Cell4ClientHooks::openTextureFolder);
        ctx.get().setPacketHandled(true);
    }
}
