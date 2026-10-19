package pers.roinflam.battlecorrection.client.editor.page;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import pers.roinflam.battlecorrection.client.editor.EditorSession;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.ItemEditorScreen;
import pers.roinflam.battlecorrection.client.editor.ui.Component;
import pers.roinflam.battlecorrection.client.editor.ui.GuiGraphics;
import pers.roinflam.battlecorrection.client.editor.ui.Mth;
import pers.roinflam.battlecorrection.client.editor.ui.Tooltip;
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
 *   <li>附魔光效：1.12.2 只要 ench 列表不空物品就发光。但空条目 {@code {}} 读出来是 0 号附魔（保护）0 级，
 *   提示框里会多一行"保护 enchantment.level.0"；写一个没注册的附魔 ID 倒是不显示，可铁砧合并时会拿它去比兼容性，
 *   碰到空值直接抛异常。所以这里放一条 0 级的保护（0 级不起任何作用），同时打开 HideFlags 的"附魔信息"位把那一行藏起来，
 *   关掉光效时两样一起撤。物品已有真实附魔时光效本来就有，开关锁定</li>
 *   <li>HideFlags：1.12.2 只有 6 个位，每位隐藏一类提示信息（见 ItemStack#getTooltip）</li>
 *   <li>损害值、RepairCost、Count：数字。1.12.2 的损害值在物品根上：会掉耐久的物品是耐久损耗；
 *   羊毛、染料这类有子类型的物品是子类型，改它等于换物品，这里只显示不让改（用 F4 换）</li>
 * </ul>
 * 每个控件改完立刻写回物品副本；数字框输入非法内容时忽略，不报错。
 */
public class TagsPage extends EditorPageBase {

    private static final int COLUMN_GAP = 8;
    private static final int ROW_GAP = 3;
    private static final int NUMBER_BOX_WIDTH = 64;
    private static final int NUMBER_BOX_HEIGHT = 14;
    private static final String TAG_ENCHANTMENTS = "ench";

    /**
     * 光效占位用的附魔：0 号（保护），等级写 0
     */
    private static final int GLINT_PLACEHOLDER_ID = 0;

    /**
     * HideFlags 里"附魔信息"那一位
     */
    private static final int HIDE_ENCHANTMENTS = 1;

    /**
     * HideFlags 的各个位：掩码 + 翻译键后缀（1.12.2 只有这 6 个）
     */
    private static final Object[][] HIDE_FLAGS = {
            {1, "enchantments"},
            {2, "modifiers"},
            {4, "unbreakable"},
            {8, "can_destroy"},
            {16, "can_place"},
            {32, "additional"},
    };

    /**
     * 损害值在这件物品上是什么意思
     */
    private enum DamageKind {
        /**
         * 耐久损耗
         */
        DURABILITY,
        /**
         * 子类型（羊毛的颜色之类）
         */
        SUBTYPE,
        /**
         * 没有意义
         */
        NONE
    }

    @Nullable
    private FlatToggle hideEnchantmentsToggle;
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
        NBTTagCompound tag = session.tagOrNull();
        int columnWidth = (width - COLUMN_GAP) / 2;
        int rightX = x + columnWidth + COLUMN_GAP;
        int cursorY = y;

        // 第一行：不可破坏 | 附魔光效
        add(new FlatToggle(x, cursorY, Component.translatable("gui.battlecorrection.editor.tags.unbreakable"),
                tag != null && tag.getBoolean("Unbreakable"), this::setUnbreakable)
                .tooltip(Component.translatable("gui.battlecorrection.editor.tags.unbreakable.tooltip")));

        FlatToggle glintToggle = add(new FlatToggle(rightX, cursorY,
                Component.translatable("gui.battlecorrection.editor.tags.glint"), working.isItemEnchanted(), this::setGlint));
        if (hasRealEnchantments(tag)) {
            glintToggle.active = false;
            glintToggle.setSelected(true);
            glintToggle.tooltip(Component.translatable("gui.battlecorrection.editor.tags.glint.locked"));
        } else {
            glintToggle.tooltip(Component.translatable("gui.battlecorrection.editor.tags.glint.tooltip"));
        }
        cursorY += FlatToggle.HEIGHT + ROW_GAP + 4;

