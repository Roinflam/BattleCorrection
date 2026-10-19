package pers.roinflam.battlecorrection.client.editor.widget;

import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.ui.Component;
import pers.roinflam.battlecorrection.client.editor.ui.Font;
import pers.roinflam.battlecorrection.client.editor.ui.GuiGraphics;
import pers.roinflam.battlecorrection.client.editor.ui.Keys;
import pers.roinflam.battlecorrection.editor.ColorMath;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 渐变条 + 色标：上面一条渐变，下面每个色标一个小色块，选中的那个描亮边、带一根指向渐变条的小针
 * <p>
 * 渐变条画的是写进文字时真正会出现的颜色：每一点按位置插值后换成最接近的原版颜色（{@link ColorMath#sampleLegacy}），
 * 所以看到的是一段段纯色，和"彩虹字"里每个字的颜色一致。
 * <p>
 * 色标等距分布（和写进文字时一样，见 LegacyEdits#gradient），所以这里只管选中：点哪儿就选离得最近的色标，
 * 按住左右拖可以一路扫过去；有焦点时左右方向键 / Home / End 换选中的色标。
 */
public class GradientBar extends PickerWidget {

    /**
     * 控件高度：渐变条 10 + 小针 2 + 色标块 7 + 焦点下划线 1
     */
    public static final int HEIGHT = 20;

    private static final int BAR_HEIGHT = 10;
    private static final int HANDLE = 7;
    /**
     * 渐变条左右各缩进的像素：两端色标的小色块以端点为中心，要有地方放
     */
    private static final int INSET = HANDLE / 2;

    private final Supplier<int[]> stops;
    private final IntSupplier selected;
    private final IntConsumer onSelect;
    private final Bands bands = new Bands();
    @Nullable
    private Component hoverTip;
    @Nullable
    private List<Component> hoverLines;

    /**
     * @param x        左
     * @param y        上
     * @param width    宽
     * @param message  控件的名字
     * @param stops    当前色标（每帧读，调用方别每次新建数组）
     * @param selected 选中的色标下标
     * @param onSelect 用户选了某个色标
     */
    public GradientBar(int x, int y, int width, @Nonnull Component message, @Nonnull Supplier<int[]> stops,
                       @Nonnull IntSupplier selected, @Nonnull IntConsumer onSelect) {
        super(x, y, width, HEIGHT, message, true);
        this.stops = stops;
        this.selected = selected;
        this.onSelect = onSelect;
    }

    /**
     * 悬停时的说明（和 setTooltip 不同：按住拖的时候不弹）
     *
     * @param tip 说明
     * @return this
     */
    @Nonnull
    public GradientBar hoverTip(@Nonnull Component tip) {
        this.hoverTip = tip;
        this.hoverLines = null;
        return this;
    }

    // ═══════════════════════════════════════════════════════════════
    // 色带（调色盘里几个控件共用）
    // ═══════════════════════════════════════════════════════════════

    /**
     * 换成原版颜色后的渐变：一段段纯色，按色标和宽度缓存，内容不变时每帧不重算
     * <p>
     * 每个控件自己拿一份（预设一格一份），不同的渐变不会互相挤掉缓存。
     */
    public static final class Bands {
        @Nullable
        private int[] key;
        private int keyWidth = -1;
        private int[] starts = new int[0];
        private int[] colors = new int[0];
        private int count;

        /**
         * 画出来
         *
         * @param g     画布
         * @param x     左
         * @param y     上
         * @param w     宽
         * @param h     高
         * @param stops 色标（RGB，至少一个）
         */
        public void fill(@Nonnull GuiGraphics g, int x, int y, int w, int h, @Nonnull int[] stops) {
            if (w <= 0 || h <= 0 || stops.length == 0) {
                return;
            }
            if (w != keyWidth || key == null || !Arrays.equals(key, stops)) {
                rebuild(w, stops);
            }
            for (int i = 0; i < count; i++) {
                int from = x + starts[i];
                int to = i + 1 < count ? x + starts[i + 1] : x + w;
                g.fill(from, y, to, y + h, 0xFF000000 | colors[i]);
            }
        }

        private void rebuild(int w, @Nonnull int[] stops) {
            key = stops.clone();
            keyWidth = w;
            if (starts.length < w) {
                starts = new int[w];
                colors = new int[w];
            }
            count = 0;
            int last = -1;
            for (int px = 0; px < w; px++) {
                double t = w <= 1 ? 0.0 : (double) px / (w - 1);
                int rgb = ColorMath.sampleLegacy(stops, t);
                if (rgb != last) {
                    starts[count] = px;
                    colors[count] = rgb;
                    count++;
                    last = rgb;
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 位置
    // ═══════════════════════════════════════════════════════════════

    /**
     * 第 index 个色标的中心 X（count 个色标等距分布在渐变条上）
     *
     * @param index 下标
     * @param count 色标数
     * @return 中心 X
     */
    public int stopCenter(int index, int count) {
        int barWidth = getWidth() - 2 * INSET;
        if (count <= 1) {
            return getX() + INSET;
        }
        return getX() + INSET + Math.round(index * (barWidth - 1) / (float) (count - 1));
    }

    /**
     * 离 mouseX 最近的色标
     *
     * @param mouseX 鼠标 X
     * @return 下标
     */
    public int nearestStop(double mouseX) {
        int count = stops.get().length;
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            double distance = Math.abs(mouseX - stopCenter(i, count));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    @Override
    protected void pick(double mouseX, double mouseY, boolean press) {
        int index = nearestStop(mouseX);
        if (press || index != selected.getAsInt()) {
            onSelect.accept(index);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!active || !visible) {
            return false;
        }
        int count = stops.get().length;
        int current = selected.getAsInt();
        int next;
        switch (keyCode) {
            case Keys.KEY_LEFT:
            case Keys.KEY_UP:
                next = Math.max(0, current - 1);
                break;
            case Keys.KEY_RIGHT:
            case Keys.KEY_DOWN:
                next = Math.min(count - 1, current + 1);
                break;
            case Keys.KEY_HOME:
                next = 0;
                break;
            case Keys.KEY_END:
                next = count - 1;
                break;
            default:
                return false;
        }
        if (next != current) {
            onSelect.accept(next);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!active || !visible || delta == 0) {
            return false;
        }
        int count = stops.get().length;
        int next = Math.max(0, Math.min(count - 1, selected.getAsInt() - (int) Math.signum(delta)));
        if (next != selected.getAsInt()) {
            onSelect.accept(next);
        }
        return true;
    }

    // ═══════════════════════════════════════════════════════════════
    // 绘制
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void renderWidget(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int[] colors = stops.get();
        int count = colors.length;
        int x = getX();
        int y = getY();
        int barX = x + INSET;
        int barWidth = getWidth() - 2 * INSET;

        // 渐变条：沟槽色外框 + 换成原版颜色后的色带
        EditorTheme.rect(g, barX - 1, y, barWidth + 2, BAR_HEIGHT, isFocused() ? EditorTheme.ACCENT_MID : EditorTheme.GROOVE);
        bands.fill(g, barX, y + 1, barWidth, BAR_HEIGHT - 2, colors);

        int current = selected.getAsInt();
        int handleY = y + BAR_HEIGHT + 2;
        for (int i = 0; i < count; i++) {
            int center = stopCenter(i, count);
            boolean isSelected = i == current;
            int edge = isSelected ? EditorTheme.ACCENT_HI : EditorTheme.BUTTON_BORDER;
            if (isSelected) {
                // 小针：从色标块顶上连到渐变条底边，标出这个色标在条上的位置
                EditorTheme.rect(g, center, y + BAR_HEIGHT, 1, 2, EditorTheme.ACCENT_HI);
            }
            EditorTheme.chamfer(g, center - INSET, handleY, HANDLE, HANDLE, 2, edge);
            EditorTheme.rect(g, center - INSET + 1, handleY + 1, HANDLE - 2, HANDLE - 2, 0xFF000000 | colors[i]);
        }
        if (isFocused()) {
            EditorTheme.rect(g, stopCenter(current, count) - INSET, handleY + HANDLE, HANDLE, 1, EditorTheme.ACCENT);
        }

        if (hoverTip != null && isHovered() && !isDragging()) {
            if (hoverLines == null) {
                hoverLines = Font.get().split(hoverTip, 200);
            }
            showTooltip(hoverLines);
        }
    }
}
