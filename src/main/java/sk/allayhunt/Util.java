package sk.allayhunt;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class Util {
    private Util() {}

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    /** "&dText" -> Component */
    public static Component c(String s) {
        return LEGACY.deserialize(s);
    }

    /** Component bez kurzivy (pre nazvy a lore itemov) */
    public static Component nc(String s) {
        return c(s).decoration(TextDecoration.ITALIC, false);
    }

    /** "&d" -> "§d" (pre scoreboard / bossbar) */
    public static String col(String s) {
        return s.replace('&', '\u00a7');
    }

    public static String time(int sec) {
        sec = Math.max(0, sec);
        return String.format("%d:%02d", sec / 60, sec % 60);
    }

    public static void give(Player p, ItemStack it) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(it);
        left.values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
    }

    public static void deleteDir(File dir) throws IOException {
        if (!dir.exists()) return;
        List<Path> paths;
        try (Stream<Path> s = Files.walk(dir.toPath())) {
            paths = s.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path p : paths) Files.deleteIfExists(p);
    }

    public static void copyDir(File src, File dst) throws IOException {
        Path s = src.toPath();
        Path d = dst.toPath();
        List<Path> paths;
        try (Stream<Path> st = Files.walk(s)) {
            paths = st.toList();
        }
        for (Path p : paths) {
            Path target = d.resolve(s.relativize(p).toString());
            if (Files.isDirectory(p)) {
                Files.createDirectories(target);
            } else {
                Files.createDirectories(target.getParent());
                Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
