package com.avenoxstudio.economyshop.gui;

import com.avenoxstudio.economyshop.EconomyShop;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;

public final class MenuListener implements Listener {
    private final EconomyShop plugin;
    public MenuListener(EconomyShop plugin){this.plugin=plugin;}
    @EventHandler public void onClick(InventoryClickEvent event){
        if(!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder))return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player player))return;
        if(event.getClickedInventory()==null||event.getClickedInventory()!=event.getView().getTopInventory())return;
        if(event.getClick()==ClickType.DOUBLE_CLICK||event.getClick()==ClickType.NUMBER_KEY||event.getClick()==ClickType.SWAP_OFFHAND||event.getClick()==ClickType.DROP||event.getClick()==ClickType.CONTROL_DROP)return;
        // Bukkit inventory events execute on the server thread; prices and balances are revalidated by the command service.
        plugin.shopCommand().onMenuClick(player,holder,event.getRawSlot(),event.getClick(),event.getCurrentItem());
    }
    @EventHandler public void onDrag(InventoryDragEvent event){
        if(event.getView().getTopInventory().getHolder() instanceof MenuHolder){
            for(int raw:event.getRawSlots())if(raw<event.getView().getTopInventory().getSize()){event.setCancelled(true);return;}
        }
    }
    @EventHandler public void onClose(InventoryCloseEvent event) {
        // No temporary item is stored in menu inventories; closing is safe.
    }
}