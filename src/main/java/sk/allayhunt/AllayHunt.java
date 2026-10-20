package sk.allayhunt;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class AllayHunt extends JavaPlugin {

    private LobbyManager lobby;
    private ArenaManager arenas;
    private GameManager game;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        new File(getDataFolder(), "import").mkdirs();
        Kit.init(this);

        lobby = new LobbyManager(this);
        lobby.load();
        arenas = new ArenaManager(this);
        arenas.cleanupLeftovers();
        game = new GameManager(this, arenas, lobby, new Hud());

        getServer().getPluginManager().registerEvents(new GameListener(game, lobby), this);

        Commands cmd = new Commands(this, game, lobby);
        for (String name : new String[] {"allayhunt", "lobby", "spectate"}) {
            PluginCommand pc = getCommand(name);
            if (pc != null) {
                pc.setExecutor(cmd);
                pc.setTabCompleter(cmd);
            }
        }

        for (Player p : Bukkit.getOnlinePlayers()) game.onJoin(p);
        game.start();
        getLogger().info("AllayHunt enabled. Lobby world: " + lobby.world().getName());
    }

    @Override
    public void onDisable() {
        if (game != null) game.shutdown();
    }
}