        // 隐藏信息：标签在 render 里画，下面两列开关
        hideLabelY = cursorY + 4;
        cursorY += 15;
        int hideFlags = tag != null ? tag.getInteger("HideFlags") : 0;
        hideEnchantmentsToggle = null;
        for (int i = 0; i < HIDE_FLAGS.length; i++) {
            int mask = (Integer) HIDE_FLAGS[i][0];
            String key = (String) HIDE_FLAGS[i][1];
            int column = i % 2;
            int row = i / 2;
            int toggleX = column == 0 ? x : rightX;
            int toggleY = cursorY + row * (FlatToggle.HEIGHT + ROW_GAP);
            FlatToggle toggle = add(new FlatToggle(toggleX, toggleY,
                    Component.translatable("gui.battlecorrection.editor.tags.hide." + key),
                    (hideFlags & mask) != 0, value -> setHideFlag(mask, value)));
            if (mask == HIDE_ENCHANTMENTS) {
                hideEnchantmentsToggle = toggle;
            }
        }
        cursorY += ((HIDE_FLAGS.length + 1) / 2) * (FlatToggle.HEIGHT + ROW_GAP) + 6;

        // 数字：第一行 损害值 | 修复惩罚，第二行 数量
        numbersLabelY = cursorY;
        cursorY += 11;
        int numberRowHeight = NUMBER_BOX_HEIGHT + 16;

        DamageKind damageKind = damageKind(working);
        FlatEditBox damageBox = addNumberBox(x, cursorY, columnWidth, "damage",
                damageKind == DamageKind.NONE ? null : working.getItemDamage(), this::setDamage,
                damageKind == DamageKind.DURABILITY);
        if (damageKind != DamageKind.DURABILITY) {
            damageBox.setEditable(false);
            damageBox.setTooltip(Tooltip.create(Component.translatable(damageKind == DamageKind.SUBTYPE
                    ? "gui.battlecorrection.editor.tags.damage.subtype.tooltip"
                    : "gui.battlecorrection.editor.tags.damage.na")));
        }

        addNumberBox(rightX, cursorY, columnWidth, "repair_cost",
                working.getRepairCost() > 0 ? working.getRepairCost() : null, this::setRepairCost, true);
        cursorY += numberRowHeight;

        addNumberBox(x, cursorY, columnWidth, "count", working.getCount(), this::setCount, true);
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int columnWidth = (width - COLUMN_GAP) / 2;
        int rightX = x + columnWidth + COLUMN_GAP;

        label(g, Component.translatable("gui.battlecorrection.editor.tags.hide"), x, hideLabelY);
        EditorTheme.divider(g, x, hideLabelY - 4, width);

        label(g, Component.translatable("gui.battlecorrection.editor.tags.numbers"), x, numbersLabelY);
        EditorTheme.divider(g, x, numbersLabelY - 4, width);

