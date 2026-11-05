package pers.roinflam.battlecorrection.client.editor.page;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import pers.roinflam.battlecorrection.client.editor.EditorSession;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.ItemEditorScreen;
import pers.roinflam.battlecorrection.client.editor.ItemRoots;
import pers.roinflam.battlecorrection.client.editor.ItemTemplates;
import pers.roinflam.battlecorrection.client.editor.widget.FlatButton;
import pers.roinflam.battlecorrection.client.editor.widget.FlatEditBox;
import pers.roinflam.battlecorrection.client.editor.widget.FlatToggle;
import pers.roinflam.battlecorrection.client.editor.widget.ScrollList;
import pers.roinflam.battlecorrection.compat.CurioMark;
import pers.roinflam.battlecorrection.editor.EditorPage;
import pers.roinflam.battlecorrection.editor.ItemQuality;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 模板页：把当前物品存成模板，或把模板套到当前物品上
 * <p>
 * 左边是已存的模板列表，右边上半是"存为模板"（起个名字），下半是"套用"：勾选要套哪些部分——名字、Lore、附魔、属性、饰品栏、
 * 标签、其他——全勾就是整份数据原样覆盖；只勾一部分就只替换那部分，物品上其他东西不动。
 * 模板存在客户端本地文件里（见 {@link ItemTemplates}），换电脑不带。
 * <p>
 * 1.21.1 每一部分对应的是一组数据组件（附魔光效改用独立组件，归在"附魔"里，和 1.20.1 借用附魔列表时一样），
 * 本模组和别的模组的自定义数据在 {@code minecraft:custom_data} 里按键名分到各部分，没认领的键归"其他"。
 * 1.20.1 的 HideFlags 拆进了各组件自己的显示开关，所以"隐藏信息"跟着各自的组件走，"标签"里只剩"隐藏其他信息"这一项。
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
        NAME("name", List.of(DataComponents.CUSTOM_NAME), List.of()),
        LORE("lore", List.of(DataComponents.LORE), List.of()),
        ENCHANTMENTS("enchantments", List.of(DataComponents.ENCHANTMENTS, DataComponents.STORED_ENCHANTMENTS,
                DataComponents.ENCHANTMENT_GLINT_OVERRIDE), List.of()),
        ATTRIBUTES("attributes", List.of(DataComponents.ATTRIBUTE_MODIFIERS), List.of(CurioMark.TAG_MODIFIERS)),
        CURIO("curio", List.of(), List.of(CurioMark.TAG_SLOTS)),
        TAGS("tags", List.of(DataComponents.UNBREAKABLE, DataComponents.HIDE_ADDITIONAL_TOOLTIP,
                DataComponents.CUSTOM_MODEL_DATA, DataComponents.REPAIR_COST), qualityKeys()),
        OTHER("other", List.of(), List.of());

        final String langKey;
        /**
         * 这一部分管的数据组件
         */
        final List<DataComponentType<?>> components;
        /**
         * 这一部分管的 custom_data 键
         */
        final List<String> dataKeys;

        Part(@Nonnull String langKey, @Nonnull List<DataComponentType<?>> components, @Nonnull List<String> dataKeys) {
            this.langKey = langKey;
            this.components = components;
            this.dataKeys = dataKeys;
        }

        @Nonnull
        private static List<String> qualityKeys() {
            List<String> keys = new ArrayList<>();
            for (ItemQuality quality : ItemQuality.values()) {
                if (quality.nbtKey() != null) {
                    keys.add(quality.nbtKey());
                }
            }
            return keys;
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
        if (ItemTemplates.skipped() > 0 && status.getString().isEmpty()) {
            // 1.20.1 存的旧模板有升级不了的：不显示，但留在文件里，提示一句
            setStatus("gui.battlecorrection.editor.templates.status.skipped", EditorTheme.WARNING, ItemTemplates.skipped());
        }

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
        ItemTemplates.save(name, session.working(), session.itemId(), session.registries());
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
     * <p>
     * 模板里的组件按当前世界的注册表解析，解析不了（比如附魔在这个服务器上不存在）就什么都不改，状态栏报原因。
     */
    private void applySelected() {
        if (list == null || list.selectedItem() == null) {
            return;
        }
        ItemTemplates.Template template = list.selectedItem().template;
        boolean all = selectedParts.values().stream().allMatch(Boolean::booleanValue);
        DataComponentPatch patch;
        try {
            patch = template.patch(session.registries());
        } catch (IllegalArgumentException e) {
            setStatus("gui.battlecorrection.editor.templates.status.invalid", EditorTheme.DANGER, String.valueOf(e.getMessage()));
            return;
        }

        if (all) {
            // 整份套用：组件补丁原样换上，数量跟着走（数量不属于任何一部分，只在整份套用时带）
            ItemStack working = session.working();
            session.replaceWorking(new ItemStack(working.getItemHolder(), template.count(), patch));
        } else {
            ItemStack working = session.working();
            for (Part part : Part.values()) {
                if (!selectedParts.get(part)) {
                    continue;
                }
                if (part == Part.OTHER) {
                    copyOther(working, patch);
                    continue;
                }
                for (DataComponentType<?> type : part.components) {
                    ItemRoots.copyFromPatch(working, patch, type);
                }
                if (!part.dataKeys.isEmpty()) {
                    copyDataKeys(working, patch, part.dataKeys, false);
                }
            }
            session.cleanupEmpty();
        }
        setStatus("gui.battlecorrection.editor.templates.status.applied", EditorTheme.SUCCESS, template.name());
    }

    /**
     * 模板补丁里的 custom_data（没有就是空的）
     */
    @Nonnull
    private static CompoundTag patchData(@Nonnull DataComponentPatch patch) {
        Optional<? extends CustomData> data = patch.get(DataComponents.CUSTOM_DATA);
        return data == null || data.isEmpty() ? new CompoundTag() : data.get().copyTag();
    }

    /**
     * 按键名替换 custom_data 里的一部分：目标上这些键先删，模板里有的再拷过来
     *
     * @param keys   键名
     * @param invert true = 处理的是"不在 keys 里的所有键"（"其他"用）
     */
    private static void copyDataKeys(@Nonnull ItemStack target, @Nonnull DataComponentPatch patch,
                                     @Nonnull List<String> keys, boolean invert) {
        CompoundTag source = patchData(patch);
        CustomData.update(DataComponents.CUSTOM_DATA, target, tag -> {
            for (String key : new ArrayList<>(tag.getAllKeys())) {
                if (keys.contains(key) != invert) {
                    tag.remove(key);
                }
            }
            for (String key : source.getAllKeys()) {
                if (keys.contains(key) != invert) {
                    tag.put(key, source.get(key).copy());
                }
            }
        });
    }

    /**
     * 复制"其他"：不属于任何已知部分的组件（目标和模板里出现过的都算），以及 custom_data 里没被认领的键
     */
    private static void copyOther(@Nonnull ItemStack target, @Nonnull DataComponentPatch patch) {
        List<DataComponentType<?>> known = new ArrayList<>();
        List<String> knownKeys = new ArrayList<>();
        for (Part part : Part.values()) {
            known.addAll(part.components);
            knownKeys.addAll(part.dataKeys);
        }
        known.add(DataComponents.CUSTOM_DATA);
        Set<DataComponentType<?>> others = new LinkedHashSet<>();
        for (var entry : target.getComponentsPatch().entrySet()) {
            others.add(entry.getKey());
        }
        for (var entry : patch.entrySet()) {
            others.add(entry.getKey());
        }
        for (DataComponentType<?> type : others) {
            if (!known.contains(type) && !type.isTransient()) {
                ItemRoots.copyFromPatch(target, patch, type);
            }
        }
        copyDataKeys(target, patch, knownKeys, true);
    }
}
