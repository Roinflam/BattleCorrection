package pers.roinflam.battlecorrection.client.editor.page;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import pers.roinflam.battlecorrection.client.editor.CurioSlotsClient;
import pers.roinflam.battlecorrection.client.editor.EditorSession;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.ItemEditorScreen;
import pers.roinflam.battlecorrection.client.editor.widget.FlatButton;
import pers.roinflam.battlecorrection.client.editor.widget.FlatEditBox;
import pers.roinflam.battlecorrection.client.editor.widget.ScrollList;
import pers.roinflam.battlecorrection.compat.CurioMark;
import pers.roinflam.battlecorrection.compat.CurioSlotManager;
import pers.roinflam.battlecorrection.compat.CuriosIntegration;
import pers.roinflam.battlecorrection.editor.EditorPage;
import pers.roinflam.battlecorrection.network.ModNetwork;
import pers.roinflam.battlecorrection.network.packet.SetCurioSlotsC2SPacket;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 玩家饰品栏页：看自己有哪几种饰品栏、各几格，直接改（测试用）
 * <p>
 * 和物品无关，放在编辑器里只是图方便。列表 = 已注册的饰品栏类型 ∪ 玩家现在已有的类型，
 * 右边标注来源：数据包分配的、只是注册了还没给玩家的、自定义的。
 * 选中一行后用 - / + 或直接输数字改数量，改动发给服务端处理（OP + 创造），服务端同步回来后列表自己刷新。
 * 想加一种没注册的类型（开发环境里连 Curios 自带的 10 种都可能没注册），在下面输 ID 点"添加"。
 * <p>
 * 服务端会把编辑器设过的数量记在玩家数据里，重进后恢复；但 Curios 重进时只按数据包重建饰品栏，
 * 现加的类型里放的物品会先退回背包，恢复出来的是空格子。
 * <p>
 * 前提：玩家得先"有饰品栏能力"。Curios 只给至少被分配了一种饰品栏的实体装能力，一种都没有的玩家这页什么都改不了，
 * 会显示提示让你打开配置 devCurioSlots（模组自带的分配文件，给 10 种默认饰品栏）再 /reload。
 */
public class PlayerSlotsPage extends EditorPageBase {

    private static final int BOX_HEIGHT = 14;
    private static final int FORM_HEIGHT = 2 * BOX_HEIGHT + 8;
    private static final int GAP = 4;
    private static final int REFRESH_INTERVAL = 10;

    /**
     * 一种饰品栏
     */
    private static final class Row implements ScrollList.Row {
        final String id;
        final Component name;
        int count;
        boolean assigned;
        boolean registered;

        Row(@Nonnull String id) {
            this.id = id;
            this.name = CurioMark.slotName(id);
        }

        @Nonnull
        @Override
        public Component label() {
            return Component.literal("").append(name).append(Component.literal("  " + id).withStyle(style -> style.withColor(EditorTheme.TEXT_DIM & 0xFFFFFF)));
        }

        @Nullable
        @Override
        public Component secondary() {
            String source = assigned ? "gui.battlecorrection.editor.slots.source.datapack"
                    : (registered ? "gui.battlecorrection.editor.slots.source.registered"
                    : "gui.battlecorrection.editor.slots.source.custom");
            return Component.literal("×" + count + "  ").append(Component.translatable(source));
        }

