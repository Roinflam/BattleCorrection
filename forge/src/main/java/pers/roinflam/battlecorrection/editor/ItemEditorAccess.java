package pers.roinflam.battlecorrection.editor;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import pers.roinflam.battlecorrection.config.ConfigAttribute;

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
        return ConfigAttribute.ITEM_EDITOR_ENABLED.get();
    }

    /**
     * 玩家是否有资格用（OP + 创造）
     *
     * @param player 玩家（两端都可以）
     * @return true = 可以用
     */
    public static boolean canUse(@Nonnull Player player) {
        return player.hasPermissions(PERMISSION_LEVEL) && player.isCreative();
    }

    /**
     * 检查开关和资格，不满足时给玩家发一条红字提示
     *
     * @param player 玩家（两端都可以）
     * @return true = 允许使用
     */
    public static boolean checkAndNotify(@Nonnull Player player) {
        if (!isEnabled()) {
            player.displayClientMessage(disabledMessage(), false);
            return false;
        }
        if (!canUse(player)) {
            player.displayClientMessage(noPermissionMessage(), false);
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
        return ConfigAttribute.ITEM_EDITOR_MAX_NBT_KB.get() * 1024;
    }

    /**
     * "功能已关闭"提示
     *
     * @return 红字提示
     */
    @Nonnull
    public static Component disabledMessage() {
        return Component.translatable("message.battlecorrection.editor.disabled").withStyle(ChatFormatting.RED);
    }

    /**
     * "没有权限"提示
     *
     * @return 红字提示
     */
    @Nonnull
    public static Component noPermissionMessage() {
        return Component.translatable("message.battlecorrection.editor.no_permission").withStyle(ChatFormatting.RED);
    }
}
