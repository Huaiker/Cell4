package com.cell4.client;

import com.cell4.Cell4;
import com.cell4.common.item.*;
import com.cell4.common.menu.CellConfiguratorMenu;
import com.cell4.common.util.Cell4Util;
import com.cell4.network.CellConfigSavePacket;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.*;

public class CellConfiguratorScreen extends AbstractContainerScreen<CellConfiguratorMenu> {

    private static final Identifier GUI_TEXTURE = Identifier.fromNamespaceAndPath(Cell4.MODID, "textures/guis/cell_configurator.png");

    // UI dimensions
    private static final int CONTENT_X = 8;
    private static final int CONTENT_Y = 44;
    private static final int CONTENT_W = 300;
    private static final int VIEWPORT_H = 138; // visible scroll area height
    private static final int ENTRY_H = 14;
    private static final int GROUP_HEADER_H = 14;
    private static final int GROUP_GAP = 4;
    private static final int SCROLLBAR_W = 6;
    private static final int SCROLLBAR_X_OFFSET = 306; // from leftPos

    // Name field at top (next to item slot)
    private EditBox nameField;

    // Group data
    private static final int GROUP_ITEMS = 0;
    private static final int GROUP_TAGS = 1;
    private static final int GROUP_MODIDS = 2;
    private static final int GROUP_BLACKLIST = 3;

    private final List<EntryGroup> groups = new ArrayList<>();

    // Scroll state
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private boolean scrolling = false;

    // Track cell changes
    private ItemStack lastCellItem = ItemStack.EMPTY;

    // Save result feedback
    private static boolean lastSaveSuccess = false;
    private static String lastSaveMessage = "";
    private static long saveResultTime = 0;
    private static final long SAVE_RESULT_DISPLAY_MS = 3000;

    // Snapshots for reset
    private List<String> snapshotItems = new ArrayList<>();
    private List<String> snapshotTags = new ArrayList<>();
    private List<String> snapshotModIds = new ArrayList<>();
    private List<String> snapshotBlacklist = new ArrayList<>();
    private String snapshotName = "";

    // Help button hover state
    private boolean helpHovered = false;

    // Cached mouse position for rendering (since extract model doesn't pass mouse coords to all methods)
    private int cachedMouseX = 0;
    private int cachedMouseY = 0;

    // Inner class: represents a collapsible group of entries
    private class EntryGroup {
        final int groupId;
        final String labelKey;
        final List<String> entries = new ArrayList<>();
        final List<EditBox> entryFields = new ArrayList<>();
        boolean collapsed = false;

        EntryGroup(int groupId, String labelKey) {
            this.groupId = groupId;
            this.labelKey = labelKey;
        }

        boolean isEditable() {
            ItemStack cell = menu.getCellInSlot();
            if (cell.isEmpty()) return false;
            return switch (groupId) {
                case GROUP_ITEMS -> CellConfiguratorMenu.canEditCell4Item(cell);
                case GROUP_TAGS -> CellConfiguratorMenu.canEditCell4Tag(cell);
                case GROUP_MODIDS -> CellConfiguratorMenu.canEditCell4ModId(cell);
                case GROUP_BLACKLIST -> CellConfiguratorMenu.canEditBlacklist(cell);
                default -> false;
            };
        }

        void clearEntries() {
            for (EditBox field : entryFields) {
                removeWidget(field);
            }
            entryFields.clear();
            entries.clear();
        }

        void setEntries(List<String> values) {
            clearEntries();
            for (String val : values) {
                addEntry(val);
            }
            collapsed = values.isEmpty();
        }

        void addEntry(String value) {
            entries.add(value);
            EditBox field = createEntryEditBox(entries.size() - 1, value);
            entryFields.add(field);
            addRenderableWidget(field);
        }

        void addEmptyEntry() {
            addEntry("");
            collapsed = false;
            rebuildFields();
        }

        void removeEntry(int index) {
            if (index >= 0 && index < entries.size()) {
                entries.remove(index);
                rebuildFields();
            }
        }

