package pers.roinflam.battlecorrection.client.editor.page;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import pers.roinflam.battlecorrection.client.editor.EditorSession;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.ItemEditorScreen;
import pers.roinflam.battlecorrection.client.editor.ui.Component;
import pers.roinflam.battlecorrection.client.editor.ui.EditBox;
import pers.roinflam.battlecorrection.client.editor.ui.GuiGraphics;
import pers.roinflam.battlecorrection.client.editor.ui.Keys;
import pers.roinflam.battlecorrection.client.editor.ui.Mth;
import pers.roinflam.battlecorrection.client.editor.widget.ColorToolbar;
import pers.roinflam.battlecorrection.client.editor.widget.FlatButton;
import pers.roinflam.battlecorrection.client.editor.widget.FlatEditBox;
import pers.roinflam.battlecorrection.client.editor.widget.FlatToggle;
import pers.roinflam.battlecorrection.client.editor.widget.LossyMarker;
import pers.roinflam.battlecorrection.editor.EditorPage;
import pers.roinflam.battlecorrection.editor.LoreLines;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Lore 页：一行一个输入框，加/删/上移/下移，颜色工具条作用于当前（或最近一次）有焦点的那一行
 * <p>
 * 数据在 {@link LoreLines} 里（载入、改、写回都在那，界面只管把输入框和它对上），每次改动立刻写回物品副本的 display.Lore。
 * 没动过的行写回的是原文，编辑器放不下的控制字符、原文里多余的代码都不会因为改了别的行而变；
 * 原文里有编辑文本表达不了的东西的行，行尾亮一个小标记，改了这一行之后标记变黄，提醒这些东西会丢。
 * <p>
 * 界面上只创建"看得见的那几行"的控件（行数 = 列表区高度 / 行高），滚动时把控件重新绑定到不同的行，
 * 而不是给每一行都建控件——Lore 几十行时控件太多会卡。
 * <p>
 * "原版样式"开关决定没写颜色码的字的样子：关 = 白色不斜体（每行最前面写 §r），
 * 开 = 什么都不写，交给原版渲染成紫色斜体（提示框给每行加的 §5§o）。载入时自动选让编辑文本最短的那种。
 * <p>
 * 编辑：回车在光标处断行（后半截挪到下一行，样式不变），行首退格把这一行并到上一行，
 * 粘贴多行文本按文本编辑器的方式拆开。列表上面一行预览当前行在提示框里的样子：§7§5§o 加上写进物品的那一行，和原版提示框拼法一致。
 */
public class LorePage extends EditorPageBase {

    private static final int ROW_HEIGHT = 20;
    private static final int BOX_HEIGHT = 16;
    private static final int BUTTON_WIDTH = 14;
    private static final int GAP = 3;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int MAX_LINE_LENGTH = 1024;
    /**
     * 输入框和上移按钮之间留给"表达不了"标记的宽度
     */
    private static final int MARKER_SPACE = LossyMarker.SIZE + 3;

    private static final Component PREVIEW_LABEL = Component.translatable("gui.battlecorrection.editor.preview");
    private static final Component PREVIEW_NONE = Component.translatable("gui.battlecorrection.editor.lore.preview.none");
    private static final Component EMPTY_HINT = Component.translatable("gui.battlecorrection.editor.lore.empty");

    /**
     * 一行控件
     */
    private static final class Row {
        FlatEditBox box;
        FlatButton up;
        FlatButton down;
        FlatButton delete;
        int markerX;
        int markerY;
        /**
         * 当前绑定的行下标，-1 = 未绑定
         */
        int index = -1;

        void setVisible(boolean visible) {
            box.visible = visible;
            box.active = visible;
            up.visible = visible;
            down.visible = visible;
            delete.visible = visible;
            delete.active = visible;
            if (!visible) {
                up.active = false;
                down.active = false;
                box.setFocused(false);
            }
        }
    }

