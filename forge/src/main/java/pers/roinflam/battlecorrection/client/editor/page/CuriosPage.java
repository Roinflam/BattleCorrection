package pers.roinflam.battlecorrection.client.editor.page;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.battlecorrection.client.editor.CurioSlotsClient;
import pers.roinflam.battlecorrection.client.editor.EditorSession;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.ItemEditorScreen;
import pers.roinflam.battlecorrection.client.editor.widget.ScrollList;
import pers.roinflam.battlecorrection.compat.CurioMark;
import pers.roinflam.battlecorrection.compat.CuriosIntegration;
import pers.roinflam.battlecorrection.editor.EditorPage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 饰品栏页：给物品打饰品栏标记
 * <p>
 * 一行一种饰品栏，点一下切换勾选（对应命令 /battlecorrection curio add / remove）。列表可滚动，饰品栏再多也放得下。
 * 勾了"任意饰品栏"就哪都能放，其他行灰掉；取消"任意"后再单独勾。
 * 打了标记的物品变成"只能当饰品"：自身属性只在饰品栏生效，拿在手上、穿在身上都不给属性，防具也不能再穿。
 * <p>
 * 饰品栏专属的附加属性在"属性"页里加，生效位置选饰品栏时会自动打上对应标记，这一页不管属性。
 * 没装 Curios 时这一页只显示一句提示。
 */
public class CuriosPage extends EditorPageBase {

    private static final int FOOTER_LINES = 3;

    /**
     * 一种饰品栏
     */
    private static final class SlotRow implements ScrollList.Row {
        final String id;
        final Component name;
        boolean checked;
        boolean covered;

        SlotRow(@Nonnull String id) {
            this.id = id;
            this.name = CurioMark.ANY.equals(id)
                    ? Component.translatable("gui.battlecorrection.editor.curios.any")
                    : CurioMark.slotName(id);
        }

        @Nonnull
        @Override
        public Component label() {
            String box = checked ? "[x] " : "[ ] ";
            return Component.literal(box).append(name);
        }

        @Nullable
        @Override
        public Component secondary() {
            if (covered) {
                return Component.translatable("gui.battlecorrection.editor.curios.covered");
            }
            return CurioMark.ANY.equals(id) ? null : Component.literal(id);
        }

        @Override
        public int color() {
            if (covered) {
                return EditorTheme.TEXT_DIM;
            }
            return checked ? EditorTheme.SUCCESS : EditorTheme.TEXT;
        }
    }

    private final List<SlotRow> rows = new ArrayList<>();
    private final List<String> slotIds = new ArrayList<>();
    @Nullable
    private ScrollList<SlotRow> list;
    private int footerY;

    /**
     * @param screen  所属界面
     * @param session 编辑会话
     */
    public CuriosPage(@Nonnull ItemEditorScreen screen, @Nonnull EditorSession session) {
        super(screen, session);
    }

    @Nonnull
    @Override
    public EditorPage page() {
        return EditorPage.CURIOS;
    }

    @Override
    protected void build() {
        rows.clear();
        slotIds.clear();
        list = null;
        if (!CuriosIntegration.isCuriosLoaded()) {
            return;
        }
        slotIds.addAll(CurioSlotsClient.slotIds());
        // 物品上已经标了、但客户端不认识的饰品栏（别的服务器标的）也列出来，不然没法取消
        for (String marked : CurioMark.getSlots(session.working())) {
            if (!CurioMark.ANY.equals(marked) && !slotIds.contains(marked)) {
                slotIds.add(marked);
            }
        }

        int introHeight = font.split(Component.translatable("gui.battlecorrection.editor.curios.intro"), width).size()
                * (font.lineHeight + 1) + 6;
        int footerHeight = FOOTER_LINES * (font.lineHeight + 1) + 4;
        int listTop = y + introHeight;
        footerY = y + height - footerHeight + 2;

        list = add(new ScrollList<SlotRow>(x, listTop, width, footerY - 4 - listTop,
                Component.translatable("gui.battlecorrection.editor.curios.no_slots"))
                .onSelect(this::toggle));
        refreshRows();
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!CuriosIntegration.isCuriosLoaded()) {
            paragraph(g, Component.translatable("gui.battlecorrection.editor.curios.not_loaded"), x, y, width,
                    EditorTheme.TEXT_MUTED);
            return;
        }
        paragraph(g, Component.translatable("gui.battlecorrection.editor.curios.intro"), x, y, width, EditorTheme.TEXT_DIM);

        ItemStack working = session.working();
        List<String> slots = CurioMark.getSlots(working);
        Component status;
        int color;
        if (slots.isEmpty()) {
            status = Component.translatable("gui.battlecorrection.editor.curios.status.none");
            color = EditorTheme.TEXT_MUTED;
        } else {
            status = Component.translatable("gui.battlecorrection.editor.curios.status.marked", CurioMark.joinSlotNames(slots));
            color = EditorTheme.SUCCESS;
        }
        g.enableScissor(x, footerY, x + width, y + height);
        int bottom = footerY + paragraph(g, status, x, footerY, width, color) + 1;
        if (CurioMark.hasNativeCuriosModifiers(working)) {
            paragraph(g, Component.translatable("command.battlecorrection.curio.native_warning"), x, bottom, width,
                    EditorTheme.WARNING);
        }
        g.disableScissor();
    }

    /**
     * 点一行：切换这种饰品栏的标记
     *
     * @param row 行
     */
    private void toggle(@Nonnull SlotRow row) {
        if (row.covered) {
            return;
        }
        ItemStack working = session.working();
        if (row.checked) {
            CurioMark.removeSlot(working, row.id);
        } else {
            CurioMark.addSlot(working, row.id);
        }
        refreshRows();
    }

    /**
     * 按物品当前的标记重建行
     */
    private void refreshRows() {
        if (list == null) {
            return;
        }
        List<String> marked = CurioMark.getSlots(session.working());
        boolean any = marked.contains(CurioMark.ANY);

        SlotRow selected = list.selectedItem();
        String selectedId = selected == null ? null : selected.id;

        rows.clear();
        SlotRow anyRow = new SlotRow(CurioMark.ANY);
        anyRow.checked = any;
        rows.add(anyRow);
        for (String id : slotIds) {
            SlotRow row = new SlotRow(id);
            row.checked = !any && marked.contains(id);
            row.covered = any;
            rows.add(row);
        }
        list.setItems(rows);

        if (selectedId != null) {
            for (SlotRow row : rows) {
                if (row.id.equals(selectedId)) {
                    list.select(row);
                    break;
                }
            }
        }
    }
}
