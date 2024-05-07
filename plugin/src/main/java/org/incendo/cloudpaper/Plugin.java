package org.incendo.cloudpaper;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Plugin extends JavaPlugin {

    public static Logger LOGGER;
    private DatabaseManager databaseManager;
    private TicketManager ticketManager;
    private PermissonsManager permissonsManager;

    @Override
    public void onEnable() {

        this.saveDefaultConfig();
        LOGGER = this.getLogger();
        permissonsManager = new PermissonsManager(this);
        databaseManager = new DatabaseManager(this.getConfig());
        ticketManager = new TicketManager(this, databaseManager, permissonsManager, this.getConfig());
        setupDatabase();
        LOGGER.info("Enabled!"); // Log plugin enable status
    }

    @Override
    public void onDisable() {
        permissonsManager.save();
        databaseManager.disconnectFromDatabase(); // Disconnect from the database
        getLogger().info("Disabled!"); // Log plugin disable status
    }

    private void setupDatabase() {
        databaseManager.connectToDatabase();
        databaseManager.createTicketsTable();
    }
}