        // 数字框的标签
        int firstRowLabelY = numbersLabelY + 11;
        int secondRowLabelY = firstRowLabelY + NUMBER_BOX_HEIGHT + 16;
        ItemStack working = session.working();
        Component damageLabel;
        switch (damageKind(working)) {
            case DURABILITY:
                damageLabel = Component.translatable("gui.battlecorrection.editor.tags.damage.max", working.getMaxDamage());
                break;
            case SUBTYPE:
                damageLabel = Component.translatable("gui.battlecorrection.editor.tags.damage.subtype");
                break;
            default:
                damageLabel = Component.translatable("gui.battlecorrection.editor.tags.damage");
                break;
        }
        label(g, damageLabel, x, firstRowLabelY);
        label(g, Component.translatable("gui.battlecorrection.editor.tags.repair_cost"), rightX, firstRowLabelY);
        label(g, Component.translatable("gui.battlecorrection.editor.tags.count"), x, secondRowLabelY);
    }

    /**
     * 损害值在这件物品上的含义：有子类型就是子类型；会掉耐久（且没有 Unbreakable）就是耐久损耗；否则没有意义
     *
     * @param stack 物品
     * @return 含义
     */
    @Nonnull
    private static DamageKind damageKind(@Nonnull ItemStack stack) {
        if (stack.getHasSubtypes()) {
            return DamageKind.SUBTYPE;
        }
        return stack.isItemStackDamageable() ? DamageKind.DURABILITY : DamageKind.NONE;
    }

    /**
     * ench 里有没有真正起作用的附魔（认得出来的附魔，等级不是 0）
     *
     * @param tag 物品 tag
     * @return true = 有
     */
    private static boolean hasRealEnchantments(@Nullable NBTTagCompound tag) {
        if (tag == null || !tag.hasKey(TAG_ENCHANTMENTS, 9)) {
            return false;
        }
        NBTTagList list = tag.getTagList(TAG_ENCHANTMENTS, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (!isGlintPlaceholder(entry) && entry.hasKey("id", 99)
                    && Enchantment.getEnchantmentByID(entry.getShort("id")) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是不是光效占位：等级为 0 的条目（含空条目 {}）。0 级的附魔在原版里不起任何作用
     *
     * @param entry 附魔条目
     * @return true = 占位
     */
    private static boolean isGlintPlaceholder(@Nonnull NBTTagCompound entry) {
        return entry.getShort("lvl") == 0;
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
     *
     * @param value 开/关
     */
    private void setUnbreakable(boolean value) {
        if (value) {
            session.tag().setBoolean("Unbreakable", true);
        } else {
            NBTTagCompound tag = session.tagOrNull();
            if (tag != null) {
                tag.removeTag("Unbreakable");
            }
        }
    }

    /**
     * 附魔光效：靠一条 0 级的占位附魔实现，同时把附魔那一行藏起来
     *
     * @param value 开/关
     */
    private void setGlint(boolean value) {
        if (value) {
            NBTTagCompound tag = session.tag();
            NBTTagList enchantments = tag.getTagList(TAG_ENCHANTMENTS, 10);
            if (enchantments.hasNoTags()) {
                NBTTagCompound placeholder = new NBTTagCompound();
                placeholder.setShort("id", (short) GLINT_PLACEHOLDER_ID);
                placeholder.setShort("lvl", (short) 0);
                enchantments.appendTag(placeholder);
                tag.setTag(TAG_ENCHANTMENTS, enchantments);
            }
            setHideFlag(HIDE_ENCHANTMENTS, true);
            if (hideEnchantmentsToggle != null) {
                hideEnchantmentsToggle.setSelected(true);
            }
            return;
        }
        NBTTagCompound tag = session.tagOrNull();
        if (tag == null || !tag.hasKey(TAG_ENCHANTMENTS, 9)) {
            return;
        }
        NBTTagList enchantments = tag.getTagList(TAG_ENCHANTMENTS, 10);
        NBTTagList kept = new NBTTagList();
        for (int i = 0; i < enchantments.tagCount(); i++) {
            NBTTagCompound entry = enchantments.getCompoundTagAt(i);
            if (!isGlintPlaceholder(entry)) {
                kept.appendTag(entry);
            }
        }
        if (kept.hasNoTags()) {
            tag.removeTag(TAG_ENCHANTMENTS);
            // 一条附魔都没有了，"隐藏附魔信息"这一位不再有任何效果，跟着光效一起撤掉
            setHideFlag(HIDE_ENCHANTMENTS, false);
            if (hideEnchantmentsToggle != null) {
                hideEnchantmentsToggle.setSelected(false);
            }
        } else {
            tag.setTag(TAG_ENCHANTMENTS, kept);
        }
    }

    /**
     * 设置/清除 HideFlags 的某一位
     *
     * @param mask  位掩码
     * @param value 开/关
     */
    private void setHideFlag(int mask, boolean value) {
        NBTTagCompound tag = session.tag();
        int flags = tag.getInteger("HideFlags");
        flags = value ? (flags | mask) : (flags & ~mask);
        if (flags == 0) {
            tag.removeTag("HideFlags");
        } else {
            tag.setInteger("HideFlags", flags);
        }
    }

    /**
     * 耐久损耗（0 = 全新），只对会掉耐久、没有子类型的物品生效
     *
     * @param value 数值；Integer.MIN_VALUE = 归零
     */
    private void setDamage(int value) {
        ItemStack working = session.working();
        if (damageKind(working) != DamageKind.DURABILITY) {
            return;
        }
        session.setDamage(value == Integer.MIN_VALUE ? 0 : Mth.clamp(value, 0, working.getMaxDamage()));
    }

    /**
     * 铁砧修复惩罚
     *
     * @param value 数值；Integer.MIN_VALUE 或 0 = 清除
     */
    private void setRepairCost(int value) {
        if (value == Integer.MIN_VALUE || value <= 0) {
            NBTTagCompound tag = session.tagOrNull();
            if (tag != null) {
                tag.removeTag("RepairCost");
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
