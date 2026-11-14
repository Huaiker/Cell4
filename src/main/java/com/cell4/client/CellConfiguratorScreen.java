package com.cell4.client;

import com.cell4.Cell4;
import com.cell4.common.item.*;
import com.cell4.common.menu.CellConfiguratorMenu;
import com.cell4.common.util.Cell4Config;
import com.cell4.common.util.Cell4Util;
import com.cell4.common.util.PortableCellPower;
import com.cell4.network.CellConfigSavePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.joml.Vector2ic;

import java.util.*;

public class CellConfiguratorScreen extends AbstractContainerScreen<CellConfiguratorMenu> {

    private static final Identifier GUI_TEXTURE = Identifier.fromNamespaceAndPath(Cell4.MODID, "textures/guis/cell_configurator.png");
    private static final Identifier AE2_CELLWORKBENCH =
            Identifier.fromNamespaceAndPath("ae2", "textures/guis/cellworkbench.png");
    private static final Identifier AE2_STATES =
            Identifier.fromNamespaceAndPath("ae2", "textures/guis/states.png");

    private static final int SIDEBAR_W = CellConfiguratorMenu.SIDEBAR_WIDTH;
    private static final int MAIN_W = 320;
    private static final int MAIN_H = 280;

    // AE2 原版元件工作台布局
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

    // 第一个 UI 的槽位坐标（与 CellConfiguratorMenu 一致）
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
    private List<net.minecraft.world.inventory.Slot> baseSlots;

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
    /** 预览 Tooltip 中内容物品图标的单行数量（与 1.21.1 的一排布局一致）。 */
    private static final int PREVIEW_ICONS_PER_ROW = 6;

    // Name field at top (next to item slot)
    private EditBox nameField;
    private EditBox textureField;
    private String snapshotTexture = "";

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

    // 预览缓存：以元件 NBT 快照 + 当前编辑内容为键，避免每帧深拷贝长 NBT 并重建 Tooltip。
    private ItemStack previewStackCache = ItemStack.EMPTY;
    private List<ClientTooltipComponent> previewComponentsCache;
    private ItemStack previewComponentsStack = ItemStack.EMPTY;
    private boolean previewComponentsAdvanced;
    private boolean previewCacheValid;
    private Object previewSourceData;
    private net.minecraft.world.item.Item previewSourceItem;
    private List<String> previewItems = List.of();
    private List<String> previewTags = List.of();
    private List<String> previewModIds = List.of();
    private List<String> previewPower = List.of();
    private String previewName = "";
    private String previewTexture = "";

    // Help button hover state
    private boolean helpHovered = false;

    // Cached mouse position for rendering (since extract model doesn't pass mouse coords to all methods)
    private int cachedMouseX = 0;
    private int cachedMouseY = 0;

    /** 拖放目标：UI1 的条目组（groupId >= 0）或 UI2 的网格槽（gridIndex >= 0）。 */
    public record DropZone(Rect2i bounds, int groupId, int gridIndex) {}

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
        super(menu, inventory, Component.translatable("item.cell4.cell_configurator"), MAIN_W + SIDEBAR_W, 280);
        this.inventoryLabelY = -9999; // Hide default inventory label off-screen
        this.titleLabelY = -9999; // Hide default title label off-screen

