package sk.allayhunt;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/** Srdce hry: WAITING -> COUNTDOWN (10 s prestavka) -> RUNNING (7 min) -> znova. */
public final class GameManager {

    public enum State { WAITING, COUNTDOWN, RUNNING }

    private final AllayHunt plugin;
    private final ArenaManager arenas;
    private final LobbyManager lobby;
    private final Hud hud;

    private State state = State.WAITING;
    private final Set<UUID> alive = new LinkedHashSet<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final BossBar bar = Bukkit.createBossBar("", BarColor.PURPLE, BarStyle.SOLID);

    private World arena;
    private boolean arenaReady;
    private boolean force;
    private UUID allayId;
    private Location allayHome;
    private Vector radarFuzz = new Vector();
    private int round, elapsed, timeLeft, countdown;
    private BukkitTask task;

    public GameManager(AllayHunt plugin, ArenaManager arenas, LobbyManager lobby, Hud hud) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.lobby = lobby;
        this.hud = hud;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    // ================================================================= lifecycle

    public void start() {
        bar.setVisible(false);
        for (Player p : Bukkit.getOnlinePlayers()) bar.addPlayer(p);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        removeAllay();
        bar.removeAll();
    }

    private void tick() {
        try {
            switch (state) {
                case WAITING -> tickWaiting();
                case COUNTDOWN -> tickCountdown();
                case RUNNING -> tickRunning();
            }
            keepLobbyPlayersSafe();
            hud.update(this);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Game tick failed", ex);
        }
    }

    // ================================================================= waiting / countdown

    private void tickWaiting() {
        int online = Bukkit.getOnlinePlayers().size();
        if (online >= minPlayers() || (force && online > 0)) {
            beginCountdown();
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendActionBar(Util.c("&7Waiting for players... &f" + online + "&7/&f" + minPlayers()));
        }
    }

    private void beginCountdown() {
        if (!arenaReady) {
            arena = arenas.create();
            arenaReady = arena != null;
            if (!arenaReady) {
                plugin.getLogger().severe("Could not create/load the arena world!");
                return;
            }
        }
        state = State.COUNTDOWN;
        countdown = cfg().getInt("game.break-seconds", 10);
        bar.setColor(BarColor.GREEN);
        bar.setVisible(true);
    }

