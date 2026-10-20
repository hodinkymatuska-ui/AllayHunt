package sk.allayhunt;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class Commands implements CommandExecutor, TabCompleter {
    private final AllayHunt plugin;
    private final GameManager game;
    private final LobbyManager lobby;

    public Commands(AllayHunt plugin, GameManager game, LobbyManager lobby) {
        this.plugin = plugin;
        this.game = game;
        this.lobby = lobby;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "lobby" -> {
                if (!(sender instanceof Player p)) return true;
                if (game.isAlive(p)) p.sendMessage(Util.c("&cYou can't leave in the middle of a match!"));
                else game.toLobby(p);
            }
            case "spectate" -> {
                if (!(sender instanceof Player p)) return true;
                if (args.length == 0) {
                    p.openInventory(new SpectateMenu(game).getInventory());
                } else {
                    Player t = Bukkit.getPlayerExact(args[0]);
                    if (t == null) p.sendMessage(Util.c("&cPlayer not found."));
                    else game.spectate(p, t);
                }
            }
            case "allayhunt" -> admin(sender, args);
            default -> { }
        }
        return true;
    }

    private void admin(CommandSender s, String[] a) {
        if (!s.hasPermission("allayhunt.admin")) {
            s.sendMessage(Util.c("&cNo permission."));
            return;
        }
        String sub = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "setlobby" -> {
                if (!(s instanceof Player p)) {
                    s.sendMessage("Players only.");
                    return;
                }
                lobby.setSpawn(p.getLocation());
                s.sendMessage(Util.c("&aLobby spawn set to your position."));
            }
            case "importlobby" -> {
                if (a.length < 2) s.sendMessage(Util.c("&cUsage: /ah importlobby <folder in plugins/AllayHunt/import>"));
                else lobby.importWorld(s, a[1], game);
            }
            case "start" -> {
                game.forceStart();
                s.sendMessage(Util.c("&aForcing start (ignoring min players)."));
            }
            case "stop" -> {
                game.end(null);
                s.sendMessage(Util.c("&eRound stopped."));
            }
            case "reload" -> {
                plugin.reloadConfig();
                lobby.load();
                s.sendMessage(Util.c("&aConfig reloaded."));
            }
            default -> {
                s.sendMessage(Util.c("&d&lAllayHunt &7admin:"));
                s.sendMessage(Util.c("&f/ah setlobby &7- set lobby spawn to your position"));
                s.sendMessage(Util.c("&f/ah importlobby <folder> &7- import a (converted) Java world as the lobby"));
                s.sendMessage(Util.c("&f/ah start &7- force start | &f/ah stop &7- end the round | &f/ah reload"));
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (name.equals("allayhunt") && sender.hasPermission("allayhunt.admin")) {
            if (args.length == 1) {
                for (String s : List.of("setlobby", "importlobby", "start", "stop", "reload")) {
                    if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("importlobby")) {
                File[] files = new File(plugin.getDataFolder(), "import").listFiles(File::isDirectory);
                if (files != null) for (File f : files) out.add(f.getName());
            }
        } else if (name.equals("spectate") && args.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (game.isAlive(p) && p.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }
}
