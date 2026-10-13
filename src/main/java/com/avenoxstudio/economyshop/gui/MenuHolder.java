package com.avenoxstudio.economyshop.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class MenuHolder implements InventoryHolder {
    private final String type;
    private final String argument;
    private Inventory inventory;

    public MenuHolder(String type, String argument) {
        this.type = type;
        this.argument = argument;
    }

    public String type() { return type; }
    public String argument() { return argument; }
    public void bind(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }
}
