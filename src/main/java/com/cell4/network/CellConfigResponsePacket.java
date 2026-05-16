package com.cell4.network;

import com.cell4.Cell4;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C packet sent from server to client after a configuration save attempt.
 * Provides success/failure feedback to the configurator screen.
 */
public record CellConfigResponsePacket(boolean success, String message) implements CustomPacketPayload {

    public static final Type<CellConfigResponsePacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Cell4.MODID, "cell_config_response")
    );

    public static final StreamCodec<FriendlyByteBuf, CellConfigResponsePacket> STREAM_CODEC = StreamCodec.of(
        CellConfigResponsePacket::encode,
        CellConfigResponsePacket::decode
    );

    private static void encode(FriendlyByteBuf buf, CellConfigResponsePacket pkt) {
        buf.writeBoolean(pkt.success);
        buf.writeUtf(pkt.message, 4096);
    }

    private static CellConfigResponsePacket decode(FriendlyByteBuf buf) {
        return new CellConfigResponsePacket(buf.readBoolean(), buf.readUtf(4096));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CellConfigResponsePacket pkt, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().isClientSide) {
                // Client-side: store the response for the screen to pick up
                com.cell4.client.CellConfiguratorScreen.setLastSaveResult(pkt.success, pkt.message);
            }
        });
    }
}
