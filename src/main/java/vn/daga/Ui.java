package vn.daga;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class Ui {
    private Ui() {}

    public static ItemStack icon(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Msg.c(name));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(Msg.c(s));
            meta.lore(l);
        }
        // ẩn dòng thuộc tính (kiếm, khiên...) và hiệu ứng cho menu gọn gàng
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(meta);
        return it;
    }

    public static ItemStack glow(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        m.setEnchantmentGlintOverride(true);
        it.setItemMeta(m);
        return it;
    }

    public static ItemStack withExtraLore(ItemStack base, String... extra) {
        ItemStack it = base.clone();
        ItemMeta m = it.getItemMeta();
        List<Component> l = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
        for (String s : extra) l.add(Msg.c(s));
        m.lore(l);
        it.setItemMeta(m);
        return it;
    }

    public static ItemStack pane() {
        return icon(Material.BLACK_STAINED_GLASS_PANE, " ");
    }

    /** Thanh tiến độ 10 ô, ví dụ ███░░░░░░░ */
    public static String bar(int value, int max) {
        int filled = max <= 0 ? 0 : (int) Math.round(10.0 * Math.max(0, Math.min(value, max)) / max);
        StringBuilder sb = new StringBuilder("&8[&a");
        for (int i = 0; i < 10; i++) {
            if (i == filled) sb.append("&7");
            sb.append("█");
        }
        return sb.append("&8]").toString();
    }
}
