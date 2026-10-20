package sk.allayhunt;

import java.io.File;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.Difficulty;
import org.bukkit.block.Block;

/** Vytvara (a maze) arenu pre kazde kolo + hlada bezpecne nahodne miesta. */
public final class ArenaManager {
    private static final String PREFIX = "allayhunt_arena_";

    private final AllayHunt plugin;

    public ArenaManager(AllayHunt plugin) {
        this.plugin = plugin;
    }

    /** Zmaze zvysky arien po padnutom servri. */
    public void cleanupLeftovers() {
        File[] files = Bukkit.getWorldContainer().listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory() && f.getName().startsWith(PREFIX) && Bukkit.getWorld(f.getName()) == null) {
                try {
                    Util.deleteDir(f);
                } catch (Exception ex) {
                    plugin.getLogger().warning("Could not delete old arena " + f.getName());
                }
            }
        }
    }

    public World create() {
        String mode = plugin.getConfig().getString("arena.mode", "generate");
        World w;
        if (mode.equalsIgnoreCase("existing")) {
            w = Bukkit.getWorld(plugin.getConfig().getString("arena.existing-world", "world"));
        } else {
            WorldCreator wc = new WorldCreator(PREFIX + System.currentTimeMillis())
                    .environment(World.Environment.NORMAL)
                    .seed(ThreadLocalRandom.current().nextLong());
            w = wc.createWorld();
        }
        if (w != null) setup(w);
        return w;
    }

    private void setup(World w) {
        w.setPVP(true);
        w.setDifficulty(Difficulty.NORMAL);
        w.setGameRule(GameRule.DO_MOB_SPAWNING, plugin.getConfig().getBoolean("arena.mob-spawning", false));
        w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        WorldBorder b = w.getWorldBorder();
        b.setCenter(0, 0);
        b.setSize(plugin.getConfig().getInt("arena.border-size", 1000));
    }

    /** Zrusi vygenerovanu arenu (volaj az ked su vsetci hraci von). */
    public void dispose(World w) {
        if (w == null || !w.getName().startsWith(PREFIX)) return;
        File folder = w.getWorldFolder();
        if (Bukkit.unloadWorld(w, false)) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    Util.deleteDir(folder);
                } catch (Exception ex) {
                    plugin.getLogger().warning("Could not delete arena folder " + folder.getName());
                }
            });
        } else {
            plugin.getLogger().warning("Could not unload arena " + w.getName());
        }
    }

    /** Nahodne bezpecne miesto (nie voda / lava / listie) v dosahu +-radius od stredu. */
    public Location randomSafe(World w, int radius) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Block last = null;
        for (int i = 0; i < 40; i++) {
            int x = r.nextInt(-radius, radius + 1);
            int z = r.nextInt(-radius, radius + 1);
            Block top = w.getHighestBlockAt(x, z);
            last = top;
            Material t = top.getType();
            if (top.isLiquid() || Tag.LEAVES.isTagged(t) || t == Material.LAVA || t == Material.MAGMA_BLOCK
                    || t == Material.CACTUS || t == Material.FIRE || top.getY() <= w.getMinHeight()) {
                continue;
            }
            return top.getLocation().add(0.5, 1, 0.5);
        }
        if (last == null) last = w.getHighestBlockAt(0, 0);
        return last.getLocation().add(0.5, 1, 0.5);
    }
}
