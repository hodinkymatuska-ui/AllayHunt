package sk.allayhunt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/** Sidebar scoreboard pre kazdeho hraca. */
public final class Hud {
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public void update(GameManager g) {
        int online = Bukkit.getOnlinePlayers().size();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Scoreboard sb = boards.computeIfAbsent(p.getUniqueId(), k -> create());
            List<String> l = new ArrayList<>();
            l.add("");
            switch (g.state()) {
                case WAITING -> {
                    l.add("&7Waiting for players");
                    l.add("&f" + online + "&7/&f" + g.minPlayers());
                }
                case COUNTDOWN -> {
                    l.add("&7Next round in &a" + g.countdown() + "s");
                    l.add("&7Players: &f" + online);
                }
                case RUNNING -> {
                    l.add("&7Round: &f#" + g.round());
                    l.add("&7Time: &f" + Util.time(g.timeLeft()));
                    l.add("&7Alive: &a" + g.aliveCount());
                    l.add("");
                    if (g.isAlive(p)) {
                        l.add("&7Kills: &c" + g.kills(p.getUniqueId()));
                        l.add("&7Status: &aPlaying");
                    } else {
                        l.add("&7Status: &cSpectating");
                    }
                }
            }
            l.add("");
            l.add("&d&oFind the Allay!");
            render(sb, l);
            if (p.getScoreboard() != sb) p.setScoreboard(sb);
        }
        boards.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    private Scoreboard create() {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective o = sb.registerNewObjective("ah", Criteria.DUMMY, Util.c("&d&lALLAY HUNT"));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        o.numberFormat(NumberFormat.blank());
        return sb;
    }

    private void render(Scoreboard sb, List<String> lines) {
        Objective o = sb.getObjective("ah");
        if (o == null) return;
        for (String e : new ArrayList<>(sb.getEntries())) sb.resetScores(e);
        int score = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            // neviditelna farba na konci robi kazdy riadok unikatny
            String entry = Util.col(lines.get(i)) + "\u00a7" + Integer.toHexString(i);
            o.getScore(entry).setScore(score--);
        }
    }
}
