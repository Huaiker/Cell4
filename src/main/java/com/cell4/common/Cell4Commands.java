package com.cell4.common;

import com.cell4.Cell4;
import com.cell4.common.menu.CellConfiguratorMenu;
import com.cell4.network.OpenTextureFolderPacket;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * /cell4 gui    - 打开元件编辑器
 * /cell4 texfile - 打开自定义材质文件夹（客户端执行）
 */
@EventBusSubscriber(modid = Cell4.MODID)
public final class Cell4Commands {

    private Cell4Commands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("cell4")
                .then(Commands.literal("gui").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    player.openMenu(new SimpleMenuProvider(
                            (id, inv, p) -> new CellConfiguratorMenu(id, inv),
                            Component.translatable("item.cell4.cell_configurator")));
                    return 1;
                }))
                .then(Commands.literal("texfile").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    PacketDistributor.sendToPlayer(player, OpenTextureFolderPacket.INSTANCE);
                    return 1;
                })));
    }
}
