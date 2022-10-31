package pers.roinflam.battlecorrection.proxy;

import net.minecraft.item.Item;

public class CommonProxy {

    public void registerItemRenderer(Item item, int meta, String id) {

    }

    public void setInfinityItemEditor() {
//        try {
//            Class<?> guiAttributes = Class.forName("ruukas.infinityeditor.gui.GuiAttributes");
//            Field field = guiAttributes.getDeclaredField("sharedAttributes");
//            field.setAccessible(true);
//            IAttribute[] sharedAttributes = (IAttribute[]) field.get(null);
//            List<IAttribute> list = new ArrayList<>(Arrays.asList(sharedAttributes));
//            list.add(AttributesMagicDamage.MAGIC_DAMAGE);
//
//            field = guiAttributes.getDeclaredField("sharedAttributes");
//            field.setAccessible(true);
//            System.out.println(Arrays.toString(new IAttribute[]{AttributesMagicDamage.MAGIC_DAMAGE}));
//            field.set(guiAttributes, new IAttribute[]{});
//        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
//            throw new RuntimeException(e);
//        }
    }

}
