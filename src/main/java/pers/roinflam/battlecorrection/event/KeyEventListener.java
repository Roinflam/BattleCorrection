package pers.roinflam.battlecorrection.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.GuiModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.battlecorrection.BattleCorrection;
import pers.roinflam.battlecorrection.utils.LogUtil;

/**
 * 按键事件监听器
 * 处理模组列表快捷键
 */
@Mod.EventBusSubscriber
public class KeyEventListener {

    /**
     * 处理按键输入事件
     * 当玩家按下快捷键时打开模组列表
     */
    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onKeyInput(InputEvent.KeyInputEvent event) {
        if (BattleCorrection.keyOpenModList.isPressed() && Minecraft.getMinecraft().currentScreen == null) {
            GuiScreen gui = new GuiModList(Minecraft.getMinecraft().currentScreen);
            Minecraft.getMinecraft().displayGuiScreen(gui);

            LogUtil.debug("打开模组列表界面 - 触发按键: " + BattleCorrection.keyOpenModList.getDisplayName());
        }
    }
}