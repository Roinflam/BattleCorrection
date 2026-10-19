package pers.roinflam.battlecorrection.client.editor.page;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import pers.roinflam.battlecorrection.client.editor.EditorSession;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.ItemEditorScreen;
import pers.roinflam.battlecorrection.client.editor.ItemTemplates;
import pers.roinflam.battlecorrection.client.editor.ui.Component;
import pers.roinflam.battlecorrection.client.editor.ui.GuiGraphics;
import pers.roinflam.battlecorrection.client.editor.ui.Mth;
import pers.roinflam.battlecorrection.client.editor.widget.FlatButton;
import pers.roinflam.battlecorrection.client.editor.widget.FlatEditBox;
import pers.roinflam.battlecorrection.client.editor.widget.FlatToggle;
import pers.roinflam.battlecorrection.client.editor.widget.ScrollList;
import pers.roinflam.battlecorrection.compat.BaubleMark;
import pers.roinflam.battlecorrection.editor.EditorPage;
import pers.roinflam.battlecorrection.network.packet.ApplyItemEditC2SPacket;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 模板页：把当前物品存成模板，或把模板套到当前物品上
 * <p>
 * 左边是已存的模板列表，右边上半是"存为模板"（起个名字），下半是"套用"：勾选要套哪些部分——名字、Lore、附魔、属性、饰品栏标记、
 * 标签页那些、其他——全勾就是整份 NBT 原样覆盖；只勾一部分就只替换那部分的键，物品上其他东西不动。
 * 模板存在客户端本地文件里（见 {@link ItemTemplates}），换电脑不带。
 * <p>
 * 1.12.2 的损害值在物品根上：会掉耐久的物品是耐久，归在"标签页那些"里（标签页上就能改它）；
 * 有子类型的物品是子类型，属于物品本身，套模板不动它，换种类用 F4。
 */
public class TemplatesPage extends EditorPageBase {

    private static final int COLUMN_GAP = 8;
    private static final int BOX_HEIGHT = 14;
    private static final int ROW_GAP = 3;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM-dd HH:mm");

    /**
     * 可以单独套用的部分
     */
    private enum Part {
        NAME("name"),
        LORE("lore"),
        ENCHANTMENTS("enchantments", "ench", "StoredEnchantments"),
        ATTRIBUTES("attributes", "AttributeModifiers", BaubleMark.TAG_MODIFIERS),
        CURIO("curio", BaubleMark.TAG_SLOTS),
        TAGS("tags", "Unbreakable", "HideFlags", "RepairCost"),
        OTHER("other");

        final String langKey;
        final String[] keys;

        Part(@Nonnull String langKey, @Nonnull String... keys) {
            this.langKey = langKey;
            this.keys = keys;
        }

        @Nonnull
        Component label() {
            return Component.translatable("gui.battlecorrection.editor.templates.part." + langKey);
        }
    }

    /**
     * 列表里的一份模板
     */
    private static final class TemplateRow implements ScrollList.Row {
        final ItemTemplates.Template template;

        TemplateRow(@Nonnull ItemTemplates.Template template) {
            this.template = template;
        }

        @Nonnull
        @Override
        public Component label() {
            // 模板名是玩家自己起的，分节号原样显示
            return Component.raw(template.name());
        }

        @Nullable
        @Override
        public Component secondary() {
            return Component.raw(template.item().getResourcePath() + " ×" + template.count() + "  "
                    + DATE_FORMAT.format(new Date(template.created())));
        }
    }

    private final Map<Part, Boolean> selectedParts = new EnumMap<>(Part.class);
    private Component status = Component.empty();
    private int statusColor = EditorTheme.TEXT_DIM;

    @Nullable
    private ScrollList<TemplateRow> list;
    @Nullable
    private FlatEditBox nameBox;
    @Nullable
    private FlatButton applyButton;
    @Nullable
    private FlatButton deleteButton;
    private int rightX;
    private int applyLabelY;

    /**
     * @param screen  所属界面
     * @param session 编辑会话
     */
    public TemplatesPage(@Nonnull ItemEditorScreen screen, @Nonnull EditorSession session) {
        super(screen, session);
        for (Part part : Part.values()) {
            selectedParts.put(part, true);
        }
    }

    @Nonnull
    @Override
    public EditorPage page() {
        return EditorPage.TEMPLATES;
    }

    @Override
    public boolean wantsPreview() {
        return false;
    }

