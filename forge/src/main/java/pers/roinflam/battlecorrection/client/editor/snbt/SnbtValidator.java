package pers.roinflam.battlecorrection.client.editor.snbt;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * SNBT 校验：用原版 TagParser 解析，失败时给出出错位置的行列号
 * <p>
 * 和"应用"用的是同一个解析器，所以这里说有效，应用时就一定能解析过去；结果里带着解析出来的 NBT，
 * 文本没再改动的话应用时直接复用，2MB 的文本不用在主线程再解析一遍。
 * 纯数据处理，可以在后台线程对文本快照调用。
 */
public final class SnbtValidator {

    /**
     * 嵌套太深导致栈溢出时的消息键（原版解析器是递归的，没有深度限制）
     */
    public static final String KEY_TOO_DEEP = "gui.battlecorrection.editor.snbt.error.too_deep";

    /**
     * 校验结果
     *
     * @param tag        解析成功时的 NBT
     * @param offset     出错位置（全文下标），成功时 -1
     * @param line       出错行（从 1 起）
     * @param column     出错列（从 1 起）
     * @param message    出错原因（已经是当前语言的文字），成功时为空串
     * @param messageKey 需要界面自己翻译的原因（目前只有嵌套太深），没有时为 null
     */
    public record Result(@Nullable CompoundTag tag, int offset, int line, int column, @Nonnull String message,
                         @Nullable String messageKey) {

        public boolean ok() {
            return tag != null;
        }
    }

    private SnbtValidator() {
    }

    /**
     * 解析
     *
     * @param text SNBT 文本
     * @return 结果
     */
    @Nonnull
    public static Result validate(@Nonnull String text) {
        try {
            return new Result(TagParser.parseTag(text), -1, 0, 0, "", null);
        } catch (CommandSyntaxException e) {
            int offset = e.getCursor() < 0 ? text.length() : Math.min(e.getCursor(), text.length());
            String reason = e.getRawMessage() == null ? String.valueOf(e.getMessage()) : e.getRawMessage().getString();
            return error(text, offset, reason, null);
        } catch (StackOverflowError e) {
            return error(text, 0, "", KEY_TOO_DEEP);
        }
    }

    @Nonnull
    private static Result error(@Nonnull String text, int offset, @Nonnull String reason, @Nullable String key) {
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        return new Result(null, offset, line, offset - lineStart + 1, reason, key);
    }
}
