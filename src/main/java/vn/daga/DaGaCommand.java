package vn.daga;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;

public class DaGaCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = List.of("giup", "xem", "cho", "tap", "nghi", "chua", "ghep", "doiten",
            "ap", "nhan", "thachdau", "chapnhan", "tuchoi", "top", "hoisinh", "setarena");

    private final DaGaPlugin pl;
    private final Map<UUID, Long> trainCd = new HashMap<>();
    private final Map<UUID, Long> restCd = new HashMap<>();

    public DaGaCommand(DaGaPlugin pl) {
        this.pl = pl;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "giup" : a[0].toLowerCase();
        if (sub.equals("reload") || sub.equals("give")) {
            if (!s.hasPermission("daga.admin")) { Msg.send(s, "&cBạn không có quyền."); return true; }
            if (sub.equals("reload")) {
                pl.reloadConfig();
                Msg.send(s, "&aĐã tải lại config.");
            } else {
                give(s, a);
            }
            return true;
        }
        if (!(s instanceof Player p)) { Msg.send(s, "&cChỉ người chơi mới dùng được lệnh này."); return true; }
        switch (sub) {
            case "xem" -> info(p);
            case "cho" -> feed(p);
            case "tap" -> train(p);
            case "nghi" -> rest(p);
            case "chua" -> cure(p);
            case "ghep" -> breed(p);
            case "doiten" -> rename(p, a);
            case "ap" -> incubate(p);
            case "nhan" -> collect(p);
            case "thachdau" -> challenge(p, a);
            case "chapnhan" -> pl.fights().accept(p);
            case "tuchoi" -> pl.fights().decline(p);
            case "top" -> top(p);
            case "hoisinh" -> revive(p);
            case "setarena" -> setArena(p);
            default -> help(p);
        }
        return true;
    }

    private void help(Player p) {
        Msg.raw(p, "&6&l=== ĐÁ GÀ ===");
        String[] l = {
                "&e/daga xem &7- xem gà đang cầm (tay chính)",
                "&e/daga cho &7- cho ăn (gà tay chính, thức ăn tay phụ: hạt giống/lúa mì/bánh mì/cà rốt vàng)",
                "&e/daga tap &7- huấn luyện, tăng chỉ số (tốn thể lực)",
                "&e/daga nghi &7- cho gà nghỉ (đứng gần bó rơm = chuồng gà, hồi nhiều hơn)",
                "&e/daga chua &7- chữa gà bị thương (tốn 1 cà rốt vàng)",
                "&e/daga hoisinh &7- hồi sinh ngay gà đã chết (tốn 1 táo vàng)",
                "&e/daga ghep &7- ghép gen: gà tay chính + gà tay phụ ra trứng gà lai",
                "&e/daga doiten <tên> &7- đổi tên gà",
                "&e/daga ap &7- ấp trứng đang cầm | &e/daga nhan &7- nhận gà đã nở / xem tiến độ",
                "&e/daga thachdau <người> [tiền] &7- thách đấu | &e/daga chapnhan &7| &e/daga tuchoi",
                "&e/daga top &7- bảng xếp hạng | &e/daga setarena &7- (admin) đặt sàn đấu",
                "&7Kiếm gà: trứng rơi từ quái/boss, câu cá, rương hầm ngục, thuần gà hoang ở biome tuyết/sa mạc/nấm."
        };
        for (String x : l) Msg.raw(p, x);
    }

    // ---------- tiện ích ----------
    private ChickenData main(Player p) {
        ChickenData d = ChickenData.fromItem(p.getInventory().getItemInMainHand());
        if (d == null) { Msg.send(p, "&cHãy cầm 1 con gà chiến ở tay chính."); return null; }
        d.refresh();
        return d;
    }

    private void save(Player p, ChickenData d) {
        p.getInventory().setItemInMainHand(d.toItem());
    }

    private boolean near(Location loc, Material m, int r) {
        for (int x = -r; x <= r; x++)
            for (int y = -r; y <= r; y++)
                for (int z = -r; z <= r; z++)
                    if (loc.getBlock().getRelative(x, y, z).getType() == m) return true;
        return false;
    }

    private boolean busy(Player p) {
        if (pl.fights().inMatch(p)) { Msg.send(p, "&cBạn đang trong trận đấu."); return true; }
        return false;
    }

    // ---------- chăm gà ----------
    private void info(Player p) {
        ChickenData d = main(p);
        if (d == null) return;
        save(p, d);
        Msg.raw(p, rarityHeader(d));
        for (String l : d.infoLines()) Msg.raw(p, l);
    }

    private String rarityHeader(ChickenData d) {
        return "&6=== " + d.rarity.color + d.name + " &6===";
    }

    private int food(Material m) {
        return switch (m) {
            case WHEAT_SEEDS, BEETROOT_SEEDS, MELON_SEEDS, PUMPKIN_SEEDS -> 10;
            case WHEAT, BREAD -> 25;
            case GOLDEN_CARROT -> 35;
            default -> 0;
        };
    }

    private void feed(Player p) {
        if (busy(p)) return;
        ChickenData d = main(p);
        if (d == null) return;
        ItemStack off = p.getInventory().getItemInOffHand();
        int v = food(off.getType());
        if (v == 0) { Msg.send(p, "&cCầm thức ăn ở tay phụ (hạt giống, lúa mì, bánh mì, cà rốt vàng)."); return; }
        if (d.hunger >= 100) { Msg.send(p, "&eGà đã no căng rồi."); return; }
        d.hunger = Math.min(100, d.hunger + v);
        if (off.getAmount() <= 1) p.getInventory().setItemInOffHand(null);
        else off.setAmount(off.getAmount() - 1);
        String extra = "";
        if (off.getType() == Material.GOLDEN_CARROT && pl.rnd().nextDouble() < 0.25) {
            int s = pl.rnd().nextInt(5);
            if (d.train(s)) extra = " &d(+ " + ChickenData.STAT_NAMES[s] + ")";
        }
        save(p, d);
        Msg.send(p, "&aĐã cho gà ăn, độ no: &f" + d.hunger + "/100" + extra);
    }

    private void train(Player p) {
        if (busy(p)) return;
        ChickenData d = main(p);
        if (d == null) return;
        long now = System.currentTimeMillis();
        long cd = pl.getConfig().getLong("training-cooldown-seconds", 30) * 1000L;
        long last = trainCd.getOrDefault(p.getUniqueId(), 0L);
        if (now - last < cd) { Msg.send(p, "&cĐợi thêm " + ((cd - (now - last)) / 1000 + 1) + " giây."); return; }
        if (isDead(p, d)) return;
        if (d.injured()) { Msg.send(p, "&cGà đang bị thương, hãy chữa trước."); return; }
        if (d.sta < 20) { Msg.send(p, "&cGà quá mệt, cho nghỉ đã (/daga nghi)."); return; }
        if (d.hunger < 20) { Msg.send(p, "&cGà đói quá, cho ăn đã (/daga cho)."); return; }
        trainCd.put(p.getUniqueId(), now);
        d.sta -= 20;
        d.hunger = Math.max(0, d.hunger - 3);
        double r = pl.rnd().nextDouble();
        int stat = r < 0.30 ? ChickenData.HP : r < 0.55 ? ChickenData.ATK : r < 0.75 ? ChickenData.DEF
                : r < 0.90 ? ChickenData.SPD : ChickenData.LUCK;
        boolean ok = d.train(stat);
        save(p, d);
        if (ok) Msg.send(p, "&aTập luyện xong! &f" + ChickenData.STAT_NAMES[stat] + " &atăng lên &f" + d.st[stat]
                + "&7/" + d.cap(stat) + " &8| &7Thể lực: " + d.sta + "/" + d.maxSta);
        else Msg.send(p, "&e" + ChickenData.STAT_NAMES[stat] + " đã chạm giới hạn của độ hiếm này (" + d.cap(stat) + ").");
    }

    private void rest(Player p) {
        if (busy(p)) return;
        ChickenData d = main(p);
        if (d == null) return;
        if (isDead(p, d)) return;
        long now = System.currentTimeMillis();
        long cd = pl.getConfig().getLong("rest-cooldown-seconds", 120) * 1000L;
        long last = restCd.getOrDefault(p.getUniqueId(), 0L);
        if (now - last < cd) { Msg.send(p, "&cĐợi thêm " + ((cd - (now - last)) / 1000 + 1) + " giây."); return; }
        restCd.put(p.getUniqueId(), now);
        boolean coop = near(p.getLocation(), Material.HAY_BLOCK, 5);
        int gain = coop ? 60 : 25;
        d.sta = Math.min(d.maxSta, d.sta + gain);
        d.hunger = Math.max(0, d.hunger - 2);
        save(p, d);
        Msg.send(p, "&aGà nghỉ ngơi " + (coop ? "trong chuồng (gần bó rơm)" : "ngoài trời") + ", +" + gain
                + " thể lực (" + d.sta + "/" + d.maxSta + ").");
    }

    private void cure(Player p) {
        ChickenData d = main(p);
        if (d == null) return;
        if (!d.injured()) { Msg.send(p, "&eGà không bị thương."); return; }
        Map<Integer, ItemStack> left = p.getInventory().removeItem(new ItemStack(Material.GOLDEN_CARROT, 1));
        if (!left.isEmpty()) { Msg.send(p, "&cCần 1 cà rốt vàng trong túi để chữa."); return; }
        d.injuredUntil = 0;
        save(p, d);
        Msg.send(p, "&aĐã chữa khỏi cho gà.");
    }

    private void rename(Player p, String[] a) {
        if (a.length < 2) { Msg.send(p, "&cDùng: /daga doiten <tên>"); return; }
        ChickenData d = main(p);
        if (d == null) return;
        String n = String.join(" ", Arrays.copyOfRange(a, 1, a.length)).replaceAll("[;=|&§]", "").trim();
        if (n.isEmpty() || n.length() > 16) { Msg.send(p, "&cTên phải từ 1 đến 16 ký tự."); return; }
        d.name = n;
        save(p, d);
        Msg.send(p, "&aĐã đổi tên gà thành &f" + n);
    }

    private boolean isDead(Player p, ChickenData d) {
        if (!d.dead()) return false;
        Msg.send(p, "&cGà đã chết, còn ~" + ((d.deadUntil - System.currentTimeMillis()) / 60000L + 1)
                + " phút nữa hồi sinh (hoặc /daga hoisinh với 1 táo vàng).");
        return true;
    }

    private void revive(Player p) {
        ChickenData d = main(p);
        if (d == null) return;
        if (!d.dead()) { Msg.send(p, "&eGà vẫn còn sống."); return; }
        Map<Integer, ItemStack> left = p.getInventory().removeItem(new ItemStack(Material.GOLDEN_APPLE, 1));
        if (!left.isEmpty()) { Msg.send(p, "&cCần 1 táo vàng trong túi để hồi sinh ngay."); return; }
        long weakMin = pl.getConfig().getLong("death.weak-minutes", 10);
        d.deadUntil = 0;
        d.weakUntil = System.currentTimeMillis() + weakMin * 60000L;
        d.sta = Math.max(d.sta, 20);
        d.hunger = Math.max(d.hunger, 30);
        save(p, d);
        Msg.send(p, "&aGà đã hồi sinh! Nhưng còn yếu " + weakMin + " phút (chỉ số giảm 20%).");
    }

    private void setArena(Player p) {
        if (!p.hasPermission("daga.admin")) { Msg.send(p, "&cBạn không có quyền."); return; }
        Location l = p.getLocation();
        pl.getConfig().set("arena.world", l.getWorld().getName());
        pl.getConfig().set("arena.x", l.getX());
        pl.getConfig().set("arena.y", l.getY());
        pl.getConfig().set("arena.z", l.getZ());
        pl.saveConfig();
        Msg.send(p, "&aĐã đặt tâm sàn đấu tại chỗ bạn đứng. Hai gà sẽ đứng cách nhau 3 block theo trục X, "
                + "người chơi được dịch chuyển tới cách tâm 4 block theo X và 5 block về phía Z âm, rồi tự về lại sau trận. "
                + "Hãy dọn trống/xây sàn quanh đó. Muốn bỏ sàn đấu thì xóa mục 'arena' trong config.yml.");
    }

    // ---------- ghép gen ----------
    private void breed(Player p) {
        if (busy(p)) return;
        PlayerInventory inv = p.getInventory();
        ChickenData a = ChickenData.fromItem(inv.getItemInMainHand());
        ChickenData b = ChickenData.fromItem(inv.getItemInOffHand());
        if (a == null || b == null) { Msg.send(p, "&cCầm 2 con gà: một ở tay chính, một ở tay phụ."); return; }
        if (a.id.equals(b.id)) { Msg.send(p, "&cPhải là 2 con gà khác nhau."); return; }
        a.refresh(); b.refresh();
        long now = System.currentTimeMillis();
        for (ChickenData d : new ChickenData[]{a, b}) {
            if (d.dead()) { Msg.send(p, "&c" + d.name + " đã chết, chưa ghép được."); return; }
            if (d.injured()) { Msg.send(p, "&c" + d.name + " đang bị thương."); return; }
            if (d.sta < 50 || d.hunger < 50) { Msg.send(p, "&c" + d.name + " cần thể lực và độ no từ 50 trở lên."); return; }
            if (d.breedReady > now) {
                Msg.send(p, "&c" + d.name + " cần nghỉ " + ((d.breedReady - now) / 60000 + 1) + " phút nữa mới ghép được.");
                return;
            }
        }
        long cd = pl.getConfig().getLong("breed-cooldown-minutes", 30) * 60000L;
        for (ChickenData d : new ChickenData[]{a, b}) {
            d.sta -= 40;
            d.hunger -= 20;
            d.breedReady = now + cd;
        }
        ItemStack egg = Eggs.bred(a, b);
        inv.setItemInMainHand(a.toItem());
        inv.setItemInOffHand(b.toItem());
        DaGaPlugin.give(p, egg);
        Msg.send(p, "&aGhép gen thành công! Bạn nhận được &eTrứng Gà Lai&a. Mang đi ấp bằng /daga ap.");
    }

    // ---------- lò ấp ----------
    private void incubate(Player p) {
        String raw = Eggs.read(p.getInventory().getItemInMainHand());
        if (raw == null) { Msg.send(p, "&cHãy cầm 1 quả trứng gà (trứng bí ẩn / trứng gà lai) ở tay chính."); return; }
        List<DataStore.Slot> slots = pl.store().slots(p.getUniqueId());
        int max = pl.getConfig().getInt("incubator-slots", 2);
        if (slots.size() >= max) { Msg.send(p, "&cLò ấp đã đầy (" + max + " ô). Dùng /daga nhan để lấy gà."); return; }
        boolean fullMoon = (p.getWorld().getFullTime() / 24000L) % 8 == 0;
        ChickenData c = Eggs.hatch(raw, fullMoon, pl.getConfig(), pl.rnd());
        double mult = pl.getConfig().getDouble("hatch-time-multiplier", 1.0);
        boolean fire = near(p.getLocation(), Material.CAMPFIRE, 5) || near(p.getLocation(), Material.SOUL_CAMPFIRE, 5);
        if (fire) mult *= (1.0 - pl.getConfig().getDouble("campfire-hatch-speedup", 0.3));
        long ms = (long) (c.rarity.hatchMinutes * 60000L * mult);
        DataStore.Slot sl = new DataStore.Slot();
        sl.chicken = c.serialize();
        sl.ready = System.currentTimeMillis() + ms;
        slots.add(sl);
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getAmount() <= 1) p.getInventory().setItemInMainHand(null);
        else hand.setAmount(hand.getAmount() - 1);
        pl.store().save();
        Msg.send(p, "&aĐã cho trứng vào lò ấp" + (fire ? " &7(gần lửa trại nên ấp nhanh hơn)" : "")
                + (fullMoon ? " &7(đêm trăng tròn, dễ đột biến!)" : "") + "&a. Gõ /daga nhan để xem tiến độ.");
    }

    private void collect(Player p) {
        List<DataStore.Slot> slots = pl.store().slots(p.getUniqueId());
        if (slots.isEmpty()) { Msg.send(p, "&7Lò ấp của bạn đang trống."); return; }
        long now = System.currentTimeMillis();
        Iterator<DataStore.Slot> it = slots.iterator();
        int got = 0, waiting = 0;
        while (it.hasNext()) {
            DataStore.Slot sl = it.next();
            if (sl.ready <= now) {
                ChickenData c = ChickenData.parse(sl.chicken);
                it.remove();
                if (c == null) continue;
                c.staStamp = now;
                c.hungerStamp = now;
                DaGaPlugin.give(p, c.toItem());
                got++;
                Msg.send(p, "&aMột chú gà đã nở: " + c.rarity.color + c.name + " &7(" + c.rarity.tag() + "&7)");
            } else {
                waiting++;
                Msg.send(p, "&7Còn trứng đang ấp, còn khoảng &f" + ((sl.ready - now) / 60000 + 1) + " &7phút.");
            }
        }
        if (got > 0) {
            p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.ENTITY_CHICKEN_EGG, 1f, 1f);
            pl.store().save();
        }
        if (got == 0 && waiting == 0) Msg.send(p, "&7Lò ấp trống.");
    }

    // ---------- đấu ----------
    private void challenge(Player p, String[] a) {
        if (a.length < 2) { Msg.send(p, "&cDùng: /daga thachdau <người chơi> [số tiền]"); return; }
        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) { Msg.send(p, "&cKhông thấy người chơi &f" + a[1] + "&c đang online."); return; }
        double bet = 0;
        if (a.length >= 3) {
            try { bet = Double.parseDouble(a[2]); } catch (NumberFormatException e) { Msg.send(p, "&cSố tiền không hợp lệ."); return; }
            if (bet < 0 || Double.isNaN(bet) || Double.isInfinite(bet)) { Msg.send(p, "&cSố tiền không hợp lệ."); return; }
        }
        pl.fights().challenge(p, t, bet);
    }

    private void top(Player p) {
        List<DataStore.Stat> l = pl.store().top(10);
        if (l.isEmpty()) { Msg.send(p, "&7Chưa có trận đấu nào."); return; }
        Msg.raw(p, "&6&l=== BẢNG XẾP HẠNG ĐÁ GÀ ===");
        int i = 1;
        for (DataStore.Stat s : l) {
            Msg.raw(p, "&e#" + i++ + " &f" + s.name + " &8- &a" + s.wins + " thắng &7/ &c" + s.losses + " thua &8- &6" + (long) s.earned);
        }
    }

    // ---------- admin ----------
    private void give(CommandSender s, String[] a) {
        if (a.length < 3) { Msg.send(s, "&cDùng: /daga give <người> <trung [cấp]|ga <thuong|kha|hiem|huyenthoai>>"); return; }
        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) { Msg.send(s, "&cKhông thấy người chơi đó."); return; }
        if (a[2].equalsIgnoreCase("trung")) {
            int bonus = 0;
            if (a.length >= 4) { try { bonus = Integer.parseInt(a[3]); } catch (NumberFormatException ignored) { } }
            DaGaPlugin.give(t, Eggs.wild(bonus));
            Msg.send(s, "&aĐã đưa trứng cho " + t.getName());
        } else if (a[2].equalsIgnoreCase("ga") && a.length >= 4) {
            Rarity r = Rarity.parse(a[3]);
            if (r == null) { Msg.send(s, "&cĐộ hiếm: thuong, kha, hiem, huyenthoai"); return; }
            DaGaPlugin.give(t, ChickenData.create(r, pl.rnd()).toItem());
            Msg.send(s, "&aĐã đưa gà " + r.label + " cho " + t.getName());
        } else {
            Msg.send(s, "&cDùng: /daga give <người> trung [cấp] | ga <độ hiếm>");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String x : SUBS) if (x.startsWith(a[0].toLowerCase())) out.add(x);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("thachdau")) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase().startsWith(a[1].toLowerCase())) out.add(p.getName());
        }
        return out;
    }
}
