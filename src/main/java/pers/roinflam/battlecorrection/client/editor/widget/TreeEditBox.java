package pers.roinflam.battlecorrection.client.editor.widget;

import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.ui.Component;
import pers.roinflam.battlecorrection.client.editor.ui.Font;
import pers.roinflam.battlecorrection.client.editor.ui.GuiGraphics;
import pers.roinflam.battlecorrection.editor.LegacyText;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * NBT 树页、SNBT 页用的输入框：能原样保留 §，能在原版处理按键之前截下几个键，提示文字自己画
 * <p>
 * 输入框底下的字符过滤（1.12.2 的 ChatAllowedCharacters）不让输入 §：打字时 charTyped 直接拒收，
 * 粘贴时 insertText 里的 filterAllowedCharacters 把它删掉。
 * 字符串值经常就是带 § 颜色码的文本，删了等于改坏数据。这里打开 {@link #keepSectionSigns} 后：
 * <ul>
 *   <li>粘贴 / 插入：先把 § 换成一个私用区字符（过滤器不会删它），交给原版插入，再在值里换回来，光标位置不变。
 *   替身每次现挑一个值里和插入文字里都没有的字符：资源包的自定义图标就爱用私用区开头那几个码位，
 *   用固定的替身会把值里原有的图标字符也一起换成 §</li>
 *   <li>打字：charTyped 收到 § 时自己走上面那条插入路径</li>
 * </ul>
 * setValue 本来就不过滤，选中节点时把带 § 的值填进来一直是好的。
 * <p>
 * {@link #onKey} 给页面一个在原版之前处理按键的机会：原版对上下方向键返回 false，界面会拿它去切换焦点，
 * 搜索框想用上下键在命中之间跳，就得在这里先截住。
 * <p>
 * 提示文字不交给原版画：原版只在没焦点时画，而查找、跳到行这类框一打开就有焦点，提示永远看不到；
 * 原版也不裁剪，长提示（路径栏的英文提示）会画到框外面、压在旁边的按钮上。这里框空着就画，超宽的裁成省略号；
 * 有焦点时颜色更暗，位置和 {@link FlatEditBox} 补画的一致：放在 "_" 光标右边，只有从起点画才放得下时退回起点。
 * 提示也不交给 FlatEditBox（{@link #setHint} 不往上传），免得同一句画两遍。
 */
public class TreeEditBox extends FlatEditBox {

    private static final char SECTION = LegacyText.SECTION;
    /**
     * § 替身的候选范围：Unicode 基本平面的私用区，正常输入很少出现，原版的字符过滤也放行
     */
    private static final char STAND_IN_FIRST = (char) 0xE000;
    private static final char STAND_IN_LAST = (char) 0xF8FF;
    private static final String ELLIPSIS = "…";

    /**
     * 按键钩子
     */
    @FunctionalInterface
    public interface KeyHook {

        /**
         * @param keyCode   键码
         * @param modifiers 修饰键
         * @return true = 已处理，原版不再处理
         */
        boolean handle(int keyCode, int modifiers);
    }

    private boolean keepSectionSigns;
    @Nullable
    private KeyHook keyHook;
    private final Font hintFont;
    private final int cursorWidth;
    /**
     * 提示文字。父类构造器里就会调 {@link #setHint}，所以这里不能写初值（写了会在父类构造之后被冲掉）
     */
    @Nullable
    private Component hintText;
    /**
     * 排好的提示：对应的提示对象、可用宽度、是否有焦点，以及结果——离文字起点的偏移、
     * 裁好的文字（null = 不用裁，直接画 hintText）
     */
    @Nullable
    private Component hintShownFor;
    private int hintShownWidth = -1;
    private boolean hintShownFocused;
    private int hintOffset;
    @Nullable
    private String hintClipped;

    /**
     * @param font   字体
     * @param x      左
     * @param y      上
     * @param width  宽
     * @param height 高
     * @param hint   空时显示的灰字提示
     */
    public TreeEditBox(@Nonnull Font font, int x, int y, int width, int height, @Nonnull Component hint) {
        super(font, x, y, width, height, hint, false);
        this.hintFont = font;
        this.cursorWidth = font.width("_");
        this.hintText = hint;
    }

    /**
     * 是否原样保留 §（链式）
     *
     * @param keep true = 保留
     * @return this
     */
    @Nonnull
    public TreeEditBox keepSectionSigns(boolean keep) {
        this.keepSectionSigns = keep;
        return this;
    }

    /**
     * 有焦点时先交给钩子处理按键（链式）
     *
     * @param hook 钩子
     * @return this
     */
    @Nonnull
    public TreeEditBox onKey(@Nonnull KeyHook hook) {
        this.keyHook = hook;
        return this;
    }

    @Override
    public void insertText(@Nonnull String text) {
        if (!keepSectionSigns || text.indexOf(SECTION) < 0) {
            super.insertText(text);
            return;
        }
        char standIn = pickStandIn(getValue(), text);
        if (standIn == 0) {
            // 私用区六千多个字符全被占了，现实里碰不到；真碰到了就按原版处理（§ 被过滤掉），至少不改坏原有的字符
            super.insertText(text);
            return;
        }
        super.insertText(text.replace(SECTION, standIn));
        restoreSectionSigns(standIn);
    }

    /**
     * 挑一个 value 和 text 里都没有的私用区字符当 § 的替身，这样插入之后值里出现的替身只可能来自这次插入
     *
     * @return 替身；候选全被占用返回 0
     */
    private static char pickStandIn(@Nonnull String value, @Nonnull String text) {
        for (char c = STAND_IN_FIRST; c <= STAND_IN_LAST; c++) {
            if (value.indexOf(c) < 0 && text.indexOf(c) < 0) {
                return c;
            }
        }
        return 0;
    }

    /**
     * 把值里的替身换回 §；替换前后长度一样，光标原样放回
     */
    private void restoreSectionSigns(char standIn) {
        String value = getValue();
        if (value.indexOf(standIn) < 0) {
            return;
        }
        int cursor = getCursorPosition();
        setValue(value.replace(standIn, SECTION));
        // setValue 会把光标挪到末尾；插入之后选区已经收拢到光标处，两头都放回去
        setCursorPosition(cursor);
        setHighlightPos(cursor);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (keepSectionSigns && c == SECTION && canConsumeInput()) {
            insertText(String.valueOf(SECTION));
            return true;
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyHook != null && canConsumeInput() && keyHook.handle(keyCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * 提示文字记在自己这里，不往父类传：原版和 FlatEditBox 都拿不到它，也就不会再画一遍（原因见类注释）
     */
    @Override
    public void setHint(@Nullable Component hint) {
        this.hintText = hint;
    }

    @Override
    public void renderWidget(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(g, mouseX, mouseY, partialTick);
        Component hint = hintText;
        if (hint == null || !isVisible() || !getValue().isEmpty()) {
            return;
        }
        int available = getInnerWidth();
        boolean focused = isFocused();
        if (hint != hintShownFor || available != hintShownWidth || focused != hintShownFocused) {
            layoutHint(hint, available, focused);
        }
        // 和原版画文字的位置一致：有边框时左边留 4，竖直居中
        int textX = getX() + 4 + hintOffset;
        int textY = getY() + (getHeight() - 8) / 2;
        int color = focused ? EditorTheme.TEXT_DIM : EditorTheme.TEXT_MUTED;
        if (hintClipped != null) {
            g.drawString(hintFont, hintClipped, textX, textY, color, false);
        } else {
            g.drawString(hintFont, hint, textX, textY, color, false);
        }
    }

    /**
     * 提示、宽度或焦点变了才重新排，平时每帧只比较三个值
     */
    private void layoutHint(@Nonnull Component hint, int available, boolean focused) {
        hintShownFor = hint;
        hintShownWidth = available;
        hintShownFocused = focused;
        int full = hintFont.width(hint);
        // 有焦点时起点上有个闪烁的 "_" 光标，提示让到它右边；只有从起点画才放得下时才和光标叠在一起
        hintOffset = focused && !(full > available - cursorWidth && full <= available) ? cursorWidth : 0;
        int room = available - hintOffset;
        if (full <= room) {
            hintClipped = null;
            return;
        }
        int ellipsis = hintFont.width(ELLIPSIS);
        hintClipped = room <= ellipsis ? "" : hintFont.plainSubstrByWidth(hint.getString(), room - ellipsis) + ELLIPSIS;
    }
}