        void rebuildFields() {
            for (EditBox field : entryFields) {
                removeWidget(field);
            }
            entryFields.clear();
            for (int i = 0; i < entries.size(); i++) {
                EditBox field = createEntryEditBox(i, entries.get(i));
                entryFields.add(field);
                addRenderableWidget(field);
            }
        }

        int getTotalHeight() {
            if (collapsed) return GROUP_HEADER_H;
            return GROUP_HEADER_H + entries.size() * ENTRY_H;
        }
    }

    public CellConfiguratorScreen(CellConfiguratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, Component.translatable("item.cell4.cell_configurator"), 320, 280);
        this.inventoryLabelY = -9999; // Hide default inventory label off-screen
        this.titleLabelY = -9999; // Hide default title label off-screen

        // Initialize groups
        groups.add(new EntryGroup(GROUP_ITEMS, "gui.cell4.group_items"));
        groups.add(new EntryGroup(GROUP_TAGS, "gui.cell4.group_tags"));
        groups.add(new EntryGroup(GROUP_MODIDS, "gui.cell4.group_modids"));
        groups.add(new EntryGroup(GROUP_BLACKLIST, "gui.cell4.group_blacklist"));
    }

    public static void setLastSaveResult(boolean success, String message) {
        lastSaveSuccess = success;
        lastSaveMessage = message;
        saveResultTime = System.currentTimeMillis();
    }

    private EditBox createEntryEditBox(int index, String value) {
        int fieldX = leftPos + CONTENT_X + 24;
        int fieldW = CONTENT_W - 24 - 14 - SCROLLBAR_W - 4; // 24 for seq number, 14 for X button, scrollbar
        EditBox field = new EditBox(this.font, fieldX, 0, fieldW, 12, Component.literal("entry_" + index));
        field.setMaxLength(1024);
        field.setBordered(false);
        field.setTextColor(0xFFFFFFFF);
        field.setValue(value != null ? value : "");
        return field;
    }

    @Override
    protected void init() {
        super.init();

        int x = this.leftPos;
        int y = this.topPos;

        // Name field next to item slot (slot is at x+8, y+22; name starts at x+40, moved 10px right)
        this.nameField = new EditBox(this.font, x + 44, y + 27, 168, 12, Component.literal("cell4name"));
        this.nameField.setMaxLength(256);
        this.nameField.setBordered(false);
        this.nameField.setTextColor(0xFFFFFFFF);
        this.addRenderableWidget(this.nameField);

        // Rebuild all group fields
        for (EntryGroup group : groups) {
            group.rebuildFields();
        }

        this.lastCellItem = ItemStack.EMPTY;
    }

    @Override
    protected void containerTick() {
        super.containerTick();

        ItemStack currentCell = this.menu.getCellInSlot();

        if (!ItemStack.matches(currentCell, this.lastCellItem)) {
            this.lastCellItem = currentCell.copy();

            if (!currentCell.isEmpty()) {
                List<String> itemEntries = new ArrayList<>();
                List<String> tagEntries = new ArrayList<>();
                List<String> modIdEntries = new ArrayList<>();
                List<String> blacklistEntries = new ArrayList<>();
                String nameValue = "";

                if (currentCell.getItem() instanceof InfinityItemCell) {
                    itemEntries = new ArrayList<>(InfinityItemCell.getIdentifiers(currentCell));
                }
                if (currentCell.getItem() instanceof InfinityTagCell) {
                    tagEntries = new ArrayList<>(InfinityTagCell.getTagNames(currentCell));
                    modIdEntries = new ArrayList<>(InfinityTagCell.getModIds(currentCell));
                }
                if (currentCell.getItem() instanceof InfinityModIdCell) {
                    modIdEntries = new ArrayList<>(InfinityModIdCell.getModIds(currentCell));
                }
                blacklistEntries = new ArrayList<>(Cell4Util.getBlacklistIds(currentCell));

                if (currentCell.getItem() instanceof IInfinityCell cell) {
                    nameValue = cell.getCustomName(currentCell);
                }

                groups.get(GROUP_ITEMS).setEntries(itemEntries);
                groups.get(GROUP_TAGS).setEntries(tagEntries);
                groups.get(GROUP_MODIDS).setEntries(modIdEntries);
                groups.get(GROUP_BLACKLIST).setEntries(blacklistEntries);
                this.nameField.setValue(nameValue);

                this.nameField.setEditable(CellConfiguratorMenu.canEditName(currentCell));

                // Save snapshot for reset
                snapshotItems = new ArrayList<>(itemEntries);
                snapshotTags = new ArrayList<>(tagEntries);
                snapshotModIds = new ArrayList<>(modIdEntries);
                snapshotBlacklist = new ArrayList<>(blacklistEntries);
                snapshotName = nameValue;

                scrollOffset = 0;
            } else {
                for (EntryGroup group : groups) {
                    group.clearEntries();
                    group.collapsed = true;
                }
                this.nameField.setValue("");
                this.nameField.setEditable(false);
                scrollOffset = 0;
            }
            updateFieldPositions();
        }

        // Sync entry values from EditBoxes back to entries lists
        for (EntryGroup group : groups) {
            for (int i = 0; i < group.entryFields.size() && i < group.entries.size(); i++) {
                group.entries.set(i, group.entryFields.get(i).getValue());
            }
        }
    }

    private void updateFieldPositions() {
        int baseY = topPos + CONTENT_Y - scrollOffset;
        int currentY = baseY;

        for (EntryGroup group : groups) {
            if (!group.collapsed) {
                for (int i = 0; i < group.entryFields.size(); i++) {
                    EditBox field = group.entryFields.get(i);
                    int fieldY = currentY + GROUP_HEADER_H + i * ENTRY_H + 1;
                    field.setY(fieldY);
                    field.setEditable(group.isEditable());
                    field.visible = isRowVisible(fieldY - topPos, ENTRY_H);
                }
            } else {
                for (EditBox field : group.entryFields) {
                    field.visible = false;
                }
            }
            currentY += group.getTotalHeight() + GROUP_GAP;
        }
    }

    private boolean isRowVisible(int rowY, int rowH) {
        int viewTop = CONTENT_Y;
        int viewBottom = CONTENT_Y + VIEWPORT_H;
        return rowY + rowH > viewTop && rowY < viewBottom;
    }

    private int calcTotalContentHeight() {
        int total = 0;
        for (EntryGroup group : groups) {
            total += group.getTotalHeight() + GROUP_GAP;
        }
        return total;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

        // Draw main background - blit with explicit texture dimensions for 320x280 texture
        graphics.blit(RenderPipelines.GUI_TEXTURED, GUI_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 320, 280);

        // Darken the scrollable content area slightly
        graphics.fill(this.leftPos + CONTENT_X - 2, this.topPos + CONTENT_Y - 2,
                this.leftPos + CONTENT_X + CONTENT_W + 2, this.topPos + CONTENT_Y + VIEWPORT_H + 2,
                0x20000000);

        // Name field white border and background (must be drawn BEFORE EditBox renders its text)
        int nfX = this.leftPos + 40;
        int nfY = this.topPos + 25;
        int nfW = 170;
        int nfH = 14;
        graphics.fill(nfX, nfY, nfX + nfW, nfY + 1, 0xFFFFFFFF); // top
        graphics.fill(nfX, nfY + nfH - 1, nfX + nfW, nfY + nfH, 0xFFFFFFFF); // bottom
        graphics.fill(nfX, nfY, nfX + 1, nfY + nfH, 0xFFFFFFFF); // left
        graphics.fill(nfX + nfW - 1, nfY, nfX + nfW, nfY + nfH, 0xFFFFFFFF); // right
        // Inner dark fill behind text
        graphics.fill(nfX + 1, nfY + 1, nfX + nfW - 1, nfY + nfH - 1, 0x80000000);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int x, int y) {
        // Do not render default title and inventory labels - we handle all rendering ourselves
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // Suppress default slot tooltip for cell slot (index 0)
        // We have our own preview panel instead
        if (this.hoveredSlot != null && this.hoveredSlot.index == 0) {
            return;
        }
        super.extractTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Cache mouse position for use in helper methods
        this.cachedMouseX = mouseX;
        this.cachedMouseY = mouseY;

        // Let super handle widgets (EditBox fields), slots, carried item, tooltips
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        // Our custom overlay rendering on top
        int x = this.leftPos;
        int y = this.topPos;
        ItemStack targetCell = this.menu.getCellInSlot();

        // Title text (top-left, like 1.20.1/1.21.1)
        graphics.text(this.font, this.title, x + 8, y + 6, 0xFF2A3E5C, false);

        // "?" help button (below Save/Reset/Clear buttons)
        int helpX = x + this.imageWidth - 16;
        int helpY = y + 20;
        int helpColor = isHoveringHelp(mouseX, mouseY) ? 0xFFFFFFFF : 0xFF2A3E5C;
        graphics.text(this.font, "?", helpX, helpY, helpColor, true);
        this.helpHovered = isHoveringHelp(mouseX, mouseY);

        // Help tooltip
        if (helpHovered) {
            List<Component> helpLines = List.of(
                Component.translatable("gui.cell4.help_shortcuts"),
                Component.translatable("gui.cell4.help_blacklist")
            );
            graphics.setTooltipForNextFrame(this.font, helpLines, Optional.empty(), mouseX, mouseY);
        }

        // Name label (next to item slot, moved right to match nameField position)
        graphics.text(this.font, Component.translatable("gui.cell4.cell_name"), x + 40, y + 15,
                !targetCell.isEmpty() && CellConfiguratorMenu.canEditName(targetCell) ? 0xFF2A3E5C : 0xFF8899AA, false);

        // Name field border and background are now drawn in extractBackground (before EditBox text renders)

        // Save, Reset, Clear buttons area (top right)
        int btnX = x + 215;
        int btnY = y + 6;
        // Save button
        renderMiniButton(graphics, btnX, btnY, 30, 12, Component.translatable("gui.cell4.save"), 0xFF2A6E2A);
        // Reset button
        renderMiniButton(graphics, btnX + 34, btnY, 30, 12, Component.translatable("gui.cell4.reset"), 0xFF2A3E5C);
        // Clear button
        renderMiniButton(graphics, btnX + 68, btnY, 30, 12, Component.translatable("gui.cell4.clear"), 0xFF6E2A2A);

        // Save result feedback
        if (saveResultTime > 0 && System.currentTimeMillis() - saveResultTime < SAVE_RESULT_DISPLAY_MS) {
            int feedbackColor = lastSaveSuccess ? 0xFF55FF55 : 0xFFFF5555;
            graphics.text(this.font,
                Component.translatable(lastSaveMessage),
                btnX, btnY + 14, feedbackColor, false);
        }

        // No cell warning - show it centered in the content area
        if (targetCell.isEmpty()) {
            Component noCellText = Component.translatable("gui.cell4.no_cell");
            int textWidth = this.font.width(noCellText);
            int centerX = x + CONTENT_X + (CONTENT_W - textWidth) / 2;
            int centerY = y + CONTENT_Y + VIEWPORT_H / 2 - 4;
            graphics.text(this.font, noCellText, centerX, centerY, 0xFFFF5555, false);
        }

        // Render scrollable content area
        enableScissorForContent(graphics);
        renderContent(graphics, mouseX, mouseY);
        disableScissor(graphics);

        // Render scrollbar
        renderScrollbar(graphics);

        // === Tooltip Preview Panel (right side of GUI) ===
        ItemStack previewCell = this.menu.getCellInSlot();
        if (!previewCell.isEmpty()) {
            int previewX = x + this.imageWidth + 6;
            int previewY = y + 18;

            // Small label above the preview tooltip
            graphics.text(this.font, Component.translatable("gui.cell4.preview"), previewX, y + 4, 0xFF888888, false);

            // Get tooltip lines from the cell item (exactly as shown in inventory)
            List<Component> tooltipLines = new ArrayList<>(previewCell.getTooltipLines(
                net.minecraft.world.item.Item.TooltipContext.of(this.minecraft.level),
                this.minecraft.player,
                this.minecraft.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL
            ));

            // Apply pending name from the name field
            String pendingName = this.nameField.getValue();
            if (!pendingName.isEmpty() && !tooltipLines.isEmpty()) {
                // Preserve the original rarity color but add italic for custom name
                Style originalStyle = tooltipLines.get(0).getStyle();
                tooltipLines.set(0, Component.literal(pendingName).withStyle(originalStyle.withItalic(true)));
            }

            // Render tooltip manually at fixed position (setTooltipForNextFrame doesn't respect position)
            renderTooltipAtFixedPosition(graphics, tooltipLines, previewX, previewY);
        }
    }

    private void enableScissorForContent(GuiGraphicsExtractor graphics) {
        int scissorX = this.leftPos + CONTENT_X - 2;
        int scissorY = this.topPos + CONTENT_Y - 2;
        int scissorW = CONTENT_W + 4;
        int scissorH = VIEWPORT_H + 4;
        graphics.enableScissor(scissorX, scissorY, scissorX + scissorW, scissorY + scissorH);
    }

    private void disableScissor(GuiGraphicsExtractor graphics) {
        graphics.disableScissor();
    }

    private void renderContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = this.leftPos;
        int baseY = this.topPos + CONTENT_Y - scrollOffset;
        int currentY = baseY;

        ItemStack targetCell = this.menu.getCellInSlot();

        for (EntryGroup group : groups) {
            int screenY = currentY - this.topPos;
            boolean headerVisible = isRowVisible(screenY, GROUP_HEADER_H);

            if (headerVisible) {
                // Group header background
                graphics.fill(x + CONTENT_X, currentY, x + CONTENT_X + CONTENT_W, currentY + GROUP_HEADER_H, 0x30406080);

                // Collapse/expand arrow
                String arrow = group.collapsed ? "\u25BA" : "\u25BC"; // ► or ▼
                int arrowColor = group.isEditable() ? 0xFFFFFFFF : 0xFF8899AA;
                graphics.text(this.font, arrow, x + CONTENT_X + 2, currentY + 3, arrowColor, true);

                // Group label
                graphics.text(this.font, Component.translatable(group.labelKey),
                    x + CONTENT_X + 14, currentY + 3, arrowColor, true);

                // "+" button
                String plusText = "+";
                int plusX = x + CONTENT_X + CONTENT_W - SCROLLBAR_W - 14;
                boolean plusHovered = isHoveringPlus(mouseX, mouseY, plusX, currentY);
                int plusColor = plusHovered ? 0xFFFFFF55 : (group.isEditable() ? 0xFF55FF55 : 0xFF8899AA);
                graphics.text(this.font, plusText, plusX, currentY + 3, plusColor, true);

                // Count badge
                if (!group.entries.isEmpty()) {
                    String count = "(" + group.entries.size() + ")";
                    graphics.text(this.font, count, x + CONTENT_X + 14 + this.font.width(Component.translatable(group.labelKey)) + 4,
                        currentY + 3, 0xFF8899AA, false);
                }
            }

            // Entry rows (only if expanded)
            if (!group.collapsed) {
                for (int i = 0; i < group.entries.size(); i++) {
                    int entryY = currentY + GROUP_HEADER_H + i * ENTRY_H;
                    int entryScreenY = entryY - this.topPos;

                    if (isRowVisible(entryScreenY, ENTRY_H)) {
                        // Row background
                        graphics.fill(x + CONTENT_X, entryY, x + CONTENT_X + CONTENT_W, entryY + ENTRY_H,
                            i % 2 == 0 ? 0x10000000 : 0x18000000);

                        // Sequence number
                        String seqNum = (i + 1) + ".";
                        graphics.text(this.font, seqNum, x + CONTENT_X + 4, entryY + 3, 0xFFAAAAAA, false);

                        // "X" delete button
                        String xText = "x";
                        int xBtnX = x + CONTENT_X + CONTENT_W - SCROLLBAR_W - 12;
                        boolean xHovered = isHoveringX(mouseX, mouseY, xBtnX, entryY);
                        int xColor = xHovered ? 0xFFFF5555 : 0xFF995555;
                        graphics.text(this.font, xText, xBtnX, entryY + 3, xColor, true);
                    }
                }
            }

            currentY += group.getTotalHeight() + GROUP_GAP;
        }

        // Update maxScroll
        int totalH = calcTotalContentHeight();
        maxScroll = Math.max(0, totalH - VIEWPORT_H);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
    }

    private void renderScrollbar(GuiGraphicsExtractor graphics) {
        int totalH = calcTotalContentHeight();
        if (totalH <= VIEWPORT_H) return;

        int x = this.leftPos + SCROLLBAR_X_OFFSET;
        int y = this.topPos + CONTENT_Y;
        int h = VIEWPORT_H;

        // Scrollbar track
        graphics.fill(x, y, x + SCROLLBAR_W, y + h, 0x20FFFFFF);

        // Scrollbar thumb
        float ratio = (float) VIEWPORT_H / totalH;
        int thumbH = Math.max(12, (int) (h * ratio));
        int thumbY = y + (int) ((h - thumbH) * ((float) scrollOffset / maxScroll));
        graphics.fill(x, thumbY, x + SCROLLBAR_W, thumbY + thumbH, 0x80FFFFFF);
    }

    private void renderMiniButton(GuiGraphicsExtractor graphics, int x, int y, int w, int h, Component text, int color) {
        graphics.fill(x, y, x + w, y + h, 0xFF3C5078);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFAFCAE6);
        graphics.centeredText(this.font, text, x + w / 2, y + 2, color);
    }

    // --- Hit testing helpers ---

    private boolean isHoveringHelp(int mouseX, int mouseY) {
        int helpX = this.leftPos + this.imageWidth - 16;
        int helpY = this.topPos + 20;
        return mouseX >= helpX && mouseX <= helpX + 8 && mouseY >= helpY && mouseY <= helpY + 10;
    }

    private boolean isHoveringPlus(int mouseX, int mouseY, int plusX, int plusY) {
        return mouseX >= plusX && mouseX <= plusX + 8 && mouseY >= plusY && mouseY <= plusY + 12;
    }

    private boolean isHoveringX(int mouseX, int mouseY, int xBtnX, int entryY) {
        return mouseX >= xBtnX && mouseX <= xBtnX + 8 && mouseY >= entryY && mouseY <= entryY + 12;
    }

    private boolean isHoveringGroupHeader(int mouseX, int mouseY, int headerY) {
        int hx = this.leftPos + CONTENT_X;
        return mouseX >= hx && mouseX <= hx + CONTENT_W && mouseY >= headerY && mouseY <= headerY + GROUP_HEADER_H;
    }

    // --- Button action helpers ---

    private boolean findAndClickPlus(int mouseX, int mouseY) {
        int baseY = this.topPos + CONTENT_Y - scrollOffset;
        int currentY = baseY;
        for (EntryGroup group : groups) {
            int plusX = this.leftPos + CONTENT_X + CONTENT_W - SCROLLBAR_W - 14;
            if (isHoveringPlus(mouseX, mouseY, plusX, currentY) && group.isEditable()) {
                group.addEmptyEntry();
                updateFieldPositions();
                return true;
            }
            currentY += group.getTotalHeight() + GROUP_GAP;
        }
        return false;
    }

    private boolean findAndClickX(int mouseX, int mouseY) {
        int baseY = this.topPos + CONTENT_Y - scrollOffset;
        int currentY = baseY;
        for (EntryGroup group : groups) {
            if (!group.collapsed) {
                for (int i = 0; i < group.entries.size(); i++) {
                    int entryY = currentY + GROUP_HEADER_H + i * ENTRY_H;
                    int xBtnX = this.leftPos + CONTENT_X + CONTENT_W - SCROLLBAR_W - 12;
                    if (isHoveringX(mouseX, mouseY, xBtnX, entryY) && group.isEditable()) {
                        group.removeEntry(i);
                        updateFieldPositions();
                        return true;
                    }
                }
            }
            currentY += group.getTotalHeight() + GROUP_GAP;
        }
        return false;
    }

    private void findAndToggleGroup(int mouseX, int mouseY) {
        int baseY = this.topPos + CONTENT_Y - scrollOffset;
        int currentY = baseY;
        for (EntryGroup group : groups) {
            if (isHoveringGroupHeader(mouseX, mouseY, currentY)) {
                group.collapsed = !group.collapsed;
                updateFieldPositions();
                return;
            }
            currentY += group.getTotalHeight() + GROUP_GAP;
        }
    }

    private boolean findAndClickSaveResetClear(int mouseX, int mouseY) {
        int btnX = this.leftPos + 215;
        int btnY = this.topPos + 6;
        int btnW = 30, btnH = 12;

        // Save button
        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            onSave();
            return true;
        }
        // Reset button
        if (mouseX >= btnX + 34 && mouseX <= btnX + 34 + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            onReset();
            return true;
        }
        // Clear button
        if (mouseX >= btnX + 68 && mouseX <= btnX + 68 + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            onClear();
            return true;
        }
        return false;
    }

    // --- Save / Reset / Clear ---

    private void onSave() {
        ItemStack targetCell = this.menu.getCellInSlot();
        if (targetCell.isEmpty()) return;

        // Collect values from groups
        syncEntriesFromFields();
        String itemValue = String.join(",", groups.get(GROUP_ITEMS).entries);
        String tagValue = String.join(",", groups.get(GROUP_TAGS).entries);
        String modIdValue = String.join(",", groups.get(GROUP_MODIDS).entries);
        String blacklistValue = String.join(",", groups.get(GROUP_BLACKLIST).entries);

        ClientPacketDistributor.sendToServer(new CellConfigSavePacket(
            itemValue, tagValue, modIdValue, blacklistValue, this.nameField.getValue()
        ));
    }

    private void onReset() {
        groups.get(GROUP_ITEMS).setEntries(new ArrayList<>(snapshotItems));
        groups.get(GROUP_TAGS).setEntries(new ArrayList<>(snapshotTags));
        groups.get(GROUP_MODIDS).setEntries(new ArrayList<>(snapshotModIds));
        groups.get(GROUP_BLACKLIST).setEntries(new ArrayList<>(snapshotBlacklist));
        this.nameField.setValue(snapshotName);
        updateFieldPositions();
    }

    private void onClear() {
        ItemStack cell = this.menu.getCellInSlot();
        if (cell.isEmpty()) return;
        for (EntryGroup group : groups) {
            if (group.isEditable()) {
                group.setEntries(new ArrayList<>());
            }
        }
        if (CellConfiguratorMenu.canEditName(cell)) {
            this.nameField.setValue("");
        }
        updateFieldPositions();
    }

    private void syncEntriesFromFields() {
        for (EntryGroup group : groups) {
            for (int i = 0; i < group.entryFields.size() && i < group.entries.size(); i++) {
                group.entries.set(i, group.entryFields.get(i).getValue());
            }
        }
    }

    // --- Input handling ---

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();

        // Check custom UI elements first
        if (findAndClickSaveResetClear(mouseX, mouseY)) return true;

        // Check content area clicks
        int contentTop = this.topPos + CONTENT_Y;
        int contentBottom = contentTop + VIEWPORT_H;
        if (mouseY >= contentTop && mouseY <= contentBottom) {
            // Check plus buttons first - clicking "+" should ONLY add entry, NOT toggle group
            if (findAndClickPlus(mouseX, mouseY)) {
                return true;
            }
            // Check X buttons - clicking "x" should ONLY remove entry
            if (findAndClickX(mouseX, mouseY)) {
                return true;
            }
            // Check group headers - only toggle collapse/expand if not on +/x buttons
            findAndToggleGroup(mouseX, mouseY);
        }

        // Check scrollbar
        int sbX = this.leftPos + SCROLLBAR_X_OFFSET;
        if (mouseX >= sbX && mouseX <= sbX + SCROLLBAR_W && mouseY >= contentTop && mouseY <= contentBottom) {
            this.scrolling = true;
            return true;
        }

        // Let super handle EditBox focus and inventory clicks
        boolean result = super.mouseClicked(event, doubleClick);
        if (this.menu.getCellInSlot().isEmpty()) {
            clearAllFieldFocus();
        }
        return result;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.scrolling = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.scrolling) {
            int totalH = calcTotalContentHeight();
            if (totalH > VIEWPORT_H) {
                float ratio = (float) dy / (VIEWPORT_H - 12);
                scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + (int) (ratio * totalH)));
                updateFieldPositions();
            }
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int contentTop = this.topPos + CONTENT_Y;
        int contentBottom = contentTop + VIEWPORT_H;
        if (mouseX >= this.leftPos && mouseX <= this.leftPos + this.imageWidth &&
            mouseY >= contentTop && mouseY <= contentBottom) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (verticalAmount * 14)));
            updateFieldPositions();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (this.nameField.isFocused()) {
            if (keyCode == 256) { this.onClose(); return true; }
            if (keyCode == 257) { onSave(); return true; } // Enter to save
            this.nameField.keyPressed(event);
            return true;
        }
        // Check if any entry EditBox is focused
        for (EntryGroup group : groups) {
            for (EditBox field : group.entryFields) {
                if (field.isFocused()) {
                    if (keyCode == 256) { this.onClose(); return true; }
                    if (keyCode == 257) { onSave(); return true; } // Enter to save
                    field.keyPressed(event);
                    return true;
                }
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.nameField.isFocused()) {
            this.nameField.charTyped(event);
            return true;
        }
        for (EntryGroup group : groups) {
            for (EditBox field : group.entryFields) {
                if (field.isFocused()) {
                    field.charTyped(event);
                    return true;
                }
            }
        }
        return super.charTyped(event);
    }

    private void clearAllFieldFocus() {
        this.nameField.setFocused(false);
        for (EntryGroup group : groups) {
            for (EditBox field : group.entryFields) {
                field.setFocused(false);
            }
        }
    }

    /**
     * Renders a tooltip at a fixed screen position, bypassing Minecraft's built-in
     * tooltip positioning logic. This manually draws the tooltip background and text
     * lines at the exact (x, y) given.
     */
    private void renderTooltipAtFixedPosition(GuiGraphicsExtractor graphics, List<Component> lines, int x, int y) {
        if (lines.isEmpty()) return;

        // Calculate tooltip dimensions
        int maxWidth = 0;
        for (Component line : lines) {
            int w = this.font.width(line);
            if (w > maxWidth) maxWidth = w;
        }
        int tooltipWidth = maxWidth + 8; // 4px padding each side
        int lineHeight = 10; // Minecraft's tooltip line height
        int tooltipHeight = lines.size() * lineHeight + 4; // 2px padding top and bottom

        // Draw tooltip background (dark purple border + dark inner)
        int borderColor = 0xF0100010;
        int bgColor = 0xF0100010;

        // Outer border (top)
        graphics.fill(x - 1, y - 1, x + tooltipWidth + 1, y, borderColor);
        // Outer border (bottom)
        graphics.fill(x - 1, y + tooltipHeight, x + tooltipWidth + 1, y + tooltipHeight + 1, borderColor);
        // Outer border (left)
        graphics.fill(x - 1, y, x, y + tooltipHeight, borderColor);
        // Outer border (right)
        graphics.fill(x + tooltipWidth, y, x + tooltipWidth + 1, y + tooltipHeight, borderColor);

        // Inner background
        graphics.fill(x, y, x + tooltipWidth, y + tooltipHeight, bgColor);

        // Gradient borders (purple-blue, like vanilla Minecraft tooltips)
        int borderGradTop = 0x505000FF;
        int borderGradBot = 0x5028007F;

        // Top gradient border
        graphics.fill(x, y, x + tooltipWidth, y + 1, borderGradTop);
        // Left gradient border
        graphics.fill(x, y, x + 1, y + tooltipHeight, borderGradTop);
        // Bottom gradient border
        graphics.fill(x, y + tooltipHeight - 1, x + tooltipWidth, y + tooltipHeight, borderGradBot);
        // Right gradient border
        graphics.fill(x + tooltipWidth - 1, y, x + tooltipWidth, y + tooltipHeight, borderGradBot);

        // Draw text lines
        int currentY = y + 3;
        for (Component line : lines) {
            graphics.text(this.font, line, x + 4, currentY, 0xFFFFFFFF, true);
            currentY += lineHeight;
        }
    }
}
