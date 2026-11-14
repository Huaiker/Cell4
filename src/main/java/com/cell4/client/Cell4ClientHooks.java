package com.cell4.client;

import com.cell4.common.util.Cell4Config;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;

/**
 * 客户端工具：在系统文件管理器中打开自定义材质文件夹、提供当前世界的注册表访问。
 */
public final class Cell4ClientHooks {

    private Cell4ClientHooks() {}

    public static void openTextureFolder() {
        try {
            var dir = Cell4Config.getTextureDir();
            if (dir == null) return;
            java.nio.file.Files.createDirectories(dir);
            net.minecraft.util.Util.getPlatform().openFile(dir.toFile());
        } catch (Throwable ignored) {
        }
    }

    /**
     * 当前客户端世界的注册表（用于解析物品组件，例如附魔）。未进入世界时返回 null。
     */
    public static HolderLookup.Provider getRegistryAccess() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) return null;
        return minecraft.level.registryAccess();
    }
}
