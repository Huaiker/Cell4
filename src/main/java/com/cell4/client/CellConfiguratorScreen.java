package com.cell4.client;

import com.cell4.Cell4;
import com.cell4.common.item.*;
import com.cell4.common.menu.CellConfiguratorMenu;
import com.cell4.common.util.Cell4Config;
import com.cell4.common.util.Cell4Util;
import com.cell4.common.util.NBTKeys;
import com.cell4.common.util.PortableCellPower;
import com.cell4.network.CellConfigSavePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

public class CellConfiguratorScreen extends AbstractContainerScreen<CellConfiguratorMenu> {

    private static final ResourceLocation GUI_TEXTURE = ResourceLocation.fromNamespaceAndPath(Cell4.MODID, "textures/guis/cell_configurator.png");
    // AE2 原版元件工作台背景（176x251）
    private static final ResourceLocation AE2_CELLWORKBENCH =
            ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/cellworkbench.png");
    // AE2 原版槽位底板（states.png 的 SLOT_BACKGROUND）
    private static final ResourceLocation AE2_STATES =
            ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/states.png");

    private static final int SIDEBAR_W = CellConfiguratorMenu.SIDEBAR_WIDTH;
    private static final int MAIN_W = 320;
    private static final int MAIN_H = 280;

    // AE2 原版元件工作台布局（相对背景左上角）
    private static final int PANEL_W = 176;
    private static final int PANEL_H = 251;
    private static final int PANEL_CELL_X = 152;
    private static final int PANEL_CELL_Y = 8;
    private static final int PANEL_GRID_X = 8;
    private static final int PANEL_GRID_Y = 29;
    private static final int PANEL_INV_X = 8;
    private static final int PANEL_INV_Y = 169;
    private static final int PANEL_HOTBAR_Y = 227;
    // 工作台 UI 右侧控制栏宽度（保存/翻页/材质输入等）
    private static final int WORKBENCH_SIDE_W = 88;

    // 第一个 UI（列表编辑器）的槽位坐标（与 CellConfiguratorMenu 一致）
    private static final int LIST_CELL_X = 9 + SIDEBAR_W;
    private static final int LIST_CELL_Y = 23;
    private static final int LIST_INV_X = 64 + SIDEBAR_W;
    private static final int LIST_INV_Y = 192;
    private static final int LIST_HOTBAR_Y = 250;

    private static final int UI_MODE_LIST = 0;
    private static final int UI_MODE_WORKBENCH = 1;
    private static final int GRID_COLS = 9;
    private static final int GRID_ROWS = 7;
    private static final int GRID_CELL = 18;
    private static final int GRID_LIMIT = GRID_COLS * GRID_ROWS;
    private int uiMode = UI_MODE_LIST;
    private int workbenchPage = 0;
    private final List<String> gridItems = new ArrayList<>();
    private final List<String> gridOtherItems = new ArrayList<>();
    // 原始槽位（用于在两个 UI 之间切换坐标）
    private List<net.minecraft.world.inventory.Slot> baseSlots;
    private EditBox textureField;
    private String snapshotTexture = "";


    // UI dimensions
    private static final int CONTENT_X = 8;
    private static final int CONTENT_Y = 44;
    private static final int CONTENT_W = 300;
    private static final int VIEWPORT_H = 138; // visible scroll area height
    private static final int ENTRY_H = 14;
    private static final int GROUP_HEADER_H = 14;
    private static final int GROUP_GAP = 4;
    private static final int SCROLLBAR_W = 6;
    private static final int SCROLLBAR_X_OFFSET = 306;

    // Name field at top (next to item slot)
    private EditBox nameField;

    // Group data
    private static final int GROUP_ITEMS = 0;
    private static final int GROUP_TAGS = 1;
    private static final int GROUP_MODIDS = 2;
    private static final int GROUP_BLACKLIST = 3;
    private static final int GROUP_POWER = 4;

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
    private List<String> snapshotPower = new ArrayList<>();
    private String snapshotName = "";

    // Help button hover state
    private boolean helpHovered = false;

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
                case GROUP_POWER -> CellConfiguratorMenu.canEditPower(cell);
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
        super(menu, inventory, title);
        this.imageWidth = MAIN_W + SIDEBAR_W;
        this.imageHeight = 280;
        this.inventoryLabelY = 194;
        this.titleLabelY = 6;

