package pers.roinflam.battlecorrection.client.editor.page;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import pers.roinflam.battlecorrection.client.editor.EditorSession;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.ItemEditorScreen;
import pers.roinflam.battlecorrection.editor.EditorPage;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 编辑器页面基类
 * <p>
 * 生命周期：
 * <ol>
 *   <li>{@link #init}：界面给出内容区坐标，页面在 {@link #build()} 里创建控件并用 {@link #add} 登记。
 *   切换标签页、窗口大小变化时都会重新 init，所以控件里不要保存唯一的状态——状态放在 session 的物品副本里，
 *   或者页面自己的字段里，build 时再读回来。</li>
 *   <li>{@link #render}：画控件之下的东西（标签、说明）；{@link #renderForeground}：画控件之上的东西。</li>
 *   <li>{@link #beforeLeave}：切走或保存前的最后机会，返回 false 可以拦住（原始 NBT 页解析失败时用）。</li>
 * </ol>
 * 页面改物品时直接改 session.working()，改完不用通知谁，预览每帧都从副本重新读。
 */
public abstract class EditorPageBase {

    protected final ItemEditorScreen screen;
    protected final EditorSession session;
    protected final Font font;

    /**
     * 内容区
     */
    protected int x;
    protected int y;
    protected int width;
    protected int height;

    private final List<AbstractWidget> widgets = new ArrayList<>();

    /**
     * @param screen  所属界面
     * @param session 编辑会话
     */
    protected EditorPageBase(@Nonnull ItemEditorScreen screen, @Nonnull EditorSession session) {
        this.screen = screen;
        this.session = session;
        this.font = Minecraft.getInstance().font;
    }

    /**
     * 对应的标签页
     *
     * @return 页面枚举
     */
    @Nonnull
    public abstract EditorPage page();

    /**
     * 标签页名称
     *
     * @return 名称
     */
    @Nonnull
    public Component title() {
        return Component.translatable(page().translationKey());
    }

    /**
     * 布局并创建控件
     *
     * @param x      内容区左
     * @param y      内容区上
     * @param width  内容区宽
     * @param height 内容区高
     */
    public final void init(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        widgets.clear();
        build();
    }

    /**
     * 创建控件（子类实现），用 {@link #add} 登记
     */
    protected abstract void build();

    /**
     * 登记一个控件
     *
     * @param widget 控件
     * @param <T>    控件类型
     * @return 同一个控件，方便链式赋值
     */
    @Nonnull
    protected <T extends AbstractWidget> T add(@Nonnull T widget) {
        widgets.add(widget);
        return widget;
    }

    /**
     * 本页的全部控件（只读）
     *
     * @return 控件列表
     */
    @Nonnull
    public List<AbstractWidget> widgets() {
        return Collections.unmodifiableList(widgets);
    }

    /**
     * 画控件之下的内容（标签文字、卡片背景等）
     *
     * @param g           画布
     * @param mouseX      鼠标 X
     * @param mouseY      鼠标 Y
     * @param partialTick 帧间插值
     */
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    /**
     * 画控件之上的内容（预览、状态提示等）
     *
     * @param g           画布
     * @param mouseX      鼠标 X
     * @param mouseY      鼠标 Y
     * @param partialTick 帧间插值
     */
    public void renderForeground(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    /**
     * 每 tick 调一次
     */
    public void tick() {
    }

    /**
     * 鼠标滚轮
     *
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @param delta  滚动量（正 = 向上）
     * @return true = 已处理
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

    /**
     * 鼠标点击（在所有控件之前调用，用于弹出列表这类要盖在控件上面的东西）
     *
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @param button 键
     * @return true = 已处理，不再交给控件
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    /**
     * 按下 Esc（在关闭界面之前调用）
     *
     * @return true = 页面自己消费了这次 Esc（例如关掉弹出列表），界面不关
     */
    public boolean onEscape() {
        return false;
    }

    /**
     * 按键（控件都没处理时才会到这里）
     *
     * @param keyCode   键码
     * @param scanCode  扫描码
     * @param modifiers 修饰键
     * @return true = 已处理
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /**
     * 切走或保存前调用
     *
     * @return false = 有问题，拦住这次切换/保存
     */
    public boolean beforeLeave() {
        return true;
    }

    /**
     * 这一页要不要右侧预览栏
     * <p>
     * 列表类页面（附魔、属性）需要宽度，返回 false 让界面把预览栏收起来，内容区变宽。
     *
     * @return true = 显示预览栏（宽度够的话）
     */
    public boolean wantsPreview() {
        return true;
    }

    /**
     * 画一行小标题（灰字）
     *
     * @param g    画布
     * @param text 文字
     * @param x    左
     * @param y    上
     */
    protected void label(@Nonnull GuiGraphics g, @Nonnull Component text, int x, int y) {
        g.drawString(font, text, x, y, EditorTheme.TEXT_MUTED, false);
    }

    /**
     * 画一段会自动换行的说明文字
     *
     * @param g        画布
     * @param text     文字
     * @param x        左
     * @param y        上
     * @param maxWidth 最大宽度
     * @param color    颜色
     * @return 占用的高度
     */
    protected int paragraph(@Nonnull GuiGraphics g, @Nonnull Component text, int x, int y, int maxWidth, int color) {
        int lineY = y;
        for (FormattedCharSequence line : font.split(text, maxWidth)) {
            g.drawString(font, line, x, lineY, color, false);
            lineY += font.lineHeight + 1;
        }
        return lineY - y;
    }
}
