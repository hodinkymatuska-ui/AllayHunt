package sk.allayhunt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/** GUI so zoznamom zivych hracov (hlavy) - klik = spectate. */
public final class SpectateMenu implements InventoryHolder {
    private final Inventory inv;
    private final Map<Integer, UUID> slots = new HashMap<>();

    public SpectateMenu(GameManager game) {
        inv = Bukkit.createInventory(this, 54, Util.c("&8Spectate a player"));
        int i = 0;
        for (UUID id : game.aliveIds()) {
            Player t = Bukkit.getPlayer(id);
            if (t == null || i >= 54) continue;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta m = (SkullMeta) head.getItemMeta();
            m.setOwningPlayer(t);
            m.displayName(Util.nc("&f" + t.getName()));
            m.lore(List.of(Util.nc("&7Kills: &c" + game.kills(id)), Util.nc("&eClick to spectate")));
            head.setItemMeta(m);
            slots.put(i, id);
            inv.setItem(i++, head);
        }
        if (slots.isEmpty()) {
            ItemStack none = new ItemStack(Material.BARRIER);
            ItemMeta m = none.getItemMeta();
            m.displayName(Util.nc("&cNobody to spectate right now"));
            none.setItemMeta(m);
            inv.setItem(22, none);
        }
    }

    public UUID at(int slot) {
        return slots.get(slot);
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
