package vn.daga;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Dữ liệu 1 con gà chiến, lưu thẳng trong item (PersistentDataContainer). */
public class ChickenData {
    public static NamespacedKey KEY;

    public static final int HP = 0, ATK = 1, DEF = 2, SPD = 3, LUCK = 4;
    public static final String[] STAT_NAMES = {"HP", "Công", "Thủ", "Tốc độ", "May mắn"};
    public static final int[] BASE = {80, 12, 6, 10, 5};

    public static final String[] COLORS = {"Nâu", "Đen", "Trắng", "Đỏ", "Xám", "Vàng Kim", "Xanh Lam", "Tím Huyền"};
    public static final String[] SPURS = {"Cựa Cùn", "Cựa Nhọn", "Cựa Sắt", "Cựa Thép"};
    public static final String[] COMBS = {"Mào Nhỏ", "Mào Hoa", "Mào Cờ", "Mào Vương"};
    private static final String[] NAMES = {"Chiến Thần", "Xích Hổ", "Hắc Phong", "Bạch Vân", "Kim Cương", "Lửa Rừng",
            "Tía Sét", "Mã Vương", "Thiết Cước", "Ưng Vàng", "Quỷ Đỏ", "Bão Táp", "Long Vũ", "Tử Kê"};

    public String id = UUID.randomUUID().toString().substring(0, 8);
    public String name = "Gà";
    public Rarity rarity = Rarity.THUONG;
    public int[] st = new int[5];
    public int maxSta = 100;
    public int sta = 100;
    public long staStamp = System.currentTimeMillis();
    public int hunger = 80;
    public long hungerStamp = System.currentTimeMillis();
    public int color, spur, comb;
    public int wins, losses, gen = 1;
    public long injuredUntil = 0;
    public long breedReady = 0;
    public long deadUntil = 0;
    public long weakUntil = 0;
    public int mood = 60;
    public long moodStamp = System.currentTimeMillis();
    public static final double WEAK = 0.8;

    public static String randomName(Random rnd) {
        return "Gà " + NAMES[rnd.nextInt(NAMES.length)];
    }

    public static ChickenData create(Rarity r, Random rnd) {
        ChickenData c = new ChickenData();
        c.rarity = r;
        c.name = randomName(rnd);
        for (int i = 0; i < 5; i++) {
            c.st[i] = Math.max(1, (int) Math.round(BASE[i] * r.mult * (0.85 + rnd.nextDouble() * 0.3)));
        }
        c.maxSta = 100 + 10 * r.ordinal();
        c.sta = c.maxSta;
        c.color = rnd.nextDouble() < 0.03 ? 5 + rnd.nextInt(3) : rnd.nextInt(5);
        c.spur = rnd.nextInt(2);
        c.comb = rnd.nextInt(2);
        return c;
    }

    public static ChickenData breed(ChickenData a, ChickenData b, double mutation, Random rnd) {
        ChickenData c = new ChickenData();
        Rarity r = rnd.nextBoolean() ? a.rarity : b.rarity;
        boolean mutated = rnd.nextDouble() < mutation;
        if (mutated) r = r.next();
        c.rarity = r;
        c.name = randomName(rnd);
        for (int i = 0; i < 5; i++) {
            double avg = (a.st[i] + b.st[i]) / 2.0 * (0.92 + rnd.nextDouble() * 0.2);
            int lo = (int) Math.round(BASE[i] * r.mult * 0.8);
            c.st[i] = Math.min(c.cap(i), Math.max(lo, (int) Math.round(avg)));
        }
        c.maxSta = 100 + 10 * r.ordinal();
        c.sta = c.maxSta;
        c.color = rnd.nextBoolean() ? a.color : b.color;
        c.spur = rnd.nextBoolean() ? a.spur : b.spur;
        c.comb = rnd.nextBoolean() ? a.comb : b.comb;
        if (mutated) {
            if (rnd.nextBoolean()) c.color = 5 + rnd.nextInt(3);
            if (rnd.nextBoolean()) c.spur = Math.min(3, c.spur + 1);
            if (rnd.nextBoolean()) c.comb = Math.min(3, c.comb + 1);
        }
        c.gen = Math.max(a.gen, b.gen) + 1;
        return c;
    }

    /** Giới hạn tối đa của 1 chỉ số theo độ hiếm. */
    public int cap(int stat) {
        return (int) Math.round(BASE[stat] * rarity.mult * 1.8);
    }

    public boolean train(int stat) {
        if (st[stat] >= cap(stat)) return false;
        st[stat] = Math.min(cap(stat), st[stat] + (stat == HP ? 2 : 1));
        return true;
    }

    public boolean dead() {
        return deadUntil > System.currentTimeMillis();
    }

    public boolean weak() {
        return weakUntil > System.currentTimeMillis();
    }

    private double moodMult() {
        return mood >= 70 ? 1.05 : (mood < 20 ? 0.9 : 1.0);
    }

    private double wk() {
        return weak() ? WEAK : 1.0;
    }

    public double fightHp() {
        return st[HP] * wk();
    }

    public double spd() {
        return st[SPD] * wk();
    }

    public double luck() {
        return st[LUCK] * wk();
    }

    public double effAtk() {
        double v = (st[ATK] + spur * 2) * wk() * moodMult();
        return hunger < 30 ? v * 0.85 : v;
    }

    public double effDef() {
        return (st[DEF] + comb * 2) * wk();
    }

    public boolean injured() {
        return injuredUntil > System.currentTimeMillis();
    }

