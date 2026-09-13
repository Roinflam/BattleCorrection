package pers.roinflam.battlecorrection.client.editor.page;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import pers.roinflam.battlecorrection.client.editor.EditorSession;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.ItemEditorScreen;
import pers.roinflam.battlecorrection.client.editor.widget.FlatEditBox;
import pers.roinflam.battlecorrection.client.editor.widget.FlatToggle;
import pers.roinflam.battlecorrection.editor.EditorPage;
import pers.roinflam.battlecorrection.network.packet.ApplyItemEditC2SPacket;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.IntConsumer;

/**
 * 标签页：常用的特殊 NBT 标签，全是开关和数字框
 * <ul>
 *   <li>Unbreakable：不可破坏</li>
 *   <li>附魔光效：往 Enchantments 里塞一个空条目 {@code [{}]}，物品会发光但不显示任何附魔。
 *   物品已有真实附魔时光效本来就有，开关锁定</li>
 *   <li>HideFlags：8 个位，每位隐藏一类提示信息</li>
 *   <li>CustomModelData、Damage、RepairCost、Count：数字</li>
 * </ul>
 * 每个控件改完立刻写回物品副本；数字框输入非法内容时忽略，不报错。
 */
public class TagsPage extends EditorPageBase {

    private static final int COLUMN_GAP = 8;
    private static final int ROW_GAP = 3;
    private static final int NUMBER_BOX_WIDTH = 64;
    private static final int NUMBER_BOX_HEIGHT = 14;

    /**
     * HideFlags 的各个位：掩码 + 翻译键后缀
     */
    private static final Object[][] HIDE_FLAGS = {
            {1, "enchantments"},
            {2, "modifiers"},
            {4, "unbreakable"},
            {8, "can_destroy"},
            {16, "can_place"},
            {32, "additional"},
            {64, "dye"},
            {128, "upgrades"},
    };

    @Nullable
    private FlatToggle glintToggle;
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
        CompoundTag tag = session.tagOrNull();
        int columnWidth = (width - COLUMN_GAP) / 2;
        int rightX = x + columnWidth + COLUMN_GAP;
        int cursorY = y;

        // 第一行：不可破坏 | 附魔光效
        add(new FlatToggle(x, cursorY, Component.translatable("gui.battlecorrection.editor.tags.unbreakable"),
                tag != null && tag.getBoolean("Unbreakable"), this::setUnbreakable)
                .tooltip(Component.translatable("gui.battlecorrection.editor.tags.unbreakable.tooltip")));

        boolean hasRealEnchantments = !EnchantmentHelper.getEnchantments(working).isEmpty();
        glintToggle = add(new FlatToggle(rightX, cursorY, Component.translatable("gui.battlecorrection.editor.tags.glint"),
                working.isEnchanted(), this::setGlint));
        if (hasRealEnchantments) {
            glintToggle.active = false;
            glintToggle.setSelected(true);
            glintToggle.tooltip(Component.translatable("gui.battlecorrection.editor.tags.glint.locked"));
        } else {
            glintToggle.tooltip(Component.translatable("gui.battlecorrection.editor.tags.glint.tooltip"));
        }
        cursorY += FlatToggle.HEIGHT + ROW_GAP + 8;

        // 隐藏信息：标签在 render 里画，下面两列开关
        cursorY += 11;
        int hideFlags = tag != null ? tag.getInt("HideFlags") : 0;
        for (int i = 0; i < HIDE_FLAGS.length; i++) {
            int mask = (Integer) HIDE_FLAGS[i][0];
            String key = (String) HIDE_FLAGS[i][1];
            int column = i % 2;
            int row = i / 2;
            int toggleX = column == 0 ? x : rightX;
            int toggleY = cursorY + row * (FlatToggle.HEIGHT + ROW_GAP);
            add(new FlatToggle(toggleX, toggleY, Component.translatable("gui.battlecorrection.editor.tags.hide." + key),
                    (hideFlags & mask) != 0, value -> setHideFlag(mask, value)));
        }
        cursorY += 4 * (FlatToggle.HEIGHT + ROW_GAP) + 6;

        // 数字：两列两行
        numbersLabelY = cursorY;
        cursorY += 11;
        int numberRowHeight = NUMBER_BOX_HEIGHT + 12;

        addNumberBox(x, cursorY, columnWidth, "custom_model_data",
                tag != null && tag.contains("CustomModelData", Tag.TAG_ANY_NUMERIC) ? tag.getInt("CustomModelData") : null,
                this::setCustomModelData, true);

        boolean damageable = working.isDamageableItem();
        FlatEditBox damageBox = addNumberBox(rightX, cursorY, columnWidth, "damage",
                damageable ? working.getDamageValue() : null, this::setDamage, damageable);
        if (!damageable) {
            damageBox.setEditable(false);
            damageBox.setTooltip(Tooltip.create(Component.translatable("gui.battlecorrection.editor.tags.damage.na")));
        }
        cursorY += numberRowHeight;

