package com.cell4.client;

import com.cell4.Cell4;
import com.cell4.common.item.*;
import com.cell4.common.menu.CellConfiguratorMenu;
import com.cell4.common.util.Cell4Util;
import com.cell4.network.CellConfigSavePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class CellConfiguratorScreen extends AbstractContainerScreen<CellConfiguratorMenu> {
    
    private static final ResourceLocation GUI_TEXTURE = ResourceLocation.fromNamespaceAndPath(Cell4.MODID, "textures/guis/cell_configurator.png");
    
    private EditBox cell4itemField;
    private EditBox cell4tagField;
    private EditBox cell4modidField;
    private EditBox blacklistField;
    private Cell4Button saveButton;
    
    private ItemStack lastCellItem = ItemStack.EMPTY;

    public CellConfiguratorScreen(CellConfiguratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 256;
        this.imageHeight = 256;
        this.inventoryLabelY = 167;
        this.titleLabelY = 6;
    }
    
    @Override
    protected void init() {
        super.init();
        
        int x = this.leftPos;
        int y = this.topPos;
        
        int fieldX = x + 71;
        int fieldWidth = 174;
        
        this.cell4itemField = new EditBox(this.font, fieldX, y + 80, fieldWidth, 12, Component.literal("cell4item"));
        this.cell4itemField.setMaxLength(1024);
        this.cell4itemField.setBordered(false);
        this.cell4itemField.setTextColor(0x2A3E5C);
        this.addRenderableWidget(this.cell4itemField);
        
        this.cell4tagField = new EditBox(this.font, fieldX, y + 100, fieldWidth, 12, Component.literal("cell4tag"));
        this.cell4tagField.setMaxLength(1024);
        this.cell4tagField.setBordered(false);
        this.cell4tagField.setTextColor(0x2A3E5C);
        this.addRenderableWidget(this.cell4tagField);
        
        this.cell4modidField = new EditBox(this.font, fieldX, y + 120, fieldWidth, 12, Component.literal("cell4modid"));
        this.cell4modidField.setMaxLength(1024);
        this.cell4modidField.setBordered(false);
        this.cell4modidField.setTextColor(0x2A3E5C);
        this.addRenderableWidget(this.cell4modidField);
        
        this.blacklistField = new EditBox(this.font, fieldX, y + 140, fieldWidth, 12, Component.literal("blacklist"));
        this.blacklistField.setMaxLength(1024);
        this.blacklistField.setBordered(false);
        this.blacklistField.setTextColor(0x2A3E5C);
        this.addRenderableWidget(this.blacklistField);
        
        // Save button aligned with cell slot, at far right of GUI
        this.saveButton = new Cell4Button(x + 176, y + 22, 72, 20, Component.translatable("gui.cell4.save"), this::onSave);
        this.addRenderableWidget(this.saveButton);
        
        this.lastCellItem = ItemStack.EMPTY;
    }
    
    @Override
    protected void containerTick() {
        super.containerTick();
        
        ItemStack currentCell = this.menu.getCellInSlot();
        
        if (!ItemStack.matches(currentCell, this.lastCellItem)) {
            this.lastCellItem = currentCell.copy();
            
            if (!currentCell.isEmpty()) {
                String itemValue = "";
                String tagValue = "";
                String modIdValue = "";
                String blacklistValue = "";
                
                if (currentCell.getItem() instanceof InfinityItemCell) {
                    itemValue = String.join(",", InfinityItemCell.getIdentifiers(currentCell));
                }
                if (currentCell.getItem() instanceof InfinityTagCell) {
                    tagValue = String.join(",", InfinityTagCell.getTagNames(currentCell));
                    modIdValue = String.join(",", InfinityTagCell.getModIds(currentCell));
                }
                if (currentCell.getItem() instanceof InfinityModIdCell) {
                    modIdValue = String.join(",", InfinityModIdCell.getModIds(currentCell));
                }
                blacklistValue = String.join(",", Cell4Util.getBlacklistIds(currentCell));
                
                this.cell4itemField.setValue(itemValue);
                this.cell4tagField.setValue(tagValue);
                this.cell4modidField.setValue(modIdValue);
                this.blacklistField.setValue(blacklistValue);
                
                this.cell4itemField.setEditable(CellConfiguratorMenu.canEditCell4Item(currentCell));
                this.cell4tagField.setEditable(CellConfiguratorMenu.canEditCell4Tag(currentCell));
                this.cell4modidField.setEditable(CellConfiguratorMenu.canEditCell4ModId(currentCell));
                this.blacklistField.setEditable(CellConfiguratorMenu.canEditBlacklist(currentCell));
            } else {
                this.cell4itemField.setValue("");
                this.cell4tagField.setValue("");
                this.cell4modidField.setValue("");
                this.blacklistField.setValue("");
                
                this.cell4itemField.setEditable(false);
                this.cell4tagField.setEditable(false);
                this.cell4modidField.setEditable(false);
                this.blacklistField.setEditable(false);

                // No cell: remove focus from all fields immediately
                clearAllFieldFocus();
            }
        }
    }
    
    private void onSave(net.minecraft.client.gui.components.Button button) {
        ItemStack targetCell = this.menu.getCellInSlot();
        if (targetCell.isEmpty()) return;
        
        // Send C2S packet — server applies the changes
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new CellConfigSavePacket(
            this.cell4itemField.getValue(),
            this.cell4tagField.getValue(),
            this.cell4modidField.getValue(),
            this.blacklistField.getValue()
        ));
    }
    
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(GUI_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int x, int y) {
        // Labels are drawn manually in render() — prevent default duplicate rendering
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        int x = this.leftPos;
        int y = this.topPos;
        
        guiGraphics.drawString(this.font, this.title, x + 8, y + 6, 0x2A3E5C, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.cell4.cell_slot"), x + 30, y + 26, 0x2A3E5C, false);
        
        ItemStack targetCell = this.menu.getCellInSlot();
        int labelActive = 0x2A3E5C;
        int labelInactive = 0x8899AA;
        
        guiGraphics.drawString(this.font, Component.translatable("gui.cell4.cell4item"), x + 8, y + 81,
                !targetCell.isEmpty() && CellConfiguratorMenu.canEditCell4Item(targetCell) ? labelActive : labelInactive, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.cell4.cell4tag"), x + 8, y + 101,
                !targetCell.isEmpty() && CellConfiguratorMenu.canEditCell4Tag(targetCell) ? labelActive : labelInactive, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.cell4.cell4modid"), x + 8, y + 121,
                !targetCell.isEmpty() && CellConfiguratorMenu.canEditCell4ModId(targetCell) ? labelActive : labelInactive, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.cell4.blacklist"), x + 8, y + 141,
                !targetCell.isEmpty() && CellConfiguratorMenu.canEditBlacklist(targetCell) ? labelActive : labelInactive, false);
        
        // Status line on the left
        if (!targetCell.isEmpty()) {
            guiGraphics.drawString(this.font, 
                Component.translatable("gui.cell4.editing", targetCell.getHoverName()), 
                x + 8, y + 156, 0x506A8C, false);
        } else {
            guiGraphics.drawString(this.font, 
                Component.translatable("gui.cell4.no_cell"), 
                x + 8, y + 156, 0xFF5555, false);
        }
        
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
    
    private EditBox getFocusedField() {
        if (this.cell4itemField.isFocused()) return this.cell4itemField;
        if (this.cell4tagField.isFocused()) return this.cell4tagField;
        if (this.cell4modidField.isFocused()) return this.cell4modidField;
        if (this.blacklistField.isFocused()) return this.blacklistField;
        return null;
    }

    private void clearAllFieldFocus() {
        this.cell4itemField.setFocused(false);
        this.cell4tagField.setFocused(false);
        this.cell4modidField.setFocused(false);
        this.blacklistField.setFocused(false);
    }

    private void switchFocusTo(EditBox target) {
        // Unfocus all fields first, then use Screen's setFocused
        // so that Screen properly tracks the focused widget
        clearAllFieldFocus();
        this.setFocused(target);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Before handling click, clear all EditBox focus so that
        // clicking a different field properly removes the cursor from the old one
        clearAllFieldFocus();
        // Always let super handle the click (inventory slot interaction, etc.)
        boolean result = super.mouseClicked(mouseX, mouseY, button);
        // If no cell is placed, remove any focus that super might have given to text fields
        if (this.menu.getCellInSlot().isEmpty()) {
            clearAllFieldFocus();
        }
        return result;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // When a text field is focused, we must NOT call super.keyPressed()
        // because AbstractContainerScreen checks key bindings there (e.g. 'E' closes inventory)
        // Instead, forward the key directly to the focused EditBox
        EditBox focused = getFocusedField();
        if (focused != null) {
            if (keyCode == 256) { // ESC - close screen
                this.onClose();
                return true;
            }
            // Tab key cycles focus between fields
            if (keyCode == 258) { // GLFW_KEY_TAB
                EditBox next = getNextField(focused);
                switchFocusTo(next);
                return true;
            }
            // Forward key to EditBox directly, skip super to prevent key bindings
            focused.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean isFieldEditable(EditBox field) {
        ItemStack cell = this.menu.getCellInSlot();
        if (cell.isEmpty()) return false;
        if (field == this.cell4itemField) return CellConfiguratorMenu.canEditCell4Item(cell);
        if (field == this.cell4tagField) return CellConfiguratorMenu.canEditCell4Tag(cell);
        if (field == this.cell4modidField) return CellConfiguratorMenu.canEditCell4ModId(cell);
        if (field == this.blacklistField) return CellConfiguratorMenu.canEditBlacklist(cell);
        return false;
    }

    private EditBox getNextField(EditBox current) {
        // Cycle: item -> tag -> modid -> blacklist -> item
        // Skip fields that are not editable
        EditBox[] order = {this.cell4itemField, this.cell4tagField, this.cell4modidField, this.blacklistField};
        int startIdx = -1;
        for (int i = 0; i < order.length; i++) {
            if (order[i] == current) { startIdx = i; break; }
        }
        for (int i = 1; i <= order.length; i++) {
            EditBox candidate = order[(startIdx + i) % order.length];
            if (isFieldEditable(candidate)) return candidate;
        }
        return current;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        // When a text field is focused, consume char events to prevent
        // them from reaching key binding handlers
        if (getFocusedField() != null) {
            getFocusedField().charTyped(codePoint, modifiers);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }
}
