package pers.roinflam.battlecorrection.editor;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;

/**
 * 颜色码文本工具：编辑器里用 {@code &a} 这种写法，存进 NBT 时转成 {@code §a}
 * <p>
 * 为什么不直接编辑 §：原版的 EditBox 会把 § 当非法字符过滤掉（SharedConstants.isAllowedChatCharacter），
 * 打不进也粘不进。用 & 代替是 Bukkit 那边的老习惯，服主都熟。
 * <p>
 * 三种形态：
 * <ul>
 *   <li>editable：{@code &a绿色&l粗体}，编辑框里显示和编辑用的</li>
 *   <li>section：{@code §a绿色§l粗体}，写进文本组件的</li>
 *   <li>Component：读取物品上已有的名字/Lore 时，把带样式的组件"反编译"成 editable</li>
 * </ul>
 * 只有 & 后面紧跟合法代码字符（0-9 a-f k-o r 不分大小写，以及模组注册的自定义代码如 !）才会被当成颜色码，其他 & 原样保留。
 * 十六进制颜色（{@code #RRGGBB}）没有对应的 § 码，反编译时会丢掉，这种文本请走原始 NBT 页编辑。
 */
public final class LegacyText {

    /**
     * 原版颜色码前缀
     */
    public static final char SECTION = '\u00A7';

    /**
     * 编辑器里代替 § 的字符
     */
    public static final char AMPERSAND = '&';

    /**
     * 合法的代码字符（小写）
     */
    private static final String CODE_CHARS = "0123456789abcdefklmnor";

    /**
     * 原版的 16 种颜色，按 &0 ~ &f 的顺序
     * <p>
     * 不用 ChatFormatting.values()：有些模组会往这个枚举里塞自己的颜色，那些没有 § 码。
     */
    public static final ChatFormatting[] VANILLA_COLORS = {
            ChatFormatting.BLACK, ChatFormatting.DARK_BLUE, ChatFormatting.DARK_GREEN, ChatFormatting.DARK_AQUA,
            ChatFormatting.DARK_RED, ChatFormatting.DARK_PURPLE, ChatFormatting.GOLD, ChatFormatting.GRAY,
            ChatFormatting.DARK_GRAY, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.AQUA,
            ChatFormatting.RED, ChatFormatting.LIGHT_PURPLE, ChatFormatting.YELLOW, ChatFormatting.WHITE
    };

    /**
     * 重置码
     */
    private static final String RESET = "&r";

    private LegacyText() {
    }

    /**
     * 是否是合法的颜色/格式代码字符
     * <p>
     * 原版的 0-9 a-f k-o r 之外，模组往 ChatFormatting 里塞的自定义代码也算（例如某些模组用 {@code §!} 做流光/动态文字）：
     * 原版渲染时是按 ChatFormatting.getByCode 认的，这里保持一致，否则这类名字一经编辑 {@code §!} 就会变成普通字符，特效消失。
     *
     * @param c 字符
     * @return true = 合法
     */
    public static boolean isCodeChar(char c) {
        if (CODE_CHARS.indexOf(Character.toLowerCase(c)) >= 0) {
            return true;
        }
        return ChatFormatting.getByCode(c) != null;
    }

