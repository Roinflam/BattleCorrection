package pers.roinflam.battlecorrection.client.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.battlecorrection.client.editor.widget.FlatEditBox;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 换物品：搜索并挑选一种物品
 * <p>
 * 注册表里有几千上万种物品（装了大型模组更多），所以列表是虚拟滚动——整个列表一个控件，只画看得见的那十几行，
 * 过滤也是在预先算好的小写名字上做子串匹配，打一个字立刻出结果。
 * <p>
 * 搜索规则：空格分开的每个词都得出现在"显示名"或"注册名"里（不分大小写）；{@code @模组ID} 限定模组，
 * 例如 {@code @minecraft 剑}。没写任何东西时，最近挑过的排在最上面。
 * 键盘：上 / 下 / PageUp / PageDown / Home / End 移动，Enter 确认，Esc 取消；鼠标点一行直接确认。
 * <p>
 * 条目表（含显示名）按游戏语言缓存一份，第一次打开时建，之后复用；换了语言才重建。
 */
public class ItemPickerScreen extends Screen {

    private static final int WIDTH = 330;
    private static final int MAX_HEIGHT = 238;
    /**
     * 面板四角的准线角饰占的宽度，标题和底栏文字从它里面让开
     */
    private static final int CORNER_CLEARANCE = 22;
    private static final int ROW_HEIGHT = 18;
    private static final int PAD = 8;
    private static final int SEARCH_HEIGHT = 16;
    private static final int RECENT_LIMIT = 12;
    private static final int SCROLLBAR_WIDTH = 3;

    /**
     * 一个候选物品
     */
    private record Entry(ResourceLocation id, ItemStack icon, String name, String lowerName, String lowerId) {
    }

    @Nullable
    private static List<Entry> cache;
    private static String cacheLanguage = "";
    private static final Deque<ResourceLocation> RECENT = new ArrayDeque<>();

    private final Screen parent;
    private final Consumer<ResourceLocation> onPick;
    private final ResourceLocation current;

    private List<Entry> results = new ArrayList<>();
    private int selected;
    private int scrollRows;
    private int hovered = -1;
    private int boxX;
    private int boxY;
    private int boxHeight;
    private int listX;
    private int listY;
    private int listWidth;
    private int listHeight;
    @Nullable
    private FlatEditBox search;

    /**
     * @param parent  关掉后回到的界面（编辑器）
     * @param current 现在的物品，用来定位初始选中
     * @param onPick  选定后的回调，回调时已经回到 parent
     */
    public ItemPickerScreen(@Nonnull Screen parent, @Nonnull ResourceLocation current,
                            @Nonnull Consumer<ResourceLocation> onPick) {
        super(Component.translatable("gui.battlecorrection.editor.item.picker.title"));
        this.parent = parent;
        this.current = current;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        boxHeight = Math.min(MAX_HEIGHT, height - 16);
        boxX = (width - WIDTH) / 2;
        boxY = (height - boxHeight) / 2;
        listX = boxX + PAD;
        listY = boxY + 24 + SEARCH_HEIGHT + 8;
        listWidth = WIDTH - 2 * PAD;
        listHeight = Math.max(ROW_HEIGHT, boxY + boxHeight - 22 - listY);

        String previous = search == null ? "" : search.getValue();
        search = new FlatEditBox(font, boxX + PAD + 1, boxY + 24, WIDTH - 2 * PAD - 2, SEARCH_HEIGHT,
                Component.translatable("gui.battlecorrection.editor.item.picker.hint"), false);
        search.setMaxLength(120);
        search.setValue(previous);
        search.setResponder(text -> refilter());
        addRenderableWidget(search);
        setInitialFocus(search);
        ensureEntries();
        refilter();
        if (previous.isEmpty()) {
            for (int i = 0; i < results.size(); i++) {
                if (results.get(i).id().equals(current)) {
                    selected = i;
                    scrollRows = Math.max(0, i - visibleRows() / 2);
                    break;
                }
            }
            clampScroll();
        }
    }

    // 条目表与过滤

