package pers.roinflam.battlecorrection.editor;

import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 编辑文本（{@code &} 码写法，见 {@link LegacyText}）和物品里存的 § 字符串之间的转换，以及"显示出来是什么样"的比较
 * <p>
 * 1.12.2 的物品名、Lore 本身就是带 § 的字符串，没有 1.20.1 那种文本组件，也就没有点击 / 悬停事件、字体、翻译键这些
 * 编辑文本表达不了的东西。剩下要处理的是"基础样式"：
 * <ul>
 *   <li>提示框给名字前面加稀有度颜色和 §o（ItemStack#getTooltip、GuiScreen#getItemToolTip），给 Lore 每行前面加 §5§o，
 *   这是"外层样式"（{@link #nameParent}、{@link #LORE_PARENT}）</li>
 *   <li>基础样式二选一：原版（什么都不加，没写颜色码的字跟着外层样式）；或者存的时候在最前面加 §r（没写颜色码的字白色、不斜体）。
 *   1.12.2 的 §r 回到的是画字时给的颜色，提示框里是白色，回不到外层样式——所以去掉斜体只能连稀有度颜色一起去掉</li>
 * </ul>
 * 编辑文本的含义和原版 § 码一致：颜色码换颜色，同时把粗体等五种格式清成"关"；格式码打开一种格式；{@code &r} 变成白色并关掉所有格式；
 * 认不出的代码按白色处理。
 * <p>
 * 写回（{@link #encode}）就是 {@link LegacyText#toSection}，加 §r 基础样式时在前面补 §r（编辑文本一开头就是颜色码或 {@code &r} 时不用补）。
 * 读取（{@link #decode}）尽量保留原文的写法，只在需要时补前缀，保证写回去以后每个字的颜色和五种格式都和原来一样。
 * 切换基础样式时要逐字算效果（{@link #withItalic}、{@link #sameLook}），这里按字体渲染器的规则把字符串拍平成逐字的颜色和格式。
 */
public final class LegacyComponents {

    /**
     * 五种格式在位掩码里的顺序，也是补代码时的输出顺序：粗体、斜体、下划线、删除线、乱码
     */
    private static final TextFormatting[] FLAG_FORMATS = {
            TextFormatting.BOLD, TextFormatting.ITALIC, TextFormatting.UNDERLINE,
            TextFormatting.STRIKETHROUGH, TextFormatting.OBFUSCATED
    };
    private static final int FLAG_COUNT = FLAG_FORMATS.length;

    /**
     * 斜体在位掩码里的位
     */
    public static final int ITALIC = 1 << 1;

    private static final int WHITE = 0xFFFFFF;

    /**
     * 一个字显示出来的样子：颜色 + 打开的格式
     */
    public static final class Look {
        private final int rgb;
        private final int mask;

        /**
         * @param rgb  颜色
         * @param mask 打开的格式（位同 {@link #formatBit}）
         */
        public Look(int rgb, int mask) {
            this.rgb = rgb & 0xFFFFFF;
            this.mask = mask;
        }

        public int rgb() {
            return rgb;
        }

        public int mask() {
            return mask;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Look && ((Look) o).rgb == rgb && ((Look) o).mask == mask;
        }

        @Override
        public int hashCode() {
            return rgb * 31 + mask;
        }
    }

    /**
     * §r 之后的样子：白色、不带格式
     */
    public static final Look RESET = new Look(WHITE, 0);

    /**
     * 原版 Lore 外层样式（ItemStack#getTooltip 给每行加的 §5§o）：紫色斜体
     */
    public static final Look LORE_PARENT = new Look(ColorMath.legacyRgb(TextFormatting.DARK_PURPLE), ITALIC);

    /**
     * 编辑文本表达不了、写回时会丢掉的东西
     */
    public enum Lossy {
        /**
         * 换行、制表符这类控制字符：输入框里放不下
         */
        CONTROL("control");

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
     */
    public static final class Decoded {
        @Nonnull
        private final String editable;
        @Nonnull
        private final Set<Lossy> lossy;

        /**
         * @param editable 编辑文本
         * @param lossy    原文里有、编辑文本表达不了的东西（空 = 完全无损）
         */
        public Decoded(@Nonnull String editable, @Nonnull Set<Lossy> lossy) {
            this.editable = editable;
            this.lossy = lossy;
        }

        @Nonnull
        public String editable() {
            return editable;
        }

        @Nonnull
        public Set<Lossy> lossy() {
            return lossy;
        }
    }

    private LegacyComponents() {
    }

    // 外层样式 / 基础样式

    /**
     * 名字的外层样式：和原版提示框一致，稀有度颜色 + 斜体
     *
     * @param rarity 稀有度颜色（Item#getForgeRarity(stack).getColor()）
     * @return 样式
     */
    @Nonnull
    public static Look nameParent(@Nonnull TextFormatting rarity) {
        return new Look(ColorMath.legacyRgb(rarity), ITALIC);
    }

    /**
     * 基础样式下，一行开头没写代码的字是什么样子
     *
     * @param vanilla true = 原版（跟着外层样式）；false = 存的时候前面加 §r（白色、不斜体）
     * @param parent  外层样式
     * @return 样子
     */
    @Nonnull
    public static Look start(boolean vanilla, @Nonnull Look parent) {
        return vanilla ? parent : RESET;
    }

    /**
     * 从任意状态回到某个样子要写的最短代码（白色用 {@code &r}，否则颜色码，再补格式码）
     *
     * @param look 样子（颜色得是 16 色之一）
     * @return 编辑文本写法的代码，例如 {@code &5&o}
     */
    @Nonnull
    public static String restoreCode(@Nonnull Look look) {
        String color = look.rgb == WHITE ? "&r" : LegacyText.colorCode(look.rgb);
        return color + formatCodes(look.mask);
    }

    // 编辑文本 → 物品里存的字符串

    /**
     * 编辑文本 → 存进物品的 § 字符串
     *
     * @param editable 编辑文本
     * @param vanilla  true = 原版基础样式（不加前缀）；false = 前面加 §r
     * @return § 字符串
     */
    @Nonnull
    public static String encode(@Nonnull String editable, boolean vanilla) {
        String section = LegacyText.toSection(editable);
        if (vanilla || section.isEmpty() || startsWithReset(editable)) {
            return section;
        }
        return TextFormatting.RESET + section;
    }

    /**
     * 编辑文本的第一个记号是不是颜色 / 重置 / 认不出的代码（它自己就会把外层样式全部盖掉）
     */
    private static boolean startsWithReset(@Nonnull String editable) {
        LegacyText.Scanner scanner = LegacyText.scanEditable(editable);
        return scanner.next() && scanner.token().resetsFormats();
    }

    // 物品里存的字符串 → 编辑文本

    /**
     * 物品里的 § 字符串 → 编辑文本
     * <p>
     * 原版基础样式：直接 {@link LegacyText#fromStored}，代码原样搬过来。
     * §r 基础样式：开头的 §r 就是基础样式本身，去掉；一开头就有字跟着外层样式的（没写颜色码），前面补上外层样式的代码
     * （Lore 是 {@code &5&o}），写回去还是同样的效果；其余照原文。
     *
     * @param stored  § 字符串
     * @param vanilla 用哪种基础样式读
     * @param parent  外层样式
     * @return 编辑文本 + 表达不了的东西
     */
    @Nonnull
    public static Decoded decode(@Nonnull String stored, boolean vanilla, @Nonnull Look parent) {
        String editable;
        if (vanilla) {
            editable = LegacyText.fromStored(stored);
        } else if (startsWithResetCode(stored)) {
            editable = LegacyText.fromStored(stored.substring(2));
        } else if (textBeforeReset(stored)) {
            editable = restoreCode(parent) + LegacyText.fromStored(stored);
        } else {
            editable = LegacyText.fromStored(stored);
        }
        return new Decoded(editable, lossyOf(stored));
    }

    /**
     * 名字该用哪种基础样式读（名字页的"斜体"开关）
     * <p>
     * 每个字都是斜体（例如铁砧改的名字）→ 原版，一个代码都不用多写；以 §r 开头 → §r 基础样式；
     * 还有字跟着稀有度颜色显示（前面没写颜色码）→ 原版，保住会随稀有度变的颜色；剩下的（一开头就是颜色码）→ §r 基础样式，
     * 这时两种写法存出来一样，只是开关显示成"不斜体"更符合看到的样子。
     *
     * @param stored 名字
     * @param parent 外层样式
     * @return true = 原版（斜体）
     */
    public static boolean preferVanilla(@Nonnull String stored, @Nonnull Look parent) {
        if (allItalic(stored, parent)) {
            return true;
        }
        if (startsWithResetCode(stored)) {
            return false;
        }
        return textBeforeReset(stored);
    }

    /**
     * 以 §r（或 §R）开头
     */
    private static boolean startsWithResetCode(@Nonnull String stored) {
        return stored.length() >= 2 && stored.charAt(0) == LegacyText.SECTION
                && LegacyText.codeOf(stored.charAt(1)) == TextFormatting.RESET;
    }

    /**
     * 第一个颜色 / 重置代码之前有没有看得见的字（这些字显示成外层样式）
     */
    private static boolean textBeforeReset(@Nonnull String stored) {
        LegacyText.Scanner scanner = LegacyText.scanSection(stored);
        while (scanner.next()) {
            switch (scanner.type()) {
                case TEXT:
                case ESCAPE:
                    return true;
                case COLOR:
                case RESET:
                case CUSTOM:
                    return false;
                default:
                    break;
            }
        }
        return false;
    }

    /**
     * 能看见的字是不是全都是斜体
     *
     * @param stored § 字符串
     * @param parent 外层样式
     * @return true = 至少有一个字，而且每个字都是斜体
     */
    public static boolean allItalic(@Nonnull String stored, @Nonnull Look parent) {
        Flat flat = flatten(stored, parent);
        boolean any = false;
        for (int i = 0; i < flat.size; i++) {
            if (flat.custom[i]) {
                continue;
            }
            if ((flat.mask[i] & ITALIC) == 0) {
                return false;
            }
            any = true;
        }
        return any;
    }

    /**
     * 原文里有哪些编辑文本表达不了的东西
     *
     * @param stored § 字符串
     * @return 集合（空 = 无损）
     */
    @Nonnull
    public static Set<Lossy> lossyOf(@Nonnull String stored) {
        return LegacyText.hasControlChars(stored) ? Collections.unmodifiableSet(EnumSet.of(Lossy.CONTROL))
                : Collections.<Lossy>emptySet();
    }

    // 切换基础样式

    /**
     * 把整段文字的斜体统一改成同一个值，并换到新的基础样式下（名字页的"斜体"开关）
     * <p>
     * 只换基础样式管不到文字里的代码：颜色码会把前面的格式一起清掉，所以颜色码之后的字要斜体就得写 {@code &o}，
     * 关掉开关时这些 {@code &o} 还在，名字照样斜体；反过来，打开开关时颜色码后面的字也不会跟着斜体。
     * 这里按旧基础样式算出每个字现在的样子，只把斜体换成新值（颜色和其余四种格式不动），再按新基础样式写回编辑文本。
     * 文字在新基础样式下本来就是这个效果时（例如开头就是颜色码、本来就不斜体）原样返回，保留原来的写法；
     * 否则逐字补最短的代码。§r 回不到稀有度颜色，原来跟着稀有度颜色的字关掉斜体后会写成那个颜色本身的代码。
     *
     * @param editable   编辑文本
     * @param oldVanilla 现在的基础样式
     * @param newVanilla 切换后的基础样式
     * @param parent     外层样式
     * @param italic     每个字要不要斜体
     * @return 新的编辑文本（不需要改时返回原对象）
     */
    @Nonnull
    public static String withItalic(@Nonnull String editable, boolean oldVanilla, boolean newVanilla,
                                    @Nonnull Look parent, boolean italic) {
        Flat wanted = flatten(encode(editable, oldVanilla), parent);
        for (int i = 0; i < wanted.size; i++) {
            if (!wanted.custom[i]) {
                wanted.mask[i] = italic ? wanted.mask[i] | ITALIC : wanted.mask[i] & ~ITALIC;
            }
        }
        if (sameLook(wanted, flatten(encode(editable, newVanilla), parent))) {
            return editable;
        }
        return generate(wanted, start(newVanilla, parent));
    }

    /**
     * 一段编辑文本在两种基础样式下显示得是不是一模一样（每个字的颜色和五种格式都相同）
     * <p>
     * Lore 页切"原版样式"开关时用：一样的话这一行切了也没变化，可以继续写原文。
     *
     * @param editable 编辑文本
     * @param vanillaA 基础样式一
     * @param vanillaB 基础样式二
     * @param parent   外层样式
     * @return true = 一样
     */
    public static boolean sameLook(@Nonnull String editable, boolean vanillaA, boolean vanillaB, @Nonnull Look parent) {
        if (vanillaA == vanillaB) {
            return true;
        }
        return sameLook(flatten(encode(editable, vanillaA), parent), flatten(encode(editable, vanillaB), parent));
    }

    /**
     * 两份拍平结果是否逐字一样（字符、颜色、五种格式；认不出的代码只比代码字符）
     */
    private static boolean sameLook(@Nonnull Flat a, @Nonnull Flat b) {
        if (a.size != b.size) {
            return false;
        }
        for (int i = 0; i < a.size; i++) {
            if (a.chars[i] != b.chars[i] || a.custom[i] != b.custom[i]) {
                return false;
            }
            if (!a.custom[i] && (a.rgb[i] != b.rgb[i] || a.mask[i] != b.mask[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * 拍平结果 → 编辑文本：从基础样式的起点出发，逐字补最短的代码
     *
     * @param flat  逐字的样子
     * @param start 基础样式下行首的样子
     * @return 编辑文本
     */
    @Nonnull
    private static String generate(@Nonnull Flat flat, @Nonnull Look start) {
        LegacyText.Writer writer = new LegacyText.Writer(flat.size + 16);
        int color = start.rgb;
        int mask = start.mask;
        for (int i = 0; i < flat.size; i++) {
            char c = flat.chars[i];
            if (flat.custom[i]) {
                // 认不出的代码：原样放回，原版按白色处理
                writer.raw(String.valueOf(LegacyText.SECTION) + c);
                color = WHITE;
                mask = 0;
                continue;
            }
            int targetRgb = flat.rgb[i];
            int targetMask = flat.mask[i];
            if (color != targetRgb || mask != targetMask) {
                writer.code(transition(color, mask, targetRgb, targetMask));
                color = targetRgb;
                mask = targetMask;
            }
            if (c == LegacyText.SECTION) {
                writer.raw(String.valueOf(c));
            } else {
                writer.literal(c);
            }
        }
        return writer.build();
    }

    /**
     * 从当前状态过渡到目标状态的最短代码
     * <p>
     * 三条路：在当前状态上只追加格式码；{@code &r}（白色）再追加；写颜色码（会清掉格式）再追加。取最短的。
     */
    @Nonnull
    private static String transition(int color, int mask, int targetRgb, int targetMask) {
        String best = null;
        if (color == targetRgb && (mask & ~targetMask) == 0) {
            best = formatCodes(targetMask & ~mask);
        }
        if (targetRgb == WHITE) {
            String candidate = "&r" + formatCodes(targetMask);
            if (best == null || candidate.length() < best.length()) {
                best = candidate;
            }
        }
        String candidate = LegacyText.colorCode(targetRgb) + formatCodes(targetMask);
        if (best == null || candidate.length() < best.length()) {
            best = candidate;
        }
        return best;
    }

    // 格式位

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
                codes.append(LegacyText.AMPERSAND).append(LegacyText.codeChar(FLAG_FORMATS[i]));
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
    public static int formatBit(@Nullable TextFormatting format) {
        for (int i = 0; i < FLAG_COUNT; i++) {
            if (FLAG_FORMATS[i] == format) {
                return 1 << i;
            }
        }
        return 0;
    }

    // 拍平

    /**
     * 拍平结果：逐字的字符和样子
     * <p>
     * custom[i] 为 true 的位置是认不出的代码（chars 里放的是代码字符），不是要显示的字。
     */
    private static final class Flat {
        char[] chars = new char[16];
        int[] rgb = new int[16];
        int[] mask = new int[16];
        boolean[] custom = new boolean[16];
        int size;

        void add(char c, int color, int formats, boolean isCustom) {
            if (size == chars.length) {
                int capacity = size * 2;
                chars = Arrays.copyOf(chars, capacity);
                rgb = Arrays.copyOf(rgb, capacity);
                mask = Arrays.copyOf(mask, capacity);
                custom = Arrays.copyOf(custom, capacity);
            }
            chars[size] = c;
            rgb[size] = color;
            mask[size] = formats;
            custom[size] = isCustom;
            size++;
        }
    }

    /**
     * 按字体渲染器的规则拍平一段 § 字符串
     * <p>
     * 从外层样式出发：颜色码换颜色并关掉格式，格式码打开格式，§r 和认不出的代码变白色并关掉格式，最末尾的 § 是普通字符。
     *
     * @param stored § 字符串（基础样式的 §r 前缀已经在里面）
     * @param parent 外层样式
     * @return 拍平结果
     */
    @Nonnull
    private static Flat flatten(@Nonnull String stored, @Nonnull Look parent) {
        Flat flat = new Flat();
        int color = parent.rgb;
        int mask = parent.mask;
        LegacyText.Scanner scanner = LegacyText.scanSection(stored);
        while (scanner.next()) {
            switch (scanner.type()) {
                case TEXT:
                case ESCAPE:
                    flat.add(scanner.ch(), color, mask, false);
                    break;
                case COLOR:
                case RESET:
                    color = scanner.rgb();
                    mask = 0;
                    break;
                case FORMAT:
                    mask |= formatBit(scanner.format());
                    break;
                case CUSTOM:
                    flat.add(scanner.ch(), WHITE, 0, true);
                    color = WHITE;
                    mask = 0;
                    break;
                default:
                    break;
            }
        }
        return flat;
    }
}
