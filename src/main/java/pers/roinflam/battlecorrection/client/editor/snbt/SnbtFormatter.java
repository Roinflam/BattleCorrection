package pers.roinflam.battlecorrection.client.editor.snbt;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.SnbtPrinterTagVisitor;
import net.minecraft.nbt.TagParser;

import javax.annotation.Nonnull;
import java.util.ArrayList;

/**
 * SNBT 的格式化（缩进换行）和压缩（压成一行）
 * <p>
 * 格式化用原版 SnbtPrinterTagVisitor，缩进改成两格：编辑区不宽，四格缩进到第三四层就挤得没地方放内容了；
 * 编辑时回车自动缩进也是两格，两边一致。
 * <p>
 * 压缩不走 tag.toString()（它会把键的顺序打乱成哈希顺序），而是在原文上去掉字符串外面的空白，保留原来的书写顺序和数字写法。
 * 合法 SNBT 里字符串外的空白只起分隔作用，去掉不改语义；保险起见压完再解析一遍和原结果比，不一致就退回 tag.toString()。
 * 纯数据处理，可以在后台线程调用。
 */
public final class SnbtFormatter {

    /**
     * 缩进单位
     */
    public static final String INDENT = "  ";

    private SnbtFormatter() {
    }

    /**
     * 格式化成带缩进换行的 SNBT（键按字母排序，和原版 /data 的输出一致）
     *
     * @param tag NBT
     * @return 文本
     */
    @Nonnull
    public static String pretty(@Nonnull CompoundTag tag) {
        return new SnbtPrinterTagVisitor(INDENT, 0, new ArrayList<>()).visit(tag);
    }

    /**
     * 压成一行
     *
     * @param text   原文（必须是能解析成 parsed 的合法 SNBT）
     * @param parsed 原文解析出来的 NBT
     * @return 一行的 SNBT
     */
    @Nonnull
    public static String compact(@Nonnull String text, @Nonnull CompoundTag parsed) {
        String stripped = stripWhitespace(text);
        try {
            if (TagParser.parseTag(stripped).equals(parsed)) {
                return stripped;
            }
        } catch (CommandSyntaxException | StackOverflowError ignored) {
            // 理论上不会走到；万一去空白改变了语义，就用原版的紧凑输出兜底
        }
        return parsed.toString();
    }

    /**
     * 去掉字符串外面的所有空白（引号里的内容原样保留，转义照常跳过）
     *
     * @param text 文本
     * @return 去掉空白后的文本
     */
    @Nonnull
    public static String stripWhitespace(@Nonnull CharSequence text) {
        int length = text.length();
        StringBuilder out = new StringBuilder(length);
        char quote = 0;
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                out.append(c);
                if (c == '\\' && i + 1 < length) {
                    out.append(text.charAt(++i));
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                out.append(c);
            } else if (!Character.isWhitespace(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }
}
