package vn.daga;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Random;

/** Trứng: "wild|<bonus>" (trứng bí ẩn) hoặc "bred|<gà bố>|<gà mẹ>" (trứng ghép gen). */
public final class Eggs {
    public static NamespacedKey KEY;

    private Eggs() {}

    public static ItemStack wild(int bonus) {
        ItemStack it = new ItemStack(Material.EGG);
        ItemMeta m = it.getItemMeta();
        m.displayName(Msg.c(bonus >= 1 ? "&5&lTrứng Cổ Đại" : "&d&lTrứng Bí Ẩn"));
        m.lore(List.of(Msg.c("&7Không biết bên trong là gà gì..."), Msg.c("&7Cầm trên tay và gõ &e/daga ap &7để ấp.")));
        if (bonus >= 1) m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, "wild|" + bonus);
        it.setItemMeta(m);
        return it;
    }

    public static ItemStack bred(ChickenData a, ChickenData b) {
        ItemStack it = new ItemStack(Material.EGG);
        ItemMeta m = it.getItemMeta();
        m.displayName(Msg.c("&e&lTrứng Gà Lai"));
        m.lore(List.of(Msg.c("&7Bố mẹ: &f" + a.name + " &7x &f" + b.name),
                Msg.c("&7Đời: &f" + (Math.max(a.gen, b.gen) + 1)),
                Msg.c("&7Cầm trên tay và gõ &e/daga ap &7để ấp.")));
        m.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, "bred|" + a.serialize() + "|" + b.serialize());
        it.setItemMeta(m);
        return it;
    }

    public static String read(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
    }

    private static Rarity rollOnce(FileConfiguration cfg, Random rnd) {
        double[] w = {
                cfg.getDouble("rarity-weights.thuong", 55),
                cfg.getDouble("rarity-weights.kha", 28),
                cfg.getDouble("rarity-weights.hiem", 14),
                cfg.getDouble("rarity-weights.huyen-thoai", 3)
        };
        double total = 0;
        for (double d : w) total += d;
        double r = rnd.nextDouble() * total;
        for (int i = 0; i < w.length; i++) {
            r -= w[i];
            if (r < 0) return Rarity.values()[i];
        }
        return Rarity.THUONG;
    }

    /** bonus = số lần quay thêm, lấy kết quả cao nhất. */
    public static Rarity roll(FileConfiguration cfg, Random rnd, int bonus) {
        Rarity best = rollOnce(cfg, rnd);
        for (int t = 0; t < bonus; t++) {
            Rarity r = rollOnce(cfg, rnd);
            if (r.ordinal() > best.ordinal()) best = r;
        }
        return best;
    }

    public static ChickenData hatch(String raw, boolean fullMoon, FileConfiguration cfg, Random rnd) {
        String[] p = raw.split("\\|");
        if (p[0].equals("bred") && p.length >= 3) {
            ChickenData a = ChickenData.parse(p[1]);
            ChickenData b = ChickenData.parse(p[2]);
            if (a != null && b != null) {
                double mut = cfg.getDouble("mutation-chance", 0.06);
                if (fullMoon) mut *= cfg.getDouble("full-moon-mutation-multiplier", 3);
                return ChickenData.breed(a, b, mut, rnd);
            }
        }
        int bonus = 0;
        if (p.length >= 2) {
            try { bonus = Integer.parseInt(p[1]); } catch (NumberFormatException ignored) { }
        }
        return ChickenData.create(roll(cfg, rnd, bonus), rnd);
    }
}
