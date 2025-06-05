package bwillows.wildernesstp.commands;

import bwillows.wildernesstp.WildernessTP;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WildCommand implements CommandExecutor {
    private final WildernessTP plugin;

    public WildCommand(WildernessTP plugin) {
        this.plugin = plugin;
    }

    public Map<UUID, Long> teleportCooldownMap = new HashMap<>();

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        if(!player.hasPermission("wildernesstp.use")) {
            return true;
        }

        String playerWorldName = player.getWorld().getName();
        boolean worldEnabled = plugin.getConfig().getBoolean("world-settings." + playerWorldName + ".enabled", false);

        if(!worldEnabled) {
            String raw = plugin.getConfig().getString("messages.world-disabled", "&cYou cannot use /wild in this world.");
            String message = ChatColor.translateAlternateColorCodes('&', raw);
            player.sendMessage(message);
            return true;
        }

        if(plugin.getConfig().getBoolean("teleport-cooldown.enabled")) {
            // time in seconds since epoch
            Long now = System.currentTimeMillis() / 1000;
            if(teleportCooldownMap.containsKey(player.getUniqueId())) {
                Long lastRan = teleportCooldownMap.get(player.getUniqueId());
                Integer teleportCooldown = plugin.getConfig().getInt("teleport-cooldown.duration");
                Long timeElapsedSinceLastRan = now - lastRan;

                if(timeElapsedSinceLastRan < teleportCooldown && !player.hasPermission("wildernesstp.bypass-cooldown")) {
                    long seconds = teleportCooldown - timeElapsedSinceLastRan;
                    long minutes = seconds / 60;
                    long secs = seconds % 60;

                    String formatted = (minutes > 0 ? minutes + "m " : "") + secs + "s";

                    String raw = plugin.getConfig().getString("messages.cooldown-active", "&cYou must wait {cooldown} before using /wild again.");
                    String formattedMessage = ChatColor.translateAlternateColorCodes('&', raw.replace("{cooldown}", formatted));
                    player.sendMessage(formattedMessage);
                    return true;
                }
            }
            if(!player.hasPermission("wildernesstp.bypass-cooldown")) {
                teleportCooldownMap.put(player.getUniqueId(), now);
            }
        }

        plugin.teleportManager.startTeleport(player);

        return true;
    }
}
