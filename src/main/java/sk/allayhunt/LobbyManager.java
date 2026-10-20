package sk.allayhunt;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class LobbyManager {
    private static final String IMPORTED_NAME = "allayhunt_lobby";

    private final AllayHunt plugin;
    private World world;

    public LobbyManager(AllayHunt plugin) {
        this.plugin = plugin;
    }

    public World world() {
        return world;
    }

    public void load() {
        String name = plugin.getConfig().getString("lobby.world", IMPORTED_NAME);
        world = Bukkit.getWorld(name);
        if (world == null) {
            WorldCreator wc = new WorldCreator(name);
            if (!new File(Bukkit.getWorldContainer(), name).exists()) {
                wc.type(WorldType.FLAT);
                plugin.getLogger().warning("Lobby world '" + name + "' not found - creating a flat one. "
                        + "Import yours with /ah importlobby <folder>.");
            }
            world = wc.createWorld();
        }
        if (world == null) world = Bukkit.getWorlds().get(0);

        world.setDifficulty(Difficulty.PEACEFUL);
        world.setTime(6000);
        world.setStorm(false);
        world.setPVP(false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
    }

    public Location spawn() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("lobby.spawn");
        if (s != null && s.contains("x")) {
            return new Location(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                    (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
        }
        return world.getSpawnLocation().add(0.5, 0, 0.5);
    }

    public void setSpawn(Location l) {
        world = l.getWorld();
        plugin.getConfig().set("lobby.world", world.getName());
        plugin.getConfig().set("lobby.spawn.x", l.getX());
        plugin.getConfig().set("lobby.spawn.y", l.getY());
        plugin.getConfig().set("lobby.spawn.z", l.getZ());
        plugin.getConfig().set("lobby.spawn.yaw", l.getYaw());
        plugin.getConfig().set("lobby.spawn.pitch", l.getPitch());
        plugin.saveConfig();
    }

    /**
     * Importuje lobby zoska plugins/AllayHunt/import/<folder>.
     * Musi to byt JAVA svet (Bedrock svet sa najprv prekonvertuje cez Chunker).
     */
    public void importWorld(CommandSender s, String folder, GameManager game) {
        File src = new File(new File(plugin.getDataFolder(), "import"), folder);
        if (!src.isDirectory()) {
            s.sendMessage(Util.c("&cFolder not found: &fplugins/AllayHunt/import/" + folder));
            return;
        }
        if (new File(src, "db").isDirectory() || new File(src, "levelname.txt").exists()) {
            s.sendMessage(Util.c("&cThis is a &lBedrock&c world - Java can't read it directly."));
            s.sendMessage(Util.c("&7Convert it to Java Edition with &fChunker &7(chunker.app), then put the "
                    + "output folder into &fplugins/AllayHunt/import/&7 and run this again."));
            return;
        }
        if (!new File(src, "level.dat").exists()) {
            s.sendMessage(Util.c("&cNo level.dat in that folder - is it a Java world?"));
            return;
        }

        World old = Bukkit.getWorld(IMPORTED_NAME);
        if (old != null) {
            Location fallback = Bukkit.getWorlds().get(0).getSpawnLocation();
            for (Player p : old.getPlayers()) p.teleport(fallback);
            Bukkit.unloadWorld(old, false);
        }

        File dest = new File(Bukkit.getWorldContainer(), IMPORTED_NAME);
        s.sendMessage(Util.c("&7Copying world..."));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Util.deleteDir(dest);
                Util.copyDir(src, dest);
                new File(dest, "uid.dat").delete();
                new File(dest, "session.lock").delete();
            } catch (Exception ex) {
                Bukkit.getScheduler().runTask(plugin, () -> s.sendMessage(Util.c("&cCopy failed: " + ex.getMessage())));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getConfig().set("lobby.world", IMPORTED_NAME);
                plugin.getConfig().set("lobby.spawn", null);
                plugin.saveConfig();
                load();
                game.sendAllToLobby();
                s.sendMessage(Util.c("&aLobby imported! &7Stand where players should appear and use &f/ah setlobby"));
            });
        });
    }
}