    @Override
    protected void build() {
        int columnWidth = (width - COLUMN_GAP) / 2;
        rightX = x + columnWidth + COLUMN_GAP;
        int listTop = y + 11;

        // 左：模板列表（右键删除）
        list = add(new ScrollList<TemplateRow>(x, listTop, columnWidth, y + height - listTop,
                Component.translatable("gui.battlecorrection.editor.templates.empty"))
                .onSelect(row -> updateButtons())
                .onRightClick(row -> deleteTemplate(row.template)));
        refreshList();

        // 右上：存为模板
        int cursorY = listTop;
        int saveWidth = 48;
        nameBox = add(new FlatEditBox(font, rightX + 1, cursorY, columnWidth - saveWidth - 6, BOX_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.templates.name.hint"), false));
        nameBox.setMaxLength(48);
        add(new FlatButton(rightX + columnWidth - saveWidth, cursorY, saveWidth, BOX_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.templates.save"), this::saveTemplate)
                .style(FlatButton.Style.PRIMARY)
                .tooltip(Component.translatable("gui.battlecorrection.editor.templates.save.tooltip")));
        cursorY += BOX_HEIGHT + 10;

        // 右下：套用哪些部分（两列）
        applyLabelY = cursorY;
        cursorY += 11;
        Part[] parts = Part.values();
        int halfWidth = (columnWidth - 4) / 2;
        for (int i = 0; i < parts.length; i++) {
            Part part = parts[i];
            int toggleX = rightX + (i % 2) * (halfWidth + 4);
            int toggleY = cursorY + (i / 2) * (FlatToggle.HEIGHT + ROW_GAP);
            FlatToggle toggle = add(new FlatToggle(toggleX, toggleY, part.label(), selectedParts.get(part),
                    value -> selectedParts.put(part, value)));
            if (part == Part.TAGS) {
                toggle.tooltip(Component.translatable("gui.battlecorrection.editor.templates.part.tags.tooltip"));
            }
        }
        cursorY += ((parts.length + 1) / 2) * (FlatToggle.HEIGHT + ROW_GAP) + 4;

        int buttonWidth = (columnWidth - 4) / 2;
        applyButton = add(new FlatButton(rightX, cursorY, buttonWidth, BOX_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.templates.apply"), this::applySelected)
                .style(FlatButton.Style.PRIMARY)
                .tooltip(Component.translatable("gui.battlecorrection.editor.templates.apply.tooltip")));
        deleteButton = add(new FlatButton(rightX + buttonWidth + 4, cursorY, buttonWidth, BOX_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.templates.delete"), () -> {
            if (list != null && list.selectedItem() != null) {
                deleteTemplate(list.selectedItem().template);
            }
        }).style(FlatButton.Style.DANGER));
        updateButtons();
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        label(g, Component.translatable("gui.battlecorrection.editor.templates.list"), x, y);
        label(g, Component.translatable("gui.battlecorrection.editor.templates.save_section"), rightX, y);
        label(g, Component.translatable("gui.battlecorrection.editor.templates.apply_section"), rightX, applyLabelY);
        EditorTheme.divider(g, rightX, applyLabelY - 4, width - (rightX - x));
        int statusWidth = font.width(status);
        if (statusWidth > 0) {
            int statusY = y + height - font.lineHeight;
            g.drawString(font, status, rightX + (width - (rightX - x)) - statusWidth, statusY, statusColor, false);
        }
    }

    private void refreshList() {
        if (list == null) {
            return;
        }
        List<TemplateRow> rows = new ArrayList<>();
        for (ItemTemplates.Template template : ItemTemplates.all()) {
            rows.add(new TemplateRow(template));
        }
        list.setItems(rows);
        updateButtons();
    }

    private void updateButtons() {
        boolean hasSelection = list != null && list.selectedItem() != null;
        if (applyButton != null) {
            applyButton.active = hasSelection;
        }
        if (deleteButton != null) {
            deleteButton.active = hasSelection;
        }
    }

    private void setStatus(@Nonnull String key, int color, @Nonnull Object... args) {
        status = Component.translatable(key, args);
        statusColor = color;
    }

    /**
     * 把当前物品存成模板
     */
    private void saveTemplate() {
        if (nameBox == null) {
            return;
        }
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) {
            setStatus("gui.battlecorrection.editor.templates.status.name_empty", EditorTheme.WARNING);
            return;
        }
        session.cleanupEmpty();
        ItemTemplates.save(name, session.working(), session.itemId());
        refreshList();
        setStatus("gui.battlecorrection.editor.templates.status.saved", EditorTheme.SUCCESS, Component.raw(name));
    }

    /**
     * 删除一份模板
     */
    private void deleteTemplate(@Nonnull ItemTemplates.Template template) {
        ItemTemplates.delete(template.name());
        if (list != null) {
            list.select(null);
        }
        refreshList();
        setStatus("gui.battlecorrection.editor.templates.status.deleted", EditorTheme.SUCCESS, Component.raw(template.name()));
    }

    /**
     * 把选中的模板按勾选的部分套到当前物品上
     */
    private void applySelected() {
        if (list == null || list.selectedItem() == null) {
            return;
        }
        ItemTemplates.Template template = list.selectedItem().template;
        boolean all = selectedParts.values().stream().allMatch(Boolean::booleanValue);

        if (all) {
            session.setTag(template.tag().copy());
        } else {
            NBTTagCompound source = template.tag();
            NBTTagCompound target = session.tag();
            for (Part part : Part.values()) {
                if (!selectedParts.get(part)) {
                    continue;
                }
                switch (part) {
                    case NAME:
                        copyDisplayKey(source, target, "Name");
                        break;
                    case LORE:
                        copyDisplayKey(source, target, "Lore");
                        break;
                    case OTHER:
                        copyOtherKeys(source, target);
                        break;
                    default:
                        for (String key : part.keys) {
                            target.removeTag(key);
                            if (source.hasKey(key)) {
                                target.setTag(key, source.getTag(key).copy());
                            }
                        }
                        break;
                }
            }
            session.cleanupEmpty();
        }
        // 耐久不在 tag 里，跟着"标签页那些"走（全勾时也在里面）
        if (selectedParts.get(Part.TAGS)) {
            applyDurability(template);
        }
        // 数量不属于任何一部分，只在整份套用时跟着走
        if (all) {
            session.working().setCount(Mth.clamp(template.count(), ApplyItemEditC2SPacket.MIN_COUNT,
                    ApplyItemEditC2SPacket.MAX_COUNT));
        }
        setStatus("gui.battlecorrection.editor.templates.status.applied", EditorTheme.SUCCESS, Component.raw(template.name()));
    }

    /**
     * 套耐久：当前物品和模板的物品都把损害值当耐久用时，换成模板的（按当前物品的最大耐久夹住）；
     * 模板来自不掉耐久的物品时当作全新（0）。当前物品有子类型或不掉耐久时不动——那时损害值是物品本身的一部分
     *
     * @param template 模板
     */
    private void applyDurability(@Nonnull ItemTemplates.Template template) {
        ItemStack working = session.working();
        if (!usesDurability(working)) {
            return;
        }
        Item sourceItem = ForgeRegistries.ITEMS.containsKey(template.item()) ? ForgeRegistries.ITEMS.getValue(template.item()) : null;
        boolean sourceDurability = sourceItem != null && usesDurability(new ItemStack(sourceItem));
        int damage = sourceDurability ? template.damage() : 0;
        session.setDamage(Mth.clamp(damage, 0, working.getMaxDamage()));
    }

    /**
     * 损害值在这件物品上是不是耐久（会掉耐久、没有子类型）
     */
    private static boolean usesDurability(@Nonnull ItemStack stack) {
        return !stack.isEmpty() && !stack.getHasSubtypes() && stack.getMaxDamage() > 0;
    }

    /**
     * 复制 display 下的一个键（Name / Lore）
     */
    private static void copyDisplayKey(@Nonnull NBTTagCompound source, @Nonnull NBTTagCompound target, @Nonnull String key) {
        NBTTagCompound targetDisplay = target.getCompoundTag("display");
        targetDisplay.removeTag(key);
        if (source.hasKey("display", 10) && source.getCompoundTag("display").hasKey(key)) {
            targetDisplay.setTag(key, source.getCompoundTag("display").getTag(key).copy());
        }
        if (targetDisplay.hasNoTags()) {
            target.removeTag("display");
        } else {
            target.setTag("display", targetDisplay);
        }
    }

    /**
     * 复制"其他"：不属于任何已知部分的顶层键，以及 display 下除 Name/Lore 之外的键（皮革的 color 之类）
     */
    private static void copyOtherKeys(@Nonnull NBTTagCompound source, @Nonnull NBTTagCompound target) {
        List<String> known = new ArrayList<>();
        for (Part part : Part.values()) {
            for (String key : part.keys) {
                known.add(key);
            }
        }
        for (String key : new ArrayList<>(target.getKeySet())) {
            if (!known.contains(key) && !"display".equals(key)) {
                target.removeTag(key);
            }
        }
        for (String key : source.getKeySet()) {
            if (!known.contains(key) && !"display".equals(key)) {
                target.setTag(key, source.getTag(key).copy());
            }
        }
        if (source.hasKey("display", 10)) {
            NBTTagCompound sourceDisplay = source.getCompoundTag("display");
            NBTTagCompound targetDisplay = target.getCompoundTag("display");
            for (String key : sourceDisplay.getKeySet()) {
                if (!"Name".equals(key) && !"Lore".equals(key)) {
                    targetDisplay.setTag(key, sourceDisplay.getTag(key).copy());
                }
            }
            if (!targetDisplay.hasNoTags()) {
                target.setTag("display", targetDisplay);
            }
        }
    }
}
