package com.cell4.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class Cell4Button extends Button {

    public Cell4Button(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int borderColor = 0xFF3C5078;
        int bgColor = this.isHovered() ? 0xFFC8DDEF : 0xFFAFCAE6;
        int highlightColor = 0xFFDCEAFA;

        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), borderColor);
        graphics.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, bgColor);
        graphics.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + 2, highlightColor);

        Minecraft mc = Minecraft.getInstance();
        int textColor = this.isHovered() ? 0x2A3E5C : 0x3C5078;
        graphics.drawCenteredString(mc.font, this.getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, textColor);
    }
}
