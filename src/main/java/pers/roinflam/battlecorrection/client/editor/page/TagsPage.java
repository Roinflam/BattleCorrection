package pers.roinflam.battlecorrection.client.editor.page;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import pers.roinflam.battlecorrection.client.editor.EditorSession;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.ItemEditorScreen;
import pers.roinflam.battlecorrection.client.editor.ItemRoots;
import pers.roinflam.battlecorrection.client.editor.TooltipFlags;
import pers.roinflam.battlecorrection.client.editor.widget.FlatButton;
import pers.roinflam.battlecorrection.client.editor.widget.FlatEditBox;
import pers.roinflam.battlecorrection.client.editor.widget.FlatToggle;
import pers.roinflam.battlecorrection.client.editor.widget.PopupList;
import pers.roinflam.battlecorrection.client.editor.widget.ScrollList;
import pers.roinflam.battlecorrection.config.ConfigAttribute;
import pers.roinflam.battlecorrection.editor.EditorPage;
import pers.roinflam.battlecorrection.editor.ItemQuality;
import pers.roinflam.battlecorrection.network.packet.ApplyItemEditC2SPacket;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * 标签页：常用的特殊数据，全是开关和数字框
 * <ul>
 *   <li>不可破坏：{@code minecraft:unbreakable}</li>
 *   <li>附魔光效：{@code minecraft:enchantment_glint_override} 设为 true，物品会发光但不显示任何附魔
 *   （1.20.1 是往 Enchantments 里塞一个空条目 {@code [{}]}，原版数据修复器也是把它换成这个组件）。
 *   物品已有真实附魔时光效本来就有，开关锁定</li>
 *   <li>隐藏信息：8 个开关，每个隐藏一类提示信息。1.20.1 是 HideFlags 的 8 个位；1.21.1 拆进了各组件自己的
 *   {@code show_in_tooltip}，对应关系见 {@link TooltipFlags}。物品上没有那个组件（例如不是不可破坏的物品）时没东西可藏，开关灰掉</li>
 *   <li>CustomModelData、Damage、RepairCost、Count：数字</li>
 *   <li>最低品质：提示框模组可以读的 Epic / Legendary / Myth / Genesis 标记，见 {@link ItemQuality}，
 *   配置 itemEditorQualityTags 关掉就不显示这一行</li>
 * </ul>
 * 每个控件改完立刻写回物品副本；数字框输入非法内容时忽略，不报错。
 */
public class TagsPage extends EditorPageBase {

    private static final int COLUMN_GAP = 8;
    private static final int ROW_GAP = 3;
    private static final int NUMBER_BOX_WIDTH = 64;
    private static final int NUMBER_BOX_HEIGHT = 14;

    /**
     * 隐藏信息的各个开关：对应 1.20.1 HideFlags 的位 + 翻译键后缀
     */
    private static final Object[][] HIDE_FLAGS = {
            {TooltipFlags.ENCHANTMENTS, "enchantments"},
            {TooltipFlags.MODIFIERS, "modifiers"},
            {TooltipFlags.UNBREAKABLE, "unbreakable"},
            {TooltipFlags.CAN_BREAK, "can_destroy"},
            {TooltipFlags.CAN_PLACE_ON, "can_place"},
            {TooltipFlags.ADDITIONAL, "additional"},
            {TooltipFlags.DYE, "dye"},
            {TooltipFlags.TRIM, "upgrades"},
    };

    /**
     * 最低品质弹出列表里的一项
     */
    private static final class QualityRow implements ScrollList.Row {
        final ItemQuality quality;

        QualityRow(@Nonnull ItemQuality quality) {
            this.quality = quality;
        }

        @Nonnull
        @Override
        public Component label() {
            return quality.displayName();
        }
    }

    @Nullable
    private FlatToggle glintToggle;
    /**
     * 隐藏信息的开关，按 {@link #HIDE_FLAGS} 的顺序；物品上有没有对应组件会变（例如刚勾上不可破坏），要跟着刷新能不能点
     */
    private final List<FlatToggle> hideToggles = new ArrayList<>();
    @Nullable
    private FlatButton qualityButton;
    private final PopupList<QualityRow> qualityPopup = new PopupList<>();
    private int qualityRowY = -1;
    private int hideLabelY;
    private int numbersLabelY;