        @Override
        public int color() {
            return count > 0 ? EditorTheme.TEXT : EditorTheme.TEXT_MUTED;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private Component status = Component.empty();
    private int statusColor = EditorTheme.TEXT_DIM;
    private int ticks;

    @Nullable
    private ScrollList<Row> list;
    @Nullable
    private FlatEditBox countBox;
    @Nullable
    private FlatButton minusButton;
    @Nullable
    private FlatButton plusButton;
    @Nullable
    private FlatButton applyButton;
    @Nullable
    private FlatEditBox customBox;
    private int formTop;

    /**
     * @param screen  所属界面
     * @param session 编辑会话
     */
    public PlayerSlotsPage(@Nonnull ItemEditorScreen screen, @Nonnull EditorSession session) {
        super(screen, session);
    }

    @Nonnull
    @Override
    public EditorPage page() {
        return EditorPage.PLAYER_SLOTS;
    }

    @Override
    public boolean wantsPreview() {
        return false;
    }

    @Override
    protected void build() {
        if (!CuriosIntegration.isCuriosLoaded() || !hasInventory()) {
            return;
        }
        formTop = y + height - FORM_HEIGHT;
        int listTop = y + 11;

        list = add(new ScrollList<Row>(x, listTop, width, formTop - 4 - listTop,
                Component.translatable("gui.battlecorrection.editor.slots.empty"))
                .onSelect(this::onRowSelected));
        refreshRows();

        // 第一行：数量 [-] [框] [+] [应用]
        int row1 = formTop + 2;
        int labelWidth = Math.max(font.width(Component.translatable("gui.battlecorrection.editor.slots.count")),
                font.width(Component.translatable("gui.battlecorrection.editor.slots.custom"))) + 6;
        int cursorX = x + labelWidth;
        minusButton = add(new FlatButton(cursorX, row1, BOX_HEIGHT, BOX_HEIGHT, Component.literal("-"),
                () -> adjust(-1)));
        cursorX += BOX_HEIGHT + GAP;
        countBox = add(new FlatEditBox(font, cursorX + 1, row1, 40, BOX_HEIGHT, Component.literal("0"), false));
        countBox.setMaxLength(3);
        countBox.setFilter(text -> text.isEmpty() || text.matches("\\d+"));
        cursorX += 40 + GAP + 2;
        plusButton = add(new FlatButton(cursorX, row1, BOX_HEIGHT, BOX_HEIGHT, Component.literal("+"),
                () -> adjust(1)));
        cursorX += BOX_HEIGHT + GAP * 2;
        applyButton = add(new FlatButton(cursorX, row1, 48, BOX_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.slots.apply"), this::applyCount)
                .style(FlatButton.Style.PRIMARY)
                .tooltip(Component.translatable("gui.battlecorrection.editor.slots.apply.tooltip")));

        // 第二行：自定义 ID [框........] [添加]
        int row2 = formTop + BOX_HEIGHT + 6;
        int addWidth = 48;
        customBox = add(new FlatEditBox(font, x + labelWidth + 1, row2, width - labelWidth - addWidth - GAP - 2,
                BOX_HEIGHT, Component.translatable("gui.battlecorrection.editor.slots.custom.hint"), false));
        customBox.setMaxLength(32);
        customBox.setFilter(text -> text.matches("[a-z0-9_.\\-]*"));
        add(new FlatButton(x + width - addWidth, row2, addWidth, BOX_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.slots.add"), this::addCustom)
                .tooltip(Component.translatable("gui.battlecorrection.editor.slots.add.tooltip")));

        setFormEnabled(false);
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!CuriosIntegration.isCuriosLoaded()) {
            paragraph(g, Component.translatable("gui.battlecorrection.editor.curios.not_loaded"), x, y, width,
                    EditorTheme.TEXT_MUTED);
            return;
        }
        if (!hasInventory()) {
            int bottom = y + paragraph(g, Component.translatable("gui.battlecorrection.editor.slots.no_inventory"),
                    x, y, width, EditorTheme.WARNING);
            paragraph(g, Component.translatable("gui.battlecorrection.editor.slots.no_inventory.fix"), x, bottom + 4,
                    width, EditorTheme.TEXT);
            return;
        }
        label(g, Component.translatable("gui.battlecorrection.editor.slots.header"), x, y);
        int statusWidth = font.width(status);
        if (statusWidth > 0) {
            g.drawString(font, status, x + width - statusWidth, y, statusColor, false);
        }

        EditorTheme.divider(g, x, formTop - 2, width);
        int row1 = formTop + 2 + (BOX_HEIGHT - 8) / 2;
        int row2 = formTop + BOX_HEIGHT + 6 + (BOX_HEIGHT - 8) / 2;
        label(g, Component.translatable("gui.battlecorrection.editor.slots.count"), x, row1);
        label(g, Component.translatable("gui.battlecorrection.editor.slots.custom"), x, row2);
    }

    @Override
    public void tick() {
        if (list == null) {
            return;
        }
        // 服务端同步过来后列表自己刷新；十个 tick 看一次就够
        if (++ticks % REFRESH_INTERVAL == 0) {
            refreshRows();
        }
    }

    /**
     * 本地玩家有没有饰品栏能力
     *
     * @return true = 有
     */
    private static boolean hasInventory() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && CurioSlotsClient.hasInventory(player);
    }

    /**
     * 重建行：已注册的类型 ∪ 玩家已有的类型
     */
    private void refreshRows() {
        if (list == null) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        Map<String, Integer> counts = player == null ? Map.of() : CurioSlotsClient.slotCounts(player);
        Set<String> assigned = CurioSlotsClient.assignedSlotIds();
        Set<String> registered = new TreeSet<>(CurioSlotsClient.slotIds());

        Set<String> ids = new TreeSet<>(registered);
        ids.addAll(counts.keySet());

        Row selected = list.selectedItem();
        String selectedId = selected == null ? null : selected.id;

        rows.clear();
        for (String id : ids) {
            Row row = new Row(id);
            row.count = counts.getOrDefault(id, 0);
            row.assigned = assigned.contains(id);
            row.registered = registered.contains(id);
            rows.add(row);
        }
        list.setItems(rows);

        if (selectedId != null) {
            for (Row row : rows) {
                if (row.id.equals(selectedId)) {
                    list.select(row);
                    break;
                }
            }
        }
    }

    /**
     * 选中一行：把数量填进框
     *
     * @param row 行
     */
    private void onRowSelected(@Nonnull Row row) {
        if (countBox == null) {
            return;
        }
        countBox.setValue(String.valueOf(row.count));
        setFormEnabled(true);
    }

    private void setFormEnabled(boolean enabled) {
        if (countBox == null || minusButton == null || plusButton == null || applyButton == null) {
            return;
        }
        countBox.active = enabled;
        minusButton.active = enabled;
        plusButton.active = enabled;
        applyButton.active = enabled;
    }

    /**
     * - / + 按钮：在框里的数字基础上加减并立即发送
     *
     * @param delta 增减量
     */
    private void adjust(int delta) {
        if (list == null || countBox == null) {
            return;
        }
        Row row = list.selectedItem();
        if (row == null) {
            return;
        }
        int current = parseCount(countBox.getValue(), row.count);
        int target = Mth.clamp(current + delta, 0, CurioSlotManager.MAX_SLOTS);
        countBox.setValue(String.valueOf(target));
        send(row.id, target);
    }

    /**
     * "应用"：把框里的数字发出去
     */
    private void applyCount() {
        if (list == null || countBox == null) {
            return;
        }
        Row row = list.selectedItem();
        if (row == null) {
            return;
        }
        send(row.id, Mth.clamp(parseCount(countBox.getValue(), row.count), 0, CurioSlotManager.MAX_SLOTS));
    }

    /**
     * "添加"：给自定义 ID 加 1 格
     */
    private void addCustom() {
        if (customBox == null) {
            return;
        }
        String id = customBox.getValue().trim();
        if (!CurioSlotManager.isValidId(id)) {
            status = Component.translatable("gui.battlecorrection.editor.slots.status.bad_id");
            statusColor = EditorTheme.DANGER;
            return;
        }
        send(id, 1);
    }

    private static int parseCount(@Nonnull String text, int fallback) {
        if (text.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /**
     * 发给服务端
     *
     * @param id     饰品栏 ID
     * @param amount 目标数量
     */
    private void send(@Nonnull String id, int amount) {
        ModNetwork.sendToServer(new SetCurioSlotsC2SPacket(id, amount));
        status = Component.translatable("gui.battlecorrection.editor.slots.status.sent", id, amount);
        statusColor = EditorTheme.SUCCESS;
        ticks = REFRESH_INTERVAL - 2;
    }
}
