package sk.allayhunt;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class Kit {
    private Kit() {}

    private static NamespacedKey GIFT;
    private static NamespacedKey SPECTATE;

    public static void init(JavaPlugin plugin) {
        GIFT = new NamespacedKey(plugin, "gift_book");
        SPECTATE = new NamespacedKey(plugin, "spectate_compass");
    }

    /** enchanted(Material.X, Enchantment.A, 3, Enchantment.B, 1, ...) */
    private static ItemStack enchanted(Material mat, Object... spec) {
        ItemStack it = new ItemStack(mat);
        for (int i = 0; i + 1 < spec.length; i += 2) {
            it.addUnsafeEnchantment((Enchantment) spec[i], (Integer) spec[i + 1]);
        }
        return it;
    }

    public static void giveLoadout(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setArmorContents(new ItemStack[4]);
        inv.setItemInOffHand(null);

        // --- netherite armor ---
        inv.setHelmet(enchanted(Material.NETHERITE_HELMET,
                Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1,
                Enchantment.RESPIRATION, 3, Enchantment.AQUA_AFFINITY, 1));
        inv.setChestplate(enchanted(Material.NETHERITE_CHESTPLATE,
                Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
        inv.setLeggings(enchanted(Material.NETHERITE_LEGGINGS,
                Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1,
                Enchantment.SWIFT_SNEAK, 3));
        inv.setBoots(enchanted(Material.NETHERITE_BOOTS,
                Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1,
                Enchantment.FEATHER_FALLING, 4, Enchantment.DEPTH_STRIDER, 3));

        // --- netherite tools ---
        inv.setItem(0, enchanted(Material.NETHERITE_SWORD,
                Enchantment.SHARPNESS, 5, Enchantment.FIRE_ASPECT, 2, Enchantment.LOOTING, 3,
                Enchantment.SWEEPING_EDGE, 3, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
        inv.setItem(1, enchanted(Material.NETHERITE_PICKAXE,
                Enchantment.EFFICIENCY, 5, Enchantment.FORTUNE, 3, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
        inv.setItem(2, enchanted(Material.NETHERITE_AXE,
                Enchantment.SHARPNESS, 5, Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
        inv.setItem(3, enchanted(Material.NETHERITE_SHOVEL,
                Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
        inv.setItem(4, enchanted(Material.NETHERITE_HOE,
                Enchantment.EFFICIENCY, 5, Enchantment.FORTUNE, 3, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));

        // --- food, water, book ---
        inv.setItem(5, new ItemStack(Material.GOLDEN_APPLE, 64));
        inv.setItem(6, new ItemStack(Material.COOKED_BEEF, 64));
        inv.setItem(7, new ItemStack(Material.WATER_BUCKET));
        inv.setItem(8, giftBook());
        inv.addItem(new ItemStack(Material.COOKED_BEEF, 64)); // druhy stack steakov

        // --- shield ---
        inv.setItemInOffHand(enchanted(Material.SHIELD, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
    }

    // ------------------------------------------------------------------ gift book

    public static ItemStack giftBook() {
        ItemStack b = new ItemStack(Material.BOOK);
        ItemMeta m = b.getItemMeta();
        m.displayName(Util.nc("&dGift for Allay"));
        m.lore(List.of(Util.nc("&7Right-click the &bAllay&7 with this!"),
                Util.nc("&8Can't be lost - a new one appears.")));
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(GIFT, PersistentDataType.BYTE, (byte) 1);
        b.setItemMeta(m);
        return b;
    }

    public static boolean isGift(ItemStack it) {
        return it != null && it.getType() == Material.BOOK && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(GIFT, PersistentDataType.BYTE);
    }

    /** Ak hrac nema knihu (vyhodil ju, strcil...), objavi sa nova. */
    public static void ensureGift(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (isGift(it)) return;
        }
        if (isGift(p.getItemOnCursor())) return;
        Util.give(p, giftBook());
    }

    // ------------------------------------------------------------------ lobby / radar items

    public static ItemStack spectateCompass() {
        ItemStack it = new ItemStack(Material.COMPASS);
        ItemMeta m = it.getItemMeta();
        m.displayName(Util.nc("&b&lSpectate players"));
        m.lore(List.of(Util.nc("&7Right-click to pick a player to watch.")));
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(SPECTATE, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    public static boolean isSpectateItem(ItemStack it) {
        return it != null && it.getType() == Material.COMPASS && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(SPECTATE, PersistentDataType.BYTE);
    }

    public static ItemStack radar() {
        ItemStack it = new ItemStack(Material.COMPASS);
        ItemMeta m = it.getItemMeta();
        m.displayName(Util.nc("&b&lAllay Radar"));
        m.lore(List.of(Util.nc("&7Points toward the Allay.")));
        it.setItemMeta(m);
        return it;
    }
}