    /**
     * @param screen  所属界面
     * @param session 编辑会话
     */
    public TagsPage(@Nonnull ItemEditorScreen screen, @Nonnull EditorSession session) {
        super(screen, session);
    }

    @Nonnull
    @Override
    public EditorPage page() {
        return EditorPage.TAGS;
    }

    @Override
    protected void build() {
        ItemStack working = session.working();
        int columnWidth = (width - COLUMN_GAP) / 2;
        int rightX = x + columnWidth + COLUMN_GAP;
        int cursorY = y;

        // 第一行：不可破坏 | 附魔光效
        add(new FlatToggle(x, cursorY, Component.translatable("gui.battlecorrection.editor.tags.unbreakable"),
                working.has(DataComponents.UNBREAKABLE), this::setUnbreakable)
                .tooltip(Component.translatable("gui.battlecorrection.editor.tags.unbreakable.tooltip")));

        boolean hasRealEnchantments = !EnchantmentHelper.getEnchantmentsForCrafting(working).isEmpty();
        glintToggle = add(new FlatToggle(rightX, cursorY, Component.translatable("gui.battlecorrection.editor.tags.glint"),
                hasGlintOverride(working), this::setGlint));
        if (hasRealEnchantments) {
            glintToggle.active = false;
            glintToggle.setSelected(true);
            glintToggle.tooltip(Component.translatable("gui.battlecorrection.editor.tags.glint.locked"));
        } else {
            glintToggle.tooltip(Component.translatable("gui.battlecorrection.editor.tags.glint.tooltip"));
        }
        cursorY += FlatToggle.HEIGHT + ROW_GAP + 4;

        // 最低品质：一行标签 + 弹出选择按钮（配置关了就不显示）
        if (ConfigAttribute.ITEM_EDITOR_QUALITY_TAGS.get()) {
            qualityRowY = cursorY;
            int labelWidth = font.width(Component.translatable("gui.battlecorrection.editor.tags.quality")) + 6;
            qualityButton = add(new FlatButton(x + labelWidth, cursorY, Math.min(120, width - labelWidth), NUMBER_BOX_HEIGHT,
                    ItemQuality.read(working).displayName(), this::openQualityPopup)
                    .tooltip(Component.translatable("gui.battlecorrection.editor.tags.quality.tooltip")));
            cursorY += NUMBER_BOX_HEIGHT + 6;
        } else {
            qualityRowY = -1;
            qualityButton = null;
        }

        // 隐藏信息：标签在 render 里画，下面两列开关
        hideLabelY = cursorY + 4;
        cursorY += 15;
        int hideFlags = TooltipFlags.read(working);
        hideToggles.clear();
        for (int i = 0; i < HIDE_FLAGS.length; i++) {
            int mask = (Integer) HIDE_FLAGS[i][0];
            String key = (String) HIDE_FLAGS[i][1];
            int column = i % 2;
            int row = i / 2;
            int toggleX = column == 0 ? x : rightX;
            int toggleY = cursorY + row * (FlatToggle.HEIGHT + ROW_GAP);
            hideToggles.add(add(new FlatToggle(toggleX, toggleY, Component.translatable("gui.battlecorrection.editor.tags.hide." + key),
                    (hideFlags & mask) != 0, value -> setHideFlag(mask, value))));
        }
        refreshHideToggles();
        cursorY += 4 * (FlatToggle.HEIGHT + ROW_GAP) + 6;

        // 数字：两列两行
        numbersLabelY = cursorY;
        cursorY += 11;
        int numberRowHeight = NUMBER_BOX_HEIGHT + 16;

        addNumberBox(x, cursorY, columnWidth, "custom_model_data",
                working.has(DataComponents.CUSTOM_MODEL_DATA) ? working.getOrDefault(DataComponents.CUSTOM_MODEL_DATA,
                        CustomModelData.DEFAULT).value() : null,
                this::setCustomModelData, true);

        boolean damageable = working.isDamageableItem();
        FlatEditBox damageBox = addNumberBox(rightX, cursorY, columnWidth, "damage",
                damageable ? working.getDamageValue() : null, this::setDamage, damageable);
        if (!damageable) {
            damageBox.setEditable(false);
            damageBox.setTooltip(Tooltip.create(Component.translatable("gui.battlecorrection.editor.tags.damage.na")));
        }
        cursorY += numberRowHeight;

        int repairCost = working.getOrDefault(DataComponents.REPAIR_COST, 0);
        addNumberBox(x, cursorY, columnWidth, "repair_cost",
                repairCost > 0 ? repairCost : null, this::setRepairCost, true);
        addNumberBox(rightX, cursorY, columnWidth, "count", working.getCount(), this::setCount, true);
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int columnWidth = (width - COLUMN_GAP) / 2;
        int rightX = x + columnWidth + COLUMN_GAP;

        if (qualityRowY >= 0) {
            label(g, Component.translatable("gui.battlecorrection.editor.tags.quality"), x, qualityRowY + 3);
        }
        label(g, Component.translatable("gui.battlecorrection.editor.tags.hide"), x, hideLabelY);
        EditorTheme.divider(g, x, hideLabelY - 4, width);

        label(g, Component.translatable("gui.battlecorrection.editor.tags.numbers"), x, numbersLabelY);
        EditorTheme.divider(g, x, numbersLabelY - 4, width);

        // 数字框的标签
        int firstRowLabelY = numbersLabelY + 11;
        int secondRowLabelY = firstRowLabelY + NUMBER_BOX_HEIGHT + 16;
        label(g, Component.translatable("gui.battlecorrection.editor.tags.custom_model_data"), x, firstRowLabelY);
        ItemStack working = session.working();
        Component damageLabel = working.isDamageableItem()
                ? Component.translatable("gui.battlecorrection.editor.tags.damage.max", working.getMaxDamage())
                : Component.translatable("gui.battlecorrection.editor.tags.damage");
        label(g, damageLabel, rightX, firstRowLabelY);
        label(g, Component.translatable("gui.battlecorrection.editor.tags.repair_cost"), x, secondRowLabelY);
        label(g, Component.translatable("gui.battlecorrection.editor.tags.count"), rightX, secondRowLabelY);
    }