    private final List<Row> rows = new ArrayList<>();

    /**
     * 行模型；null = 还没载入
     */
    @Nullable
    private LoreLines model;
    /**
     * 没有模型时给界面用的空模型（只读：所有改动入口都先判断 model 是不是 null）
     */
    private final LoreLines placeholder = LoreLines.empty(false);

    /**
     * 上次写进物品（或载入时读到）的那个列表对象：页面重建时物品里还是它，说明没被别的页改过，保留模型和编辑状态
     */
    @Nullable
    private NBTTagList written;
    private boolean synced;

    private int scroll;
    private boolean binding;
    /**
     * 最近一次有焦点的行：点颜色框时焦点会被拿走，颜色工具条靠它找回目标行
     */
    private int lastFocusedLine = -1;

    @Nullable
    private FlatToggle vanillaToggle;

    private int listY;
    private int listHeight;
    private int visibleRows;
    private int previewY;

    /**
     * 预览缓存：对应的 § 字符串（比身份）和拼好提示框前缀的组件
     */
    @Nullable
    private String previewSource;
    @Nullable
    private Component previewShown;

    /**
     * @param screen  所属界面
     * @param session 编辑会话
     */
    public LorePage(@Nonnull ItemEditorScreen screen, @Nonnull EditorSession session) {
        super(screen, session);
    }

    @Nonnull
    @Override
    public EditorPage page() {
        return EditorPage.LORE;
    }