        addNumberBox(x, cursorY, columnWidth, "repair_cost",
                working.getBaseRepairCost() > 0 ? working.getBaseRepairCost() : null, this::setRepairCost, true);
        addNumberBox(rightX, cursorY, columnWidth, "count", working.getCount(), this::setCount, true);
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int columnWidth = (width - COLUMN_GAP) / 2;
        int rightX = x + columnWidth + COLUMN_GAP;

        int hideLabelY = y + FlatToggle.HEIGHT + ROW_GAP + 8;
        label(g, Component.translatable("gui.battlecorrection.editor.tags.hide"), x, hideLabelY);
        EditorTheme.divider(g, x, hideLabelY - 4, width);

        label(g, Component.translatable("gui.battlecorrection.editor.tags.numbers"), x, numbersLabelY);
        EditorTheme.divider(g, x, numbersLabelY - 4, width);

        // 数字框的标签
        int firstRowLabelY = numbersLabelY + 11;
        int secondRowLabelY = firstRowLabelY + NUMBER_BOX_HEIGHT + 12;
        label(g, Component.translatable("gui.battlecorrection.editor.tags.custom_model_data"), x, firstRowLabelY);
        ItemStack working = session.working();
        Component damageLabel = working.isDamageableItem()
                ? Component.translatable("gui.battlecorrection.editor.tags.damage.max", working.getMaxDamage())
                : Component.translatable("gui.battlecorrection.editor.tags.damage");
        label(g, damageLabel, rightX, firstRowLabelY);
        label(g, Component.translatable("gui.battlecorrection.editor.tags.repair_cost"), x, secondRowLabelY);
        label(g, Component.translatable("gui.battlecorrection.editor.tags.count"), rightX, secondRowLabelY);
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
        FlatEditBox box = add(new FlatEditBox(font, boxX + 1, labelY + 11, boxWidth, NUMBER_BOX_HEIGHT,
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
     *
     * @param value 开/关
     */
    private void setUnbreakable(boolean value) {
        if (value) {
            session.tag().putBoolean("Unbreakable", true);
        } else {
            CompoundTag tag = session.tagOrNull();
            if (tag != null) {
                tag.remove("Unbreakable");
            }
        }
    }

    /**
     * 附魔光效：靠一个空的附魔条目实现
     *
     * @param value 开/关
     */
    private void setGlint(boolean value) {
        if (value) {
            CompoundTag tag = session.tag();
            ListTag enchantments = tag.getList("Enchantments", Tag.TAG_COMPOUND);
            if (enchantments.isEmpty()) {
                enchantments.add(new CompoundTag());
                tag.put("Enchantments", enchantments);
            }
            return;
        }
        CompoundTag tag = session.tagOrNull();
        if (tag == null || !tag.contains("Enchantments", Tag.TAG_LIST)) {
            return;
        }
        ListTag enchantments = tag.getList("Enchantments", Tag.TAG_COMPOUND);
        ListTag kept = new ListTag();
        for (int i = 0; i < enchantments.size(); i++) {
            CompoundTag entry = enchantments.getCompound(i);
            if (!entry.isEmpty()) {
                kept.add(entry);
            }
        }
        if (kept.isEmpty()) {
            tag.remove("Enchantments");
        } else {
            tag.put("Enchantments", kept);
        }
    }

    /**
     * 设置/清除 HideFlags 的某一位
     *
     * @param mask  位掩码
     * @param value 开/关
     */
    private void setHideFlag(int mask, boolean value) {
        CompoundTag tag = session.tag();
        int flags = tag.getInt("HideFlags");
        flags = value ? (flags | mask) : (flags & ~mask);
        if (flags == 0) {
            tag.remove("HideFlags");
        } else {
            tag.putInt("HideFlags", flags);
        }
    }

    /**
     * CustomModelData
     *
     * @param value 数值；Integer.MIN_VALUE = 清除
     */
    private void setCustomModelData(int value) {
        if (value == Integer.MIN_VALUE) {
            CompoundTag tag = session.tagOrNull();
            if (tag != null) {
                tag.remove("CustomModelData");
            }
            return;
        }
        session.tag().putInt("CustomModelData", value);
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
     * @param value 数值；Integer.MIN_VALUE 或 0 = 清除
     */
    private void setRepairCost(int value) {
        if (value == Integer.MIN_VALUE || value <= 0) {
            CompoundTag tag = session.tagOrNull();
            if (tag != null) {
                tag.remove("RepairCost");
            }
            return;
        }
        session.working().setRepairCost(value);
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
