package pers.roinflam.battlecorrection.editor;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import pers.roinflam.battlecorrection.config.ConfigEditor;

import javax.annotation.Nonnull;

/**
 * 物品编辑器的使用条件：功能开关 + OP 2 级 + 创造模式
 * <p>
 * 客户端按快捷键时先用本地数据检查一遍（客户端知道自己的 OP 等级和游戏模式），
 * 不满足直接提示，省一次网络往返；服务端在开界面和保存时**都会再查一遍**，客户端的检查只是为了体验，不是安全边界。
 */
public final class ItemEditorAccess {

    /**
     * 需要的权限等级（2 = OP）
     */
    public static final int PERMISSION_LEVEL = 2;

    private ItemEditorAccess() {
    }

    /**
     * 功能是否在配置里开着
     *
     * @return true = 开启
     */
    public static boolean isEnabled() {
        return ConfigEditor.itemEditorEnabled;
    }

    /**
     * 玩家是否有资格用（OP + 创造）
     * <p>
     * canUseCommand 的命令名传空串：传具体命令名时原版对 seed / tell / help 之类有特殊放行。
     *
     * @param player 玩家（两端都可以）
     * @return true = 可以用
     */
    public static boolean canUse(@Nonnull EntityPlayer player) {
        return player.canUseCommand(PERMISSION_LEVEL, "") && player.isCreative();
    }

    /**
     * 检查开关和资格，不满足时给玩家发一条红字提示
     *
     * @param player 玩家（两端都可以）
     * @return true = 允许使用
     */
    public static boolean checkAndNotify(@Nonnull EntityPlayer player) {
        if (!isEnabled()) {
            player.sendStatusMessage(disabledMessage(), false);
            return false;
        }
        if (!canUse(player)) {
            player.sendStatusMessage(noPermissionMessage(), false);
            return false;
        }
        return true;
    }

    /**
     * NBT 大小上限（字节）
     *
     * @return 字节数
     */
    public static int maxNbtBytes() {
        return Math.max(16, Math.min(2048, ConfigEditor.itemEditorMaxNbtKb)) * 1024;
    }

    /**
     * "功能已关闭"提示
     *
     * @return 红字提示
     */
    @Nonnull
    public static ITextComponent disabledMessage() {
        return red("message.battlecorrection.editor.disabled");
    }

    /**
     * "没有权限"提示
     *
     * @return 红字提示
     */
    @Nonnull
    public static ITextComponent noPermissionMessage() {
        return red("message.battlecorrection.editor.no_permission");
    }

    @Nonnull
    private static ITextComponent red(@Nonnull String key, @Nonnull Object... args) {
        return new TextComponentTranslation(key, args).setStyle(new Style().setColor(TextFormatting.RED));
    }
}