        // Initialize groups
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
        int fieldW = CONTENT_W - 24 - 14 - SCROLLBAR_W - 4; // 24 for seq number, 14 for X button, scrollbar
        EditBox field = new EditBox(this.font, fieldX, 0, fieldW, 12, Component.literal("entry_" + index));
        field.setMaxLength(1024);
        field.setBordered(false);
        field.setTextColor(0xFFFFFFFF);
        field.setTextColorUneditable(0xFFFFFFFF);
        field.setValue(value != null ? value : "");
        // 工作台 UI 不使用条目输入框：创建时就必须隐藏，否则默认坐标 (0,0) 会在屏幕顶部渲染白色文字。
        field.visible = uiMode != UI_MODE_WORKBENCH;
        return field;
    }

    /** 当前 UI 实际使用的窗口尺寸（imageWidth/imageHeight 为 final，只能按模式计算）。 */
    private int uiWidth() {
        return uiMode == UI_MODE_WORKBENCH ? SIDEBAR_W + PANEL_W + WORKBENCH_SIDE_W : SIDEBAR_W + MAIN_W;
    }

    private int uiHeight() {
        return uiMode == UI_MODE_WORKBENCH ? PANEL_H : MAIN_H;
    }

    /** 按当前 UI 模式重新计算界面原点（两种 UI 分别居中）。 */
    private void applyLayoutForMode() {
        if (uiMode == UI_MODE_WORKBENCH) {
            this.leftPos = (this.width - (SIDEBAR_W + PANEL_W + WORKBENCH_SIDE_W)) / 2;
            this.topPos = (this.height - PANEL_H) / 2;
        } else {
            this.leftPos = (this.width - (SIDEBAR_W + MAIN_W)) / 2;
            this.topPos = (this.height - MAIN_H) / 2;
        }
        applySlotLayout();
        if (this.nameField != null) {
            this.nameField.visible = uiMode == UI_MODE_LIST;
        }
    }

    /** 材质名称输入框（所有元件都可编辑，用于自定义材质）。 */
    private void applyTextureFieldValue(ItemStack cell) {
        if (this.textureField == null) return;
        String value = cell.isEmpty() || !(cell.getItem() instanceof IInfinityCell c) ? "" : c.getTextureName(cell);
        this.textureField.setValue(value);
        this.textureField.setEditable(!cell.isEmpty());
        this.snapshotTexture = value;
    }

    private void drawInputBorder(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + 1, 0xFFFFFFFF);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFFFFFFFF);
        graphics.fill(x, y, x + 1, y + h, 0xFFFFFFFF);
        graphics.fill(x + w - 1, y, x + w, y + h, 0xFFFFFFFF);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x80000000);
    }

    /**
     * 客户端槽位换位：Slot 的 x/y 是 final，无法直接改坐标，
     * 因此用包装槽位替换 menu.slots 中的条目（服务端仍保留自己的槽位，
     * 点击只按 index 同步，坐标纯客户端表现）。
     */
    private void applySlotLayout() {
        List<net.minecraft.world.inventory.Slot> slots = this.menu.slots;
        if (slots.size() < 37) return;
        if (baseSlots == null) baseSlots = new ArrayList<>(slots);
        boolean wb = uiMode == UI_MODE_WORKBENCH;

        for (int i = 0; i < baseSlots.size(); i++) {
            if (!wb) {
                // UI1 使用 CellConfiguratorMenu 中原本的槽位坐标
                slots.set(i, baseSlots.get(i));
                continue;
            }
            int x;
            int y;
            if (i == 0) {
                x = SIDEBAR_W + PANEL_CELL_X;
                y = PANEL_CELL_Y;
            } else if (i <= 27) {
                int k = i - 1;
                x = SIDEBAR_W + PANEL_INV_X + (k % 9) * 18;
                y = PANEL_INV_Y + (k / 9) * 18;
            } else if (i <= 36) {
                x = SIDEBAR_W + PANEL_INV_X + (i - 28) * 18;
                y = PANEL_HOTBAR_Y;
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
        public boolean mayPlace(ItemStack stack) { return delegate.mayPlace(stack); }

        @Override
        public boolean mayPickup(net.minecraft.world.entity.player.Player player) { return delegate.mayPickup(player); }

        @Override
        public int getMaxStackSize() { return delegate.getMaxStackSize(); }

        @Override
        public int getMaxStackSize(ItemStack stack) { return delegate.getMaxStackSize(stack); }

        @Override
        public net.minecraft.resources.Identifier getNoItemIcon() { return delegate.getNoItemIcon(); }

        @Override
        public ItemStack getItem() { return delegate.getItem(); }

        @Override
        public boolean hasItem() { return delegate.hasItem(); }

        @Override
        public void set(ItemStack stack) { delegate.set(stack); }

        @Override
        public void setByPlayer(ItemStack stack) { delegate.setByPlayer(stack); }

        @Override
        public void setByPlayer(ItemStack stack, ItemStack previous) { delegate.setByPlayer(stack, previous); }

        @Override
        public void setChanged() { delegate.setChanged(); }

        @Override
        public ItemStack remove(int amount) { return delegate.remove(amount); }

        @Override
        public void onTake(net.minecraft.world.entity.player.Player player, ItemStack carried) {
            delegate.onTake(player, carried);
        }

        @Override
        public boolean isActive() { return delegate.isActive(); }

        @Override
        public boolean isHighlightable() { return delegate.isHighlightable(); }
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
        return mx < xo || my < yo || mx >= xo + uiWidth() || my >= yo + uiHeight();
    }

    @Override
    protected void init() {
        super.init();
        applyLayoutForMode();

        int x = this.leftPos;
        int y = this.topPos;

        // Name field next to item slot (slot is at x+8, y+22; name starts at x+40)
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

        // Rebuild all group fields
        for (EntryGroup group : groups) {
            group.rebuildFields();
        }
        // 新建/重建的字段必须先按当前 UI 模式摆位或隐藏，否则会渲染在屏幕左上角。
        updateFieldPositions();

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

                // Save snapshot for reset
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
                        applyLayoutForMode();
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
                snapshotTexture = "";
                gridItems.clear();
                gridOtherItems.clear();
                snapshotPower = new ArrayList<>();
                boolean wasWorkbench = uiMode == UI_MODE_WORKBENCH;
                uiMode = UI_MODE_LIST;
                applyLayoutForMode();
                if (wasWorkbench) {
                    this.rebuildWidgets();
                    return;
                }
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
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

        int mainX = this.leftPos + SIDEBAR_W;
        renderSidebar(graphics, mouseX, mouseY);

        if (uiMode == UI_MODE_WORKBENCH) {
            // 直接使用 AE2 原版元件工作台背景（176x251）
            graphics.blit(RenderPipelines.GUI_TEXTURED, AE2_CELLWORKBENCH,
                    mainX, this.topPos, 0, 0, PANEL_W, PANEL_H, 256, 256);
            // 右侧控制栏底色 + 左边线 + 材质输入框边框
            int sideX = mainX + PANEL_W;
            graphics.fill(sideX, this.topPos, sideX + WORKBENCH_SIDE_W, this.topPos + PANEL_H, 0xFFC6C6C6);
            graphics.fill(sideX, this.topPos, sideX + 1, this.topPos + PANEL_H, 0xFF8B8B8B);
            drawInputBorder(graphics, sideX + 5, this.topPos + 40, 78, 14);
            return;
        }

        // 列表编辑器背景（320x280）
        graphics.blit(RenderPipelines.GUI_TEXTURED, GUI_TEXTURE, mainX, this.topPos, 0, 0, MAIN_W, MAIN_H, 320, 280);

        // Darken the scrollable content area slightly
        graphics.fill(mainX + CONTENT_X - 2, this.topPos + CONTENT_Y - 2,
                mainX + CONTENT_X + CONTENT_W + 2, this.topPos + CONTENT_Y + VIEWPORT_H + 2,
                0x20000000);

        // Name field white border and background (must be drawn BEFORE EditBox renders its text)
        drawInputBorder(graphics, mainX + 40, this.topPos + 28, 100, 14);
        // Texture field border
        drawInputBorder(graphics, mainX + 141, this.topPos + 28, 74, 14);
    }

    /**
     * 左侧扩展栏：顶部一个 14x14 方形切换按钮（列表图标/网格图标），
     * 工作台模式下再显示保存/重置/清空三个小按钮，底部是帮助按钮。
     */
    private void renderSidebar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int sbX = this.leftPos;
        int sbH = uiHeight();

        graphics.fill(sbX, this.topPos, sbX + SIDEBAR_W, this.topPos + sbH, 0xFF3C5078);
        graphics.fill(sbX + 1, this.topPos + 1, sbX + SIDEBAR_W - 1, this.topPos + sbH - 1, 0xFFAFCAE6);

        boolean itemCell = canUseWorkbench();
        int tabX = sbX + 2;
        int tabY = this.topPos + 26;
        boolean hovered = isHoveringToggle(mouseX, mouseY);
        int border = itemCell ? 0xFF2A3E5C : 0xFF7D8FA6;
        int bg = itemCell ? (hovered ? 0xFFF2F8FF : 0xFFDCEAFA) : 0xFFB9C7D8;
        drawSidebarButton(graphics, tabX, tabY, bg, border);

        // 列表图标 / 网格图标
        int icon = itemCell ? 0xFF2A3E5C : 0xFF8899AA;
        if (uiMode == UI_MODE_LIST) {
            for (int i = 0; i < 3; i++) {
                int ly = tabY + 4 + i * 3;
                graphics.fill(tabX + 4, ly, tabX + 10, ly + 1, icon);
            }
        } else {
            graphics.fill(tabX + 4, tabY + 4, tabX + 7, tabY + 7, icon);
            graphics.fill(tabX + 8, tabY + 4, tabX + 11, tabY + 7, icon);
            graphics.fill(tabX + 4, tabY + 8, tabX + 7, tabY + 11, icon);
            graphics.fill(tabX + 8, tabY + 8, tabX + 11, tabY + 11, icon);
        }

        // 工作台模式下：保存 / 重置 / 清空
        if (uiMode == UI_MODE_WORKBENCH) {
            drawSidebarActionButton(graphics, 1, "R", 0xFF2A3E5C, mouseX, mouseY);
            drawSidebarActionButton(graphics, 2, "C", 0xFF6E2A2A, mouseX, mouseY);
        }

        // 底部帮助按钮
        int helpX = sbX + 3;
        int helpY = this.topPos + sbH - 14;
        graphics.text(this.font, "?", helpX, helpY,
                isHoveringHelp(mouseX, mouseY) ? 0xFF2A3E5C : 0xFF5A6E88, true);
    }

    private void drawSidebarButton(GuiGraphicsExtractor graphics, int x, int y, int fill, int border) {
        graphics.fill(x, y, x + 14, y + 14, border);
        graphics.fill(x + 1, y + 1, x + 13, y + 13, fill);
    }

    private void drawSidebarActionButton(GuiGraphicsExtractor graphics, int index, String glyph,
                                         int color, int mouseX, int mouseY) {
        int bx = this.leftPos + 2;
        int by = this.topPos + 48 + index * 18;
        boolean hovered = isHoveringSidebarAction(mouseX, mouseY, index);
        drawSidebarButton(graphics, bx, by, hovered ? 0xFFF2F8FF : 0xFFDCEAFA, 0xFF2A3E5C);
        graphics.centeredText(this.font, glyph, bx + 7, by + 4, color);
    }


    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int x, int y) {
        // Do not render default title and inventory labels - we handle all rendering ourselves
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // 元件槽也要像原版一样显示物品 Tooltip（预览面板固定显示在右侧）。
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
        int x = this.leftPos + SIDEBAR_W;
        int y = this.topPos;
        ItemStack targetCell = this.menu.getCellInSlot();
        boolean workbench = uiMode == UI_MODE_WORKBENCH;

        // Title text (top-left, like 1.20.1/1.21.1)
        graphics.text(this.font, this.title, x + 8, y + 6, workbench ? 0xFF404040 : 0xFF2A3E5C, false);

        // Help tooltip (button itself is drawn in the sidebar background)
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
            graphics.setTooltipForNextFrame(this.font, helpLines, Optional.empty(), mouseX, mouseY);
        }

        // Layout toggle tooltip
        if (isHoveringToggle(mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(this.font, Component.translatable("gui.cell4.toggle_ui"), mouseX, mouseY);
        }

        if (workbench) {
            // AE2 风格：标题栏已画，直接渲染配置网格与物品栏标题
            renderWorkbenchContent(graphics, mouseX, mouseY);
            graphics.text(this.font, Component.translatable("container.inventory"),
                    x + 8, y + PANEL_INV_Y - 12, 0xFF404040, false);
        } else {
            // Name label (next to item slot)
            graphics.text(this.font, Component.translatable("gui.cell4.cell_name"), x + 40, y + 18,
                    !targetCell.isEmpty() && CellConfiguratorMenu.canEditName(targetCell) ? 0xFF2A3E5C : 0xFF8899AA, false);
            graphics.text(this.font, Component.translatable("gui.cell4.cell_texture"), x + 141, y + 18,
                    targetCell.isEmpty() ? 0xFF8899AA : 0xFF2A3E5C, false);

            // Name field border and background are now drawn in extractBackground (before EditBox text renders)

            // Save, Reset, Clear buttons area (top right)
            int btnX = x + 215;
            int btnY = y + 6;
            renderMiniButton(graphics, btnX - 34, btnY, 30, 12, Component.translatable("gui.cell4.open_folder"), 0xFF2A3E5C);
            renderMiniButton(graphics, btnX, btnY, 30, 12, Component.translatable("gui.cell4.save"), 0xFF2A6E2A);
            renderMiniButton(graphics, btnX + 34, btnY, 30, 12, Component.translatable("gui.cell4.reset"), 0xFF2A3E5C);
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
        }

        // === Tooltip Preview Panel (right side of GUI) ===
        ItemStack previewCell = buildLivePreviewStack(this.menu.getCellInSlot());
        if (!previewCell.isEmpty()) {
            int previewX = this.leftPos + uiWidth() + 6;
            int previewY = y + 18;

            // Small label above the preview tooltip
            graphics.text(this.font, Component.translatable("gui.cell4.preview"), previewX, y + 4, 0xFF888888, false);

            // 与原版一致：整块预览（文本 + 元件/内容图标网格）由带背景的 Tooltip 绘制
            graphics.tooltip(this.font, buildPreviewTooltipComponents(previewCell),
                    previewX, previewY, DefaultTooltipPositioner.INSTANCE, null);
        }
    }

    private void enableScissorForContent(GuiGraphicsExtractor graphics) {
        int scissorX = this.leftPos + SIDEBAR_W + CONTENT_X - 2;
        int scissorY = this.topPos + CONTENT_Y - 2;
        int scissorW = CONTENT_W + 4;
        int scissorH = VIEWPORT_H + 4;
        graphics.enableScissor(scissorX, scissorY, scissorX + scissorW, scissorY + scissorH);
    }

    private void disableScissor(GuiGraphicsExtractor graphics) {
        graphics.disableScissor();
    }

    private void renderContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (uiMode == UI_MODE_WORKBENCH) {
            renderWorkbenchContent(graphics, mouseX, mouseY);
            return;
        }
        int x = this.leftPos + SIDEBAR_W;
        int baseY = this.topPos + CONTENT_Y - scrollOffset;
        int currentY = baseY;

        ItemStack targetCell = this.menu.getCellInSlot();

        for (EntryGroup group : groups) {
            boolean isPower = group.groupId == GROUP_POWER;
            if (isPower && !isPortableCell()) {
                continue;
            }
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

                if (!isPower) {
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

                        if (isPower) {
                            String label = switch (i) {
                                case 0 -> Component.translatable("gui.cell4.power_max").getString();
                                case 1 -> Component.translatable("gui.cell4.power_cost").getString();
                                default -> Component.translatable("gui.cell4.power_charge").getString();
                            };
                            graphics.text(this.font, label, x + CONTENT_X + 4, entryY + 3,
                                    group.isEditable() ? 0xFFFFFFFF : 0xFF8899AA, true);
                        } else {
                            // Sequence number（黑色带阴影，与 1.20.1/1.21.1 一致）
                            String seqNum = (i + 1) + ".";
                            graphics.text(this.font, seqNum, x + CONTENT_X + 4, entryY + 3, 0xFF000000, true);

                            // "X" delete button
                            String xText = "x";
                            int xBtnX = x + CONTENT_X + CONTENT_W - SCROLLBAR_W - 12;
                            boolean xHovered = isHoveringX(mouseX, mouseY, xBtnX, entryY);
                            int xColor = xHovered ? 0xFFFF5555 : 0xFF995555;
                            graphics.text(this.font, xText, xBtnX, entryY + 3, xColor, true);
                        }
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
        // 按新 UI 重新计算窗口原点与本 UI 的控件位置
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
        List<String> unique = new ArrayList<>(new java.util.LinkedHashSet<>(gridItems));
        gridItems.clear();
        gridItems.addAll(unique);
        groups.get(GROUP_ITEMS).setEntries(mergedGridItems());
        // 重建的 EditBox 在工作台模式下必须立即隐藏，避免默认坐标渲染。
        updateFieldPositions();
    }

    /** 已绑定物品的去重数量（与 AE 终端/Tooltip 图标一致）。 */
    private int uniqueGridCount() {
        return new java.util.LinkedHashSet<>(gridItems).size();
    }

    private static ItemStack itemStackFromId(String id) {
        if (id == null || id.isEmpty()) return null;
        String value = id.trim();
        if (value.isEmpty()) return null;
        // 非物品条目（流体/正则/glob 等）
        if (value.startsWith("mekfluid:") || value.startsWith("mekchemical:")
                || value.startsWith("energy:") || value.startsWith("regex:") || value.startsWith("glob:")) {
            return null;
        }
        // 与原版 /give 一致：纯 ID、id{...}、id[...]、{id:...,components:{...}}
        return Cell4Util.parseItemStackEntry(value);
    }

    /**
     * 把物品堆转成绑定条目：没有额外组件（与默认物品一致）时写纯 ID，
     * 否则写完整 compound（{@code {id:"...",count:1,components:{...}}}），保留数据组件。
     */
    public static String itemEntryFromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Identifier id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return null;
        String plainId = id.toString();
        if (stack.isComponentsPatchEmpty()) return plainId;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return plainId;
        try {
            RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, minecraft.level.registryAccess());
            return ItemStack.CODEC.encodeStart(ops, stack).result().map(Object::toString).orElse(plainId);
        } catch (Exception ignored) {
            return plainId;
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
            if (value != null) {
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

    private void renderWorkbenchContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
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

            graphics.blit(RenderPipelines.GUI_TEXTURED, AE2_STATES, sx, sy, 192, 192, 18, 18, 256, 256);

            int absolute = offset + index;
            if (absolute < gridItems.size()) {
                ItemStack stack = itemStackFromId(gridItems.get(absolute));
                if (stack != null) {
                    graphics.item(stack, sx + 1, sy + 1);
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
        graphics.text(this.font, Component.translatable("gui.cell4.workbench_controls"),
                cx, py + 6, 0xFF404040, false);
        graphics.text(this.font, Component.translatable("gui.cell4.cell_texture"),
                cx, py + 30, 0xFF404040, false);
        // 材质输入框边框由 extractBackground 画
        renderMiniButton(graphics, cx, py + 58, contentW, 12,
                Component.translatable("gui.cell4.open_folder"), 0xFF2A3E5C);
        renderMiniButton(graphics, cx, py + 72, contentW, 12,
                Component.translatable("gui.cell4.reset"), 0xFF2A3E5C);
        renderMiniButton(graphics, cx, py + 86, contentW, 12,
                Component.translatable("gui.cell4.clear"), 0xFF6E2A2A);

        // 翻页
        renderMiniButton(graphics, cx, py + 102, 20, 12, Component.literal("<"), 0xFF2A3E5C);
        renderMiniButton(graphics, cx + 50, py + 102, 20, 12, Component.literal(">"), 0xFF2A3E5C);
        String pageText = (workbenchPage + 1) + "/" + pageCount();
        graphics.centeredText(this.font, pageText, cx + 35, py + 104, 0xFF404040);
        String count = Component.translatable("gui.cell4.workbench_count", uniqueGridCount()).getString();
        graphics.text(this.font, count, cx, py + 118, 0xFF404040, false);

        // 其他键与提示
        graphics.text(this.font, Component.translatable("gui.cell4.workbench_other",
                        gridOtherItems.size()), cx, py + 132, 0xFF404040, false);
        int shown = 0;
        for (String entry : gridOtherItems) {
            if (shown >= 4) break;
            String text = entry;
            if (text.length() > 15) text = text.substring(0, 14) + "\u2026";
            graphics.text(this.font, text, cx, py + 144 + shown * 10, 0xFF556677, false);
            shown++;
        }
        if (gridOtherItems.size() > shown) {
            graphics.text(this.font, "...", cx, py + 144 + shown * 10, 0xFF8899AA, false);
        }
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
        if (uiMode == UI_MODE_WORKBENCH) {
            List<String> merged = new ArrayList<>(gridItems);
            merged.addAll(gridOtherItems);
            return merged;
        }
        return collectNonEmpty(GROUP_ITEMS);
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
        List<ClientTooltipComponent> components = buildPreviewTooltipComponents(previewCell);
        // 与原版 Tooltip 相同：宽度取最宽行，高度为各行高度之和（单行少 2px）
        int textWidth = 0;
        int textHeight = components.size() == 1 ? -2 : 0;
        for (ClientTooltipComponent component : components) {
            textWidth = Math.max(textWidth, component.getWidth(this.font));
            textHeight += component.getHeight(this.font);
        }
        int previewX = this.leftPos + uiWidth() + 6;
        int previewY = this.topPos + 18;
        Vector2ic pos = DefaultTooltipPositioner.INSTANCE.positionTooltip(
                this.width, this.height, previewX, previewY, textWidth, textHeight);
        // TooltipRenderUtil 以 (x - 12, y - 12) 为起点、尺寸 (w + 24, h + 24)
        return new Rect2i(pos.x() - 12, pos.y() - 12, textWidth + 24, textHeight + 24);
    }

    /**
     * 构建预览 Tooltip 的组件列表：文本行 + 图标网格（元件图标与最多 6 个内容物品）。
     * 图标插在第一行之后，与 AE2 元件 Tooltip 的排布一致。
     */
    private List<ClientTooltipComponent> buildPreviewTooltipComponents(ItemStack previewCell) {
        boolean advanced = this.minecraft.options.advancedItemTooltips;
        if (previewComponentsCache != null
                && previewComponentsStack == previewCell
                && previewComponentsAdvanced == advanced) {
            return previewComponentsCache;
        }

        List<Component> tooltipLines = new ArrayList<>(previewCell.getTooltipLines(
                net.minecraft.world.item.Item.TooltipContext.of(this.minecraft.level),
                this.minecraft.player,
                advanced ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL));

        // Apply pending name from the name field
        String pendingName = this.nameField.getValue();
        if (!pendingName.isEmpty() && !tooltipLines.isEmpty()) {
            // Preserve the original rarity color but add italic for custom name
            Style originalStyle = tooltipLines.get(0).getStyle();
            tooltipLines.set(0, Component.literal(pendingName).withStyle(originalStyle.withItalic(true)));
        }

        List<ClientTooltipComponent> components = new ArrayList<>();
        for (Component line : tooltipLines) {
            components.add(ClientTooltipComponent.create(line.getVisualOrderText()));
        }
        components.add(Math.min(1, components.size()),
                new PreviewIconGrid(previewCell, previewContentStacks(previewCell), previewContentCount(previewCell)));

        previewComponentsStack = previewCell;
        previewComponentsAdvanced = advanced;
        previewComponentsCache = components;
        return components;
    }

    /** 预览网格中的内容物品：ITEM 元件的前 6 个可解析条目，其他元件为空。 */
    private static List<ItemStack> previewContentStacks(ItemStack stack) {
        List<ItemStack> stacks = new ArrayList<>();
        if (!(stack.getItem() instanceof IInfinityCell cell)
                || cell.getCellType() != IInfinityCell.CellType.ITEM) {
            return stacks;
        }
        for (String id : new java.util.LinkedHashSet<>(InfinityItemCell.getIdentifiers(stack))) {
            if (stacks.size() >= PREVIEW_ICONS_PER_ROW) break;
            ItemStack contentStack = itemStackFromId(id);
            if (contentStack != null) stacks.add(contentStack);
        }
        return stacks;
    }

    /** 预览网格要显示的绑定条目总数（用于在图标排满后显示 "..."）。 */
    private static int previewContentCount(ItemStack stack) {
        if (!(stack.getItem() instanceof IInfinityCell cell)
                || cell.getCellType() != IInfinityCell.CellType.ITEM) {
            return 0;
        }
        return new java.util.LinkedHashSet<>(InfinityItemCell.getIdentifiers(stack)).size();
    }

    /**
     * 画在 Tooltip 背景内部的图标网格：第一行是元件本身，第二行是内容物品（单行最多 6 个，
     * 与 1.21.1/AE2 的一排布局一致）；内容超出时在末尾显示 "..."。
     */
    private static final class PreviewIconGrid implements ClientTooltipComponent {
        private final ItemStack cell;
        private final List<ItemStack> content;
        private final int total;

        PreviewIconGrid(ItemStack cell, List<ItemStack> content, int total) {
            this.cell = cell;
            this.content = content;
            this.total = total;
        }

        private boolean hasMore() {
            return this.total > this.content.size();
        }

        @Override
        public int getHeight(Font font) {
            return this.content.isEmpty() ? 18 : 36;
        }

        @Override
        public int getWidth(Font font) {
            if (this.content.isEmpty()) return 18;
            int slots = Math.min(this.content.size(), PREVIEW_ICONS_PER_ROW);
            if (hasMore()) slots = Math.min(slots + 1, PREVIEW_ICONS_PER_ROW + 1);
            return 18 * slots;
        }

        @Override
        public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
            graphics.item(this.cell, x, y);
            int shown = Math.min(this.content.size(), PREVIEW_ICONS_PER_ROW);
            for (int i = 0; i < shown; i++) {
                graphics.item(this.content.get(i), x + i * 18, y + 18);
            }
            if (hasMore()) {
                graphics.text(font, "...", x + shown * 18 + 3, y + 22, 0xFFFFFFFF, true);
            }
        }
    }

    /** 当前 UI 的拖放区域（屏幕坐标），供 JEI/REI 与原版拖拽使用。 */
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

    /** 接受一个物品 ID（来自原版拖拽或 JEI/REI 拖拽）。 */
    public void acceptDrop(String itemId, double mouseX, double mouseY) {
        if (itemId == null || itemId.isEmpty()) return;
        for (DropZone zone : getDropZones()) {
            if (!zone.bounds().contains((int) mouseX, (int) mouseY)) continue;
            if (zone.gridIndex() >= 0) {
                ItemStack dropped = itemStackFromId(itemId);
                String entry = dropped != null ? itemEntryFromStack(dropped) : itemId;
                int absolute = pageOffset() + zone.gridIndex();
                if (absolute < gridItems.size()) {
                    gridItems.set(absolute, entry);
                } else if (!gridItems.contains(entry)) {
                    gridItems.add(entry);
                }
                onSave();
            } else if (zone.groupId() >= 0) {
                EntryGroup group = groups.get(zone.groupId());
                if (group.isEditable() && itemStackFromId(itemId) != null) {
                    group.addEntry(itemId);
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
        if (source == null || source.isEmpty() || !(source.getItem() instanceof IInfinityCell cell)) {
            previewCacheValid = false;
            return ItemStack.EMPTY;
        }

        IInfinityCell.CellType type = cell.getCellType();
        List<String> items = type == IInfinityCell.CellType.ITEM ? currentItemEntries() : List.of();
        List<String> tags = type == IInfinityCell.CellType.TAG ? collectNonEmpty(GROUP_TAGS) : List.of();
        List<String> modIds = type == IInfinityCell.CellType.TAG || type == IInfinityCell.CellType.MODID
                ? collectNonEmpty(GROUP_MODIDS) : List.of();
        List<String> power = cell.isPortable() ? collectNonEmpty(GROUP_POWER) : List.of();
        String name = this.nameField != null ? this.nameField.getValue() : "";
        String texture = this.textureField != null ? this.textureField.getValue() : "";
        Object sourceData = source.get(DataComponents.CUSTOM_DATA);

        // 元件 NBT 与编辑内容都没变时复用上一帧的预览，避免每帧 copyTag 深拷贝。
        if (previewCacheValid
                && previewSourceItem == source.getItem()
                && Objects.equals(previewSourceData, sourceData)
                && previewItems.equals(items)
                && previewTags.equals(tags)
                && previewModIds.equals(modIds)
                && previewPower.equals(power)
                && previewName.equals(name)
                && previewTexture.equals(texture)) {
            return previewStackCache;
        }

        ItemStack copy = source.copy();
        switch (type) {
            case ITEM -> InfinityItemCell.setIdentifiers(copy, items);
            case TAG -> {
                InfinityTagCell.setTagNames(copy, tags);
                InfinityTagCell.setModIds(copy, modIds);
            }
            case MODID -> InfinityModIdCell.setModIds(copy, modIds);
        }
        cell.setCustomName(copy, name);
        cell.setTextureName(copy, texture);
        if (cell.isPortable() && power.size() >= 3) {
            PortableCellPower.setOverrides(copy, power.get(0), power.get(1), power.get(2));
        }

        previewStackCache = copy;
        previewSourceItem = source.getItem();
        previewSourceData = sourceData;
        previewItems = List.copyOf(items);
        previewTags = List.copyOf(tags);
        previewModIds = List.copyOf(modIds);
        previewPower = List.copyOf(power);
        previewName = name;
        previewTexture = texture;
        previewCacheValid = true;
        return copy;
    }

    private void renderScrollbar(GuiGraphicsExtractor graphics) {
        if (uiMode == UI_MODE_WORKBENCH) return;
        int totalH = calcTotalContentHeight();
        if (totalH <= VIEWPORT_H) return;

        int x = this.leftPos + SIDEBAR_W + SCROLLBAR_X_OFFSET;
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
        int helpX = this.leftPos + 3;
        int helpY = this.topPos + uiHeight() - 14;
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

    // --- Button action helpers ---

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
            if (isHoveringSidebarAction(mouseX, mouseY, 1)) { onReset(); return true; }
            if (isHoveringSidebarAction(mouseX, mouseY, 2)) { onClear(); return true; }
            // 右侧控制栏按钮
            int cx = getSideX() + 5;
            int cw = WORKBENCH_SIDE_W - 10;
            int py = this.topPos;
            if (mouseX >= cx && mouseX <= cx + cw) {
                if (mouseY >= py + 58 && mouseY <= py + 70) { Cell4ClientHooks.openTextureFolder(); return true; }
                if (mouseY >= py + 72 && mouseY <= py + 84) { onReset(); return true; }
                if (mouseY >= py + 86 && mouseY <= py + 98) { onClear(); return true; }
            }
            return false;
        }
        int btnX = this.leftPos + SIDEBAR_W + 215;
        int btnY = this.topPos + 6;
        int btnW = 30, btnH = 12;

        // Open texture folder button
        if (mouseX >= btnX - 34 && mouseX <= btnX - 34 + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            Cell4ClientHooks.openTextureFolder();
            return true;
        }
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
        // 保存时顺便扫描材质文件夹，确保 F3+T 前材质包已生成。
        Cell4Config.refresh();
        ItemStack targetCell = this.menu.getCellInSlot();
        if (targetCell.isEmpty()) return;

        // Collect values from groups (workbench grid included)
        if (uiMode == UI_MODE_WORKBENCH) commitGridToGroup();
        syncEntriesFromFields();
        EntryGroup itemsGroup = groups.get(GROUP_ITEMS);
        List<String> uniqueItems = new ArrayList<>(new java.util.LinkedHashSet<>(itemsGroup.entries));
        itemsGroup.entries.clear();
        itemsGroup.entries.addAll(uniqueItems);
        List<String> power = groups.get(GROUP_POWER).entries;
        String powerMax = power.size() > 0 ? power.get(0) : "";
        String powerCost = power.size() > 1 ? power.get(1) : "";
        String powerCharge = power.size() > 2 ? power.get(2) : "";

        ClientPacketDistributor.sendToServer(new CellConfigSavePacket(
            sanitizeEntries(itemsGroup.entries),
            sanitizeEntries(groups.get(GROUP_TAGS).entries),
            sanitizeEntries(groups.get(GROUP_MODIDS).entries),
            sanitizeEntries(groups.get(GROUP_BLACKLIST).entries),
            this.nameField.getValue(),
            powerMax, powerCost, powerCharge,
            this.textureField != null ? this.textureField.getValue() : ""
        ));
    }

    /** 去掉首尾空白与空条目，避免服务端把空串当成非法 id。 */
    private static List<String> sanitizeEntries(List<String> values) {
        List<String> result = new ArrayList<>(values.size());
        for (String value : values) {
            if (value == null) continue;
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
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

    // --- Input handling ---

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();

        // 光标上已有物品时，点击条目栏/网格直接添加条目，物品继续留在光标上。
        if (event.button() == 0 && !this.menu.getCarried().isEmpty()) {
            String entry = itemEntryFromStack(this.menu.getCarried());
            if (entry != null && tryAcceptDropAt(entry, mouseX, mouseY)) {
                return true;
            }
        }

        // 左侧栏顶部的方形切换按钮
        if (isHoveringToggle(mouseX, mouseY)) {
            if (canUseWorkbench()) toggleUiMode();
            return true;
        }

        // Check custom UI elements first
        if (findAndClickSaveResetClear(mouseX, mouseY)) return true;

        // Check content area clicks
        int contentTop = this.topPos + CONTENT_Y;
        int contentBottom = contentTop + VIEWPORT_H;

        if (uiMode == UI_MODE_WORKBENCH) {
            if (handleWorkbenchClick(mouseX, mouseY, 0)) return true;
            boolean result = super.mouseClicked(event, doubleClick);
            if (this.menu.getCellInSlot().isEmpty()) clearAllFieldFocus();
            return result;
        }

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
        int sbX = this.leftPos + SIDEBAR_W + SCROLLBAR_X_OFFSET;
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
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (this.textureField != null && this.textureField.isFocused()) {
            if (keyCode == 256) { this.onClose(); return true; }
            if (keyCode == 257) { onSave(); return true; }
            this.textureField.keyPressed(event);
            return true;
        }
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
        if (this.textureField != null && this.textureField.isFocused()) {
            this.textureField.charTyped(event);
            return true;
        }
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
        if (this.textureField != null) this.textureField.setFocused(false);
        for (EntryGroup group : groups) {
            for (EditBox field : group.entryFields) {
                field.setFocused(false);
            }
        }
    }
}
