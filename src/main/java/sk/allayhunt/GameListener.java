package sk.allayhunt;

import org.bukkit.GameMode;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class GameListener implements Listener {
    private final GameManager game;
    private final LobbyManager lobby;

    public GameListener(GameManager game, LobbyManager lobby) {
        this.game = game;
        this.lobby = lobby;
    }

    private boolean protectedLobbyPlayer(Player p) {
        return !game.isAlive(p) && p.getGameMode() != GameMode.CREATIVE;
    }

    // ------------------------------------------------------------ join / quit

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        game.onJoin(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        game.onQuit(e.getPlayer());
    }

    // ------------------------------------------------------------ the Allay

    @EventHandler(priority = EventPriority.HIGH)
    public void onAllayInteract(PlayerInteractEntityEvent e) {
        if (!game.isGameAllay(e.getRightClicked())) return;
        e.setCancelled(true); // nech Allay nezoberie ziadne ine veci
        Player p = e.getPlayer();
        if (game.state() != GameManager.State.RUNNING || !game.isAlive(p)) return;

        ItemStack inHand = p.getInventory().getItem(e.getHand());
        if (Kit.isGift(inHand)) {
            game.win(p);
        } else if (e.getHand() == EquipmentSlot.HAND) {
            p.sendActionBar(Util.c("&dHold the &fGift for Allay &din your hand and right-click me!"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Allay) {
            e.setCancelled(true);
        } else if (e.getEntity() instanceof Player p && protectedLobbyPlayer(p)) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ the book can't be lost

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (Kit.isGift(e.getItemDrop().getItemStack()) || protectedLobbyPlayer(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent e) {
        if (Kit.isGift(e.getEntity().getItemStack())) e.setCancelled(true); // novu knihu dostane hrac v dalsom ticku
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        if (e.getInventory().getHolder() instanceof SpectateMenu menu) {
            e.setCancelled(true);
            if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) return;
            UUID id = menu.at(e.getSlot());
            if (id == null) return;
            Player target = p.getServer().getPlayer(id);
            p.closeInventory();
            if (target != null) game.spectate(p, target);
            return;
        }

        if (protectedLobbyPlayer(p)) {
            e.setCancelled(true);
            return;
        }

        // knihu nedaj do truhly / hoppera atd.
        Inventory top = e.getView().getTopInventory();
        if (top.getType() != InventoryType.CRAFTING) {
            boolean numberKey = e.getClick() == ClickType.NUMBER_KEY;
            boolean involvesGift = Kit.isGift(e.getCurrentItem()) || Kit.isGift(e.getCursor())
                    || (numberKey && Kit.isGift(p.getInventory().getItem(e.getHotbarButton())));
            if (involvesGift && (e.getClickedInventory() == top || e.isShiftClick() || numberKey)) {
                e.setCancelled(true);
            }
        }
    }

    // ------------------------------------------------------------ spectate compass

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!Kit.isSpectateItem(e.getItem())) return;
        e.setCancelled(true);
        e.getPlayer().openInventory(new SpectateMenu(game).getInventory());
    }

    // ------------------------------------------------------------ combat

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;

        if (game.state() != GameManager.State.RUNNING || !game.isAlive(victim)) {
            e.setCancelled(true); // lobby, spectatori, prestavka
            return;
        }
        if (e instanceof EntityDamageByEntityEvent byEntity) {
            Player attacker = attackerOf(byEntity.getDamager());
            if (attacker != null && !attacker.equals(victim)) {
                int left = game.graceLeft();
                if (left > 0) {
                    e.setCancelled(true);
                    attacker.sendActionBar(Util.c("&cPvP is disabled for another &f" + left + "s"));
                }
            }
        }
    }

    private Player attackerOf(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!game.isAlive(p) || game.state() != GameManager.State.RUNNING) return;

        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);

        Player killer = p.getKiller();
        if (killer != null && !killer.equals(p)) {
            e.deathMessage(Util.c("&c\u2620 &f" + p.getName() + " &7was slain by &f" + killer.getName()));
        } else {
            e.deathMessage(Util.c("&c\u2620 &f" + p.getName() + " &7died"));
        }
        game.handleDeath(p, killer);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        e.setRespawnLocation(lobby.spawn());
        game.afterRespawn(e.getPlayer());
    }

    // ------------------------------------------------------------ lobby protection

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && !game.isAlive(p)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (protectedLobbyPlayer(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (protectedLobbyPlayer(e.getPlayer())) e.setCancelled(true);
    }
}