    private void tickCountdown() {
        int online = Bukkit.getOnlinePlayers().size();
        if (online < minPlayers() && !force) {
            state = State.WAITING;
            bar.setVisible(false);
            return;
        }
        if (countdown <= 0) {
            startRound();
            return;
        }
        int total = Math.max(1, cfg().getInt("game.break-seconds", 10));
        bar.setTitle(Util.col("&aNext round in &f" + countdown + "s"));
        bar.setProgress(Math.max(0.0, Math.min(1.0, countdown / (double) total)));

        if (countdown <= 5) {
            Title t = Title.title(Util.c("&a&l" + countdown), Util.c("&7Get ready..."),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ZERO));
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showTitle(t);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, countdown <= 1 ? 2f : 1f);
            }
        } else {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendActionBar(Util.c("&aRound &f#" + (round + 1) + " &astarts in &f" + countdown + "s"));
            }
        }
        countdown--;
    }

    // ================================================================= round start

    private void startRound() {
        round++;
        force = false;
        elapsed = 0;
        timeLeft = cfg().getInt("game.round-seconds", 420);
        alive.clear();
        kills.clear();

        int spread = cfg().getInt("arena.spawn-spread", 420);
        for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            resetPlayer(p, GameMode.SURVIVAL);
            Kit.giveLoadout(p);
            p.teleport(arenas.randomSafe(arena, spread));
            alive.add(p.getUniqueId());
        }

        int f = Math.max(0, cfg().getInt("hints.radar-fuzz", 80));
        ThreadLocalRandom r = ThreadLocalRandom.current();
        radarFuzz = new Vector(r.nextInt(-f, f + 1), 0, r.nextInt(-f, f + 1));

        Location l = arenas.randomSafe(arena, cfg().getInt("arena.allay-radius", 450)).add(0, 2, 0);
        allayHome = l;
        l.getChunk().addPluginChunkTicket(plugin);
        spawnAllayAt(l);

        state = State.RUNNING;
        bar.setColor(BarColor.PURPLE);
        bar.setVisible(true);

        int grace = cfg().getInt("game.pvp-grace-seconds", 15);
        Title t = Title.title(Util.c("&d&lRound " + round), Util.c("&7Find the &bAllay &7and give it the gift!"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofSeconds(1)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(t);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.4f);
        }
        Bukkit.broadcast(Util.c("&8&m                              "));
        Bukkit.broadcast(Util.c("&d&lALLAY HUNT &7- round &f#" + round));
        Bukkit.broadcast(Util.c("&7A &bAllay &7is hiding somewhere on the map. Find it and right-click it"));
        Bukkit.broadcast(Util.c("&7while holding &dGift for Allay&7. &c" + grace + "s &7PvP protection."));
        Bukkit.broadcast(Util.c("&8&m                              "));
    }

    private void spawnAllayAt(Location l) {
        Allay a = l.getWorld().spawn(l, Allay.class, e -> {
            e.customName(Util.c("&bAllay"));
            e.setCustomNameVisible(true);
            e.setInvulnerable(true);
            e.setPersistent(true);
            e.setRemoveWhenFarAway(false);
            e.setCanDuplicate(false);
            e.addScoreboardTag("allayhunt_target");
        });
        allayId = a.getUniqueId();
        if (elapsed >= cfg().getInt("hints.glow-at", 240)) a.setGlowing(true);
    }

    // ================================================================= running

    private void tickRunning() {
        elapsed++;
        timeLeft--;

        alive.removeIf(id -> Bukkit.getPlayer(id) == null);
        for (UUID id : alive) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) Kit.ensureGift(p);
        }
        keepAllayHome();

        int compassAt = cfg().getInt("hints.compass-at", 120);
        int glowAt = cfg().getInt("hints.glow-at", 240);
        int exactAt = cfg().getInt("hints.exact-at", 330);

        if (elapsed == compassAt) {
            for (UUID id : alive) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) Util.give(p, Kit.radar());
            }
            Bukkit.broadcast(Util.c("&b&lHINT &7You got an &bAllay Radar&7 - it shows the &eapproximate&7 area."));
            sound(Sound.BLOCK_BEACON_ACTIVATE, 1.5f);
        }
        if (elapsed >= compassAt) updateRadar(elapsed >= exactAt);
        if (elapsed == glowAt) {
            Allay a = allay();
            if (a != null) a.setGlowing(true);
            Bukkit.broadcast(Util.c("&b&lHINT &7The Allay started &fglowing&7!"));
            sound(Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 1f);
        }
        if (elapsed == exactAt) {
            Bukkit.broadcast(Util.c("&b&lHINT &7The radar now shows the &aexact&7 position!"));
        }

        if (timeLeft == 60 || timeLeft == 30 || timeLeft == 10) {
            Bukkit.broadcast(Util.c("&e&l" + timeLeft + " &eseconds left!"));
            sound(Sound.BLOCK_NOTE_BLOCK_BELL, 1f);
        } else if (timeLeft > 0 && timeLeft <= 5) {
            sound(Sound.BLOCK_NOTE_BLOCK_HAT, 1f);
        }

        int total = Math.max(1, cfg().getInt("game.round-seconds", 420));
        bar.setTitle(Util.col("&d&lAllay Hunt &8| &f" + Util.time(timeLeft) + " &8| &a" + alive.size() + " alive"));
        bar.setProgress(Math.max(0.0, Math.min(1.0, timeLeft / (double) total)));
        bar.setColor(timeLeft <= 60 ? BarColor.RED : BarColor.PURPLE);

        if (alive.isEmpty() || timeLeft <= 0) end(null);
    }

    private void keepAllayHome() {
        if (allayHome == null) return;
        Allay a = allay();
        if (a == null) {
            spawnAllayAt(allayHome);
            return;
        }
        if (a.getLocation().distanceSquared(allayHome) > 225) a.teleport(allayHome);
    }

    private void updateRadar(boolean exact) {
        if (allayHome == null) return;
        Location target = allayHome.clone();
        if (!exact) target.add(radarFuzz);
        int bucket = exact ? 5 : 50;
        for (UUID id : alive) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            p.setCompassTarget(target);
            if (p.getWorld().equals(allayHome.getWorld())) {
                double d = p.getLocation().distance(allayHome);
                long shown = Math.round(d / bucket) * bucket;
                p.sendActionBar(Util.c("&bAllay Radar &8» &f" + (exact ? "" : "~") + shown + " blocks"));
            }
        }
    }

    // ================================================================= round end

    public void win(Player p) {
        if (state == State.RUNNING && alive.contains(p.getUniqueId())) end(p);
    }

    /** winner == null -> nikto nevyhral */
    public void end(Player winner) {
        if (state != State.RUNNING) return;
        state = State.WAITING;
        bar.setVisible(false);

        Component main;
        Component sub;
        if (winner != null) {
            main = Util.c("&6&l" + winner.getName() + " won!");
            sub = Util.c("&7gave the gift to the Allay &d\u2665");
            winner.giveExpLevels(cfg().getInt("game.reward-levels", 30));
            winner.sendMessage(Util.c("&a+" + cfg().getInt("game.reward-levels", 30) + " XP levels!"));
        } else {
            main = Util.c("&c&lNobody won");
            sub = Util.c("&7The Allay was never found...");
        }
        Title title = Title.title(main, sub,
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(5), Duration.ofSeconds(1)));

        Allay a = allay();
        if (a != null && winner != null) {
            Location al = a.getLocation().add(0, 0.8, 0);
            a.getWorld().spawnParticle(Particle.HEART, al, 30, 0.6, 0.6, 0.6, 0);
            a.getWorld().spawnParticle(Particle.END_ROD, al, 40, 0.5, 0.5, 0.5, 0.05);
            a.getWorld().playSound(al, Sound.ENTITY_ALLAY_ITEM_GIVEN, 1.5f, 1f);
        }
        removeAllay();

        Bukkit.broadcast(Util.c("&8&m                              "));
        if (winner != null) {
            Bukkit.broadcast(Util.c("&6&l" + winner.getName() + " &ewon round &f#" + round + "&e!"));
        } else {
            Bukkit.broadcast(Util.c("&c&lNobody won &7round #" + round));
        }
        kills.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(e -> {
            String n = Bukkit.getOfflinePlayer(e.getKey()).getName();
            Bukkit.broadcast(Util.c("&7Most kills: &c" + (n == null ? "?" : n) + " &7(" + e.getValue() + ")"));
        });
        Bukkit.broadcast(Util.c("&8&m                              "));

        for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            toLobby(p);
            p.showTitle(title);
            p.playSound(p.getLocation(), winner != null ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }
        alive.clear();

        arenas.dispose(arena);
        arena = null;
        arenaReady = false;

        if (winner != null) fireworks(lobby.spawn());
        // tick() za chvilu spusti dalsiu prestavku (a vygeneruje novu mapu)
    }

    private void fireworks(Location c) {
        for (int i = 0; i < 8; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                Location l = c.clone().add(r.nextDouble(-6, 6), 0, r.nextDouble(-6, 6));
                l.getWorld().spawn(l, Firework.class, f -> {
                    FireworkMeta m = f.getFireworkMeta();
                    m.addEffect(FireworkEffect.builder()
                            .withColor(Color.AQUA, Color.FUCHSIA)
                            .withFade(Color.WHITE)
                            .with(FireworkEffect.Type.BALL_LARGE)
                            .withFlicker().withTrail().build());
                    m.setPower(1);
                    f.setFireworkMeta(m);
                });
            }, i * 6L);
        }
    }

    // ================================================================= players

    public void onJoin(Player p) {
        alive.remove(p.getUniqueId());
        bar.addPlayer(p);
        toLobby(p);
        p.sendMessage(Util.c("&d&lALLAY HUNT &7- find the Allay and give it the gift!"));
        if (state == State.RUNNING) {
            p.sendMessage(Util.c("&7A round is in progress - &bspectate&7 with the compass, you join the next one."));
        }
    }

    public void onQuit(Player p) {
        alive.remove(p.getUniqueId());
        kills.remove(p.getUniqueId());
        bar.removePlayer(p);
    }

    public void handleDeath(Player p, Player killer) {
        alive.remove(p.getUniqueId());
        if (killer != null && !killer.equals(p) && alive.contains(killer.getUniqueId())) {
            kills.merge(killer.getUniqueId(), 1, Integer::sum);
            killer.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
            killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        }
        retargetSpectators(p);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && p.isDead()) p.spigot().respawn();
        });
    }

    public void afterRespawn(Player p) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            toLobby(p);
            p.showTitle(Title.title(Util.c("&c&lYou died"), Util.c("&7Use the compass to spectate"),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))));
        });
    }

    private void retargetSpectators(Player dead) {
        for (Player s : Bukkit.getOnlinePlayers()) {
            if (s.getGameMode() == GameMode.SPECTATOR && dead.equals(s.getSpectatorTarget())) {
                Player next = null;
                for (UUID id : alive) {
                    Player c = Bukkit.getPlayer(id);
                    if (c != null) {
                        next = c;
                        break;
                    }
                }
                if (next != null) spectate(s, next);
                else toLobby(s);
            }
        }
    }

    public void spectate(Player viewer, Player target) {
        if (alive.contains(viewer.getUniqueId())) {
            viewer.sendMessage(Util.c("&cYou're still in the match!"));
            return;
        }
        if (!alive.contains(target.getUniqueId())) {
            viewer.sendMessage(Util.c("&cThat player isn't in the match."));
            return;
        }
        viewer.setGameMode(GameMode.SPECTATOR);
        viewer.teleportAsync(target.getLocation()).thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (viewer.isOnline() && target.isOnline() && viewer.getGameMode() == GameMode.SPECTATOR) {
                viewer.setSpectatorTarget(target);
                viewer.sendActionBar(Util.c("&bSpectating &f" + target.getName() + " &7(sneak to detach, /lobby to leave)"));
            }
        }));
    }

    public void toLobby(Player p) {
        alive.remove(p.getUniqueId());
        resetPlayer(p, GameMode.ADVENTURE);
        p.setAllowFlight(true);
        p.teleport(lobby.spawn());
        p.getInventory().setItem(4, Kit.spectateCompass());
        p.getInventory().setHeldItemSlot(4);
    }

    public void sendAllToLobby() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!alive.contains(p.getUniqueId())) toLobby(p);
        }
    }

    private void resetPlayer(Player p, GameMode gm) {
        p.closeInventory();
        p.setGameMode(gm);
        p.setItemOnCursor(null);
        p.getInventory().clear();
        p.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[4]);
        p.getInventory().setItemInOffHand(null);
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.setFlying(false);
        p.setAllowFlight(false);
    }

    /** Ak lobby hrac spadne do voidu (Bedrock lobby moze mat void), vrati sa. */
    private void keepLobbyPlayersSafe() {
        World lw = lobby.world();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (alive.contains(p.getUniqueId())) continue;
            if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) continue;
            if (p.getWorld().equals(lw) && p.getLocation().getY() < lw.getMinHeight() - 5) {
                p.setFallDistance(0f);
                p.teleport(lobby.spawn());
            }
        }
    }

    // ================================================================= helpers

    private void sound(Sound s, float pitch) {
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), s, 1f, pitch);
    }

    private void removeAllay() {
        Allay a = allay();
        if (a != null) a.remove();
        if (allayHome != null) {
            try {
                allayHome.getChunk().removePluginChunkTicket(plugin);
            } catch (Exception ignored) {
                // svet uz mohol byt unloadnuty
            }
        }
        allayId = null;
        allayHome = null;
    }

    private Allay allay() {
        if (allayId == null) return null;
        Entity e = Bukkit.getEntity(allayId);
        return e instanceof Allay a ? a : null;
    }

    public boolean isGameAllay(Entity e) {
        return allayId != null && e.getUniqueId().equals(allayId);
    }

    public void forceStart() {
        force = true;
    }

    // ---- getters (listener, hud, menu)
    public State state() { return state; }
    public boolean isAlive(Player p) { return alive.contains(p.getUniqueId()); }
    public List<UUID> aliveIds() { return new ArrayList<>(alive); }
    public int aliveCount() { return alive.size(); }
    public int kills(UUID id) { return kills.getOrDefault(id, 0); }
    public int round() { return round; }
    public int timeLeft() { return timeLeft; }
    public int countdown() { return countdown; }
    public int minPlayers() { return Math.max(1, cfg().getInt("game.min-players", 2)); }
    public int graceLeft() { return state == State.RUNNING ? cfg().getInt("game.pvp-grace-seconds", 15) - elapsed : 0; }
}