    /** Cập nhật đói/thể lực theo thời gian thực. */
    public void refresh() {
        long now = System.currentTimeMillis();
        long pts = (now - hungerStamp) / (6 * 60000L);
        if (pts > 0) {
            hunger = (int) Math.max(0, hunger - pts);
            hungerStamp += pts * 6 * 60000L;
        }
        long mm = (now - moodStamp) / (8 * 60000L);
        if (mm > 0) {
            mood = (int) Math.max(0, mood - mm);
            moodStamp += mm * 8 * 60000L;
        }
        long mins = (now - staStamp) / 60000L;
        if (mins > 0) {
            if (hunger >= 20) sta = (int) Math.min(maxSta, sta + mins * 2);
            staStamp += mins * 60000L;
        }
    }

    public String serialize() {
        String n = name.replace(";", "").replace("=", "").replace("|", "");
        return String.join(";",
                "id=" + id, "n=" + n, "r=" + rarity.name(),
                "st=" + st[0] + "," + st[1] + "," + st[2] + "," + st[3] + "," + st[4],
                "ms=" + maxSta, "s=" + sta, "ss=" + staStamp, "h=" + hunger, "hs=" + hungerStamp,
                "c=" + color, "sp=" + spur, "cb=" + comb, "w=" + wins, "l=" + losses, "g=" + gen,
                "inj=" + injuredUntil, "br=" + breedReady, "du=" + deadUntil, "wu=" + weakUntil, "mo=" + mood, "mos=" + moodStamp);
    }

    public static ChickenData parse(String raw) {
        try {
            ChickenData c = new ChickenData();
            for (String part : raw.split(";")) {
                String[] kv = part.split("=", 2);
                if (kv.length < 2) continue;
                String v = kv[1];
                switch (kv[0]) {
                    case "id" -> c.id = v;
                    case "n" -> c.name = v;
                    case "r" -> c.rarity = Rarity.valueOf(v);
                    case "st" -> {
                        String[] p = v.split(",");
                        for (int i = 0; i < 5; i++) c.st[i] = Integer.parseInt(p[i]);
                    }
                    case "ms" -> c.maxSta = Integer.parseInt(v);
                    case "s" -> c.sta = Integer.parseInt(v);
                    case "ss" -> c.staStamp = Long.parseLong(v);
                    case "h" -> c.hunger = Integer.parseInt(v);
                    case "hs" -> c.hungerStamp = Long.parseLong(v);
                    case "c" -> c.color = Integer.parseInt(v);
                    case "sp" -> c.spur = Integer.parseInt(v);
                    case "cb" -> c.comb = Integer.parseInt(v);
                    case "w" -> c.wins = Integer.parseInt(v);
                    case "l" -> c.losses = Integer.parseInt(v);
                    case "g" -> c.gen = Integer.parseInt(v);
                    case "inj" -> c.injuredUntil = Long.parseLong(v);
                    case "br" -> c.breedReady = Long.parseLong(v);
                    case "du" -> c.deadUntil = Long.parseLong(v);
                    case "wu" -> c.weakUntil = Long.parseLong(v);
                    case "mo" -> c.mood = Integer.parseInt(v);
                    case "mos" -> c.moodStamp = Long.parseLong(v);
                    default -> { }
                }
            }
            return c;
        } catch (Exception e) {
            return null;
        }
    }

    public static ChickenData fromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String raw = item.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        return raw == null ? null : parse(raw);
    }

    public List<String> infoLines() {
        List<String> l = new ArrayList<>();
        l.add("&7Độ hiếm: " + rarity.tag() + " &8| &7Đời: &f" + gen);
        l.add("&7Ngoại hình: &f" + COLORS[color] + " &8| &f" + SPURS[spur] + " &8| &f" + COMBS[comb]);
        l.add("&cHP &f" + st[HP] + " &8| &6Công &f" + st[ATK] + " &8| &9Thủ &f" + st[DEF]);
        l.add("&bTốc độ &f" + st[SPD] + " &8| &dMay mắn &f" + st[LUCK]);
        l.add("&aThể lực &f" + sta + "/" + maxSta + " &8| &eNo &f" + hunger + "/100 &8| &dTâm trạng &f" + mood + "/100");
        l.add("&7Thắng/Thua: &a" + wins + "&7/&c" + losses);
        if (dead()) {
            long left = (deadUntil - System.currentTimeMillis()) / 60000L + 1;
            l.add("&4&l☠ ĐÃ CHẾT &7- hồi sinh sau ~" + left + " phút (hoặc /daga hoisinh)");
        } else if (weak()) {
            long left = (weakUntil - System.currentTimeMillis()) / 60000L + 1;
            l.add("&5Còn yếu sau khi hồi sinh (~" + left + " phút), chỉ số giảm 20%");
        }
        if (injured()) {
            long left = (injuredUntil - System.currentTimeMillis()) / 60000L + 1;
            l.add("&4Đang bị thương (~" + left + " phút) &7- dùng /daga chua");
        }
        return l;
    }

    public ItemStack toItem() {
        ItemStack it = new ItemStack(Material.CHICKEN_SPAWN_EGG);
        ItemMeta m = it.getItemMeta();
        m.displayName(Msg.c(dead() ? "&8&l☠ " + name + " &8(đã chết)" : rarity.color + "&l" + name));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String s : infoLines()) lore.add(Msg.c(s));
        lore.add(Msg.c("&8ID: " + id));
        m.lore(lore);
        if (rarity == Rarity.HUYEN_THOAI) m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, serialize());
        it.setItemMeta(m);
        return it;
    }
}
