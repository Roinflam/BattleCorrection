package pers.roinflam.battlecorrection.client.editor;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.battlecorrection.network.packet.ApplyItemEditC2SPacket;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 从剪贴板导入整件物品
 * <p>
 * 认这几种写法：
 * <ul>
 *   <li>{@code /give @p minecraft:diamond_sword[damage=5] 1}：1.20.5 起的给物品命令，前面的斜杠和目标选择器都可以省</li>
 *   <li>{@code minecraft:diamond_sword[damage=5]}：物品 ID 后面直接跟组件，数量可以省</li>
 *   <li>{@code /give @p minecraft:diamond_sword{Damage:5} 1}、{@code minecraft:diamond_sword{Damage:5}}：1.20.1 的旧写法，
 *   用原版数据修复器升级</li>
 *   <li>{@code {id:"minecraft:stick",count:1,components:{...}}}：存盘结构的 SNBT（1.20.1 的 {@code {id, Count, tag}} 也认，同样升级）；
 *   花括号里没有 id 的套在当前这件物品上：键全是 {@code 命名空间:组件} 的当作组件表，否则当作 1.20.1 的 tag</li>
 * </ul>
 * 结果统一成 {@code {id, count, components}} 的根，交给 {@link EditorSession#pushRoot} 写进去。
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
     * @param currentId    当前物品的 ID（只有 tag / 组件的写法要用）
     * @param currentCount 当前数量
     * @param registries   注册表（解析组件写法要用）
     * @return 结果
     */
    @Nonnull
    public static Result parse(@Nonnull String text, @Nonnull String currentId, int currentCount,
                               @Nonnull HolderLookup.Provider registries) {
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
                    return ItemRoots.isLegacy(parsed) ? upgraded(parsed) : new Result(parsed, null);
                }
                int count = Mth.clamp(currentCount, ApplyItemEditC2SPacket.MIN_COUNT, ApplyItemEditC2SPacket.MAX_COUNT);
                if (isComponentMap(parsed)) {
                    CompoundTag root = new CompoundTag();
                    root.putString(ItemRoots.KEY_ID, currentId);
                    root.putInt(ItemRoots.KEY_COUNT, count);
                    root.put(ItemRoots.KEY_COMPONENTS, parsed);
                    return new Result(root, null);
                }
                CompoundTag legacy = new CompoundTag();
                legacy.putString("id", currentId);
                legacy.putByte("Count", (byte) count);
                legacy.put("tag", parsed);
                return upgraded(legacy);
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

            if (!rest.startsWith("{")) {
                return parseComponents(input, registries);
            }
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
            CompoundTag legacy = new CompoundTag();
            legacy.putString("id", id.indexOf(':') >= 0 ? id : "minecraft:" + id);
            legacy.putByte("Count", (byte) Mth.clamp(count, ApplyItemEditC2SPacket.MIN_COUNT, 127));
            if (tag != null && !tag.isEmpty()) {
                legacy.put("tag", tag);
            }
            return upgraded(legacy);
        } catch (CommandSyntaxException e) {
            return fail(e.getMessage() == null ? "?" : e.getMessage());
        }
    }

    /**
     * 1.20.5 起的物品参数写法：{@code 物品ID[组件=值,...] 数量}，用原版的物品参数解析器读
     */
    @Nonnull
    private static Result parseComponents(@Nonnull String input, @Nonnull HolderLookup.Provider registries)
            throws CommandSyntaxException {
        StringReader reader = new StringReader(input);
        ItemParser.ItemResult item = new ItemParser(registries).parse(reader);
        String rest = reader.getRemaining().trim();
        int count = 1;
        if (!rest.isEmpty()) {
            try {
                count = Integer.parseInt(rest.split("\\s+")[0]);
            } catch (NumberFormatException e) {
                return fail(I18n.get("gui.battlecorrection.editor.import.bad_count", rest));
            }
        }
        count = Mth.clamp(count, ApplyItemEditC2SPacket.MIN_COUNT, ApplyItemEditC2SPacket.MAX_COUNT);
        ItemStack stack = new ItemStack(item.item(), count, item.components());
        return new Result(ItemRoots.save(stack, registries), null);
    }

    /**
     * 1.20.1 的写法升级成现在的根
     */
    @Nonnull
    private static Result upgraded(@Nonnull CompoundTag legacy) {
        CompoundTag root = ItemRoots.upgradeLegacy(legacy);
        return root == null ? fail(I18n.get("gui.battlecorrection.editor.item.legacy_failed")) : new Result(root, null);
    }

    /**
     * 花括号里是不是组件表：每个键都是 {@code 命名空间:路径}（删除写成 {@code !命名空间:路径}）
     */
    private static boolean isComponentMap(@Nonnull CompoundTag tag) {
        if (tag.isEmpty()) {
            return false;
        }
        for (String key : tag.getAllKeys()) {
            if (key.indexOf(':') < 0) {
                return false;
            }
        }
        return true;
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
