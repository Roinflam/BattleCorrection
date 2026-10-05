package pers.roinflam.battlecorrection.client.editor.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 渐变条 + 色标：上面一条真实的渐变，下面每个色标一个小色块，选中的那个描亮边、带一根指向渐变条的小针
 * <p>
 * 色标等距分布（和写进文字时一样，见 LegacyEdits#gradient），所以这里只管选中：点哪儿就选离得最近的色标，
 * 按住左右拖可以一路扫过去；有焦点时左右方向键 / Home / End 换选中的色标。
 * <p>
 * 原版 GuiGraphics 只有竖直方向的渐变（上一个颜色、下一个颜色），横向渐变用 {@link #fillHorizontal}：
 * 把坐标系转 90 度再画竖直渐变，一段只要一次 fillGradient。
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

    /**
     * 画横向渐变时暂存原来的变换矩阵（只在渲染线程用）
     */
    private static final Matrix4f SAVED = new Matrix4f();

    private final Supplier<int[]> stops;
    private final IntSupplier selected;
    private final IntConsumer onSelect;
    @Nullable
    private Component hoverTip;
    @Nullable
    private List<FormattedCharSequence> hoverLines;

    /**
     * @param x        左
     * @param y        上
     * @param width    宽
     * @param message  朗读用的名字
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
    // 画横向渐变（调色盘里几个控件共用）
    // ═══════════════════════════════════════════════════════════════

    /**
     * 横向渐变：左边 left 色、右边 right 色（ARGB）
     * <p>
     * 把坐标系平移到左下角、转 -90 度，原版的竖直渐变（上 → 下）就成了左 → 右。旋转不改变顶点绕向，不会被背面剔除。
     * 不用 pushPose / popPose：那样每次都新建一份矩阵，调色盘一帧要画几十段；这里直接改当前矩阵，画完从暂存的副本拷回来。
     *
     * @param g     画布
     * @param x1    左
     * @param y1    上
     * @param x2    右（不含）
     * @param y2    下（不含）
     * @param left  左端颜色
     * @param right 右端颜色
     */
    public static void fillHorizontal(@Nonnull GuiGraphics g, int x1, int y1, int x2, int y2, int left, int right) {
        if (x2 <= x1 || y2 <= y1) {
            return;
        }
        if (left == right) {
            g.fill(x1, y1, x2, y2, left);
            return;
        }
        Matrix4f matrix = g.pose().last().pose();
        SAVED.set(matrix);
        matrix.translate(x1, y2, 0).rotateZ((float) (-Math.PI / 2));
        try {
            g.fillGradient(0, 0, y2 - y1, x2 - x1, left, right);
        } finally {
            matrix.set(SAVED);
        }
    }

    /**
     * 按等距色标画一整条渐变（每两个相邻色标之间一段）
     *
     * @param g     画布
     * @param x     左
     * @param y     上
     * @param w     宽
     * @param h     高
     * @param stops 色标（RGB，至少一个）
     */
    public static void fillStops(@Nonnull GuiGraphics g, int x, int y, int w, int h, @Nonnull int[] stops) {
        int count = stops.length;
        if (w <= 0 || h <= 0 || count == 0) {
            return;
        }
        if (count == 1) {
            g.fill(x, y, x + w, y + h, 0xFF000000 | stops[0]);
            return;
        }
        int segments = count - 1;
        for (int i = 0; i < segments; i++) {
            int from = x + w * i / segments;
            int to = x + w * (i + 1) / segments;
            fillHorizontal(g, from, y, to, y + h, 0xFF000000 | stops[i], 0xFF000000 | stops[i + 1]);
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
        int next = switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_UP -> Math.max(0, current - 1);
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_DOWN -> Math.min(count - 1, current + 1);
            case GLFW.GLFW_KEY_HOME -> 0;
            case GLFW.GLFW_KEY_END -> count - 1;
            default -> -1;
        };
        if (next < 0) {
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

        // 渐变条：沟槽色外框 + 真实渐变
        EditorTheme.rect(g, barX - 1, y, barWidth + 2, BAR_HEIGHT, isFocused() ? EditorTheme.ACCENT_MID : EditorTheme.GROOVE);
        fillStops(g, barX, y + 1, barWidth, BAR_HEIGHT - 2, colors);

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
                hoverLines = Minecraft.getInstance().font.split(hoverTip, 200);
            }
            Screen screen = Minecraft.getInstance().screen;
            if (screen != null) {
                screen.setTooltipForNextRenderPass(hoverLines);
            }
        }
    }
}