    /**
     * editable → section：把 {@code &x} 转成 {@code §x}
     *
     * @param editable 编辑器文本
     * @return 带 § 的文本
     */
    @Nonnull
    public static String toSection(@Nonnull String editable) {
        if (editable.indexOf(AMPERSAND) < 0) {
            return editable;
        }
        StringBuilder result = new StringBuilder(editable.length());
        int length = editable.length();
        for (int i = 0; i < length; i++) {
            char c = editable.charAt(i);
            if (c == AMPERSAND && i + 1 < length && isCodeChar(editable.charAt(i + 1))) {
                result.append(SECTION).append(Character.toLowerCase(editable.charAt(i + 1)));
                i++;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * section → editable：把所有 § 换成 &
     *
     * @param text 带 § 的文本
     * @return 编辑器文本
     */
    @Nonnull
    public static String fromSection(@Nonnull String text) {
        return text.replace(SECTION, AMPERSAND);
    }

    /**
     * editable → 文本组件（存 NBT 用）
     *
     * @param editable 编辑器文本
     * @param base     基础样式（例如 italic=false）
     * @return 组件
     */
    @Nonnull
    public static MutableComponent toComponent(@Nonnull String editable, @Nonnull Style base) {
        return Component.literal(toSection(editable)).setStyle(base);
    }

    /**
     * editable → 用于界面预览的组件（不带基础样式）
     *
     * @param editable 编辑器文本
     * @return 组件
     */
    @Nonnull
    public static MutableComponent preview(@Nonnull String editable) {
        return Component.literal(toSection(editable));
    }

    /**
     * 文本组件 → editable
     * <p>
     * 逐段扫描组件的样式：颜色、粗体、斜体、下划线、删除线、乱码，换算成 & 码；
     * 组件根样式里已有的（例如整条 Lore 统一 italic=false）不重复输出，因为写回时会作为基础样式带上。
     * 样式变化时能追加的就追加，去掉了什么才用 &r 重置。
     *
     * @param component 组件
     * @return 编辑器文本
     */
    @Nonnull
    public static String toEditable(@Nonnull Component component) {
        Style root = component.getStyle();
        StringBuilder result = new StringBuilder();
        String[] lastCodes = {""};

        component.<Boolean>visit((style, text) -> {
            if (!text.isEmpty()) {
                String codes = codesOf(style, root);
                appendTransition(result, lastCodes[0], codes);
                lastCodes[0] = codes;
                result.append(fromSection(text));
            }
            return Optional.empty();
        }, Style.EMPTY);

        return result.toString();
    }

    /**
     * 算出一个样式相对于根样式需要的 & 码
     *
     * @param style 段落样式
     * @param root  根样式
     * @return & 码串，可能为空
     */
    @Nonnull
    private static String codesOf(@Nonnull Style style, @Nonnull Style root) {
        StringBuilder codes = new StringBuilder();

        TextColor color = style.getColor();
        if (color != null && !Objects.equals(color, root.getColor())) {
            ChatFormatting formatting = legacyColor(color);
            if (formatting != null) {
                codes.append(AMPERSAND).append(formatting.getChar());
            }
        }
        if (style.isObfuscated() && !root.isObfuscated()) {
            codes.append(AMPERSAND).append(ChatFormatting.OBFUSCATED.getChar());
        }
        if (style.isBold() && !root.isBold()) {
            codes.append(AMPERSAND).append(ChatFormatting.BOLD.getChar());
        }
        if (style.isStrikethrough() && !root.isStrikethrough()) {
            codes.append(AMPERSAND).append(ChatFormatting.STRIKETHROUGH.getChar());
        }
        if (style.isUnderlined() && !root.isUnderlined()) {
            codes.append(AMPERSAND).append(ChatFormatting.UNDERLINE.getChar());
        }
        if (style.isItalic() && !root.isItalic()) {
            codes.append(AMPERSAND).append(ChatFormatting.ITALIC.getChar());
        }
        return codes.toString();
    }

    /**
     * 从上一段的样式码过渡到这一段：能追加就追加，否则先 &r 再重写
     *
     * @param result   输出
     * @param previous 上一段的码
     * @param current  这一段的码
     */
    private static void appendTransition(@Nonnull StringBuilder result, @Nonnull String previous,
                                         @Nonnull String current) {
        if (current.equals(previous)) {
            return;
        }
        if (previous.isEmpty()) {
            result.append(current);
            return;
        }
        if (current.startsWith(previous)) {
            result.append(current.substring(previous.length()));
            return;
        }
        result.append(RESET).append(current);
    }

    /**
     * 找出颜色值对应的原版 16 色
     *
     * @param color 颜色
     * @return 对应的 ChatFormatting；不是 16 色之一时返回 null
     */
    @Nullable
    private static ChatFormatting legacyColor(@Nonnull TextColor color) {
        for (ChatFormatting formatting : VANILLA_COLORS) {
            Integer value = formatting.getColor();
            if (value != null && value == color.getValue()) {
                return formatting;
            }
        }
        return null;
    }
}