    /**
     * 建（或复用）条目表
     */
    private void ensureEntries() {
        Minecraft mc = Minecraft.getInstance();
        String language = mc.getLanguageManager().getSelected();
        if (cache != null && language.equals(cacheLanguage)) {
            return;
        }
        List<Entry> entries = new ArrayList<>(ForgeRegistries.ITEMS.getKeys().size());
        for (ResourceLocation id : ForgeRegistries.ITEMS.getKeys()) {
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item == null || item == Items.AIR) {
                continue;
            }
            ItemStack icon = new ItemStack(item);
            String name;
            try {
                name = item.getDescription().getString();
            } catch (RuntimeException e) {
                name = id.getPath();
            }
            entries.add(new Entry(id, icon, name, name.toLowerCase(Locale.ROOT), id.toString().toLowerCase(Locale.ROOT)));
        }
        entries.sort(Comparator.comparing((Entry e) -> e.id().getNamespace().equals("minecraft") ? 0 : 1)
                .thenComparing(e -> e.id().toString()));
        cache = entries;
        cacheLanguage = language;
    }

    /**
     * 按搜索框内容重新过滤
     */
    private void refilter() {
        List<Entry> all = cache == null ? List.of() : cache;
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>();
        if (query.isEmpty()) {
            for (ResourceLocation id : RECENT) {
                for (Entry entry : all) {
                    if (entry.id().equals(id)) {
                        out.add(entry);
                        break;
                    }
                }
            }
            for (Entry entry : all) {
                if (!RECENT.contains(entry.id())) {
                    out.add(entry);
                }
            }
        } else {
            String[] words = query.split("\\s+");
            String namespace = null;
            List<String> terms = new ArrayList<>();
            for (String word : words) {
                if (word.length() > 1 && word.charAt(0) == '@') {
                    namespace = word.substring(1);
                } else if (!word.isEmpty()) {
                    terms.add(word);
                }
            }
            List<Entry> prefix = new ArrayList<>();
            List<Entry> rest = new ArrayList<>();
            for (Entry entry : all) {
                if (namespace != null && !entry.id().getNamespace().startsWith(namespace)) {
                    continue;
                }
                boolean ok = true;
                for (String term : terms) {
                    if (!entry.lowerName().contains(term) && !entry.lowerId().contains(term)) {
                        ok = false;
                        break;
                    }
                }
                if (!ok) {
                    continue;
                }
                boolean startsWith = !terms.isEmpty() && (entry.lowerName().startsWith(terms.get(0))
                        || entry.id().getPath().startsWith(terms.get(0)));
                (startsWith ? prefix : rest).add(entry);
            }
            out.addAll(prefix);
            out.addAll(rest);
        }
        results = out;
        selected = 0;
        scrollRows = 0;
    }

    // 绘制

    private int visibleRows() {
        return Math.max(1, listHeight / ROW_HEIGHT);
    }

    private void clampScroll() {
        int max = Math.max(0, results.size() - visibleRows());
        scrollRows = Mth.clamp(scrollRows, 0, max);
    }

    private void ensureSelectedVisible() {
        if (selected < scrollRows) {
            scrollRows = selected;
        } else if (selected >= scrollRows + visibleRows()) {
            scrollRows = selected - visibleRows() + 1;
        }
        clampScroll();
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        EditorTheme.panel(g, boxX, boxY, WIDTH, boxHeight);
        g.drawString(font, title, boxX + CORNER_CLEARANCE, boxY + 9, EditorTheme.ACCENT_HI, false);
        EditorTheme.headingLine(g, boxX + PAD, boxY + 20, WIDTH - 2 * PAD);

        EditorTheme.well(g, listX, listY, listWidth, listHeight, false);
        hovered = -1;
        int rows = visibleRows();
        g.enableScissor(listX + 1, listY + 1, listX + listWidth - 1, listY + listHeight - 1);
        for (int i = 0; i < rows; i++) {
            int index = scrollRows + i;
            if (index >= results.size()) {
                break;
            }
            Entry entry = results.get(index);
            int rowY = listY + 1 + i * ROW_HEIGHT;
            boolean over = mouseX >= listX && mouseX < listX + listWidth - SCROLLBAR_WIDTH - 2
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT && mouseY < listY + listHeight - 1;
            if (over) {
                hovered = index;
            }
            if (index == selected) {
                EditorTheme.rect(g, listX + 1, rowY, listWidth - 2, ROW_HEIGHT, EditorTheme.ACCENT_SOFT);
            } else if (over) {
                EditorTheme.rect(g, listX + 1, rowY, listWidth - 2, ROW_HEIGHT, EditorTheme.ACCENT_GLOW);
            }
            g.renderItem(entry.icon(), listX + 3, rowY + 1);
            boolean isCurrent = entry.id().equals(current);
            int nameColor = isCurrent ? EditorTheme.ACCENT_HI : EditorTheme.TEXT;
            String name = font.plainSubstrByWidth(entry.name(), listWidth - 150);
            g.drawString(font, name, listX + 24, rowY + 5, nameColor, false);
            String id = entry.id().toString();
            int idWidth = font.width(id);
            int idMax = 130;
            if (idWidth > idMax) {
                id = font.plainSubstrByWidth(id, idMax - font.width("…")) + "…";
                idWidth = font.width(id);
            }
            g.drawString(font, id, listX + listWidth - SCROLLBAR_WIDTH - 6 - idWidth, rowY + 5, EditorTheme.TEXT_DIM, false);
        }
        g.disableScissor();

        // 滚动条
        if (results.size() > rows) {
            int trackX = listX + listWidth - SCROLLBAR_WIDTH - 1;
            int thumbHeight = Math.max(10, listHeight * rows / results.size());
            int maxScroll = results.size() - rows;
            int thumbY = listY + 1 + (listHeight - 2 - thumbHeight) * scrollRows / Math.max(1, maxScroll);
            EditorTheme.rect(g, trackX, listY + 1, SCROLLBAR_WIDTH, listHeight - 2, EditorTheme.GROOVE);
            EditorTheme.rect(g, trackX, thumbY, SCROLLBAR_WIDTH, thumbHeight, EditorTheme.ACCENT_MID);
        }
        if (results.isEmpty()) {
            Component empty = Component.translatable("gui.battlecorrection.editor.item.picker.empty");
            g.drawString(font, empty, listX + (listWidth - font.width(empty)) / 2, listY + listHeight / 2 - 4,
                    EditorTheme.TEXT_DIM, false);
        }

        Component count = Component.translatable("gui.battlecorrection.editor.item.picker.count", results.size());
        g.drawString(font, count, boxX + CORNER_CLEARANCE, boxY + boxHeight - 15, EditorTheme.TEXT_MUTED, false);
        Component hint = Component.translatable("gui.battlecorrection.editor.item.picker.keys");
        int hintX = Math.max(boxX + CORNER_CLEARANCE + font.width(count) + 8, boxX + WIDTH - CORNER_CLEARANCE - font.width(hint));
        g.drawString(font, hint, hintX, boxY + boxHeight - 15, EditorTheme.TEXT_DIM, false);

        super.render(g, mouseX, mouseY, partialTick);
    }

    // 输入

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                cancel();
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                pickSelected();
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                move(1);
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                move(-1);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                move(visibleRows());
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                move(-visibleRows());
                return true;
            }
            default -> {
            }
        }
        // Home / End 留给搜索框移动光标；其余交给搜索框打字
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void move(int delta) {
        if (results.isEmpty()) {
            return;
        }
        selected = Mth.clamp(selected + delta, 0, results.size() - 1);
        ensureSelectedVisible();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 按点击的坐标直接算是哪一行，不靠渲染时记下的悬停行（鼠标在两帧之间动了、或者点击不是真鼠标发来的，悬停行就是旧的）
            int row = rowAt(mouseX, mouseY);
            if (row >= 0) {
                selected = row;
                pickSelected();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 坐标落在哪一行的结果下标；不在列表里（含滚动条）返回 -1
     */
    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < listX || mouseX >= listX + listWidth - SCROLLBAR_WIDTH - 2
                || mouseY < listY + 1 || mouseY >= listY + listHeight - 1) {
            return -1;
        }
        int index = scrollRows + (int) ((mouseY - listY - 1) / ROW_HEIGHT);
        return index >= 0 && index < results.size() ? index : -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= listX && mouseX < listX + listWidth && mouseY >= listY && mouseY < listY + listHeight) {
            scrollRows -= (int) Math.signum(delta) * 3;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void pickSelected() {
        if (selected < 0 || selected >= results.size()) {
            return;
        }
        ResourceLocation id = results.get(selected).id();
        RECENT.remove(id);
        RECENT.addFirst(id);
        while (RECENT.size() > RECENT_LIMIT) {
            RECENT.removeLast();
        }
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
        onPick.accept(id);
    }

    private void cancel() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * 搜索框
     */
    @Nullable
    public EditBox searchBox() {
        return search;
    }
}
