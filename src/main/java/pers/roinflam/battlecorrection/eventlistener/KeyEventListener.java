package pers.roinflam.battlecorrection.eventlistener;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.GuiModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.battlecorrection.BattleCorrection;

@Mod.EventBusSubscriber
public class KeyEventListener {

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onKeyInput(InputEvent.KeyInputEvent event) {
        // 如果按下了K键，并且当前没有打开其他GUI
        if (BattleCorrection.keyOpenModList.isPressed() && Minecraft.getMinecraft().currentScreen == null) {
            // 获取Forge自带的mod list界面的对象
            GuiScreen gui = new GuiModList(Minecraft.getMinecraft().currentScreen);
            // 显示GUI
            Minecraft.getMinecraft().displayGuiScreen(gui);
        }
    }

}