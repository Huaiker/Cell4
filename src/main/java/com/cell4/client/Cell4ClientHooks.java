package com.cell4.client;

import com.cell4.common.util.Cell4Config;

/**
 * 客户端工具：在系统文件管理器中打开自定义材质文件夹。
 */
public final class Cell4ClientHooks {

    private Cell4ClientHooks() {}

    public static void openTextureFolder() {
        try {
            var dir = Cell4Config.getTextureDir();
            if (dir == null) return;
            java.nio.file.Files.createDirectories(dir);
            net.minecraft.Util.getPlatform().openFile(dir.toFile());
        } catch (Throwable ignored) {
        }
    }
}
