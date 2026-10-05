package pers.roinflam.battlecorrection.client.editor;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.Mth;
import pers.roinflam.battlecorrection.network.packet.ApplyItemEditC2SPacket;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 从剪贴板导入整件物品
 * <p>
 * 认三种写法：
 * <ul>
 *   <li>{@code /give @p minecraft:diamond_sword{Damage:5} 1}：给物品命令，前面的斜杠和目标选择器都可以省</li>
 *   <li>{@code minecraft:diamond_sword{Damage:5}}：物品 ID 后面直接跟 NBT，数量可以省</li>
 *   <li>{@code {id:"minecraft:stick",Count:1b,tag:{...}}}：存盘结构的 SNBT；
 *   花括号里没有 id 的当作只有 tag 的 NBT，套在当前这件物品上</li>
 * </ul>
 * 结果统一成 {id, Count, tag} 的根 NBT，交给 {@link EditorSession#pushRoot} 写进去。
 */
public final class ItemImport {

    /**
     * 解析结果：成功时 root 非空，失败时 error 是给玩家看的原因
     */
    public record Result(@Nullable CompoundTag root, @Nullable String error) {
    }

    private ItemImport() {
    }

    /**
     * 解析剪贴板文本
     *
     * @param text         文本
     * @param currentId    当前物品的 ID（只有 tag 的写法要用）
     * @param currentCount 当前数量
     * @return 结果
     */
    @Nonnull
    public static Result parse(@Nonnull String text, @Nonnull String currentId, int currentCount) {
        String input = text.trim();
        if (input.isEmpty()) {
            return fail(I18n.get("gui.battlecorrection.editor.import.empty"));
        }
        if (input.startsWith("/")) {
            input = input.substring(1).trim();
        }
        if (input.startsWith("give ")) {
            input = skipToken(input.substring(5).trim());
        }

        try {
            if (input.startsWith("{")) {
                CompoundTag parsed = TagParser.parseTag(input);
                if (parsed.contains("id")) {
                    return new Result(parsed, null);
                }
                CompoundTag root = new CompoundTag();
                root.putString("id", currentId);
                root.putByte("Count", (byte) Mth.clamp(currentCount, ApplyItemEditC2SPacket.MIN_COUNT,
                        ApplyItemEditC2SPacket.MAX_COUNT));
                root.put("tag", parsed);
                return new Result(root, null);
            }

            int idEnd = 0;
            while (idEnd < input.length() && isIdChar(input.charAt(idEnd))) {
                idEnd++;
            }
            if (idEnd == 0) {
                return fail(I18n.get("gui.battlecorrection.editor.import.unrecognized"));
            }
            String id = input.substring(0, idEnd);
            String rest = input.substring(idEnd).trim();

            CompoundTag tag = null;
            if (rest.startsWith("{")) {
                int close = matchingBrace(rest);
                if (close < 0) {
                    return fail(I18n.get("gui.battlecorrection.editor.import.unclosed"));
                }
                tag = TagParser.parseTag(rest.substring(0, close + 1));
                rest = rest.substring(close + 1).trim();
            }
            int count = 1;
            if (!rest.isEmpty()) {
                try {
                    count = Integer.parseInt(rest.split("\\s+")[0]);
                } catch (NumberFormatException e) {
                    return fail(I18n.get("gui.battlecorrection.editor.import.bad_count", rest));
                }
            }
            CompoundTag root = new CompoundTag();
            root.putString("id", id.indexOf(':') >= 0 ? id : "minecraft:" + id);
            root.putByte("Count", (byte) Mth.clamp(count, ApplyItemEditC2SPacket.MIN_COUNT,
                    ApplyItemEditC2SPacket.MAX_COUNT));
            if (tag != null && !tag.isEmpty()) {
                root.put("tag", tag);
            }
            return new Result(root, null);
        } catch (CommandSyntaxException e) {
            return fail(e.getMessage() == null ? "?" : e.getMessage());
        }
    }

    @Nonnull
    private static Result fail(@Nonnull String error) {
        return new Result(null, error);
    }

    /**
     * 跳过第一个以空白结尾的词（give 命令的目标选择器）
     */
    @Nonnull
    private static String skipToken(@Nonnull String text) {
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
            } else if (Character.isWhitespace(c) && depth <= 0) {
                return text.substring(i).trim();
            }
        }
        return "";
    }

    private static boolean isIdChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' || c == '/' || c == ':';
    }

    /**
     * 找和开头那个 { 配对的 }，引号里的括号和转义不算；找不到返回 -1
     */
    private static int matchingBrace(@Nonnull String text) {
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
