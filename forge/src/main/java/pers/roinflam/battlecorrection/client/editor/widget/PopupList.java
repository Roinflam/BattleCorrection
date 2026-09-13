package pers.roinflam.battlecorrection.client.editor.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * 弹出式选择列表：点一个按钮弹出来，点一项就选中并收起，点外面收起
 * <p>
 * 它不是注册到界面上的控件——注册的控件按添加顺序接收点击，盖在别的控件上面的弹出层会被下面的控件先抢走点击。
 * 所以页面自己持有它：在 renderForeground 里画（画在所有控件之上），在 mouseClicked 钩子里先问它（在所有控件之前）。
 * 内部复用 {@link ScrollList} 来画行和滚动。
 *
 * @param <T> 行类型
 */
public final class PopupList<T extends ScrollList.Row> {

    private static final int MAX_ROWS = 10;

    @Nullable
    private ScrollList<T> list;

    /**
     * 弹出
     *
     * @param x      左
     * @param y      上（会按内容高度自动往上收，保证不超出 maxBottom）
     * @param width  宽
     * @param maxBottom 允许的最大底边
     * @param items  选项
     * @param onPick 选中回调（选中后自动收起）
     */
    public void open(int x, int y, int width, int maxBottom, @Nonnull List<T> items, @Nonnull Consumer<T> onPick) {
        int rows = Math.max(1, Math.min(MAX_ROWS, items.size()));
        int height = rows * ScrollList.ROW_HEIGHT + 2;
        int top = y;
        if (top + height > maxBottom) {
            top = Math.max(0, maxBottom - height);
        }
        ScrollList<T> created = new ScrollList<>(x, top, width, height, Component.empty());
        created.setItems(items);
        created.onSelect(item -> {
            close();
            onPick.accept(item);
        });
        this.list = created;
    }

    /**
     * 收起
     */
    public void close() {
        list = null;
    }

    /**
     * 是否正弹着
     *
     * @return true = 弹着
     */
    public boolean isOpen() {
        return list != null;
    }

    /**
     * 画（放在 renderForeground 里）
     *
     * @param g           画布
     * @param mouseX      鼠标 X
     * @param mouseY      鼠标 Y
     * @param partialTick 帧间插值
     */
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (list == null) {
            return;
        }
        // 投影一圈，让它看起来浮在上面
        g.fill(list.getX() - 1, list.getY() - 1, list.getX() + list.getWidth() + 2, list.getY() + list.getHeight() + 2,
                EditorTheme.PANEL_SHADOW);
        list.render(g, mouseX, mouseY, partialTick);
        g.renderOutline(list.getX(), list.getY(), list.getWidth(), list.getHeight(), EditorTheme.ACCENT);
    }

    /**
     * 鼠标点击（放在页面的 mouseClicked 钩子里，控件之前）
     *
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @param button 键
     * @return true = 已处理（弹着的时候点哪都算处理：点里面选，点外面收起）
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (list == null) {
            return false;
        }
        if (list.isMouseOver(mouseX, mouseY)) {
            list.mouseClicked(mouseX, mouseY, button);
        } else {
            close();
        }
        return true;
    }

    /**
     * 鼠标滚轮（放在页面的 mouseScrolled 里，最先问它）
     *
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @param delta  滚动量
     * @return true = 已处理
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return list != null && list.mouseScrolled(mouseX, mouseY, delta);
    }
}
