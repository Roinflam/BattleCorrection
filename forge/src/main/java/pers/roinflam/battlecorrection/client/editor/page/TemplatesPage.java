package pers.roinflam.battlecorrection.client.editor.page;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.battlecorrection.client.editor.EditorSession;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.ItemEditorScreen;
import pers.roinflam.battlecorrection.client.editor.ItemTemplates;
import pers.roinflam.battlecorrection.client.editor.widget.FlatButton;
import pers.roinflam.battlecorrection.client.editor.widget.FlatEditBox;
import pers.roinflam.battlecorrection.client.editor.widget.FlatToggle;
import pers.roinflam.battlecorrection.client.editor.widget.ScrollList;
import pers.roinflam.battlecorrection.editor.EditorPage;

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
 * 左边是已存的模板列表，右边上半是"存为模板"（起个名字），下半是"套用"：勾选要套哪些部分——名字、Lore、附魔、属性、饰品栏、
 * 标签、其他——全勾就是整份 NBT 原样覆盖；只勾一部分就只替换那部分的键，物品上其他东西不动。
 * 模板存在客户端本地文件里（见 {@link ItemTemplates}），换电脑不带。
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
        ENCHANTMENTS("enchantments", "Enchantments", "StoredEnchantments"),
        ATTRIBUTES("attributes", "AttributeModifiers", "BattleCorrectionCurioModifiers"),
        CURIO("curio", "BattleCorrectionCurioSlots"),
        TAGS("tags", "Unbreakable", "HideFlags", "CustomModelData", "RepairCost", "Epic", "Legendary", "Myth", "Genesis"),
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
            return Component.literal(template.name());
        }

        @Nullable
        @Override
        public Component secondary() {
            return Component.literal(template.item().getPath() + " ×" + template.count() + "  "
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
            add(new FlatToggle(toggleX, toggleY, part.label(), selectedParts.get(part),
                    value -> selectedParts.put(part, value)));
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
        setStatus("gui.battlecorrection.editor.templates.status.saved", EditorTheme.SUCCESS, name);
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
        setStatus("gui.battlecorrection.editor.templates.status.deleted", EditorTheme.SUCCESS, template.name());
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
        ItemStack working = session.working();

        if (all) {
            session.setTag(template.tag().copy());
        } else {
            CompoundTag source = template.tag();
            CompoundTag target = session.tag();
            for (Part part : Part.values()) {
                if (!selectedParts.get(part)) {
                    continue;
                }
                switch (part) {
                    case NAME -> copyDisplayKey(source, target, "Name");
                    case LORE -> copyDisplayKey(source, target, "Lore");
                    case OTHER -> copyOtherKeys(source, target);
                    default -> {
                        for (String key : part.keys) {
                            target.remove(key);
                            if (source.contains(key)) {
                                target.put(key, source.get(key).copy());
                            }
                        }
                    }
                }
            }
            session.cleanupEmpty();
        }
        // 数量不属于任何一部分，只在整份套用时跟着走
        if (all) {
            working.setCount(template.count());
        }
        setStatus("gui.battlecorrection.editor.templates.status.applied", EditorTheme.SUCCESS, template.name());
    }

    /**
     * 复制 display 下的一个键（Name / Lore）
     */
    private static void copyDisplayKey(@Nonnull CompoundTag source, @Nonnull CompoundTag target, @Nonnull String key) {
        CompoundTag targetDisplay = target.getCompound("display");
        targetDisplay.remove(key);
        if (source.contains("display", Tag.TAG_COMPOUND) && source.getCompound("display").contains(key)) {
            targetDisplay.put(key, source.getCompound("display").get(key).copy());
        }
        if (targetDisplay.isEmpty()) {
            target.remove("display");
        } else {
            target.put("display", targetDisplay);
        }
    }

    /**
     * 复制"其他"：不属于任何已知部分的顶层键，以及 display 下除 Name/Lore 之外的键
     */
    private static void copyOtherKeys(@Nonnull CompoundTag source, @Nonnull CompoundTag target) {
        List<String> known = new ArrayList<>();
        for (Part part : Part.values()) {
            for (String key : part.keys) {
                known.add(key);
            }
        }
        for (String key : new ArrayList<>(target.getAllKeys())) {
            if (!known.contains(key) && !"display".equals(key)) {
                target.remove(key);
            }
        }
        for (String key : source.getAllKeys()) {
            if (!known.contains(key) && !"display".equals(key)) {
                target.put(key, source.get(key).copy());
            }
        }
        if (source.contains("display", Tag.TAG_COMPOUND)) {
            CompoundTag sourceDisplay = source.getCompound("display");
            CompoundTag targetDisplay = target.getCompound("display");
            for (String key : sourceDisplay.getAllKeys()) {
                if (!"Name".equals(key) && !"Lore".equals(key)) {
                    targetDisplay.put(key, sourceDisplay.get(key).copy());
                }
            }
            if (!targetDisplay.isEmpty()) {
                target.put("display", targetDisplay);
            }
        }
    }
}
