package pers.roinflam.battlecorrection.client.editor;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTPrimitive;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import pers.roinflam.battlecorrection.client.editor.ui.Mth;
import pers.roinflam.battlecorrection.client.editor.ui.Ui;
import pers.roinflam.battlecorrection.network.packet.ApplyItemEditC2SPacket;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 从剪贴板导入整件物品
 * <p>
 * 认这几种写法：
 * <ul>
 *   <li>{@code /give @p minecraft:diamond_sword 1 5 {display:{Name:"x"}}}：1.12.2 的给物品命令
 *   （玩家、物品、数量、损害值、NBT），前面的斜杠可以省，数量、损害值、NBT 从后往前都可以省</li>
 *   <li>{@code minecraft:wool 1 14} / {@code minecraft:diamond_sword{ench:[...]} 1}：省掉 give 和玩家，
 *   只写物品 ID；NBT 写在数量前面或者最后面都行</li>
 *   <li>{@code {id:"minecraft:stick",Count:1b,Damage:0s,tag:{...}}}：存盘结构的 SNBT；
 *   花括号里没有 id 的当作只有 tag 的 NBT，套在当前这件物品上（物品、数量、损害值不变）</li>
 * </ul>
 * 结果统一成 {id, Count, Damage, tag} 的根 NBT，交给 {@link EditorSession#pushRoot} 写进去；
 * 物品 ID 认不认识也在那里查。
 */
public final class ItemImport {

    private ItemImport() {
    }

    /**
     * 解析结果：成功时 root 是 {id, Count, Damage, tag} 形式的物品根标签，失败时 error 是给玩家看的原因
     */
    public static final class Result {
        @Nullable
        private final NBTTagCompound root;
        @Nullable
        private final String error;

        public Result(@Nullable NBTTagCompound root, @Nullable String error) {
            this.root = root;
            this.error = error;
        }

        @Nullable
        public NBTTagCompound root() {
            return root;
        }

        @Nullable
        public String error() {
            return error;
        }
    }

    /**
     * 解析剪贴板文本
     *
     * @param text          剪贴板文本
     * @param currentId     现在的物品 ID（文本里没写物品时用）
     * @param currentCount  现在的数量
     * @param currentDamage 现在的损害值
     * @return 结果
     */
    @Nonnull
    public static Result parse(@Nonnull String text, @Nonnull String currentId, int currentCount, int currentDamage) {
        String input = text.trim();
        if (input.isEmpty()) {
            return fail(Ui.translate("gui.battlecorrection.editor.import.empty"));
        }
        if (input.startsWith("/")) {
            input = input.substring(1).trim();
        }
        if (input.startsWith("give ")) {
            input = skipToken(input.substring(5).trim());
        }

        try {
            if (input.startsWith("{")) {
                return parseCompound(input, currentId, currentCount, currentDamage);
            }
            return parseItemArguments(input);
        } catch (NBTException e) {
            return fail(e.getMessage() == null ? "?" : e.getMessage());
        }
    }

    /**
     * 整段是一个花括号：带 id 的是物品根，不带的是 tag
     */
    @Nonnull
    private static Result parseCompound(@Nonnull String input, @Nonnull String currentId, int currentCount,
                                        int currentDamage) throws NBTException {
        int close = matchingBrace(input);
        if (close < 0) {
            return fail(Ui.translate("gui.battlecorrection.editor.import.unclosed"));
        }
        NBTTagCompound parsed = JsonToNBT.getTagFromJson(input.substring(0, close + 1));
        if (parsed.hasKey("id", 8)) {
            return new Result(parsed, null);
        }
        if (parsed.hasKey("id", 99) && (parsed.hasKey("Count", 99) || parsed.hasKey("tag", 10))) {
            // 很老的存档里物品 ID 是数字（id:276s），换成注册名再交出去
            Item item = Item.getItemById(((NBTPrimitive) parsed.getTag("id")).getInt());
            ResourceLocation name = item == null || item == Items.AIR ? null : item.getRegistryName();
            if (name == null) {
                return fail(Ui.translate("gui.battlecorrection.editor.import.unrecognized"));
            }
            parsed.setString("id", name.toString());
            return new Result(parsed, null);
        }
        NBTTagCompound root = new NBTTagCompound();
        root.setString("id", currentId);
        root.setByte("Count", (byte) Mth.clamp(currentCount, ApplyItemEditC2SPacket.MIN_COUNT,
                ApplyItemEditC2SPacket.MAX_COUNT));
        root.setShort("Damage", (short) Mth.clamp(currentDamage, 0, ApplyItemEditC2SPacket.MAX_DAMAGE));
        root.setTag("tag", parsed);
        return new Result(root, null);
    }

    /**
     * 物品 ID 打头：后面依次是数量、损害值，NBT 可以紧跟在 ID 后面，也可以放在最后
     */
    @Nonnull
    private static Result parseItemArguments(@Nonnull String input) throws NBTException {
        int idEnd = 0;
        while (idEnd < input.length() && isIdChar(input.charAt(idEnd))) {
            idEnd++;
        }
        if (idEnd == 0) {
            return fail(Ui.translate("gui.battlecorrection.editor.import.unrecognized"));
        }
        String id = input.substring(0, idEnd);
        String rest = input.substring(idEnd).trim();
        if (!rest.isEmpty() && !rest.startsWith("{") && !Character.isWhitespace(input.charAt(idEnd))) {
            // ID 后面紧跟着既不是空格也不是花括号的字符（比如 stone[...]），不是能认的写法
            return fail(Ui.translate("gui.battlecorrection.editor.import.unrecognized"));
        }

        NBTTagCompound tag = null;
        Integer count = null;
        Integer damage = null;
        while (!rest.isEmpty()) {
            if (rest.startsWith("{")) {
                if (tag != null) {
                    return fail(Ui.translate("gui.battlecorrection.editor.import.unrecognized"));
                }
                int close = matchingBrace(rest);
                if (close < 0) {
                    return fail(Ui.translate("gui.battlecorrection.editor.import.unclosed"));
                }
                tag = JsonToNBT.getTagFromJson(rest.substring(0, close + 1));
                rest = rest.substring(close + 1).trim();
                continue;
            }
            String[] split = rest.split("\\s+", 2);
            String token = split[0];
            rest = split.length > 1 ? split[1].trim() : "";
            if (count == null) {
                count = parseInt(token);
                if (count == null) {
                    return fail(Ui.translate("gui.battlecorrection.editor.import.bad_count", token));
                }
            } else if (damage == null) {
                damage = parseInt(token);
                if (damage == null) {
                    return fail(Ui.translate("gui.battlecorrection.editor.import.bad_data", token));
                }
            } else {
                return fail(Ui.translate("gui.battlecorrection.editor.import.unrecognized"));
            }
        }

        NBTTagCompound root = new NBTTagCompound();
        root.setString("id", id.indexOf(':') >= 0 ? id : "minecraft:" + id);
        root.setByte("Count", (byte) Mth.clamp(count == null ? 1 : count, ApplyItemEditC2SPacket.MIN_COUNT,
                ApplyItemEditC2SPacket.MAX_COUNT));
        root.setShort("Damage", (short) Mth.clamp(damage == null ? 0 : damage, 0, ApplyItemEditC2SPacket.MAX_DAMAGE));
        if (tag != null && !tag.hasNoTags()) {
            root.setTag("tag", tag);
        }
        return new Result(root, null);
    }

    @Nullable
    private static Integer parseInt(@Nonnull String token) {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nonnull
    private static Result fail(@Nonnull String error) {
        return new Result(null, error);
    }

    /**
     * 跳过第一个以空白结尾的词（give 命令的玩家名或目标选择器，选择器的方括号里可以有空格）
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

    /**
     * 1.12.2 的物品 ID 允许大写（读的时候统一转小写），所以这里也认大写字母
     */
    private static boolean isIdChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || c == '_' || c == '-' || c == '.' || c == '/' || c == ':';
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
            if (c == '"') {
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
