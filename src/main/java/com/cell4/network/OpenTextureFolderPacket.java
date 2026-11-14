package com.cell4.network;

import com.cell4.Cell4;
import com.cell4.client.Cell4ClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C：让客户端打开自定义材质文件夹。
 */
public record OpenTextureFolderPacket() implements CustomPacketPayload {

    public static final OpenTextureFolderPacket INSTANCE = new OpenTextureFolderPacket();

    public static final Type<OpenTextureFolderPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Cell4.MODID, "open_texture_folder"));

    public static final StreamCodec<FriendlyByteBuf, OpenTextureFolderPacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenTextureFolderPacket packet, IPayloadContext context) {
        context.enqueueWork(Cell4ClientHooks::openTextureFolder);
    }
}
