package com.avenoxstudio.economyshop;

import com.avenoxstudio.economyshop.command.ShopCommand;
import com.avenoxstudio.economyshop.gui.MenuListener;
import com.avenoxstudio.economyshop.storage.OrderRepository;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;
import java.util.logging.Level;

public final class EconomyShop extends JavaPlugin {
    private Economy economy;
    private OrderRepository orders;
    private ShopCommand shopCommand;

    @Override public void onEnable() {
        saveDefaultConfig();
        for (String name : new String[]{"messages.yml","categories.yml","prices.yml","gui.yml","storage.yml","orders.yml","sounds.yml","permissions.yml","integrations.yml","tax.yml","auction.yml"}) {
            if (!new File(getDataFolder(), name).exists()) saveResource(name, false);
        }
        if (!setupEconomy()) getLogger().warning("No Vault economy provider found. Monetary operations will be disabled.");
        try {
            orders = new OrderRepository(new File(getDataFolder(), getConfig().getString("storage.sqlite-file", "economyshop.db")));
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Storage failed to initialize; order transactions are disabled.", e);
        }
        shopCommand = new ShopCommand(this);
        for (String command : new String[]{"shop","sell","sellhand","sellall","orders","order","sellhistory","balance","worth","economyshop"}) {
            Objects.requireNonNull(getCommand(command), "Missing command in plugin.yml: " + command).setExecutor(shopCommand);
            Objects.requireNonNull(getCommand(command)).setTabCompleter(shopCommand);
        }
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getLogger().info("EconomyShop enabled. Economy: " + (economy == null ? "unavailable" : economy.getName()) + "; storage: " + (orders == null ? "unavailable" : "SQLite"));
    }

    private boolean setupEconomy() {
        economy = null;
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    public ShopCommand shopCommand() { return shopCommand; }
    public Economy economy() { return economy; }
    public OrderRepository orders() { return orders; }
    public boolean hasEconomy() { return economy != null; }
    public void reloadPlugin() {
        reloadConfig();
        if (shopCommand != null) shopCommand.reloadFiles();
        setupEconomy();
    }

    @Override public void onDisable() {
        if (orders != null) {
            try { orders.close(); } catch (Exception e) { getLogger().log(Level.WARNING, "Failed to close SQLite database", e); }
        }
    }
}