    @Override
    protected void build() {
        loadFromItem();
        LoreLines lines = lines();
        rows.clear();

        int cursorY = y;

        // 颜色工具条
        cursorY += ColorToolbar.build(this::add, x, cursorY, width, this::targetBox, () -> lines().start()) + 6;

        // 添加按钮 + 原版样式开关。
        // 添加、上移、下移都会把焦点放到某一行上（focusLine），所以这几个按钮不能抢焦点：
        // 界面是先调按钮的回调、再把焦点设给按钮，抢了的话焦点最后落在按钮上，接着打的字全丢
        int addWidth = Math.min(72, width / 3);
        add(new FlatButton(x, cursorY, addWidth, 14, Component.translatable("gui.battlecorrection.editor.lore.add"),
                this::appendLine).style(FlatButton.Style.PRIMARY).keepFocus()
                .tooltip(Component.translatableLines("gui.battlecorrection.editor.lore.add.tooltip.split")));
        vanillaToggle = add(new FlatToggle(x + addWidth + 8, cursorY + 1,
                Component.translatable("gui.battlecorrection.editor.lore.vanilla_style"), lines.vanilla(), this::setVanilla)
                .tooltip(Component.translatableLines("gui.battlecorrection.editor.lore.vanilla_style.tooltip")));
        cursorY += 14 + 6;

        // 预览行（在 render 里画）
        previewY = cursorY;
        cursorY += font.lineHeight + 6;

        // 行列表
        listY = cursorY;
        listHeight = y + height - listY;
        visibleRows = Math.max(1, listHeight / ROW_HEIGHT);

        int boxWidth = width - SCROLLBAR_WIDTH - 4 - 3 * (BUTTON_WIDTH + GAP) - 2 - MARKER_SPACE;
        for (int i = 0; i < visibleRows; i++) {
            final int rowIndex = i;
            int rowY = listY + i * ROW_HEIGHT;
            Row row = new Row();
            row.box = add(new FlatEditBox(font, x + 1, rowY + 1, boxWidth, BOX_HEIGHT,
                    Component.translatable("gui.battlecorrection.editor.lore.hint"), true));
            row.box.setMaxLength(MAX_LINE_LENGTH);
            row.box.setResponder(value -> onLineChanged(rowIndex, value));
            row.box.onMultiLinePaste(pasted -> pasteLines(rowIndex, pasted));
            row.box.onBackspaceAtStart(() -> joinUp(rowIndex));

            row.markerX = x + 1 + boxWidth + 3;
            row.markerY = rowY + 1 + (BOX_HEIGHT - LossyMarker.SIZE) / 2;
            int buttonX = row.markerX + MARKER_SPACE + 1;
            row.up = add(new FlatButton(buttonX, rowY + 1, BUTTON_WIDTH, BOX_HEIGHT, Component.raw("↑"),
                    () -> moveLine(rowIndex, -1)).keepFocus()
                    .tooltip(Component.translatable("gui.battlecorrection.editor.lore.up")));
            buttonX += BUTTON_WIDTH + GAP;
            row.down = add(new FlatButton(buttonX, rowY + 1, BUTTON_WIDTH, BOX_HEIGHT, Component.raw("↓"),
                    () -> moveLine(rowIndex, 1)).keepFocus()
                    .tooltip(Component.translatable("gui.battlecorrection.editor.lore.down")));
            buttonX += BUTTON_WIDTH + GAP;
            row.delete = add(new FlatButton(buttonX, rowY + 1, BUTTON_WIDTH, BOX_HEIGHT, Component.raw("×"),
                    () -> deleteLine(rowIndex)).style(FlatButton.Style.DANGER)
                    .tooltip(Component.translatable("gui.battlecorrection.editor.lore.delete")));
            rows.add(row);
        }

        clampScroll();
        rebind();
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        LoreLines lines = lines();
        renderPreview(g, lines);
        if (lines.isEmpty()) {
            paragraph(g, EMPTY_HINT, x, listY + 4, width, EditorTheme.TEXT_DIM);
            return;
        }
        // 滚动条
        if (lines.size() > visibleRows) {
            int trackX = x + width - SCROLLBAR_WIDTH;
            int trackHeight = visibleRows * ROW_HEIGHT;
            g.fill(trackX, listY, trackX + SCROLLBAR_WIDTH, listY + trackHeight, EditorTheme.CARD_BORDER);
            int thumbHeight = Math.max(8, trackHeight * visibleRows / lines.size());
            int maxScroll = lines.size() - visibleRows;
            int thumbY = listY + (trackHeight - thumbHeight) * scroll / maxScroll;
            g.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, EditorTheme.ACCENT);
        }
    }

    /**
     * 预览行：当前行在提示框里的样子；太长的部分裁掉
     * <p>
     * 提示框画字的颜色是白色（§r 回到白色），这里也用白色画，看到的就是提示框里那一行。
     */
    private void renderPreview(@Nonnull GuiGraphics g, @Nonnull LoreLines lines) {
        label(g, PREVIEW_LABEL, x, previewY);
        int previewX = x + font.width(PREVIEW_LABEL) + 4;
        int line = previewLine();
        if (line < 0) {
            g.drawString(font, PREVIEW_NONE, previewX, previewY, EditorTheme.TEXT_DIM, false);
            return;
        }
        // 写出去的字符串在模型里缓存着，没改动时是同一个对象，这里只在它变了时重新拼
        String source = lines.stored(line);
        if (source != previewSource || previewShown == null) {
            previewSource = source;
            previewShown = Component.literal(LoreLines.tooltipLine(source));
        }
        g.enableScissor(previewX, previewY - 1, x + width, previewY + font.lineHeight + 1);
        g.drawString(font, previewShown, previewX, previewY, 0xFFFFFFFF, false);
        g.disableScissor();
    }

    @Override
    public void renderForeground(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        LoreLines lines = lines();
        Row hovered = null;
        for (Row row : rows) {
            int index = row.index;
            if (index < 0 || index >= lines.size() || lines.lossy(index).isEmpty()) {
                continue;
            }
            LossyMarker.draw(g, font, row.markerX, row.markerY, lines.losing(index));
            if (LossyMarker.hovered(row.markerX, row.markerY, mouseX, mouseY)) {
                hovered = row;
            }
        }
        if (hovered != null) {
            int index = hovered.index;
            LossyMarker.renderTooltip(g, font, LossyMarker.tooltip(lines.lossy(index), lines.losing(index)), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < x || mouseX > x + width || mouseY < listY || mouseY > listY + listHeight) {
            return false;
        }
        if (lines().size() <= visibleRows) {
            return true;
        }
        scroll -= (int) Math.signum(delta);
        clampScroll();
        rebind();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        LoreLines lines = model;
        if ((keyCode == Keys.KEY_ENTER || keyCode == Keys.KEY_KP_ENTER) && lines != null) {
            Row row = focusedRow();
            if (row != null) {
                LoreLines.Caret caret = lines.breakLine(row.index, row.box.selectionStart(), row.box.selectionEnd());
                afterStructureChange(caret);
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick() {
        Row row = focusedRow();
        if (row != null) {
            lastFocusedLine = row.index;
        }
    }

    @Override
    public void onExternalChange() {
        // 撤销 / 重做 / 换物品 / 导入：物品已经不是页面上次写的样子，重建时从物品重新读
        synced = false;
    }

    /**
     * 模型；还没载入时是只读的空模型
     */
    @Nonnull
    private LoreLines lines() {
        LoreLines lines = model;
        return lines == null ? placeholder : lines;
    }

    /**
     * 切换"原版样式"
     *
     * @param value true = 原版样式
     */
    private void setVanilla(boolean value) {
        LoreLines lines = model;
        if (lines == null) {
            if (vanillaToggle != null) {
                vanillaToggle.setSelected(!value);
            }
            return;
        }
        lines.setVanilla(value);
        writeToItem();
    }

    /**
     * 现在有焦点的那一行控件
     */
    @Nullable
    private Row focusedRow() {
        for (Row row : rows) {
            if (row.index >= 0 && row.box.visible && row.box.isFocused()) {
                return row;
            }
        }
        return null;
    }

    /**
     * 显示某一行的控件
     *
     * @param index 行下标
     * @return 控件，这一行不在可见范围内时为 null
     */
    @Nullable
    private Row rowOf(int index) {
        if (index < 0) {
            return null;
        }
        for (Row row : rows) {
            if (row.index == index && row.box.visible) {
                return row;
            }
        }
        return null;
    }

    /**
     * 颜色工具条的目标：有焦点的行；焦点被颜色框拿走时用最近一次有焦点的那一行
     *
     * @return 输入框，没有则 null
     */
    @Nullable
    private EditBox targetBox() {
        Row row = focusedRow();
        if (row != null) {
            lastFocusedLine = row.index;
            return row.box;
        }
        row = rowOf(lastFocusedLine);
        return row == null ? null : row.box;
    }

    /**
     * 预览哪一行：有焦点的行，或者最近一次有焦点的行
     */
    private int previewLine() {
        Row row = focusedRow();
        int index = row != null ? row.index : lastFocusedLine;
        return index >= 0 && index < lines().size() ? index : -1;
    }

    /**
     * 把可见的控件绑定到当前滚动位置对应的行
     * <p>
     * 有焦点的输入框换绑前记下它编辑的是哪一行、光标在哪；换绑后焦点跟着那一行走（还在可见范围就挪到显示它的控件上，
     * 滚出去了就放掉焦点），不然滚一下滚轮，接着打的字就跑到别的行里去了。
     */
    private void rebind() {
        LoreLines lines = lines();
        Row focused = focusedRow();
        int focusLine = focused == null ? -1 : focused.index;
        int cursor = focused == null ? 0 : focused.box.getCursorPosition();
        int anchor = 0;
        if (focused != null) {
            anchor = cursor == focused.box.selectionStart() ? focused.box.selectionEnd() : focused.box.selectionStart();
        }

        binding = true;
        try {
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                int index = scroll + i;
                if (index < lines.size()) {
                    row.index = index;
                    row.setVisible(true);
                    String text = lines.editable(index);
                    // 载入的行可能比默认上限长（逐字渐变的一行动辄几百字符），先放宽，免得 setValue 截断
                    row.box.ensureCapacity(text.length());
                    if (!row.box.getValue().equals(text)) {
                        row.box.setValue(text);
                    }
                    row.up.active = index > 0;
                    row.down.active = index < lines.size() - 1;
                } else {
                    row.index = -1;
                    row.setVisible(false);
                }
            }
        } finally {
            binding = false;
        }

        if (focused != null) {
            Row target = rowOf(focusLine);
            if (target == null) {
                if (screen.getFocused() == focused.box) {
                    screen.setFocused(null);
                }
            } else {
                if (target != focused) {
                    screen.setFocused(target.box);
                }
                int length = target.box.getValue().length();
                target.box.select(Mth.clamp(anchor, 0, length), Mth.clamp(cursor, 0, length));
            }
        }
    }

    /**
     * 把 scroll 限制在合法范围
     */
    private void clampScroll() {
        scroll = Mth.clamp(scroll, 0, Math.max(0, lines().size() - visibleRows));
    }

    /**
     * 滚动到让某一行可见
     *
     * @param index 行下标
     */
    private void scrollTo(int index) {
        if (index < scroll) {
            scroll = index;
        } else if (index >= scroll + visibleRows) {
            scroll = index - visibleRows + 1;
        }
        clampScroll();
    }

    /**
     * 让某一行的输入框获得焦点，光标放到指定位置
     *
     * @param index 行下标
     * @param caret 光标位置
     */
    private void focusLine(int index, int caret) {
        Row row = rowOf(index);
        if (row == null) {
            return;
        }
        screen.setFocused(row.box);
        int position = Mth.clamp(caret, 0, row.box.getValue().length());
        row.box.select(position, position);
        lastFocusedLine = index;
    }

    /**
     * 行数变了之后：写回、滚到光标所在行、重新绑定、把焦点放过去
     *
     * @param caret 光标该去的地方
     */
    private void afterStructureChange(@Nonnull LoreLines.Caret caret) {
        writeToItem();
        scrollTo(caret.line());
        rebind();
        focusLine(caret.line(), caret.caret());
    }

    /**
     * 某一行输入框内容变化（移动光标也会回调，内容没变时模型什么都不做）
     *
     * @param rowIndex 控件行序号
     * @param value    新内容
     */
    private void onLineChanged(int rowIndex, @Nonnull String value) {
        LoreLines lines = model;
        if (binding || lines == null || rowIndex >= rows.size()) {
            return;
        }
        int index = rows.get(rowIndex).index;
        if (index < 0 || index >= lines.size()) {
            return;
        }
        if (lines.set(index, value)) {
            writeToItem();
        }
    }

    /**
     * 在末尾加一个空行并聚焦
     */
    private void appendLine() {
        LoreLines lines = model;
        if (lines == null) {
            return;
        }
        int index = lines.size();
        lines.insert(index, "");
        afterStructureChange(new LoreLines.Caret(index, 0));
    }

    /**
     * 往某一行粘贴多行文本
     *
     * @param rowIndex 控件行序号
     * @param pasted   拆好的各行（原样，§ 还没转换）
     */
    private void pasteLines(int rowIndex, @Nonnull List<String> pasted) {
        LoreLines lines = model;
        if (lines == null || rowIndex >= rows.size() || pasted.isEmpty()) {
            return;
        }
        Row row = rows.get(rowIndex);
        int index = row.index;
        if (index < 0 || index >= lines.size()) {
            return;
        }
        LoreLines.Caret caret = lines.paste(index, row.box.selectionStart(), row.box.selectionEnd(), pasted);
        afterStructureChange(caret);
    }

    /**
     * 行首退格：把这一行并到上一行
     *
     * @param rowIndex 控件行序号
     */
    private void joinUp(int rowIndex) {
        LoreLines lines = model;
        if (lines == null || rowIndex >= rows.size()) {
            return;
        }
        int index = rows.get(rowIndex).index;
        if (index <= 0 || index >= lines.size()) {
            return;
        }
        afterStructureChange(lines.joinWithPrevious(index));
    }

    /**
     * 上移/下移
     *
     * @param rowIndex  控件行序号
     * @param direction -1 上移，1 下移
     */
    private void moveLine(int rowIndex, int direction) {
        LoreLines lines = model;
        if (lines == null || rowIndex >= rows.size()) {
            return;
        }
        int index = rows.get(rowIndex).index;
        int target = index + direction;
        if (index < 0 || target < 0 || target >= lines.size()) {
            return;
        }
        lines.move(index, target);
        afterStructureChange(new LoreLines.Caret(target, lines.editable(target).length()));
    }

    /**
     * 删除一行
     *
     * @param rowIndex 控件行序号
     */
    private void deleteLine(int rowIndex) {
        LoreLines lines = model;
        if (lines == null || rowIndex >= rows.size()) {
            return;
        }
        int index = rows.get(rowIndex).index;
        if (index < 0 || index >= lines.size()) {
            return;
        }
        lines.remove(index);
        if (lastFocusedLine == index) {
            lastFocusedLine = -1;
        } else if (lastFocusedLine > index) {
            lastFocusedLine--;
        }
        writeToItem();
        clampScroll();
        rebind();
    }

    /**
     * 物品上现在的 display.Lore
     * <p>
     * 和原版提示框一样：Lore 不是列表就当没有；是列表但元素不是字符串时 getTagList 给的是一个新的空列表，按空 Lore 处理。
     *
     * @return 列表；没有（或者不是列表）时为 null
     */
    @Nullable
    private NBTTagList currentLore() {
        NBTTagCompound tag = session.tagOrNull();
        if (tag == null || !tag.hasKey("display", 10)) {
            return null;
        }
        NBTTagCompound display = tag.getCompoundTag("display");
        if (display.getTagId("Lore") != 9) {
            return null;
        }
        return display.getTagList("Lore", 8);
    }

    /**
     * 从物品副本读 Lore
     * <p>
     * 物品上的 Lore 还是这一页上次写的（或载入时读到的）那个时（只是切了页、窗口缩放），保留模型，不重新读：
     * 重新读会把"原文"这个基准换成页面自己生成的字符串，之后就分不清哪些行原本有表达不了的东西了。
     */
    private void loadFromItem() {
        NBTTagList current = currentLore();
        LoreLines lines = model;
        if (synced && lines != null) {
            boolean same = current == written ? current != null || lines.isEmpty() : lines.matches(current);
            if (same) {
                written = current;
                return;
            }
        }
        LoreLines loaded = LoreLines.fromTag(current);
        model = loaded;
        written = current;
        synced = true;
        previewSource = null;
        previewShown = null;
        if (lastFocusedLine >= loaded.size()) {
            lastFocusedLine = -1;
        }
    }

    /**
     * 把 Lore 写回物品副本
     */
    private void writeToItem() {
        LoreLines lines = model;
        if (lines == null) {
            return;
        }
        if (lines.isEmpty()) {
            NBTTagCompound tag = session.tagOrNull();
            if (tag != null && tag.hasKey("display", 10)) {
                NBTTagCompound display = tag.getCompoundTag("display");
                display.removeTag("Lore");
                if (display.hasNoTags()) {
                    tag.removeTag("display");
                }
            }
            written = null;
            return;
        }
        NBTTagList lore = lines.toTag();
        NBTTagCompound tag = session.tag();
        NBTTagCompound display = tag.getCompoundTag("display");
        display.setTag("Lore", lore);
        tag.setTag("display", display);
        written = lore;
    }
}
