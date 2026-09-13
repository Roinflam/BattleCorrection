package pers.roinflam.battlecorrection.command;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.battlecorrection.utils.LogUtil;
import pers.roinflam.battlecorrection.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 命令注册入口
 * <p>
 * 这个类本身不引用 Curios；只有检测到装了 Curios 才会去调用 {@link CurioMarkCommand}，
 * 没装 Curios 时 CurioMarkCommand 这个类根本不会被加载，也就不会因为找不到 Curios 的类而崩溃。
 * {@link ItemEditorCommand} 不依赖 Curios，始终注册。
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class ModCommands {

    /**
     * 服务器注册命令时触发（每次开服、/reload 都会触发）
     *
     * @param evt 命令注册事件
     */
    @SubscribeEvent
    public static void onRegisterCommands(@Nonnull RegisterCommandsEvent evt) {
        ItemEditorCommand.register(evt.getDispatcher());
        LogUtil.info("已注册 /battlecorrection edit 命令");

        if (ModList.get().isLoaded("curios")) {
            CurioMarkCommand.register(evt.getDispatcher());
            LogUtil.info("已注册 /battlecorrection curio 命令");
        } else {
            LogUtil.info("未检测到 Curios，跳过 /battlecorrection curio 命令注册");
        }
    }
}