    @Override
    public void renderForeground(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        qualityPopup.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return qualityPopup.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return qualityPopup.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean onEscape() {
        if (qualityPopup.isOpen()) {
            qualityPopup.close();
            return true;
        }
        return false;
    }

    /**
     * 弹出最低品质列表
     */
    private void openQualityPopup() {
        if (qualityButton == null) {
            return;
        }
        List<QualityRow> rows = new ArrayList<>();
        for (ItemQuality quality : ItemQuality.values()) {
            rows.add(new QualityRow(quality));
        }
        qualityPopup.open(qualityButton.getX(), qualityButton.getY() + qualityButton.getHeight() + 2,
                Math.max(qualityButton.getWidth(), 140), y + height, rows, row -> {
            ItemQuality.apply(session.working(), row.quality);
            qualityButton.setMessage(row.quality.displayName());
        });
    }

    /**
     * 创建一个只接受整数的输入框（标签由 render 画在它上面）
     *
     * @param boxX        左
     * @param labelY      标签的 Y，输入框在它下面
     * @param columnWidth 列宽
     * @param key         翻译键后缀（用于提示）
     * @param initial     初始值，null = 留空
     * @param onChange    解析成功后的回调
     * @param active      是否可编辑
     * @return 输入框
     */
    @Nonnull
    private FlatEditBox addNumberBox(int boxX, int labelY, int columnWidth, @Nonnull String key,
                                     @Nullable Integer initial, @Nonnull IntConsumer onChange, boolean active) {
        int boxWidth = Math.min(NUMBER_BOX_WIDTH, columnWidth - 2);
        FlatEditBox box = add(new FlatEditBox(font, boxX + 1, labelY + 12, boxWidth, NUMBER_BOX_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.tags." + key + ".hint"), false));
        box.setMaxLength(10);
        box.setFilter(text -> text.isEmpty() || text.equals("-") || text.matches("-?\\d+"));
        box.setValue(initial == null ? "" : String.valueOf(initial));
        box.active = active;
        box.setResponder(text -> {
            if (text.isEmpty() || text.equals("-")) {
                onChange.accept(Integer.MIN_VALUE);
                return;
            }
            try {
                onChange.accept(Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
                // 超出 int 范围：忽略这次输入
            }
        });
        return box;
    }

    /**
     * 不可破坏
     * <p>
     * 1.21.1 的"隐藏不可破坏"存在不可破坏组件自己身上，取消不可破坏时它也就没了；勾上时沿用隐藏开关当前的状态。
     *
     * @param value 开/关
     */
    private void setUnbreakable(boolean value) {
        ItemStack working = session.working();
        if (value) {
            if (!working.has(DataComponents.UNBREAKABLE)) {
                working.set(DataComponents.UNBREAKABLE, new Unbreakable(!hideToggleSelected(TooltipFlags.UNBREAKABLE)));
            }
        } else {
            working.remove(DataComponents.UNBREAKABLE);
        }
        refreshHideToggles();
    }

    /**
     * 物品上的附魔光效是不是这一页（或者别人）手动打开的：只看组件补丁，物品自带的光效（下界之星这类）不算
     */
    private static boolean hasGlintOverride(@Nonnull ItemStack stack) {
        var override = stack.getComponentsPatch().get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        return override != null && override.isPresent() && Boolean.TRUE.equals(override.get());
    }

    /**
     * 附魔光效：靠 {@code minecraft:enchantment_glint_override} 组件实现
     * <p>
     * 关掉 = 还原物品默认的光效设定（1.20.1 关掉也只是删掉那个空附魔条目，自带光效的物品照样发光）。
     *
     * @param value 开/关
     */
    private void setGlint(boolean value) {
        ItemStack working = session.working();
        if (value) {
            working.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        } else if (hasGlintOverride(working)) {
            ItemRoots.resetToDefault(working, DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        }
    }

    /**
     * 打开 / 关闭一类提示信息的隐藏
     *
     * @param mask  1.20.1 HideFlags 的位
     * @param value 开/关
     */
    private void setHideFlag(int mask, boolean value) {
        TooltipFlags.set(session.working(), mask, value);
    }

    /**
     * 按物品上现在有哪些组件刷新各隐藏开关能不能点；没东西可藏的开关显示为未勾选
     */
    private void refreshHideToggles() {
        ItemStack working = session.working();
        int flags = TooltipFlags.read(working);
        for (int i = 0; i < hideToggles.size() && i < HIDE_FLAGS.length; i++) {
            int mask = (Integer) HIDE_FLAGS[i][0];
            FlatToggle toggle = hideToggles.get(i);
            boolean available = TooltipFlags.available(working, mask);
            toggle.active = available;
            if (available) {
                toggle.setSelected((flags & mask) != 0);
                toggle.setTooltip(null);
            } else {
                toggle.setSelected(false);
                toggle.tooltip(Component.translatable("gui.battlecorrection.editor.tags.hide.na"));
            }
        }
    }

    /**
     * 某个隐藏开关现在勾没勾
     */
    private boolean hideToggleSelected(int mask) {
        for (int i = 0; i < hideToggles.size() && i < HIDE_FLAGS.length; i++) {
            if ((Integer) HIDE_FLAGS[i][0] == mask) {
                return hideToggles.get(i).isSelected();
            }
        }
        return false;
    }

    /**
     * CustomModelData
     *
     * @param value 数值；Integer.MIN_VALUE = 清除
     */
    private void setCustomModelData(int value) {
        if (value == Integer.MIN_VALUE) {
            session.working().remove(DataComponents.CUSTOM_MODEL_DATA);
            return;
        }
        session.working().set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(value));
    }

    /**
     * 耐久损耗（0 = 全新）
     *
     * @param value 数值；Integer.MIN_VALUE = 归零
     */
    private void setDamage(int value) {
        ItemStack working = session.working();
        if (!working.isDamageableItem()) {
            return;
        }
        int damage = value == Integer.MIN_VALUE ? 0 : Mth.clamp(value, 0, working.getMaxDamage());
        working.setDamageValue(damage);
    }

    /**
     * 铁砧修复惩罚
     *
     * @param value 数值；Integer.MIN_VALUE 或 0 = 清除（还原成默认的 0）
     */
    private void setRepairCost(int value) {
        if (value == Integer.MIN_VALUE || value <= 0) {
            ItemRoots.resetToDefault(session.working(), DataComponents.REPAIR_COST);
            return;
        }
        session.working().set(DataComponents.REPAIR_COST, value);
    }

    /**
     * 数量
     *
     * @param value 数值；Integer.MIN_VALUE = 保持不变
     */
    private void setCount(int value) {
        if (value == Integer.MIN_VALUE) {
            return;
        }
        session.working().setCount(Mth.clamp(value, ApplyItemEditC2SPacket.MIN_COUNT, ApplyItemEditC2SPacket.MAX_COUNT));
    }
}
