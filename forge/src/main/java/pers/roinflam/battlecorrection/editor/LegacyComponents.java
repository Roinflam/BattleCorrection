package pers.roinflam.battlecorrection.editor;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 带样式的文本组件和编辑文本（{@code &} 码写法，见 {@link LegacyText}）之间的转换
 * <p>
 * 编辑文本的含义（和原版 § 码一致，只是起点不同）：
 * <ul>
 *   <li>从"基础样式"出发（名字页 = italic:false 或什么都不写，Lore 页 = 白色不斜体或什么都不写）</li>
 *   <li>颜色码（16 色或 {@code &#RRGGBB}）换颜色，同时把粗体等五种格式清成"关"</li>
 *   <li>格式码打开一种格式，{@code &r} 回到基础样式</li>
 * </ul>
 * 渲染时外面还套着一层"外层样式"（名字 = 稀有度颜色 + 斜体，Lore = 紫色斜体），基础样式里没写的项从它继承。
 * <p>
 * 读取（{@link #decode}）：把组件按原版的渲染规则拍平成逐字的有效样式（文本里夹着的 § 码也按原版语义拆开），
 * 再从基础样式出发，逐字补出最短的代码，保证写回去以后每个字的颜色和五种格式都和原来一模一样。
 * 点击 / 悬停事件、字体、insertion、翻译键这些表达不了的东西不会被悄悄吃掉：记在 {@link Decoded#lossy()} 里，页面据此提示。
 * <p>
 * 写回（{@link #toComponent}）：按上面的含义切成样式段，每段一个子组件，样式只写和基础样式 / 外层样式不同的项；
 * 不会把整行塞成带 § 的字面量（模组自定义代码除外，它的效果只有那个模组自己认得，只能原样留在文本里）。
 */
public final class LegacyComponents {

    /**
     * 原版 Lore 外层样式（ItemStack.LORE_STYLE）：紫色斜体
     */
    public static final Style LORE_PARENT = Style.EMPTY.withColor(ChatFormatting.DARK_PURPLE).withItalic(true);

    /**
     * 编辑文本表达不了、写回时会丢掉的东西
     */
    public enum Lossy {
        /**
         * 点击事件
         */
        CLICK("click"),
        /**
         * 悬停文本
         */
        HOVER("hover"),
        /**
         * Shift 点击插入的文本
         */
        INSERTION("insertion"),
        /**
         * 非默认字体
         */
        FONT("font"),
        /**
         * 翻译键 / 按键名 / 计分板 / 选择器 / NBT 这类动态内容（写回后变成当前显示的固定文字）
         */
        DYNAMIC("dynamic");

        private final String key;

        Lossy(@Nonnull String key) {
            this.key = key;
        }

        /**
         * 名称的翻译键
         */
        @Nonnull
        public String translationKey() {
            return "gui.battlecorrection.editor.lossy." + key;
        }
    }

    /**
     * 读取结果
     *
     * @param editable 编辑文本
     * @param lossy    原组件里有、编辑文本表达不了的东西（空 = 完全无损）
     */
    public record Decoded(@Nonnull String editable, @Nonnull Set<Lossy> lossy) {
    }

    /**
     * 五种格式在位掩码里的顺序，也是补代码时的输出顺序：粗体、斜体、下划线、删除线、乱码
     */
    private static final ChatFormatting[] FLAG_FORMATS = {
            ChatFormatting.BOLD, ChatFormatting.ITALIC, ChatFormatting.UNDERLINE,
            ChatFormatting.STRIKETHROUGH, ChatFormatting.OBFUSCATED
    };
    private static final int FLAG_COUNT = FLAG_FORMATS.length;

    private static final Style ALL_TRUE = Style.EMPTY.withBold(true).withItalic(true).withUnderlined(true)
            .withStrikethrough(true).withObfuscated(true);
    private static final Style ALL_FALSE = Style.EMPTY.withBold(false).withItalic(false).withUnderlined(false)
            .withStrikethrough(false).withObfuscated(false);

    /**
     * 原版渲染落单的 surrogate 字符时显示的替换字符（StringDecomposer 里同样用 U+FFFD）
     */
    private static final char REPLACEMENT = (char) 0xFFFD;

    private static final int UNSET = 0;
    private static final int OFF = 1;
    private static final int ON = 2;

    private LegacyComponents() {
    }

    // 基础样式

    /**
     * 名字页的基础样式
     *
     * @param italic true = 什么都不写，恢复原版的斜体；false = 写 italic:false
     * @return 样式
     */
    @Nonnull
    public static Style nameBase(boolean italic) {
        return italic ? Style.EMPTY : Style.EMPTY.withItalic(false);
    }

    /**
     * 名字的外层样式：和原版提示框一致，稀有度颜色 + 斜体（ItemStack#getTooltipLines）
     *
     * @param rarityStyle 稀有度的样式修饰（Rarity#getStyleModifier 作用在空样式上的结果）
     * @return 样式
     */
    @Nonnull
    public static Style nameParent(@Nonnull Style rarityStyle) {
        return rarityStyle.withItalic(true);
    }

    /**
     * Lore 页的基础样式
     *
     * @param vanilla true = 什么都不写，交给原版渲染成紫色斜体；false = 白色、不斜体
     * @return 样式
     */
    @Nonnull
    public static Style loreBase(boolean vanilla) {
        return vanilla ? Style.EMPTY : Style.EMPTY.withItalic(false).withColor(ChatFormatting.WHITE);
    }

    /**
     * 样式是不是"什么都没写"
     * <p>
     * 不能用 Style#isEmpty：它比的是对象身份，JSON 读出来的样式就算一项没写也不是 Style.EMPTY 那个对象。
     *
     * @param style 样式
     * @return true = 颜色、五种格式、事件、insertion、字体都没写
     */
    public static boolean isBlank(@Nonnull Style style) {
        return style.getColor() == null && flagsOf(style) == 0
                && style.getClickEvent() == null && style.getHoverEvent() == null
                && style.getInsertion() == null && Style.DEFAULT_FONT.equals(style.getFont());
    }

    // 编辑文本 → 组件

    /**
     * 编辑文本 → 存盘用的组件
     *
     * @param editable 编辑文本
     * @param base     基础样式（写在根组件上）
     * @param parent   渲染时的外层样式（用来判断"关掉某种格式"要不要显式写 false）
     * @return 组件
     */
    @Nonnull
    public static MutableComponent toComponent(@Nonnull String editable, @Nonnull Style base, @Nonnull Style parent) {
        int baseFlags = flagsOf(base);
        TextColor baseColor = base.getColor();
        int inherited = resolve(baseFlags, parent);

        List<String> texts = new ArrayList<>();
        List<Style> styles = new ArrayList<>();

        // 当前状态：颜色 + 五种格式（三态）
        TextColor color = baseColor;
        int flags = baseFlags;
        boolean dirty = true;
        Style child = Style.EMPTY;
        int childFlags = 0;
        TextColor childColor = null;
        StringBuilder run = new StringBuilder();
        boolean runOpen = false;

        LegacyText.Scanner scanner = LegacyText.scanEditable(editable);
        while (scanner.next()) {
            switch (scanner.type()) {
                case TEXT, ESCAPE, CUSTOM -> {
                    if (dirty) {
                        // 子组件只写和根（基础样式 + 外层样式）不同的项
                        TextColor nextColor = color == null || (baseColor != null && sameRgb(baseColor, color)) ? null : color;
                        int nextFlags = 0;
                        for (int i = 0; i < FLAG_COUNT; i++) {
                            boolean inheritedOn = flag(inherited, i) == ON;
                            int state = flag(flags, i);
                            boolean wanted = state == UNSET ? inheritedOn : state == ON;
                            if (wanted != inheritedOn) {
                                nextFlags = withFlag(nextFlags, i, wanted ? ON : OFF);
                            }
                        }
                        if (runOpen && (nextFlags != childFlags || !sameColor(nextColor, childColor))) {
                            texts.add(run.toString());
                            styles.add(child);
                            run.setLength(0);
                            runOpen = false;
                        }
                        if (!runOpen) {
                            childColor = nextColor;
                            childFlags = nextFlags;
                            child = styleOf(nextColor, nextFlags);
                            runOpen = true;
                        }
                        dirty = false;
                    } else if (!runOpen) {
                        runOpen = true;
                    }
                    if (scanner.type() == LegacyText.TokenType.CUSTOM) {
                        run.append(LegacyText.SECTION).append(scanner.ch());
                    } else {
                        run.append(scanner.ch());
                    }
                }
                case COLOR, HEX -> {
                    color = colorOf(scanner.rgb());
                    flags = allOff();
                    dirty = true;
                }
                case FORMAT -> {
                    flags = withFlag(flags, indexOf(scanner.format()), ON);
                    dirty = true;
                }
                case RESET -> {
                    color = baseColor;
                    flags = baseFlags;
                    dirty = true;
                }
                case STRAY -> {
                }
            }
        }
        if (runOpen && run.length() > 0) {
            texts.add(run.toString());
            styles.add(child);
        }

        if (texts.isEmpty()) {
            return Component.literal("").setStyle(base);
        }
        if (texts.size() == 1) {
            return Component.literal(texts.get(0)).setStyle(styles.get(0).applyTo(base));
        }
        int first = 0;
        MutableComponent root;
        if (isBlank(styles.get(0))) {
            root = Component.literal(texts.get(0));
            first = 1;
        } else {
            root = Component.literal("");
        }
        root.setStyle(base);
        for (int i = first; i < texts.size(); i++) {
            root.append(Component.literal(texts.get(i)).setStyle(styles.get(i)));
        }
        return root;
    }

    /**
     * 给预览用：把组件套上外层样式，画出来就是提示框里的样子
     *
     * @param component 组件
     * @param parent    外层样式
     * @return 包了一层的组件
     */
    @Nonnull
    public static MutableComponent wrap(@Nonnull Component component, @Nonnull Style parent) {
        return Component.empty().setStyle(parent).append(component);
    }

    // 组件 → 编辑文本

    /**
     * 组件 → 编辑文本
     *
     * @param component 组件
     * @param base      基础样式（写回时用的那个）
     * @param parent    渲染时的外层样式
     * @return 编辑文本 + 表达不了的东西
     */
    @Nonnull
    public static Decoded decode(@Nonnull Component component, @Nonnull Style base, @Nonnull Style parent) {
        return decode(flatten(component), base, parent);
    }

    /**
     * 把整段文字的斜体统一改成同一个值，并换到新的基础样式下（名字页的"斜体"开关）
     * <p>
     * 只改基础样式管不到文字里的代码：颜色码会把前面的格式一起清掉，所以颜色码之后的字要斜体就得写 {@code &o}，
     * 关掉开关时这些 {@code &o} 还在，名字照样斜体；反过来，打开开关时颜色码后面的字也不会跟着斜体。
     * 这里按旧基础样式算出每个字现在的样子，只把斜体换成新值（颜色和其余四种格式不动），再按新基础样式写回编辑文本。
     * 文字在新基础样式下本来就是这个效果时（例如没有颜色码的纯文字）原样返回，保留原来的写法；
     * 否则按 {@link #decode(Component, Style, Style)} 的规则补最短的代码：继承外层斜体的字不额外写 {@code &o}。
     *
     * @param editable 编辑文本
     * @param oldBase  现在的基础样式
     * @param newBase  切换后的基础样式
     * @param parent   渲染时的外层样式
     * @param italic   每个字要不要斜体
     * @return 新的编辑文本（不需要改时返回原对象）
     */
    @Nonnull
    public static String withItalic(@Nonnull String editable, @Nonnull Style oldBase, @Nonnull Style newBase,
                                    @Nonnull Style parent, boolean italic) {
        Flat wanted = flatten(toComponent(editable, oldBase, parent));
        Style last = null;
        Style forced = null;
        for (int i = 0; i < wanted.styles.size(); i++) {
            Style style = wanted.styles.get(i);
            if (style == null) {
                continue;
            }
            // 同一段的字共用一个样式对象，换完也共用一个：decode 按对象身份判断样式有没有变
            if (style != last) {
                last = style;
                forced = style.withItalic(italic);
            }
            wanted.styles.set(i, forced);
        }
        if (sameLook(wanted, flatten(toComponent(editable, newBase, parent)), parent)) {
            return editable;
        }
        return decode(wanted, newBase, parent).editable();
    }

    /**
     * 一段编辑文本在两种基础样式下显示得是不是一模一样（每个字的颜色和五种格式都相同）
     * <p>
     * Lore 页切"原版样式"开关时用：一样的话这一行切了也没变化，可以继续写原文。
     *
     * @param editable 编辑文本
     * @param baseA    基础样式一
     * @param baseB    基础样式二
     * @param parent   渲染时的外层样式
     * @return true = 一样
     */
    public static boolean sameLook(@Nonnull String editable, @Nonnull Style baseA, @Nonnull Style baseB,
                                   @Nonnull Style parent) {
        return sameLook(flatten(toComponent(editable, baseA, parent)), flatten(toComponent(editable, baseB, parent)), parent);
    }

    /**
     * 两份拍平结果套上外层样式后是否逐字一样（字符、颜色、五种格式；模组自定义代码只比代码字符）
     */
    private static boolean sameLook(@Nonnull Flat a, @Nonnull Flat b, @Nonnull Style parent) {
        int length = a.chars.length();
        if (length != b.chars.length() || a.styles.size() != b.styles.size()) {
            return false;
        }
        int parentRgb = parent.getColor() == null ? 0xFFFFFF : rgb(parent.getColor());
        Style lastA = null;
        Style lastB = null;
        for (int i = 0; i < length; i++) {
            if (a.chars.charAt(i) != b.chars.charAt(i)) {
                return false;
            }
            Style styleA = a.styles.get(i);
            Style styleB = b.styles.get(i);
            if (styleA == null || styleB == null) {
                if (styleA != styleB) {
                    return false;
                }
                continue;
            }
            if (styleA == lastA && styleB == lastB) {
                continue;
            }
            lastA = styleA;
            lastB = styleB;
            Style resolvedA = styleA.applyTo(parent);
            Style resolvedB = styleB.applyTo(parent);
            int rgbA = resolvedA.getColor() == null ? parentRgb : rgb(resolvedA.getColor());
            int rgbB = resolvedB.getColor() == null ? parentRgb : rgb(resolvedB.getColor());
            if (rgbA != rgbB || mask(resolvedA) != mask(resolvedB)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 拍平结果 → 编辑文本（见 {@link #decode(Component, Style, Style)}）
     */
    @Nonnull
    private static Decoded decode(@Nonnull Flat flat, @Nonnull Style base, @Nonnull Style parent) {
        int parentRgb = parent.getColor() == null ? 0xFFFFFF : rgb(parent.getColor());
        boolean baseInherit = base.getColor() == null;
        int baseRgb = baseInherit ? parentRgb : rgb(base.getColor());
        int baseMask = mask(resolve(flagsOf(base), parent));

        boolean inherit = baseInherit;
        int color = baseRgb;
        int mask = baseMask;

        LegacyText.Writer writer = new LegacyText.Writer(flat.chars.length() + 16);
        Style lastStyle = null;
        boolean targetInherit = false;
        int targetRgb = 0;
        int targetMask = 0;
        for (int i = 0; i < flat.chars.length(); i++) {
            char c = flat.chars.charAt(i);
            Style style = flat.styles.get(i);
            if (style == null) {
                // 模组自定义代码：原样放回
                writer.code(LegacyText.AMPERSAND, c);
                continue;
            }
            if (style != lastStyle) {
                lastStyle = style;
                targetMask = mask(style.applyTo(parent));
                TextColor own = style.getColor();
                // 原来是继承外层颜色的，能继承就继续继承（例如名字跟着稀有度变色）；
                // 基础样式自带颜色、或者回到基础样式会多出关不掉的格式时，只能写成显式的同一个颜色
                targetInherit = own == null && baseInherit && (baseMask & ~targetMask) == 0;
                targetRgb = own == null ? parentRgb : rgb(own);
            }
            boolean same = inherit == targetInherit && (inherit || color == targetRgb) && mask == targetMask;
            if (!same) {
                writer.code(transition(inherit, color, mask, targetInherit, targetRgb, targetMask,
                        baseInherit, baseRgb, baseMask));
                inherit = targetInherit;
                color = targetRgb;
                mask = targetMask;
            }
            writer.literal(c);
        }
        Set<Lossy> lossy = flat.lossy.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(flat.lossy);
        return new Decoded(writer.build(), lossy);
    }

    /**
     * 能看见的字是不是全都是斜体（名字页据此决定"斜体"开关：全斜体就不写 italic:false，读写都最省事）
     *
     * @param component 组件
     * @param parent    外层样式
     * @return true = 至少有一个字，而且每个字都是斜体
     */
    public static boolean allItalic(@Nonnull Component component, @Nonnull Style parent) {
        Flat flat = flatten(component);
        boolean any = false;
        Style last = null;
        for (Style style : flat.styles) {
            if (style == null || style == last) {
                continue;
            }
            last = style;
            if (!style.applyTo(parent).isItalic()) {
                return false;
            }
            any = true;
        }
        return any;
    }

    /**
     * 只算有哪些表达不了的东西
     *
     * @param component 组件
     * @return 集合（空 = 无损）
     */
    @Nonnull
    public static Set<Lossy> lossyOf(@Nonnull Component component) {
        return flatten(component).lossy;
    }

    /**
     * 从当前状态过渡到目标状态的最短代码
     * <p>
     * 三条路：在当前状态上只追加格式码；{@code &r} 回基础样式再追加；写颜色码（会清掉格式）再追加。取最短的。
     * 调用方保证目标可达：继承颜色的目标只在"回基础样式后格式不多余"时才会出现，否则目标一定是显式颜色。
     */
    @Nonnull
    private static String transition(boolean inherit, int color, int mask,
                                     boolean targetInherit, int targetRgb, int targetMask,
                                     boolean baseInherit, int baseRgb, int baseMask) {
        String best = null;
        if (inherit == targetInherit && (inherit || color == targetRgb) && (mask & ~targetMask) == 0) {
            best = formatCodes(targetMask & ~mask);
        }
        if (baseInherit == targetInherit && (baseInherit || baseRgb == targetRgb) && (baseMask & ~targetMask) == 0) {
            String candidate = "&r" + formatCodes(targetMask & ~baseMask);
            if (best == null || candidate.length() < best.length()) {
                best = candidate;
            }
        }
        if (!targetInherit) {
            String candidate = LegacyText.colorCode(targetRgb) + formatCodes(targetMask);
            if (best == null || candidate.length() < best.length()) {
                best = candidate;
            }
        }
        return Objects.requireNonNull(best);
    }

    /**
     * 打开位掩码里这些格式的代码
     *
     * @param mask 位掩码（第 i 位 = FLAG_FORMATS[i]）
     * @return 例如 {@code &l&o}
     */
    @Nonnull
    public static String formatCodes(int mask) {
        if (mask == 0) {
            return "";
        }
        StringBuilder codes = new StringBuilder(10);
        for (int i = 0; i < FLAG_COUNT; i++) {
            if ((mask & (1 << i)) != 0) {
                codes.append(LegacyText.AMPERSAND).append(FLAG_FORMATS[i].getChar());
            }
        }
        return codes.toString();
    }

    /**
     * 格式在位掩码里的位
     *
     * @param format 格式
     * @return 位（不是五种格式之一时返回 0）
     */
    public static int formatBit(@Nullable ChatFormatting format) {
        int index = indexOf(format);
        return index < 0 ? 0 : 1 << index;
    }

    // 拍平

    /**
     * 拍平结果：逐字的字符和有效样式（相对于空外层样式，没写的项保持未设置）
     * <p>
     * styles 里的 null 表示这个位置是模组自定义代码（chars 里放的是代码字符），不是要显示的字。
     */
    private static final class Flat {
        final StringBuilder chars = new StringBuilder();
        final List<Style> styles = new ArrayList<>();
        final EnumSet<Lossy> lossy = EnumSet.noneOf(Lossy.class);
    }

    /**
     * 按原版渲染规则拍平组件
     * <p>
     * Component#visit 给出每段文字和它合并后的样式；段内的 § 码按 StringDecomposer#iterateFormatted 的规则拆：
     * 颜色码清格式、格式码打开格式、§r 回到这一段自己的样式、不成代码的 § 连同后一个字符不显示。
     * 唯一的扩展：§x§R§R§G§G§B§B 当成十六进制颜色（Bukkit 系插件的写法，原版会显示成最后那一位的颜色，显然不是作者本意）。
     * <p>
     * emoji 这类字在字符串里是一对 surrogate（高位 + 低位），也按原版的分段来：原版每段文字单独迭代，
     * 落单的高位 / 低位 surrogate（被 § 码隔开、或者一半在这个子组件一半在下一个）
     * 各显示成一个 U+FFFD。拍平后它们可能挨在一起、样式也相同，写回时会并进同一个字面量、拼成一个完整的字，
     * 所以这里直接换成 U+FFFD，显示效果不变。
     *
     * @param component 组件
     * @return 拍平结果
     */
    @Nonnull
    private static Flat flatten(@Nonnull Component component) {
        Flat flat = new Flat();
        scanContents(component, flat.lossy, 0);
        component.visit((segmentStyle, text) -> {
            if (!text.isEmpty()) {
                decompose(text, segmentStyle, flat);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return flat;
    }

    /**
     * 拆一段文字
     *
     * @param text         文字
     * @param segmentStyle 这一段的样式
     * @param flat         输出
     */
    private static void decompose(@Nonnull String text, @Nonnull Style segmentStyle, @Nonnull Flat flat) {
        if (text.indexOf(LegacyText.SECTION) < 0) {
            noteStyle(segmentStyle, flat.lossy);
            if (!hasSurrogate(text)) {
                flat.chars.append(text);
                for (int i = 0; i < text.length(); i++) {
                    flat.styles.add(segmentStyle);
                }
                return;
            }
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                    appendChar(flat, c, segmentStyle);
                    appendChar(flat, text.charAt(++i), segmentStyle);
                } else {
                    appendChar(flat, Character.isSurrogate(c) ? REPLACEMENT : c, segmentStyle);
                }
            }
            return;
        }
        Style style = segmentStyle;
        boolean noted = false;
        LegacyText.Scanner scanner = LegacyText.scanSection(text);
        while (scanner.next()) {
            switch (scanner.type()) {
                case TEXT, ESCAPE -> {
                    if (!noted) {
                        noteStyle(style, flat.lossy);
                        noted = true;
                    }
                    char c = scanner.ch();
                    if (Character.isHighSurrogate(c) && scanner.end() < text.length()
                            && Character.isLowSurrogate(text.charAt(scanner.end()))) {
                        // 低位 surrogate 不会是 § 码的一部分，下一个记号一定就是它：成对的才是一个字
                        appendChar(flat, c, style);
                        scanner.next();
                        appendChar(flat, scanner.ch(), style);
                    } else {
                        appendChar(flat, Character.isSurrogate(c) ? REPLACEMENT : c, style);
                    }
                }
                case COLOR, FORMAT -> {
                    style = style.applyLegacyFormat(Objects.requireNonNull(scanner.format()));
                }
                case HEX -> style = style.withColor(TextColor.fromRgb(scanner.rgb())).withBold(false).withItalic(false)
                        .withUnderlined(false).withStrikethrough(false).withObfuscated(false);
                case RESET -> style = segmentStyle;
                case CUSTOM -> {
                    flat.chars.append(scanner.ch());
                    flat.styles.add(null);
                }
                case STRAY -> {
                }
            }
        }
    }

    private static void appendChar(@Nonnull Flat flat, char c, @Nonnull Style style) {
        flat.chars.append(c);
        flat.styles.add(style);
    }

    private static boolean hasSurrogate(@Nonnull String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isSurrogate(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 记下样式里表达不了的项
     */
    private static void noteStyle(@Nonnull Style style, @Nonnull Set<Lossy> lossy) {
        if (style.getClickEvent() != null) {
            lossy.add(Lossy.CLICK);
        }
        if (style.getHoverEvent() != null) {
            lossy.add(Lossy.HOVER);
        }
        if (style.getInsertion() != null) {
            lossy.add(Lossy.INSERTION);
        }
        if (!Style.DEFAULT_FONT.equals(style.getFont())) {
            lossy.add(Lossy.FONT);
        }
    }

    /**
     * 找组件树里不是普通文字的内容（翻译键、按键名、计分板、选择器、NBT）
     *
     * @param component 组件
     * @param lossy     输出
     * @param depth     递归深度（防止恶意嵌套把栈撑爆）
     */
    private static void scanContents(@Nonnull Component component, @Nonnull Set<Lossy> lossy, int depth) {
        if (depth > 64) {
            return;
        }
        ComponentContents contents = component.getContents();
        if (!(contents instanceof LiteralContents) && contents != ComponentContents.EMPTY) {
            lossy.add(Lossy.DYNAMIC);
        }
        if (contents instanceof TranslatableContents translatable) {
            for (Object arg : translatable.getArgs()) {
                if (arg instanceof Component argument) {
                    scanContents(argument, lossy, depth + 1);
                }
            }
        }
        for (Component sibling : component.getSiblings()) {
            scanContents(sibling, lossy, depth + 1);
        }
    }

    // 样式小工具

    /**
     * 读出样式里五种格式的三态值
     * <p>
     * 原版 Style 只给 isBold() 这种两态读法，"没写"和"写了 false"分不出来，而写回时恰恰要区分（没写 = 继承外层的斜体）。
     * 办法是分别叠到"全开"和"全关"两个样式上：两次结果一样说明自己写了，不一样说明没写。
     *
     * @param style 样式
     * @return 每种格式 2 位（0 = 未设置，1 = 关，2 = 开）
     */
    private static int flagsOf(@Nonnull Style style) {
        Style on = style.applyTo(ALL_TRUE);
        Style off = style.applyTo(ALL_FALSE);
        int flags = 0;
        for (int i = 0; i < FLAG_COUNT; i++) {
            boolean a = isOn(on, i);
            boolean b = isOn(off, i);
            if (a == b) {
                flags = withFlag(flags, i, a ? ON : OFF);
            }
        }
        return flags;
    }

    /**
     * 三态格式叠到外层样式上，得到实际效果（每种格式都变成开 / 关）
     *
     * @param flags  三态格式
     * @param parent 外层样式
     * @return 三态格式（不再有未设置）
     */
    private static int resolve(int flags, @Nonnull Style parent) {
        int resolved = 0;
        for (int i = 0; i < FLAG_COUNT; i++) {
            int state = flag(flags, i);
            boolean on = state == UNSET ? isOn(parent, i) : state == ON;
            resolved = withFlag(resolved, i, on ? ON : OFF);
        }
        return resolved;
    }

    /**
     * 实际开着的格式
     *
     * @param style 样式
     * @return 位掩码
     */
    private static int mask(@Nonnull Style style) {
        int mask = 0;
        for (int i = 0; i < FLAG_COUNT; i++) {
            if (isOn(style, i)) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    /**
     * 三态格式 → 开着的位掩码
     */
    private static int mask(int flags) {
        int mask = 0;
        for (int i = 0; i < FLAG_COUNT; i++) {
            if (flag(flags, i) == ON) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    private static boolean isOn(@Nonnull Style style, int index) {
        return switch (index) {
            case 0 -> style.isBold();
            case 1 -> style.isItalic();
            case 2 -> style.isUnderlined();
            case 3 -> style.isStrikethrough();
            default -> style.isObfuscated();
        };
    }

    private static int flag(int flags, int index) {
        return (flags >> (2 * index)) & 3;
    }

    private static int withFlag(int flags, int index, int value) {
        return (flags & ~(3 << (2 * index))) | (value << (2 * index));
    }

    private static int allOff() {
        int flags = 0;
        for (int i = 0; i < FLAG_COUNT; i++) {
            flags = withFlag(flags, i, OFF);
        }
        return flags;
    }

    private static int indexOf(@Nullable ChatFormatting format) {
        for (int i = 0; i < FLAG_COUNT; i++) {
            if (FLAG_FORMATS[i] == format) {
                return i;
            }
        }
        return -1;
    }

    @Nonnull
    private static Style styleOf(@Nullable TextColor color, int flags) {
        return Style.EMPTY.withColor(color)
                .withBold(bool(flag(flags, 0)))
                .withItalic(bool(flag(flags, 1)))
                .withUnderlined(bool(flag(flags, 2)))
                .withStrikethrough(bool(flag(flags, 3)))
                .withObfuscated(bool(flag(flags, 4)));
    }

    @Nullable
    private static Boolean bool(int state) {
        return state == UNSET ? null : state == ON;
    }

    /**
     * 颜色值对应的 TextColor：16 色之一就用带名字的那个（存出来是 "gold" 而不是 "#FFAA00"）
     */
    @Nonnull
    private static TextColor colorOf(int rgb) {
        ChatFormatting formatting = LegacyText.vanillaColor(rgb);
        TextColor named = formatting == null ? null : TextColor.fromLegacyFormat(formatting);
        return named != null ? named : TextColor.fromRgb(rgb & 0xFFFFFF);
    }

    private static int rgb(@Nonnull TextColor color) {
        // 渲染时只用低 24 位（Font 里按 RGB 拆），高位不影响显示
        return color.getValue() & 0xFFFFFF;
    }

    private static boolean sameRgb(@Nonnull TextColor a, @Nonnull TextColor b) {
        return rgb(a) == rgb(b);
    }

    private static boolean sameColor(@Nullable TextColor a, @Nullable TextColor b) {
        return a == null ? b == null : b != null && sameRgb(a, b);
    }
}
