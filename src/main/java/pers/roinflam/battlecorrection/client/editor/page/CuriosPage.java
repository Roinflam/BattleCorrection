package pers.roinflam.battlecorrection.client.editor.page;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import pers.roinflam.battlecorrection.client.editor.EditorSession;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.ItemEditorScreen;
import pers.roinflam.battlecorrection.client.editor.ui.Component;
import pers.roinflam.battlecorrection.client.editor.ui.GuiGraphics;
import pers.roinflam.battlecorrection.client.editor.widget.ScrollList;
import pers.roinflam.battlecorrection.compat.BaubleMark;
import pers.roinflam.battlecorrection.compat.BaublesIntegration;
import pers.roinflam.battlecorrection.editor.EditorPage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 饰品栏页：给物品打 Baubles 饰品栏标记
 * <p>
 * 一行一种饰品栏，点一下切换勾选（对应命令 /battlecorrection bauble add / remove）。
 * Baubles 的饰品栏类型是固定的：任意 + 护身符、戒指、腰带、头部、身体、挂饰 6 种。
 * 勾了"任意饰品栏"就哪都能放，其他行灰掉；取消"任意"后再单独勾。
 * 打了标记的物品变成"只能当饰品"：自身属性只在饰品栏生效，拿在手上、穿在身上都不给属性，防具也不能再穿。
 * 标了不止一种时对 Baubles 报"任意"类型，放进没标的格子会被挪走（见 BaubleMarkEvents）。
 * <p>
 * 饰品栏专属的附加属性在"属性"页里加，生效位置选饰品栏时会自动打上对应标记，这一页不管属性。
 * 物品本身就是饰品（实现了 Baubles 接口）时标记管不了它放哪，页脚提示一句，和命令一样。
 * 这一页只在装了 Baubles 时创建；万一没装，只显示一句提示。
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
            this.name = BaubleMark.ANY.equals(id)
                    ? Component.translatable("gui.battlecorrection.editor.curios.any")
                    : slotName(id);
        }

        @Nonnull
        @Override
        public Component label() {
            String box = checked ? "[x] " : "[ ] ";
            return Component.raw(box).append(name);
        }

        @Nullable
        @Override
        public Component secondary() {
            if (covered) {
                return Component.translatable("gui.battlecorrection.editor.curios.covered");
            }
            return BaubleMark.ANY.equals(id) ? null : Component.raw(id);
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
        if (!BaublesIntegration.isBaublesLoaded()) {
            return;
        }
        slotIds.addAll(BaubleMark.SLOT_IDS);
        // 物品上已经标了、但不是 Baubles 饰品栏类型的 ID（手写 NBT 写错的之类）也列出来，不然没法取消
        for (String marked : BaubleMark.getSlots(session.tagOrNull())) {
            if (!BaubleMark.isAnyLike(marked) && !slotIds.contains(marked)) {
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
        if (!BaublesIntegration.isBaublesLoaded()) {
            paragraph(g, Component.translatable("gui.battlecorrection.editor.curios.not_loaded"), x, y, width,
                    EditorTheme.TEXT_MUTED);
            return;
        }
        paragraph(g, Component.translatable("gui.battlecorrection.editor.curios.intro"), x, y, width, EditorTheme.TEXT_DIM);

        ItemStack working = session.working();
        List<String> slots = BaubleMark.getSlots(working);
        Component status;
        int color;
        if (slots.isEmpty()) {
            status = Component.translatable("gui.battlecorrection.editor.curios.status.none");
            color = EditorTheme.TEXT_MUTED;
        } else {
            status = Component.translatable("gui.battlecorrection.editor.curios.status.marked", joinSlotNames(slots));
            color = EditorTheme.SUCCESS;
        }
        g.enableScissor(x, footerY, x + width, y + height);
        int bottom = footerY + paragraph(g, status, x, footerY, width, color) + 1;
        if (BaubleMark.isNativeBauble(working)) {
            paragraph(g, Component.translatable("command.battlecorrection.bauble.native_warning"), x, bottom, width,
                    EditorTheme.WARNING);
        }
        g.disableScissor();
    }

    /**
     * 饰品栏的显示名：any / trinket 显示"任意饰品栏"，认识的类型按语言翻译，不认识的 ID 原样显示
     *
     * @param id 饰品栏 ID
     * @return 组件
     */
    @Nonnull
    private static Component slotName(@Nonnull String id) {
        String key = BaubleMark.slotNameKey(id);
        return key == null ? Component.raw(id) : Component.translatable(key);
    }

    /**
     * 把几种饰品栏的名字用"、"连起来（同 BaubleMark#joinSlotNames，换成界面用的组件）
     *
     * @param ids 饰品栏 ID
     * @return 组件
     */
    @Nonnull
    private static Component joinSlotNames(@Nonnull List<String> ids) {
        Component result = Component.empty();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                result.append(Component.translatable("command.battlecorrection.bauble.separator"));
            }
            result.append(slotName(ids.get(i)));
        }
        return result;
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
        if (row.checked) {
            NBTTagCompound tag = session.tagOrNull();
            if (tag != null) {
                BaubleMark.removeSlot(tag, row.id);
                // tag 删空了就整个去掉，不然和同类的普通物品叠不到一起
                if (tag.hasNoTags()) {
                    session.setTag(null);
                }
            }
        } else {
            BaubleMark.addSlot(session.tag(), row.id);
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
        List<String> marked = BaubleMark.getSlots(session.tagOrNull());
        boolean any = false;
        for (String id : marked) {
            if (BaubleMark.isAnyLike(id)) {
                any = true;
                break;
            }
        }

        SlotRow selected = list.selectedItem();
        String selectedId = selected == null ? null : selected.id;

        rows.clear();
        SlotRow anyRow = new SlotRow(BaubleMark.ANY);
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
