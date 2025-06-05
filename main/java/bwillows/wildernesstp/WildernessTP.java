package bwillows.wildernesstp;

import bwillows.wildernesstp.commands.WildCommand;
import bwillows.wildernesstp.teleport.TeleportManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class WildernessTP extends JavaPlugin {

    public WildernessTP plugin;
    public FileConfiguration config;

    public WildCommand wildCommand;
    public TeleportManager teleportManager;



    @Override
    public void onEnable() {
        plugin = this;

        File pluginFolder = new File(getDataFolder().getParent(), getDescription().getName());
        if (!pluginFolder.exists()) {
            pluginFolder.mkdirs();  // Creates the folder if it doesn't exist
        }

        // Save default config if it doesn't exist
        saveDefaultConfig();

        config = this.getConfig();

        wildCommand = new WildCommand(plugin);

        getCommand("wild").setExecutor(wildCommand);
        teleportManager = new TeleportManager(plugin);


    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