        groups.add(new EntryGroup(GROUP_ITEMS, "gui.cell4.group_items"));
        groups.add(new EntryGroup(GROUP_TAGS, "gui.cell4.group_tags"));
        groups.add(new EntryGroup(GROUP_MODIDS, "gui.cell4.group_modids"));
        groups.add(new EntryGroup(GROUP_BLACKLIST, "gui.cell4.group_blacklist"));
        groups.add(new EntryGroup(GROUP_POWER, "gui.cell4.group_power"));
    }

    public static void setLastSaveResult(boolean success, String message) {
        lastSaveSuccess = success;
        lastSaveMessage = message;
        saveResultTime = System.currentTimeMillis();
    }

    private EditBox createEntryEditBox(int index, String value) {
        int fieldX = leftPos + SIDEBAR_W + CONTENT_X + 24;
        int fieldW = CONTENT_W - 24 - 14 - SCROLLBAR_W - 4;
        EditBox field = new EditBox(this.font, fieldX, 0, fieldW, 12, Component.literal("entry_" + index));
        field.setMaxLength(1024);
        field.setBordered(false);
        field.setTextColor(0xFFFFFFFF);
        field.setTextColorUneditable(0xFFFFFFFF);
        field.setValue(value != null ? value : "");
        return field;
    }

    /** 根据当前 UI 模式设置窗口尺寸（必须在 super.init() 之前调用）。 */
    private void applyScreenMetrics() {
        if (uiMode == UI_MODE_WORKBENCH) {
            this.imageWidth = SIDEBAR_W + PANEL_W + WORKBENCH_SIDE_W;
            this.imageHeight = PANEL_H;
        } else {
            this.imageWidth = SIDEBAR_W + MAIN_W;
            this.imageHeight = MAIN_H;
        }
    }

    /** 组件名输入框（所有元件都可编辑，用于自定义材质）。 */
    private void applyTextureFieldValue(ItemStack cell) {
        if (this.textureField == null) return;
        String value = cell.isEmpty() || !(cell.getItem() instanceof IInfinityCell c) ? "" : c.getTextureName(cell);
        this.textureField.setValue(value);
        this.textureField.setEditable(!cell.isEmpty());
        this.snapshotTexture = value;
    }

    /**
     * 客户端槽位换位：Slots 的 x/y 是 final 字段，无法直接改坐标，
     * 因此这里用包装槽位替换 menu.slots 中的条目（服务端仍保留自己的槽位，
     * 点击只按 index 同步，坐标纯客户端表现）。
     */
    private void applySlotLayout() {
        List<net.minecraft.world.inventory.Slot> slots = this.menu.slots;
        if (slots.size() < 37) return;
        if (baseSlots == null) baseSlots = new ArrayList<>(slots);
        boolean wb = uiMode == UI_MODE_WORKBENCH;

        for (int i = 0; i < baseSlots.size(); i++) {
            int x;
            int y;
            if (i == 0) {
                x = wb ? SIDEBAR_W + PANEL_CELL_X : LIST_CELL_X;
                y = wb ? PANEL_CELL_Y : LIST_CELL_Y;
            } else if (i <= 27) {
                int k = i - 1;
                x = (wb ? SIDEBAR_W + PANEL_INV_X : LIST_INV_X) + (k % 9) * 18;
                y = (wb ? PANEL_INV_Y : LIST_INV_Y) + (k / 9) * 18;
            } else if (i <= 36) {
                x = (wb ? SIDEBAR_W + PANEL_INV_X : LIST_INV_X) + (i - 28) * 18;
                y = wb ? PANEL_HOTBAR_Y : LIST_HOTBAR_Y;
            } else {
                continue;
            }
            slots.set(i, new RepositionedSlot(baseSlots.get(i), x, y));
        }
    }

    /** 只改坐标的槽位包装：行为全部委托给原槽位。 */
    private static class RepositionedSlot extends net.minecraft.world.inventory.Slot {
        private final net.minecraft.world.inventory.Slot delegate;

        RepositionedSlot(net.minecraft.world.inventory.Slot delegate, int x, int y) {
            super(delegate.container, delegate.getContainerSlot(), x, y);
            this.delegate = delegate;
            this.index = delegate.index;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return delegate.mayPlace(stack);
        }

        @Override
        public boolean mayPickup(net.minecraft.world.entity.player.Player player) {
            return delegate.mayPickup(player);
        }

        @Override
        public int getMaxStackSize() {
            return delegate.getMaxStackSize();
        }

        @Override
        public ItemStack getItem() {
            return delegate.getItem();
        }

        @Override
        public boolean hasItem() {
            return delegate.hasItem();
        }

        @Override
        public void set(ItemStack stack) {
            delegate.set(stack);
        }

        @Override
        public void setChanged() {
            delegate.setChanged();
        }

        @Override
        public ItemStack remove(int amount) {
            return delegate.remove(amount);
        }

        @Override
        public boolean isActive() {
            return delegate.isActive();
        }

        @Override
        public boolean isHighlightable() {
            return delegate.isHighlightable();
        }
    }

    @Override
    protected void init() {
        applyScreenMetrics();
        super.init();
        applySlotLayout();

        int x = this.leftPos;
        int y = this.topPos;

        // Name field next to item slot (original list layout)
        this.nameField = new EditBox(this.font, x + SIDEBAR_W + 44, y + 30, 96, 12, Component.literal("cell4name"));
        this.nameField.setMaxLength(256);
        this.nameField.setBordered(false);
        this.nameField.setTextColor(0xFFFFFFFF);
        this.nameField.visible = uiMode == UI_MODE_LIST;
        this.addRenderableWidget(this.nameField);

        // 材质名称输入框（两种 UI 都显示）
        if (uiMode == UI_MODE_WORKBENCH) {
            this.textureField = new EditBox(this.font, x + SIDEBAR_W + PANEL_W + 6, y + 41, 76, 12,
                    Component.literal("cell4texture"));
        } else {
            this.textureField = new EditBox(this.font, x + SIDEBAR_W + 145, y + 30, 66, 12,
                    Component.literal("cell4texture"));
        }
        this.textureField.setMaxLength(64);
        this.textureField.setBordered(false);
        this.textureField.setTextColor(0xFFFFFFFF);
        this.addRenderableWidget(this.textureField);
        applyTextureFieldValue(this.menu.getCellInSlot());
        // 打开界面时先生成材质包，保证 F3+T 第一次就能读到新材质。
        Cell4Config.refresh();

        for (EntryGroup group : groups) {
            group.rebuildFields();
        }

        this.lastCellItem = ItemStack.EMPTY;
    }

    private boolean isPortableCell() {
        return CellConfiguratorMenu.canEditPower(this.menu.getCellInSlot());
    }

    private List<String> powerEntries(ItemStack cell) {
        if (!CellConfiguratorMenu.canEditPower(cell)) return List.of("", "", "");
        return List.of(
                Long.toString(PortableCellPower.getMaxPower(cell)),
                Long.toString(PortableCellPower.getCostPerItem(cell)),
                Double.toString(PortableCellPower.getConfiguredChargeRate(cell))
        );
    }

    @Override
    protected void containerTick() {
        super.containerTick();

        ItemStack currentCell = this.menu.getCellInSlot();

        if (!ItemStack.matches(currentCell, this.lastCellItem)) {
            net.minecraft.world.item.Item oldItem = this.lastCellItem.getItem();
            boolean cellItemChanged = currentCell.getItem() != oldItem;
            this.lastCellItem = currentCell.copy();

            if (!currentCell.isEmpty()) {
                List<String> itemEntries = new ArrayList<>();
                List<String> tagEntries = new ArrayList<>();
                List<String> modIdEntries = new ArrayList<>();
                List<String> blacklistEntries = new ArrayList<>();
                String nameValue = "";

                if (currentCell.getItem() instanceof IInfinityCell cell
                        && cell.getCellType() == IInfinityCell.CellType.ITEM) {
                    itemEntries = new ArrayList<>(InfinityItemCell.getIdentifiers(currentCell));
                }
                if (currentCell.getItem() instanceof IInfinityCell cell
                        && cell.getCellType() == IInfinityCell.CellType.TAG) {
                    tagEntries = new ArrayList<>(InfinityTagCell.getTagNames(currentCell));
                    modIdEntries = new ArrayList<>(InfinityTagCell.getModIds(currentCell));
                }
                if (currentCell.getItem() instanceof IInfinityCell cell
                        && cell.getCellType() == IInfinityCell.CellType.MODID) {
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

                snapshotItems = new ArrayList<>(itemEntries);
                snapshotTags = new ArrayList<>(tagEntries);
                snapshotModIds = new ArrayList<>(modIdEntries);
                snapshotBlacklist = new ArrayList<>(blacklistEntries);
                snapshotName = nameValue;
                applyTextureFieldValue(currentCell);
                if (cellItemChanged) workbenchPage = 0;

                if (CellConfiguratorMenu.canEditPower(currentCell)) {
                    List<String> power = powerEntries(currentCell);
                    groups.get(GROUP_POWER).setEntries(power);
                    groups.get(GROUP_POWER).collapsed = false;
                    snapshotPower = new ArrayList<>(power);
                } else {
                    groups.get(GROUP_POWER).clearEntries();
                    groups.get(GROUP_POWER).collapsed = true;
                    snapshotPower = new ArrayList<>();
                }

                if (uiMode == UI_MODE_WORKBENCH) {
                    if (canUseWorkbench(currentCell)) {
                        // NBT 同步（例如刚保存）不能覆盖本地网格，否则第二页新增会被丢弃。
                        if (cellItemChanged) populateGridFromEntries(itemEntries);
                    } else {
                        // 元件被换掉/取出：立刻回到列表 UI 并重建界面，避免残留工作台状态
                        uiMode = UI_MODE_LIST;
                        applyScreenMetrics();
                        this.rebuildWidgets();
                        scrollOffset = 0;
                        return;
                    }
                }

                scrollOffset = 0;
            } else {
                for (EntryGroup group : groups) {
                    group.clearEntries();
                    group.collapsed = true;
                }
                this.nameField.setValue("");
                this.nameField.setEditable(false);
                if (this.textureField != null) {
                    this.textureField.setValue("");
                    this.textureField.setEditable(false);
                }
                gridItems.clear();
                gridOtherItems.clear();
                snapshotPower = new ArrayList<>();
                boolean wasWorkbench = uiMode == UI_MODE_WORKBENCH;
                uiMode = UI_MODE_LIST;
                applyScreenMetrics();
                if (wasWorkbench) {
                    this.rebuildWidgets();
                    return;
                }
                scrollOffset = 0;
            }
            updateFieldPositions();
        }

        // Sync entry values from EditBoxes
        for (EntryGroup group : groups) {
            for (int i = 0; i < group.entryFields.size() && i < group.entries.size(); i++) {
                group.entries.set(i, group.entryFields.get(i).getValue());
            }
        }
    }

    private void updateFieldPositions() {
        if (uiMode == UI_MODE_WORKBENCH) {
            for (EntryGroup group : groups) {
                for (EditBox field : group.entryFields) {
                    field.visible = false;
                }
            }
            return;
        }
        int baseY = topPos + CONTENT_Y - scrollOffset;
        int currentY = baseY;

        for (EntryGroup group : groups) {
            if (group.groupId == GROUP_POWER && !isPortableCell()) {
                for (EditBox field : group.entryFields) {
                    field.visible = false;
                }
                continue;
            }
            if (!group.collapsed) {
                for (int i = 0; i < group.entryFields.size(); i++) {
                    EditBox field = group.entryFields.get(i);
                    int fieldY = currentY + GROUP_HEADER_H + i * ENTRY_H + 1;
                    field.setY(fieldY);
                    if (group.groupId == GROUP_POWER) {
                        field.setX(leftPos + SIDEBAR_W + CONTENT_X + 48);
                        field.setWidth(CONTENT_W - 48 - SCROLLBAR_W - 4);
                        field.setMaxLength(20);
                    }
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
            if (group.groupId == GROUP_POWER && !isPortableCell()) continue;
            total += group.getTotalHeight() + GROUP_GAP;
        }
        return total;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int mainX = this.leftPos + SIDEBAR_W;
        int sbX = this.leftPos;

        // 左侧扩展栏（两种 UI 共用）
        guiGraphics.fill(sbX, this.topPos, sbX + SIDEBAR_W, this.topPos + this.imageHeight, 0xFF3C5078);
        guiGraphics.fill(sbX + 1, this.topPos + 1, sbX + SIDEBAR_W - 1,
                this.topPos + this.imageHeight - 1, 0xFFAFCAE6);

        if (uiMode == UI_MODE_WORKBENCH) {
            // 直接使用 AE2 原版元件工作台背景
            guiGraphics.blit(AE2_CELLWORKBENCH, mainX, this.topPos, 0, 0, PANEL_W, PANEL_H, 256, 256);
            // 右侧控制栏底色
            int sideX = mainX + PANEL_W;
            guiGraphics.fill(sideX, this.topPos, sideX + WORKBENCH_SIDE_W, this.topPos + PANEL_H, 0xFFC6C6C6);
            guiGraphics.fill(sideX, this.topPos, sideX + 1, this.topPos + PANEL_H, 0xFF8B8B8B);
            drawInputBorder(guiGraphics, sideX + 5, this.topPos + 40, 78, 14);
            renderSidebarButtons(guiGraphics, mouseX, mouseY);
            return;
        }

        guiGraphics.blit(GUI_TEXTURE, mainX, this.topPos, 0, 0, MAIN_W, this.imageHeight, 320, 280);

        guiGraphics.fill(mainX + CONTENT_X - 2, this.topPos + CONTENT_Y - 2,
                mainX + CONTENT_X + CONTENT_W + 2, this.topPos + CONTENT_Y + VIEWPORT_H + 2,
                0x20000000);

        // Name field white border and background (must be drawn BEFORE EditBox renders its text)
        drawInputBorder(guiGraphics, mainX + 40, this.topPos + 28, 100, 14);
        // Texture field border
        drawInputBorder(guiGraphics, mainX + 141, this.topPos + 28, 74, 14);

        renderSidebarButtons(guiGraphics, mouseX, mouseY);
    }

    private void drawInputBorder(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        guiGraphics.fill(x, y, x + w, y + 1, 0xFFFFFFFF);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, 0xFFFFFFFF);
        guiGraphics.fill(x, y, x + 1, y + h, 0xFFFFFFFF);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, 0xFFFFFFFF);
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x80000000);
    }

    /**
     * 左侧扩展栏：顶部一个方形切换按钮，工作台模式下再显示保存/重置/清空三个小按钮，
     * 底部是帮助按钮。
     */
    private void renderSidebarButtons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int sbX = this.leftPos;
        boolean itemCell = canUseWorkbench();

        // 切换按钮（14x14，不做滑块外观）
        int tabX = sbX + 2;
        int tabY = this.topPos + 26;
        boolean hovered = isHoveringToggle(mouseX, mouseY);
        int border = itemCell ? 0xFF2A3E5C : 0xFF7D8FA6;
        int bg = itemCell ? (hovered ? 0xFFF2F8FF : 0xFFDCEAFA) : 0xFFB9C7D8;
        drawSidebarButton(guiGraphics, tabX, tabY, bg, border);
        int icon = itemCell ? 0xFF2A3E5C : 0xFF8899AA;
        if (uiMode == UI_MODE_LIST) {
            for (int i = 0; i < 3; i++) {
                int ly = tabY + 4 + i * 3;
                guiGraphics.fill(tabX + 4, ly, tabX + 10, ly + 1, icon);
            }
        } else {
            guiGraphics.fill(tabX + 4, tabY + 4, tabX + 7, tabY + 7, icon);
            guiGraphics.fill(tabX + 8, tabY + 4, tabX + 11, tabY + 7, icon);
            guiGraphics.fill(tabX + 4, tabY + 8, tabX + 7, tabY + 11, icon);
            guiGraphics.fill(tabX + 8, tabY + 8, tabX + 11, tabY + 11, icon);
        }

        // 工作台模式下，把保存/重置/清空放到侧栏
        if (uiMode == UI_MODE_WORKBENCH) {
            drawSmallActionButton(guiGraphics, 1, "R", 0xFF2A3E5C, mouseX, mouseY);
            drawSmallActionButton(guiGraphics, 2, "C", 0xFF6E2A2A, mouseX, mouseY);
        }

        // 底部帮助按钮
        int helpX = sbX + 3;
        int helpY = this.topPos + this.imageHeight - 14;
        guiGraphics.drawString(this.font, "?", helpX, helpY,
                isHoveringHelp(mouseX, mouseY) ? 0xFF2A3E5C : 0xFF5A6E88, true);
    }

    private void drawSidebarButton(GuiGraphics guiGraphics, int x, int y, int fill, int border) {
        guiGraphics.fill(x, y, x + 14, y + 14, border);
        guiGraphics.fill(x + 1, y + 1, x + 13, y + 13, fill);
    }

    private void drawSmallActionButton(GuiGraphics guiGraphics, int index, String glyph, int color,
                                       int mouseX, int mouseY) {
        int bx = this.leftPos + 2;
        int by = this.topPos + 48 + index * 18;
        boolean hovered = mouseX >= bx && mouseX <= bx + 14 && mouseY >= by && mouseY <= by + 14;
        drawSidebarButton(guiGraphics, bx, by, hovered ? 0xFFF2F8FF : 0xFFDCEAFA, 0xFF2A3E5C);
        guiGraphics.drawCenteredString(this.font, glyph, bx + 7, by + 3, color);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int x, int y) {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int x = this.leftPos + SIDEBAR_W;
        int y = this.topPos;
        ItemStack targetCell = this.menu.getCellInSlot();

        // Title
        guiGraphics.drawString(this.font, this.title, x + 8, y + 6,
                uiMode == UI_MODE_WORKBENCH ? 0x404040 : 0x2A3E5C, false);

        this.helpHovered = isHoveringHelp(mouseX, mouseY);
        if (helpHovered) {
            List<Component> helpLines = List.of(
                Component.translatable("gui.cell4.help_title"),
                Component.translatable("gui.cell4.help_items"),
                Component.translatable("gui.cell4.help_patterns"),
                Component.translatable("gui.cell4.help_blacklist"),
                Component.translatable("gui.cell4.help_texture"),
                Component.translatable("gui.cell4.help_hotkeys")
            );
            guiGraphics.renderTooltip(this.font, helpLines, Optional.empty(), mouseX, mouseY);
        }

        if (isHoveringToggle(mouseX, mouseY)) {
            guiGraphics.renderTooltip(this.font,
                    Component.translatable("gui.cell4.toggle_ui"), mouseX, mouseY);
        }

        if (uiMode == UI_MODE_WORKBENCH) {
            // AE2 风格：标题栏已画，直接渲染配置网格与物品栏标题
            renderWorkbenchContent(guiGraphics, mouseX, mouseY);
            guiGraphics.drawString(this.font, Component.translatable("container.inventory"),
                    x + 8, y + this.imageHeight - 93, 0x404040, false);
        } else {
            // Name label (next to item slot)
            guiGraphics.drawString(this.font, Component.translatable("gui.cell4.cell_name"), x + 40, y + 18,
                    !targetCell.isEmpty() && CellConfiguratorMenu.canEditName(targetCell) ? 0x2A3E5C : 0x8899AA, false);
            guiGraphics.drawString(this.font, Component.translatable("gui.cell4.cell_texture"), x + 141, y + 18,
                    targetCell.isEmpty() ? 0x8899AA : 0x2A3E5C, false);

            // Buttons
            int btnX = x + 215;
            int btnY = y + 6;
            renderMiniButton(guiGraphics, btnX - 34, btnY, 30, 12,
                    Component.translatable("gui.cell4.open_folder"), 0x2A3E5C, mouseX, mouseY);
            renderMiniButton(guiGraphics, btnX, btnY, 30, 12, Component.translatable("gui.cell4.save"), 0x2A6E2A, mouseX, mouseY);
            renderMiniButton(guiGraphics, btnX + 34, btnY, 30, 12, Component.translatable("gui.cell4.reset"), 0x2A3E5C, mouseX, mouseY);
            renderMiniButton(guiGraphics, btnX + 68, btnY, 30, 12, Component.translatable("gui.cell4.clear"), 0x6E2A2A, mouseX, mouseY);

            // Save result feedback
            if (saveResultTime > 0 && System.currentTimeMillis() - saveResultTime < SAVE_RESULT_DISPLAY_MS) {
                int feedbackColor = lastSaveSuccess ? 0x55FF55 : 0xFF5555;
                guiGraphics.drawString(this.font,
                    Component.translatable(lastSaveMessage),
                    btnX, btnY + 14, feedbackColor, false);
            }

            // Render scrollable content
            enableScissorForContent(guiGraphics);
            renderContent(guiGraphics, mouseX, mouseY);
            disableScissor(guiGraphics);

            // No cell warning - centered horizontally at top of content area
            if (targetCell.isEmpty()) {
                int warningWidth = this.font.width(Component.translatable("gui.cell4.no_cell"));
                int warningCenterX = x + CONTENT_X + (CONTENT_W - warningWidth) / 2;
                int warningTopY = y + CONTENT_Y + 4;
                guiGraphics.drawString(this.font,
                    Component.translatable("gui.cell4.no_cell"),
                    warningCenterX, warningTopY, 0xFF5555, false);
            }

            // Render scrollbar
            renderScrollbar(guiGraphics);
        }

        // === Tooltip Preview Panel (right side of GUI) ===
        ItemStack previewCell = this.menu.getCellInSlot();
        if (!previewCell.isEmpty()) {
            int previewX = this.leftPos + this.imageWidth + 6;
            int previewY = y + 18;

            // Small label above the preview tooltip
            guiGraphics.drawString(this.font, Component.translatable("gui.cell4.preview"), previewX, y + 4, 0x888888, false);

            // Build a live preview stack that reflects current unsaved edits
            ItemStack livePreview = buildLivePreviewStack(previewCell);

            // Get tooltip text lines from the live preview
            TooltipFlag flag = this.minecraft.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL;
            List<Component> tooltipLines = new ArrayList<>(livePreview.getTooltipLines(
                    net.minecraft.world.item.Item.TooltipContext.of(this.minecraft.level), this.minecraft.player, flag));

            // Build a capped tooltip image component (max 6 items) so the preview
            // doesn't overflow to the left when there are many entries.
            int previewCap = 6;
            Optional<TooltipComponent> tooltipImage = buildCappedTooltipImage(livePreview, previewCap);
            // Append "...还有N-6个" overflow line if capped
            int totalCount = getPreviewContentCount(livePreview);
            if (totalCount > previewCap) {
                tooltipLines.add(Component.translatable("gui.cell4.preview_more", totalCount - previewCap)
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            }

            // Render via vanilla renderTooltip: draws text + item-icon grid
            guiGraphics.renderTooltip(this.font, tooltipLines, tooltipImage, previewX, previewY);
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    /**
     * Build a clone of the current cell ItemStack with the in-editor (unsaved) values
     * applied to its NBT. Used for the live preview tooltip so the user sees their
     * edits (items, tags, modIds, blacklist, custom name) reflected in real time.
     */
    /** 拖放目标：UI1 的条目组（groupId >= 0）或 UI2 的网格槽（gridIndex >= 0）。 */
    public record DropZone(Rect2i bounds, int groupId, int gridIndex) {}

    /** 当前 UI 的拖放区域（屏幕坐标），供 JEI/REI/EMI 与原版拖拽使用。 */
    public List<DropZone> getDropZones() {
        List<DropZone> zones = new ArrayList<>();
        int x = this.leftPos + SIDEBAR_W;
        if (uiMode == UI_MODE_WORKBENCH) {
            int gx = getWorkbenchGridX();
            int gy = getWorkbenchGridY();
            for (int i = 0; i < GRID_LIMIT; i++) {
                int col = i % GRID_COLS;
                int row = i / GRID_COLS;
                zones.add(new DropZone(new Rect2i(gx + col * GRID_CELL, gy + row * GRID_CELL,
                        GRID_CELL, GRID_CELL), -1, i));
            }
        } else {
            int viewTop = this.topPos + CONTENT_Y;
            int viewBottom = viewTop + VIEWPORT_H;
            int currentY = viewTop - scrollOffset;
            for (EntryGroup group : groups) {
                int fullH = group.getTotalHeight();
                // 只高亮带 “+” 的组标题栏，而不是整组条目。
                if ((group.groupId == GROUP_ITEMS || group.groupId == GROUP_BLACKLIST)
                        && currentY + GROUP_HEADER_H > viewTop && currentY < viewBottom) {
                    zones.add(new DropZone(new Rect2i(x + CONTENT_X, currentY, CONTENT_W, GROUP_HEADER_H),
                            group.groupId, -1));
                }
                currentY += fullH + GROUP_GAP;
            }
        }
        return zones;
    }

    /** 接受一个物品 ID（来自原版拖拽或 JEI/REI/EMI 拖拽）。 */
    public void acceptDrop(String itemId, double mouseX, double mouseY) {
        if (itemId == null || itemId.isEmpty()) return;
        // 尽量保留数据组件：能解析成物品栈时按物品栈重新生成条目。
        ItemStack dropped = itemStackFromId(itemId);
        String entry = dropped != null ? itemEntryFromStack(dropped) : itemId;
        for (DropZone zone : getDropZones()) {
            if (!zone.bounds().contains((int) mouseX, (int) mouseY)) continue;
            if (zone.gridIndex() >= 0) {
                int absolute = pageOffset() + zone.gridIndex();
                if (absolute < gridItems.size()) {
                    gridItems.set(absolute, entry);
                } else if (!gridItems.contains(entry)) {
                    gridItems.add(entry);
                }
                onSave();
            } else if (zone.groupId() >= 0) {
                EntryGroup group = groups.get(zone.groupId());
                if (group.isEditable() && dropped != null) {
                    group.addEntry(entry);
                    updateFieldPositions();
                }
            }
            return;
        }
    }

    private boolean tryAcceptDropAt(String itemId, double mouseX, double mouseY) {
        for (DropZone zone : getDropZones()) {
            if (zone.bounds().contains((int) mouseX, (int) mouseY)) {
                acceptDrop(itemId, mouseX, mouseY);
                return true;
            }
        }
        return false;
    }

    private ItemStack buildLivePreviewStack(ItemStack source) {
        ItemStack copy = source.copy();

        List<String> items = currentItemEntries();
        List<String> tags = collectNonEmpty(GROUP_TAGS);
        List<String> modIds = collectNonEmpty(GROUP_MODIDS);
        List<String> blacklist = collectNonEmpty(GROUP_BLACKLIST);

        if (copy.getItem() instanceof IInfinityCell cell
                && cell.getCellType() == IInfinityCell.CellType.ITEM) {
            InfinityItemCell.setIdentifiers(copy, items);
        } else if (copy.getItem() instanceof IInfinityCell cell
                && cell.getCellType() == IInfinityCell.CellType.TAG) {
            InfinityTagCell.setTagNames(copy, tags);
            InfinityTagCell.setModIds(copy, modIds);
        } else if (copy.getItem() instanceof IInfinityCell cell
                && cell.getCellType() == IInfinityCell.CellType.MODID) {
            InfinityModIdCell.setModIds(copy, modIds);
        }

        Cell4Util.setStringList(copy, NBTKeys.BLACKLIST, blacklist);

        String pendingName = this.nameField.getValue();
        if (copy.getItem() instanceof IInfinityCell cell) {
            cell.setCustomName(copy, pendingName);
            if (this.textureField != null) {
                cell.setTextureName(copy, this.textureField.getValue());
            }
            if (cell.isPortable()) {
                List<String> power = collectNonEmpty(GROUP_POWER);
                if (power.size() >= 3) {
                    PortableCellPower.setOverrides(copy, power.get(0), power.get(1), power.get(2));
                }
            }
        }

        return copy;
    }

    private List<String> collectNonEmpty(int groupId) {
        EntryGroup group = groups.get(groupId);
        List<String> result = new ArrayList<>();
        // 优先读取输入框的当前内容，让预览跟随输入实时更新（不必等保存）。
        for (int i = 0; i < group.entries.size(); i++) {
            String val = i < group.entryFields.size()
                    ? group.entryFields.get(i).getValue()
                    : group.entries.get(i);
            if (val != null && !val.isEmpty()) result.add(val);
        }
        return result;
    }

    private List<String> currentItemEntries() {
        // 预览按去重后的条目构建，避免重复条目让 tooltip 图标/计数出现重复。
        if (uiMode == UI_MODE_WORKBENCH) {
            List<String> merged = new ArrayList<>(gridItems);
            merged.addAll(gridOtherItems);
            return new ArrayList<>(new LinkedHashSet<>(merged));
        }
        return new ArrayList<>(new LinkedHashSet<>(collectNonEmpty(GROUP_ITEMS)));
    }

    /**
     * 预览面板占用的屏幕区域；无元件时返回 null。
     * JEI / REI / EMI 通过各自的 API 读取该区域并把物品面板移开。
     */
    public Rect2i getPreviewBounds() {
        if (this.minecraft == null || this.menu == null || this.nameField == null) return null;
        ItemStack cell = this.menu.getCellInSlot();
        if (cell.isEmpty()) return null;
        ItemStack previewCell = buildLivePreviewStack(cell);
        if (previewCell.isEmpty()) return null;
        List<Component> lines = previewCell.getTooltipLines(
                net.minecraft.world.item.Item.TooltipContext.of(this.minecraft.level),
                this.minecraft.player,
                this.minecraft.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL);
        int maxWidth = 0;
        for (Component line : lines) {
            maxWidth = Math.max(maxWidth, this.font.width(line));
        }
        int w = Math.max(120, maxWidth + 10);
        int h = Math.max(30, lines.size() * 10 + 8);
        int previewX = this.leftPos + this.imageWidth + 6;
        // 顶部留出 "预览" 标签的空间（渲染时标签在 topPos + 4，tooltip 在 topPos + 18）
        return new Rect2i(previewX - 2, this.topPos + 2, w + 4, h + 18);
    }

    /**
     * Count total matching keys the cell would store (before capping).
     * Delegates to the cell item's getPreviewTotalCount for the TRUE count
     * (the tooltip image component may be capped to 18).
     */
    private int getPreviewContentCount(ItemStack livePreview) {
        if (livePreview.getItem() instanceof IInfinityCell cell) {
            return switch (cell.getCellType()) {
                case ITEM -> InfinityItemCell.getPreviewTotalCount(livePreview);
                case TAG -> InfinityTagCell.getPreviewTotalCount(livePreview);
                case MODID -> InfinityModIdCell.getPreviewTotalCount(livePreview);
            };
        }
        return 0;
    }

    /**
     * Build a tooltip image component for the preview, capped to at most {@code max}
     * GenericStacks. hasMoreContent is always false here because the caller adds
     * its own "...还有N-6个" text line — avoiding AE2's native "..." duplicate.
     */
    private Optional<TooltipComponent> buildCappedTooltipImage(ItemStack livePreview, int max) {
        Optional<TooltipComponent> original = livePreview.getTooltipImage();
        if (original.isEmpty()) return original;
        TooltipComponent tc = original.get();
        if (!(tc instanceof appeng.items.storage.StorageCellTooltipComponent cellTc)) {
            return original;
        }
        java.util.List<appeng.api.stacks.GenericStack> content = cellTc.content();
        if (content.size() <= max) return original;
        java.util.List<appeng.api.stacks.GenericStack> capped = new ArrayList<>(content.subList(0, max));
        return Optional.of(new appeng.items.storage.StorageCellTooltipComponent(
            cellTc.upgrades(), capped, false, cellTc.showAmounts()));
    }

    /**
     * Renders a tooltip at a fixed screen position, bypassing Minecraft's built-in
     * renderTooltip logic that may reposition the tooltip based on mouse/hover state.
     * This manually draws the tooltip background and text lines at the exact (x, y) given.
     */
    private void renderTooltipAtFixedPosition(GuiGraphics guiGraphics, List<Component> lines, int x, int y) {
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
        guiGraphics.fill(x - 1, y - 1, x + tooltipWidth + 1, y, borderColor);
        // Outer border (bottom)
        guiGraphics.fill(x - 1, y + tooltipHeight, x + tooltipWidth + 1, y + tooltipHeight + 1, borderColor);
        // Outer border (left)
        guiGraphics.fill(x - 1, y, x, y + tooltipHeight, borderColor);
        // Outer border (right)
        guiGraphics.fill(x + tooltipWidth, y, x + tooltipWidth + 1, y + tooltipHeight, borderColor);

        // Inner background
        guiGraphics.fill(x, y, x + tooltipWidth, y + tooltipHeight, bgColor);

        // Gradient borders (purple-blue, like vanilla Minecraft tooltips)
        int borderGradTop = 0x505000FF;
        int borderGradBot = 0x5028007F;

        // Top gradient border
        guiGraphics.fill(x, y, x + tooltipWidth, y + 1, borderGradTop);
        // Left gradient border
        guiGraphics.fill(x, y, x + 1, y + tooltipHeight, borderGradTop);
        // Bottom gradient border
        guiGraphics.fill(x, y + tooltipHeight - 1, x + tooltipWidth, y + tooltipHeight, borderGradBot);
        // Right gradient border
        guiGraphics.fill(x + tooltipWidth - 1, y, x + tooltipWidth, y + tooltipHeight, borderGradBot);

        // Draw text lines
        int currentY = y + 3;
        for (Component line : lines) {
            guiGraphics.drawString(this.font, line, x + 4, currentY, 0xFFFFFFFF, true);
            currentY += lineHeight;
        }
    }

    private void enableScissorForContent(GuiGraphics guiGraphics) {
        int scissorX = this.leftPos + SIDEBAR_W + CONTENT_X - 2;
        int scissorY = this.topPos + CONTENT_Y - 2;
        int scissorW = CONTENT_W + 4;
        int scissorH = VIEWPORT_H + 4;
        guiGraphics.enableScissor(scissorX, scissorY, scissorX + scissorW, scissorY + scissorH);
    }

    private void disableScissor(GuiGraphics guiGraphics) {
        guiGraphics.disableScissor();
    }

    private void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (uiMode == UI_MODE_WORKBENCH) {
            renderWorkbenchContent(guiGraphics, mouseX, mouseY);
            return;
        }
        int x = this.leftPos + SIDEBAR_W;
        int baseY = this.topPos + CONTENT_Y - scrollOffset;
        int currentY = baseY;

        for (EntryGroup group : groups) {
            boolean isPower = group.groupId == GROUP_POWER;
            if (isPower && !isPortableCell()) {
                continue;
            }
            int screenY = currentY - this.topPos;
            boolean headerVisible = isRowVisible(screenY, GROUP_HEADER_H);

            if (headerVisible) {
                guiGraphics.fill(x + CONTENT_X, currentY, x + CONTENT_X + CONTENT_W, currentY + GROUP_HEADER_H, 0x30406080);

                String arrow = group.collapsed ? "\u25BA" : "\u25BC";
                int arrowColor = group.isEditable() ? 0xFFFFFF : 0x8899AA;
                guiGraphics.drawString(this.font, arrow, x + CONTENT_X + 2, currentY + 3, arrowColor, true);

                guiGraphics.drawString(this.font, Component.translatable(group.labelKey),
                    x + CONTENT_X + 14, currentY + 3, arrowColor, true);

                if (!isPower) {
                    // "+" button
                    int plusX = x + CONTENT_X + CONTENT_W - SCROLLBAR_W - 14;
                    boolean plusHovered = isHoveringPlus(mouseX, mouseY, plusX, currentY);
                    int plusColor = plusHovered ? 0xFFFF55 : (group.isEditable() ? 0x55FF55 : 0x8899AA);
                    guiGraphics.drawString(this.font, "+", plusX, currentY + 3, plusColor, true);

                    if (!group.entries.isEmpty()) {
                        String count = "(" + group.entries.size() + ")";
                        guiGraphics.drawString(this.font, count, x + CONTENT_X + 14 + this.font.width(Component.translatable(group.labelKey)) + 4,
                            currentY + 3, 0x8899AA, false);
                    }
                }
            }

            if (!group.collapsed) {
                for (int i = 0; i < group.entries.size(); i++) {
                    int entryY = currentY + GROUP_HEADER_H + i * ENTRY_H;
                    int entryScreenY = entryY - this.topPos;

                    if (isRowVisible(entryScreenY, ENTRY_H)) {
                        guiGraphics.fill(x + CONTENT_X, entryY, x + CONTENT_X + CONTENT_W, entryY + ENTRY_H,
                            i % 2 == 0 ? 0x10000000 : 0x18000000);

                        if (isPower) {
                            String label = switch (i) {
                                case 0 -> Component.translatable("gui.cell4.power_max").getString();
                                case 1 -> Component.translatable("gui.cell4.power_cost").getString();
                                default -> Component.translatable("gui.cell4.power_charge").getString();
                            };
                            guiGraphics.drawString(this.font, label, x + CONTENT_X + 4, entryY + 3,
                                    group.isEditable() ? 0xFFFFFF : 0x8899AA, true);
                        } else {
                            String seqNum = (i + 1) + ".";
                            guiGraphics.drawString(this.font, seqNum, x + CONTENT_X + 4, entryY + 3, 0x000000, true);

                            int xBtnX = x + CONTENT_X + CONTENT_W - SCROLLBAR_W - 12;
                            boolean xHovered = isHoveringX(mouseX, mouseY, xBtnX, entryY);
                            int xColor = xHovered ? 0xFF5555 : 0x995555;
                            guiGraphics.drawString(this.font, "x", xBtnX, entryY + 3, xColor, true);
                        }
                    }
                }
            }

            currentY += group.getTotalHeight() + GROUP_GAP;
        }

        int totalH = calcTotalContentHeight();
        maxScroll = Math.max(0, totalH - VIEWPORT_H);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
    }

    // === Workbench-style second UI ===

    private boolean canUseWorkbench() {
        return canUseWorkbench(this.menu.getCellInSlot());
    }

    private static boolean canUseWorkbench(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof IInfinityCell cell
                && cell.getCellType() == IInfinityCell.CellType.ITEM;
    }

    private void toggleUiMode() {
        if (uiMode == UI_MODE_LIST) {
            if (!canUseWorkbench()) return;
            syncEntriesFromFields();
            populateGridFromEntries(groups.get(GROUP_ITEMS).entries);
            uiMode = UI_MODE_WORKBENCH;
        } else {
            groups.get(GROUP_ITEMS).setEntries(mergedGridItems());
            uiMode = UI_MODE_LIST;
            scrollOffset = 0;
        }
        // 重新 init()：按新 UI 重新计算窗口尺寸与槽位坐标
        this.rebuildWidgets();
    }

    private List<String> mergedGridItems() {
        List<String> merged = new ArrayList<>(gridItems);
        merged.addAll(gridOtherItems);
        return merged;
    }

    private void populateGridFromEntries(List<String> items) {
        gridItems.clear();
        gridOtherItems.clear();
        for (String entry : items) {
            if (entry == null || entry.isEmpty()) continue;
            if (itemStackFromId(entry) != null) {
                if (!gridItems.contains(entry)) {
                    gridItems.add(entry);
                }
            } else {
                gridOtherItems.add(entry);
            }
        }
    }

    private void commitGridToGroup() {
        // 持久化前按字符串去重（保持顺序），AE 侧本身按 key 合并，重复条目没有意义。
        List<String> unique = new ArrayList<>(new LinkedHashSet<>(gridItems));
        gridItems.clear();
        gridItems.addAll(unique);
        groups.get(GROUP_ITEMS).setEntries(mergedGridItems());
    }

    /** 网格中去重后的绑定数量（用于界面上“已绑定 n 个”的显示）。 */
    private int uniqueGridCount() {
        return new java.util.LinkedHashSet<>(gridItems).size();
    }

    /**
     * 解析一条绑定项为预览用 ItemStack（与存储侧共用 {@link Cell4Util#parseItemStackEntry}），
     * 支持纯 ID、{@code id[...]} 组件语法、{@code id{...}} 旧版 SNBT 与完整物品 compound。
     * 非物品条目（流体/化学品/能量/glob/regex）返回 null。
     */
    public static ItemStack itemStackFromId(String id) {
        if (id == null || id.isEmpty()) return null;
        ItemStack stack = Cell4Util.parseItemStackEntry(id);
        return stack.isEmpty() ? null : stack;
    }

    /**
     * 物品栈 → 绑定条目字符串。无组件差异时保存纯 ID，
     * 否则保存完整物品 compound（{@code {id:"...",count:1,components:{...}}}）以保留数据组件。
     */
    public static String itemEntryFromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return "";
        String plain = id.toString();
        if (stack.getComponentsPatch().isEmpty()) return plain;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return plain;
        try {
            var ops = RegistryOps.create(NbtOps.INSTANCE, minecraft.level.registryAccess());
            return ItemStack.CODEC.encodeStart(ops, stack)
                    .result().map(Object::toString).orElse(plain);
        } catch (Throwable ignored) {
            return plain;
        }
    }

    private int getWorkbenchGridX() {
        return this.leftPos + SIDEBAR_W + PANEL_GRID_X;
    }

    private int getWorkbenchGridY() {
        return this.topPos + PANEL_GRID_Y;
    }

    private int getGridIndex(int mouseX, int mouseY) {
        int gx = getWorkbenchGridX();
        int gy = getWorkbenchGridY();
        if (mouseX < gx || mouseX >= gx + GRID_COLS * GRID_CELL) return -1;
        if (mouseY < gy || mouseY >= gy + GRID_ROWS * GRID_CELL) return -1;
        int col = (mouseX - gx) / GRID_CELL;
        int row = (mouseY - gy) / GRID_CELL;
        return row * GRID_COLS + col;
    }

    private int getSideX() {
        return this.leftPos + SIDEBAR_W + PANEL_W;
    }

    private int realPageCount() {
        return Math.max(1, (gridItems.size() + GRID_LIMIT - 1) / GRID_LIMIT);
    }

    /** 显示用页数：允许当前停留在刚新建的空页。 */
    private int pageCount() {
        return Math.max(realPageCount(), workbenchPage + 1);
    }

    private int pageOffset() {
        return workbenchPage * GRID_LIMIT;
    }

    private boolean handleWorkbenchClick(int mouseX, int mouseY, int button) {
        int sideX = getSideX();
        int py = this.topPos;
        // 翻页按钮
        if (mouseY >= py + 102 && mouseY < py + 114) {
            if (mouseX >= sideX + 5 && mouseX < sideX + 25) {
                if (workbenchPage > 0) workbenchPage--;
                return true;
            }
            if (mouseX >= sideX + 55 && mouseX < sideX + 75) {
                // 随时可以进入下一页（空白页也允许）。
                workbenchPage++;
                return true;
            }
        }
        int index = getGridIndex(mouseX, mouseY);
        if (index < 0) return false;
        int absolute = pageOffset() + index;
        ItemStack carried = this.menu.getCarried();
        if (carried.isEmpty()) {
            if (absolute < gridItems.size()) {
                gridItems.remove(absolute);
                if (workbenchPage >= realPageCount()) workbenchPage = realPageCount() - 1;
            }
        } else {
            String value = itemEntryFromStack(carried);
            if (!value.isEmpty()) {
                if (absolute < gridItems.size()) {
                    gridItems.set(absolute, value);
                } else if (!gridItems.contains(value)) {
                    // 点任意空白格都按顺序追加，不必从第一格开始。
                    gridItems.add(value);
                }
            }
        }
        // 绑定/移除后立即保存，无需再点保存按钮。
        onSave();
        return true;
    }

    private void renderWorkbenchContent(GuiGraphics graphics, int mouseX, int mouseY) {
        int px = this.leftPos + SIDEBAR_W;
        int py = this.topPos;
        int gx = getWorkbenchGridX();
        int gy = getWorkbenchGridY();
        int offset = pageOffset();

        // AE2 原版槽位底板
        for (int index = 0; index < GRID_LIMIT; index++) {
            int col = index % GRID_COLS;
            int row = index / GRID_COLS;
            int sx = gx + col * GRID_CELL;
            int sy = gy + row * GRID_CELL;
            graphics.blit(AE2_STATES, sx, sy, 192, 192, 18, 18, 256, 256);
            int absolute = offset + index;
            if (absolute < gridItems.size()) {
                ItemStack stack = itemStackFromId(gridItems.get(absolute));
                if (stack != null) {
                    graphics.renderItem(stack, sx + 1, sy + 1);
                }
            }
            if (mouseX >= sx && mouseX < sx + GRID_CELL && mouseY >= sy && mouseY < sy + GRID_CELL) {
                graphics.fill(sx + 1, sy + 1, sx + 17, sy + 2, 0xFFFFFFFF);
                graphics.fill(sx + 1, sy + 16, sx + 17, sy + 17, 0xFFFFFFFF);
                graphics.fill(sx + 1, sy + 1, sx + 2, sy + 17, 0xFFFFFFFF);
                graphics.fill(sx + 16, sy + 1, sx + 17, sy + 17, 0xFFFFFFFF);
            }
        }

        // === 右侧控制栏 ===
        int sideX = getSideX();
        int cx = sideX + 5;
        int contentW = WORKBENCH_SIDE_W - 10;
        graphics.drawString(this.font, Component.translatable("gui.cell4.workbench_controls"),
                cx, py + 6, 0x404040, false);
        graphics.drawString(this.font, Component.translatable("gui.cell4.cell_texture"),
                cx, py + 30, 0x404040, false);
        // 材质输入框边框由 renderBg 画
        renderMiniButton(graphics, cx, py + 58, contentW, 12,
                Component.translatable("gui.cell4.open_folder"), 0x2A3E5C, mouseX, mouseY);
        renderMiniButton(graphics, cx, py + 72, contentW, 12,
                Component.translatable("gui.cell4.reset"), 0x2A3E5C, mouseX, mouseY);
        renderMiniButton(graphics, cx, py + 86, contentW, 12,
                Component.translatable("gui.cell4.clear"), 0x6E2A2A, mouseX, mouseY);

        // 翻页
        renderMiniButton(graphics, cx, py + 102, 20, 12, Component.literal("<"), 0x2A3E5C, mouseX, mouseY);
        renderMiniButton(graphics, cx + 50, py + 102, 20, 12, Component.literal(">"), 0x2A3E5C, mouseX, mouseY);
        String pageText = (workbenchPage + 1) + "/" + pageCount();
        graphics.drawCenteredString(this.font, pageText, cx + 35, py + 104, 0x404040);
        String count = Component.translatable("gui.cell4.workbench_count", uniqueGridCount()).getString();
        graphics.drawString(this.font, count, cx, py + 118, 0x404040, false);

        // 其他键
        graphics.drawString(this.font, Component.translatable("gui.cell4.workbench_other",
                        gridOtherItems.size()), cx, py + 132, 0x404040, false);
        int shown = 0;
        for (String entry : gridOtherItems) {
            if (shown >= 4) break;
            String text = entry;
            if (text.length() > 15) text = text.substring(0, 14) + "\u2026";
            graphics.drawString(this.font, text, cx, py + 144 + shown * 10, 0x556677, false);
            shown++;
        }
        if (gridOtherItems.size() > shown) {
            graphics.drawString(this.font, "...", cx, py + 144 + shown * 10, 0x8899AA, false);
        }
    }

    private void renderScrollbar(GuiGraphics guiGraphics) {
        if (uiMode == UI_MODE_WORKBENCH) return;
        int totalH = calcTotalContentHeight();
        if (totalH <= VIEWPORT_H) return;

        int x = this.leftPos + SIDEBAR_W + SCROLLBAR_X_OFFSET;
        int y = this.topPos + CONTENT_Y;
        int h = VIEWPORT_H;

        guiGraphics.fill(x, y, x + SCROLLBAR_W, y + h, 0x20FFFFFF);

        float ratio = (float) VIEWPORT_H / totalH;
        int thumbH = Math.max(12, (int) (h * ratio));
        int thumbY = y + (int) ((h - thumbH) * ((float) scrollOffset / maxScroll));
        guiGraphics.fill(x, thumbY, x + SCROLLBAR_W, thumbY + thumbH, 0x80FFFFFF);
    }

    private void renderMiniButton(GuiGraphics guiGraphics, int x, int y, int w, int h, Component text, int color, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        guiGraphics.fill(x, y, x + w, y + h, 0xFF3C5078);
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFAFCAE6);
        if (hovered) {
            guiGraphics.fill(x, y, x + w, y + 1, 0xFFFFFFFF);
            guiGraphics.fill(x, y + h - 1, x + w, y + h, 0xFFFFFFFF);
            guiGraphics.fill(x, y, x + 1, y + h, 0xFFFFFFFF);
            guiGraphics.fill(x + w - 1, y, x + w, y + h, 0xFFFFFFFF);
        }
        guiGraphics.drawCenteredString(this.font, text, x + w / 2, y + 2, color);
    }

    private boolean isHoveringHelp(int mouseX, int mouseY) {
        int helpX = this.leftPos + 3;
        int helpY = this.topPos + this.imageHeight - 14;
        return mouseX >= helpX && mouseX <= helpX + 8 && mouseY >= helpY && mouseY <= helpY + 10;
    }

    private boolean isHoveringToggle(int mouseX, int mouseY) {
        int tabX = this.leftPos + 2;
        int tabY = this.topPos + 26;
        return mouseX >= tabX && mouseX <= tabX + 14 && mouseY >= tabY && mouseY <= tabY + 14;
    }

    private boolean isHoveringSidebarAction(int mouseX, int mouseY, int index) {
        int bx = this.leftPos + 2;
        int by = this.topPos + 48 + index * 18;
        return mouseX >= bx && mouseX <= bx + 14 && mouseY >= by && mouseY <= by + 14;
    }

    private boolean isHoveringPlus(int mouseX, int mouseY, int plusX, int plusY) {
        return mouseX >= plusX && mouseX <= plusX + 8 && mouseY >= plusY && mouseY <= plusY + 12;
    }

    private boolean isHoveringX(int mouseX, int mouseY, int xBtnX, int entryY) {
        return mouseX >= xBtnX && mouseX <= xBtnX + 8 && mouseY >= entryY && mouseY <= entryY + 12;
    }

    private boolean isHoveringGroupHeader(int mouseX, int mouseY, int headerY) {
        int hx = this.leftPos + SIDEBAR_W + CONTENT_X;
        return mouseX >= hx && mouseX <= hx + CONTENT_W && mouseY >= headerY && mouseY <= headerY + GROUP_HEADER_H;
    }

    private boolean findAndClickPlus(int mouseX, int mouseY) {
        int baseY = this.topPos + CONTENT_Y - scrollOffset;
        int currentY = baseY;
        for (EntryGroup group : groups) {
            if (group.groupId == GROUP_POWER) continue;
            int plusX = this.leftPos + SIDEBAR_W + CONTENT_X + CONTENT_W - SCROLLBAR_W - 14;
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
            if (group.groupId == GROUP_POWER) continue;
            if (!group.collapsed) {
                for (int i = 0; i < group.entries.size(); i++) {
                    int entryY = currentY + GROUP_HEADER_H + i * ENTRY_H;
                    int xBtnX = this.leftPos + SIDEBAR_W + CONTENT_X + CONTENT_W - SCROLLBAR_W - 12;
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
        if (uiMode == UI_MODE_WORKBENCH) {
            if (isHoveringSidebarAction(mouseX, mouseY, 1)) { playButtonSound(); onReset(); return true; }
            if (isHoveringSidebarAction(mouseX, mouseY, 2)) { playButtonSound(); onClear(); return true; }
            // 右侧控制栏按钮
            int cx = getSideX() + 5;
            int cw = WORKBENCH_SIDE_W - 10;
            int py = this.topPos;
            if (mouseX >= cx && mouseX <= cx + cw) {
                if (mouseY >= py + 58 && mouseY <= py + 70) { playButtonSound(); Cell4ClientHooks.openTextureFolder(); return true; }
                if (mouseY >= py + 72 && mouseY <= py + 84) { playButtonSound(); onReset(); return true; }
                if (mouseY >= py + 86 && mouseY <= py + 98) { playButtonSound(); onClear(); return true; }
            }
            return false;
        }
        int btnX = this.leftPos + SIDEBAR_W + 215;
        int btnY = this.topPos + 6;
        int btnW = 30, btnH = 12;

        if (mouseX >= btnX - 34 && mouseX <= btnX - 34 + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            playButtonSound();
            Cell4ClientHooks.openTextureFolder();
            return true;
        }
        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            playButtonSound();
            onSave();
            return true;
        }
        if (mouseX >= btnX + 34 && mouseX <= btnX + 34 + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            playButtonSound();
            onReset();
            return true;
        }
        if (mouseX >= btnX + 68 && mouseX <= btnX + 68 + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            playButtonSound();
            onClear();
            return true;
        }
        return false;
    }

    /** Play the vanilla button-click sound effect. */
    private void playButtonSound() {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.5F, 1.0F);
        }
    }

    private void onSave() {
        // 保存时顺便扫描材质文件夹，确保 F3+T 前材质包已生成。
        Cell4Config.refresh();
        ItemStack targetCell = this.menu.getCellInSlot();
        if (targetCell.isEmpty()) return;

        if (uiMode == UI_MODE_WORKBENCH) {
            commitGridToGroup();
        }
        syncEntriesFromFields();
        // UI1 保存的条目也去重（保持顺序），避免同一元件重复写入 NBT。
        EntryGroup itemGroup = groups.get(GROUP_ITEMS);
        List<String> uniqueItems = new ArrayList<>(new LinkedHashSet<>(itemGroup.entries));
        if (uniqueItems.size() != itemGroup.entries.size()) {
            itemGroup.entries.clear();
            itemGroup.entries.addAll(uniqueItems);
            itemGroup.rebuildFields();
        }
        String itemValue = String.join(",", groups.get(GROUP_ITEMS).entries);
        String tagValue = String.join(",", groups.get(GROUP_TAGS).entries);
        String modIdValue = String.join(",", groups.get(GROUP_MODIDS).entries);
        String blacklistValue = String.join(",", groups.get(GROUP_BLACKLIST).entries);
        List<String> power = groups.get(GROUP_POWER).entries;
        String powerMax = power.size() > 0 ? power.get(0) : "";
        String powerCost = power.size() > 1 ? power.get(1) : "";
        String powerCharge = power.size() > 2 ? power.get(2) : "";

        PacketDistributor.sendToServer(new CellConfigSavePacket(
            itemValue, tagValue, modIdValue, blacklistValue, this.nameField.getValue(),
            powerMax, powerCost, powerCharge,
            this.textureField != null ? this.textureField.getValue() : ""
        ));
    }

    private void onReset() {
        if (uiMode == UI_MODE_WORKBENCH) {
            populateGridFromEntries(snapshotItems);
            return;
        }
        groups.get(GROUP_ITEMS).setEntries(new ArrayList<>(snapshotItems));
        groups.get(GROUP_TAGS).setEntries(new ArrayList<>(snapshotTags));
        groups.get(GROUP_MODIDS).setEntries(new ArrayList<>(snapshotModIds));
        groups.get(GROUP_BLACKLIST).setEntries(new ArrayList<>(snapshotBlacklist));
        if (isPortableCell()) {
            groups.get(GROUP_POWER).setEntries(new ArrayList<>(snapshotPower));
            groups.get(GROUP_POWER).collapsed = false;
        }
        this.nameField.setValue(snapshotName);
        if (this.textureField != null) this.textureField.setValue(snapshotTexture);
        updateFieldPositions();
    }

    private void onClear() {
        ItemStack cell = this.menu.getCellInSlot();
        if (cell.isEmpty()) return;
        if (uiMode == UI_MODE_WORKBENCH) {
            gridItems.clear();
            gridOtherItems.clear();
            return;
        }
        for (EntryGroup group : groups) {
            if (group.groupId == GROUP_POWER) continue;
            if (group.isEditable()) {
                group.setEntries(new ArrayList<>());
            }
        }
        if (isPortableCell()) {
            groups.get(GROUP_POWER).setEntries(List.of("0", "0", "0"));
            groups.get(GROUP_POWER).collapsed = false;
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 光标上已有物品时，点击条目栏/网格直接添加，物品继续留在光标上。
        if (button == 0 && !this.menu.getCarried().isEmpty()) {
            ItemStack carried = this.menu.getCarried();
            String entry = itemEntryFromStack(carried);
            if (!entry.isEmpty() && tryAcceptDropAt(entry, mouseX, mouseY)) {
                return true;
            }
        }
        if (isHoveringToggle((int) mouseX, (int) mouseY)) {
            playButtonSound();
            if (canUseWorkbench()) toggleUiMode();
            return true;
        }

        if (findAndClickSaveResetClear((int) mouseX, (int) mouseY)) return true;

        int contentTop = this.topPos + CONTENT_Y;
        int contentBottom = contentTop + VIEWPORT_H;

        if (uiMode == UI_MODE_WORKBENCH) {
            if (handleWorkbenchClick((int) mouseX, (int) mouseY, button)) return true;
            boolean result = super.mouseClicked(mouseX, mouseY, button);
            if (this.menu.getCellInSlot().isEmpty()) clearAllFieldFocus();
            return result;
        }

        if (mouseY >= contentTop && mouseY <= contentBottom) {
            if (findAndClickPlus((int) mouseX, (int) mouseY)) return true;
            if (findAndClickX((int) mouseX, (int) mouseY)) return true;
            findAndToggleGroup((int) mouseX, (int) mouseY);
        }

        int sbX = this.leftPos + SIDEBAR_W + SCROLLBAR_X_OFFSET;
        if (mouseX >= sbX && mouseX <= sbX + SCROLLBAR_W && mouseY >= contentTop && mouseY <= contentBottom) {
            this.scrolling = true;
            return true;
        }

        boolean result = super.mouseClicked(mouseX, mouseY, button);
        if (this.menu.getCellInSlot().isEmpty()) {
            clearAllFieldFocus();
        }
        return result;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.scrolling = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.scrolling) {
            int totalH = calcTotalContentHeight();
            if (totalH > VIEWPORT_H) {
                float ratio = (float) dragY / (VIEWPORT_H - 12);
                scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + (int) (ratio * totalH)));
                updateFieldPositions();
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (uiMode == UI_MODE_WORKBENCH) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        int contentTop = this.topPos + CONTENT_Y;
        int contentBottom = contentTop + VIEWPORT_H;
        if (mouseX >= this.leftPos + SIDEBAR_W && mouseX <= this.leftPos + this.imageWidth &&
            mouseY >= contentTop && mouseY <= contentBottom) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (verticalAmount * 14)));
            updateFieldPositions();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.textureField != null && this.textureField.isFocused()) {
            if (keyCode == 256) { this.onClose(); return true; }
            if (keyCode == 257) { onSave(); return true; }
            this.textureField.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (this.nameField.isFocused()) {
            if (keyCode == 256) { this.onClose(); return true; }
            if (keyCode == 257) { onSave(); return true; }
            this.nameField.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        for (EntryGroup group : groups) {
            for (EditBox field : group.entryFields) {
                if (field.isFocused()) {
                    if (keyCode == 256) { this.onClose(); return true; }
                    if (keyCode == 257) { onSave(); return true; }
                    field.keyPressed(keyCode, scanCode, modifiers);
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.textureField != null && this.textureField.isFocused()) {
            this.textureField.charTyped(codePoint, modifiers);
            return true;
        }
        if (this.nameField.isFocused()) {
            this.nameField.charTyped(codePoint, modifiers);
            return true;
        }
        for (EntryGroup group : groups) {
            for (EditBox field : group.entryFields) {
                if (field.isFocused()) {
                    field.charTyped(codePoint, modifiers);
                    return true;
                }
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void clearAllFieldFocus() {
        this.nameField.setFocused(false);
        if (this.textureField != null) this.textureField.setFocused(false);
        for (EntryGroup group : groups) {
            for (EditBox field : group.entryFields) {
                field.setFocused(false);
            }
        }
    }
}
