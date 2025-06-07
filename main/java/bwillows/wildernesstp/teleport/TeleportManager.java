package bwillows.wildernesstp.teleport;

import bwillows.wildernesstp.WildernessTP;
import com.massivecraft.factions.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class TeleportManager {
    WildernessTP plugin;

    // Factions instance, may be null
    private Factions factions;  // Factions plugin instance

    public TeleportManager(WildernessTP plugin) {
        this.plugin = plugin;

        Plugin factionsPlugin = Bukkit.getPluginManager().getPlugin("Factions");

        if (factionsPlugin != null && factionsPlugin.isEnabled()) {
            // Factions is installed and enabled, safe to use
            this.factions = (Factions) factionsPlugin;
        } else {
            // Factions is not installed, handle accordingly
            this.factions = null;
        }
    }

    public class teleportData {
        public Long startTime;
        public Location startLocation;
    }

    // Player UUID to teleport initiation time
    public Map<UUID, teleportData> teleportingPlayers;


    public void startTeleport(Player player) {

        boolean teleportDelayEnabled = plugin.getConfig().getBoolean("teleport-delay.enabled", true);
        int teleportDelay = plugin.getConfig().getInt("teleport-delay.delay", 5);

        if(teleportDelayEnabled && !player.hasPermission("wildernesstp.bypass-delay")) {
            startTeleportWithMovementCheck(player, teleportDelay);
            return;
        }
        teleport(player);
    }

    public void startTeleportWithMovementCheck(Player player, int delaySeconds) {
        String raw = plugin.getConfig().getString("messages.delay-start", "&eTeleporting in {seconds} seconds...");
        String message = ChatColor.translateAlternateColorCodes('&', raw.replace("{seconds}", String.valueOf(delaySeconds)));
        player.sendMessage(message);

        Location initialLocation = player.getLocation().clone();

        new BukkitRunnable() {
            int ticksElapsed = 0;

            @Override
            public void run() {
                Location currentLocation = player.getLocation();
                if (!isSameBlock(initialLocation, currentLocation)) {
                    String raw = plugin.getConfig().getString("messages.teleport-cancelled-move", "&cTeleport cancelled because you moved.");
                    String message = ChatColor.translateAlternateColorCodes('&', raw);
                    player.sendMessage(message);
                    plugin.wildCommand.teleportCooldownMap.remove(player.getUniqueId());
                    this.cancel();
                    return;
                }

                if (ticksElapsed >= delaySeconds * 20) {
                    teleport(player);
                    this.cancel();
                    return;
                }

                ticksElapsed += 5;
            }
        }.runTaskTimer(plugin, 0L, 5L); // plugin must be available in this class
    }

    private boolean isSameBlock(Location loc1, Location loc2) {
        return loc1.getWorld().equals(loc2.getWorld()) &&
                loc1.getBlockX() == loc2.getBlockX() &&
                loc1.getBlockY() == loc2.getBlockY() &&
                loc1.getBlockZ() == loc2.getBlockZ();
    }

    public void teleport(Player player) {
        World world = player.getWorld();
        String playerWorldName = world.getName();

        int configMaxDistance = plugin.getConfig().getInt(
                "world-settings." + playerWorldName + ".max-distance",
                plugin.getConfig().getInt("default-max-distance", 1000)
        );

        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        double borderRadius = border.getSize() / 2.0;

        // Optional safety buffer to avoid edge
        double buffer = 10.0;

        // Final teleport radius: the smaller of config distance and border radius, minus buffer
        double effectiveRadius = Math.min(borderRadius, configMaxDistance) - buffer;

        int maxHeight = world.getMaxHeight() - 1;
        int minHeight = 0;

        int MAX_CHUNK_RETRY_ATTEMPTS = 20;

        for(int chunk_attempt = 0; chunk_attempt < MAX_CHUNK_RETRY_ATTEMPTS; chunk_attempt++) {
            // teleportation attempt
            {
                // Generate random X/Z within this radius from the center
                Random random = new Random();
                double chunkX = center.getX() - effectiveRadius + (random.nextDouble() * effectiveRadius * 2);
                double chunkZ = center.getZ() - effectiveRadius + (random.nextDouble() * effectiveRadius * 2);

                // Round to int block coordinates
                int blockX = (int) chunkX;
                int blockZ = (int) chunkZ;

                Location location = new Location(world, blockX, maxHeight - 1, blockZ);

                Chunk chunk = world.getChunkAt(location);

                if(factions != null) {
                    Board board = Board.getInstance();
                    Faction subChunkFaction = board.getFactionAt(new FLocation(location));
                    FPlayer fPlayer =  FPlayers.getInstance().getByPlayer(player);

                    if(!Factions.getInstance().getWilderness().equals(subChunkFaction)) {
                        // chunk must be in wilderness
                        continue;
                    }
                }


                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {

                        Block block2above;
                        Block block1above;

                        boolean skipYColumn = false;

                        if (!skipYColumn) {
                            for (int y = maxHeight - 5; y > 1; y--) {

                                Block block = chunk.getBlock(x, y, z);

                                if (block.getType().equals(Material.LAVA) || block.getType().equals(Material.WATER) || block.getType().equals(Material.WATER)) {
                                    skipYColumn = true;
                                }

                                if (!skipYColumn) {
                                    boolean isAirAbove = true;

                                    for (int yHeadspace = 1; yHeadspace < 5; yHeadspace++) {
                                        Block blockAbove = chunk.getBlock(x, y + yHeadspace, z);
                                        if (!blockAbove.getType().equals(Material.AIR)) {
                                            isAirAbove = false;
                                        }
                                    }

                                    if (block.getType().isSolid() && !block.getType().equals(Material.LAVA) && !block.getType().equals(Material.WATER) && isAirAbove) {
                                        Location teleportLocation = new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 2, block.getZ() + 0.5);
                                        player.teleport(teleportLocation);

                                        String raw = plugin.getConfig().getString("messages.teleport-success", "&aTeleported to a wild location!");
                                        String message = ChatColor.translateAlternateColorCodes('&', raw);
                                        player.sendMessage(message);

                                        if(isAtLeast1_11()) {
                                            ConfigurationSection section = plugin.getConfig().getConfigurationSection("teleport-title");
                                            if (section != null && section.getBoolean("enabled", false)) {
                                                String title = ChatColor.translateAlternateColorCodes('&', section.getString("title", ""));
                                                String subtitle = ChatColor.translateAlternateColorCodes('&', section.getString("subtitle", ""));
                                                int fadeIn = section.getInt("fade-in", 10);
                                                int stay = section.getInt("stay", 40);
                                                int fadeOut = section.getInt("fade-out", 20);

                                                player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
                                            }
                                        }
                                        return;
                                    }
                                }
                            }
                        }

                    }
                }
            }
        }
        // exceeded MAX_CHUNK_RETRY_ATTEMPTS and teleportation failed.
        String raw = plugin.getConfig().getString("messages.teleport-failed", "&cFailed to find a safe location. Try again.");
        String message = ChatColor.translateAlternateColorCodes('&', raw);
        player.sendMessage(message);

        // no teleport cooldown if teleportation failed
        plugin.wildCommand.teleportCooldownMap.remove(player.getUniqueId());
    }

    public boolean isVersion1_8() {
        String version = plugin.getServer().getVersion();  // Get server version
        Bukkit.getPlayer("bwillows").sendMessage(version);
        return version.contains("1.8");  // Check if it's 1.8 or newer
    }

    public static boolean isAtLeast1_11() {
        String version = org.bukkit.Bukkit.getBukkitVersion(); // e.g. "1.8.8-R0.1-SNAPSHOT"
        String[] parts = version.split("-")[0].split("\\.");

        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;

            // Check for 1.11 or newer
            return (major > 1) || (major == 1 && minor >= 11);
        } catch (NumberFormatException e) {
            // Fallback to false if parsing fails
            return false;
        }
    }


}
