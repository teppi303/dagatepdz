package vn.daga;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Toàn bộ giao diện menu của plugin. */
public class Gui {
    private final DaGaPlugin pl;
    private final Map<String, Long> cds = new HashMap<>();
    private final Set<UUID> renaming = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> breedSel = new HashMap<>();

    private static final Material[] SEEDS = {Material.WHEAT_SEEDS, Material.BEETROOT_SEEDS, Material.MELON_SEEDS, Material.PUMPKIN_SEEDS};

    public Gui(DaGaPlugin pl) {
        this.pl = pl;
    }

    private DataStore st() { return pl.store(); }
    private Random rnd() { return pl.rnd(); }

    // ================= tiện ích =================
    private boolean busy(Player p) {
        if (pl.fights().inMatch(p)) { Msg.send(p, "&cBạn đang trong trận đấu."); return true; }
        return false;
    }

    private ChickenData active(Player p) {
        ChickenData d = st().active(p.getUniqueId());
        if (d == null) {
            Msg.send(p, "&cBạn chưa có gà chiến. Kiếm gà (xem Hướng dẫn) rồi chọn trong Chuồng gà.");
            return null;
        }
        d.refresh();
        return d;
    }

    private boolean onCooldown(Player p, String key, long seconds) {
        if (p.hasPermission("daga.admin")) return false;
        String k = p.getUniqueId() + key;
        long now = System.currentTimeMillis();
        long last = cds.getOrDefault(k, 0L);
        if (now - last < seconds * 1000L) {
            Msg.send(p, "&cĐợi thêm " + ((seconds * 1000L - (now - last)) / 1000 + 1) + " giây.");
            return true;
        }
        cds.put(k, now);
        return false;
    }

    private int count(Player p, Material... mats) {
        int n = 0;
        for (ItemStack s : p.getInventory().getContents()) {
            if (s == null) continue;
            for (Material m : mats) if (s.getType() == m) n += s.getAmount();
        }
        return n;
    }

    private boolean take(Player p, Material... mats) {
        for (Material m : mats) {
            if (p.getInventory().containsAtLeast(new ItemStack(m), 1)) {
                p.getInventory().removeItem(new ItemStack(m, 1));
                return true;
            }
        }
        return false;
    }

    private boolean near(Location loc, Material m, int r) {
        for (int x = -r; x <= r; x++)
            for (int y = -r; y <= r; y++)
                for (int z = -r; z <= r; z++)
                    if (loc.getBlock().getRelative(x, y, z).getType() == m) return true;
        return false;
    }

    private void open(Player p, Menu m) {
        p.openInventory(m.getInventory());
    }

    private void back(Menu m, int slot, Player p) {
        m.set(slot, Ui.icon(Material.ARROW, "&e« Quay lại menu chính"), e -> openMain(p));
    }

    // ================= MENU CHÍNH =================
    public void openMain(Player p) {
        open(p, new Menu(p, 45, "&6&lĐÁ GÀ &8» &7Menu chính", m -> fillMain(m, p)));
    }

    private void fillMain(Menu m, Player p) {
        m.background();
        ChickenData d = st().active(p.getUniqueId());
        if (d != null) {
            d.refresh();
            m.set(4, Ui.withExtraLore(d.toItem(), "", "&a(Gà chiến đang chọn)"), null);
        } else {
            m.set(4, Ui.icon(Material.BARRIER, "&cChưa có gà chiến", "&7Kiếm gà rồi chọn trong Chuồng gà", "&7Xem mục Hướng dẫn để biết cách kiếm gà"), null);
        }
        m.set(19, Ui.icon(Material.HAY_BLOCK, "&e&lChuồng gà", "&7Xem tất cả gà của bạn,", "&7chọn gà chiến, rút gà ra túi.", "", "&aBấm để mở"), e -> openCoop(p));
        m.set(20, Ui.icon(Material.WHEAT, "&a&lChăm sóc gà", "&7Cho ăn, tập luyện, nghỉ ngơi,", "&7vuốt ve, chữa thương, hồi sinh.", "", "&aBấm để mở"), e -> openCare(p));
        m.set(21, Ui.icon(Material.EGG, "&d&lLò ấp trứng", "&7Ấp trứng bí ẩn và trứng gà lai,", "&7nhận gà mới khi trứng nở.", "", "&aBấm để mở"), e -> openIncubator(p));
        m.set(22, Ui.icon(Material.GOLDEN_CARROT, "&6&lGhép gen", "&7Ghép 2 gà ra trứng gà lai,", "&7có thể đột biến lên hiếm hơn.", "", "&aBấm để mở"), e -> openBreed(p));
        m.set(23, Ui.icon(Material.IRON_SWORD, "&c&lThách đấu người chơi", "&7Chọn đối thủ và mức cược,", "&72 gà thật đánh nhau.", "", "&aBấm để mở"), e -> openChallenge(p));
        m.set(24, Ui.icon(Material.ARMOR_STAND, "&b&lĐấu thử gà máy", "&7Luyện tập một mình, không sợ chết,", "&7có thưởng khi thắng.", "", "&aBấm để mở"), e -> openSpar(p));
        m.set(25, Ui.icon(Material.GOLD_INGOT, "&6&lBảng xếp hạng", "&7Top người chơi thắng nhiều nhất.", "", "&aBấm để mở"), e -> openTop(p));
        m.set(37, Ui.icon(Material.EMERALD, "&a&lCửa hàng", "&7Mua thức ăn và trứng.", pl.hook() == null ? "&cServer chưa có economy" : "&aBấm để mở"), e -> openShop(p));
        m.set(40, Ui.icon(Material.BOOK, "&f&lHướng dẫn", "&7Cách chơi từ A đến Z.", "", "&aBấm để mở"), e -> openHelp(p));
        if (p.hasPermission("daga.admin")) {
            m.set(43, Ui.icon(Material.COMMAND_BLOCK, "&4&lBảng Admin", "&7Lấy gà, trứng, đồ test,", "&7đấu thử, chỉnh sàn đấu...", "", "&aBấm để mở"), e -> openAdmin(p));
        }
    }

    // ================= CHUỒNG GÀ =================
    public void openCoop(Player p) {
        open(p, new Menu(p, 54, "&6&lChuồng gà", m -> fillCoop(m, p)));
    }

    private void fillCoop(Menu m, Player p) {
        m.background();
        int cap = st().coopCap();
        List<ChickenData> list = st().coop(p.getUniqueId());
        ChickenData act = st().active(p.getUniqueId());
        for (int i = 0; i < 45; i++) {
            if (i >= cap) { m.set(i, Ui.icon(Material.RED_STAINED_GLASS_PANE, "&cChỗ bị khóa"), null); continue; }
            if (i < list.size()) {
                final ChickenData d = list.get(i);
                d.refresh();
                boolean isAct = d == act;
                ItemStack it = Ui.withExtraLore(d.toItem(), "", isAct ? "&a▶ ĐANG LÀ GÀ CHIẾN" : "&eChuột trái: chọn làm gà chiến", "&7Chuột phải: rút ra thành vật phẩm");
                if (isAct) it = Ui.glow(it);
                m.set(i, it, e -> {
                    if (busy(p)) return;
                    if (e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT) {
                        st().removeChicken(p.getUniqueId(), d);
                        DaGaPlugin.give(p, d.toItem());
                        Msg.send(p, "&aĐã rút &f" + d.name + " &ara túi (chuột phải vào gà để gửi lại chuồng).");
                    } else {
                        st().setActive(p.getUniqueId(), d.id);
                        st().save();
                        Msg.send(p, "&aĐã chọn &f" + d.name + " &alàm gà chiến.");
                    }
                    m.refresh();
                });
            } else {
                m.set(i, Ui.icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&7Ô trống"), null);
            }
        }
        m.set(45, Ui.icon(Material.PAPER, "&fChuồng: &e" + list.size() + "&7/&e" + cap, "&7Mở rộng bằng cách chỉnh coop.slots trong config."), null);
        m.set(48, Ui.icon(Material.CHEST, "&bGửi tất cả gà trong túi vào chuồng", "&7Hoặc chuột phải vào gà (vật phẩm) cũng được."), e -> {
            int n = depositAll(p);
            Msg.send(p, n > 0 ? "&aĐã gửi " + n + " gà vào chuồng." : "&7Không có gà nào trong túi để gửi (hoặc chuồng đã đầy).");
            m.refresh();
        });
        back(m, 49, p);
    }

    private int depositAll(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] cs = inv.getContents();
        int n = 0;
        for (int i = 0; i < cs.length; i++) {
            ChickenData d = ChickenData.fromItem(cs[i]);
            if (d == null) continue;
            if (!st().addChicken(p.getUniqueId(), d)) break;
            inv.setItem(i, null);
            n++;
        }
        return n;
    }

    // ================= CHĂM SÓC =================
    public void openCare(Player p) {
        if (st().active(p.getUniqueId()) == null) { active(p); openCoop(p); return; }
        open(p, new Menu(p, 54, "&a&lChăm sóc gà", m -> fillCare(m, p)));
    }

    private void fillCare(Menu m, Player p) {
        m.background();
        ChickenData d = st().active(p.getUniqueId());
        if (d == null) { back(m, 49, p); return; }
        d.refresh();
        m.set(4, Ui.withExtraLore(d.toItem(), "", "&7(Gà đang chăm sóc)"), null);

        m.set(9, Ui.icon(Material.PAPER, "&e&lCHO ĂN"), null);
        m.set(10, Ui.icon(Material.WHEAT_SEEDS, "&aHạt giống", "&7+10 độ no", "&7Bạn có: &f" + count(p, SEEDS), "&8Bấm để cho ăn"), e -> feed(p, m, 10, SEEDS));
        m.set(11, Ui.icon(Material.WHEAT, "&aLúa mì", "&7+25 độ no", "&7Bạn có: &f" + count(p, Material.WHEAT), "&8Bấm để cho ăn"), e -> feed(p, m, 25, Material.WHEAT));
        m.set(12, Ui.icon(Material.BREAD, "&aBánh mì", "&7+25 độ no", "&7Bạn có: &f" + count(p, Material.BREAD), "&8Bấm để cho ăn"), e -> feed(p, m, 25, Material.BREAD));
        m.set(13, Ui.icon(Material.GOLDEN_CARROT, "&6Cà rốt vàng", "&7+35 độ no, 25% tăng 1 chỉ số", "&7Bạn có: &f" + count(p, Material.GOLDEN_CARROT), "&8Bấm để cho ăn"), e -> feed(p, m, 35, Material.GOLDEN_CARROT));

        m.set(18, Ui.icon(Material.PAPER, "&e&lTẬP LUYỆN", "&7Tốn 20 thể lực, mỗi lần +1 chỉ số", "&7(HP +2), có giới hạn theo độ hiếm."), null);
        Material[] tm = {Material.APPLE, Material.IRON_SWORD, Material.SHIELD, Material.FEATHER, Material.RABBIT_FOOT};
        String[] tn = {"Tập thể lực (HP)", "Tập đòn (Công)", "Tập chịu đòn (Thủ)", "Tập chạy (Tốc độ)", "Tập may mắn"};
        for (int s = 0; s < 5; s++) {
            final int stat = s;
            m.set(19 + s, Ui.icon(tm[s], "&b" + tn[s], "&7Hiện tại: &f" + d.st[s] + "&7/&f" + d.cap(s), "&8Bấm để tập"), e -> train(p, m, stat));
        }

        m.set(27, Ui.icon(Material.PAPER, "&e&lNGHỈ NGƠI & KHÁC"), null);
        m.set(28, Ui.icon(Material.HAY_BLOCK, "&aNghỉ ngơi", "&7Hồi 25 thể lực,", "&7đứng gần bó rơm (chuồng gà) hồi 60.", "&8Bấm để nghỉ"), e -> rest(p, m));
        m.set(29, Ui.icon(Material.COOKIE, "&dVuốt ve", "&7+10 tâm trạng (từ 70 trở lên", "&7gà đánh mạnh hơn 5%).", "&8Bấm để vuốt ve"), e -> pet(p, m));
        m.set(30, Ui.icon(Material.GOLDEN_CARROT, "&cChữa thương", "&7Tốn 1 cà rốt vàng.", d.injured() ? "&4Gà đang bị thương!" : "&7Gà không bị thương.", "&8Bấm để chữa"), e -> cure(p, m));
        m.set(31, Ui.icon(Material.GOLDEN_APPLE, "&6Hồi sinh ngay", "&7Tốn 1 táo vàng, gà vẫn yếu một lúc.", d.dead() ? "&4Gà đã chết!" : "&7Gà còn sống.", "&8Bấm để hồi sinh"), e -> revive(p, m));
        m.set(32, Ui.icon(Material.NAME_TAG, "&fĐổi tên", "&7Gõ tên mới vào chat.", "&8Bấm để đổi"), e -> { p.closeInventory(); startRename(p); });
        back(m, 49, p);
    }

    private void feed(Player p, Menu m, int value, Material... mats) {
        if (busy(p)) return;
        ChickenData d = active(p);
        if (d == null) return;
        if (d.dead()) { Msg.send(p, "&cGà đã chết, không ăn được."); return; }
        if (d.hunger >= 100) { Msg.send(p, "&eGà đã no căng rồi."); return; }
        if (!take(p, mats)) { Msg.send(p, "&cBạn không có thức ăn này trong túi."); return; }
        d.hunger = Math.min(100, d.hunger + value);
        d.mood = Math.min(100, d.mood + 3);
        String extra = "";
        if (mats.length == 1 && mats[0] == Material.GOLDEN_CARROT && rnd().nextDouble() < 0.25) {
            int s = rnd().nextInt(5);
            if (d.train(s)) extra = " &d(+1 " + ChickenData.STAT_NAMES[s] + ")";
        }
        st().save();
        Msg.send(p, "&aĐã cho gà ăn, độ no: &f" + d.hunger + "/100" + extra);
        m.refresh();
    }

    private void train(Player p, Menu m, int stat) {
        if (busy(p)) return;
        ChickenData d = active(p);
        if (d == null) return;
        if (d.dead()) { Msg.send(p, "&cGà đã chết."); return; }
        if (d.injured()) { Msg.send(p, "&cGà đang bị thương, hãy chữa trước."); return; }
        if (d.sta < 20) { Msg.send(p, "&cGà quá mệt, cho nghỉ đã."); return; }
        if (d.hunger < 20) { Msg.send(p, "&cGà đói quá, cho ăn đã."); return; }
        if (onCooldown(p, "train", pl.getConfig().getLong("training-cooldown-seconds", 30))) return;
        d.sta -= 20;
        d.hunger = Math.max(0, d.hunger - 3);
        d.mood = Math.max(0, d.mood - 2);
        boolean ok = d.train(stat);
        st().save();
        if (ok) Msg.send(p, "&aTập xong! &f" + ChickenData.STAT_NAMES[stat] + " &atăng lên &f" + d.st[stat] + "&7/" + d.cap(stat));
        else Msg.send(p, "&e" + ChickenData.STAT_NAMES[stat] + " đã chạm giới hạn của độ hiếm này (" + d.cap(stat) + ").");
        m.refresh();
    }

    private void rest(Player p, Menu m) {
        if (busy(p)) return;
        ChickenData d = active(p);
        if (d == null) return;
        if (d.dead()) { Msg.send(p, "&cGà đã chết."); return; }
        if (onCooldown(p, "rest", pl.getConfig().getLong("rest-cooldown-seconds", 120))) return;
        boolean coop = near(p.getLocation(), Material.HAY_BLOCK, 5);
        int gain = coop ? 60 : 25;
        d.sta = Math.min(d.maxSta, d.sta + gain);
        d.hunger = Math.max(0, d.hunger - 2);
        d.mood = Math.min(100, d.mood + 3);
        st().save();
        Msg.send(p, "&aGà nghỉ " + (coop ? "trong chuồng (gần bó rơm)" : "ngoài trời") + ", +" + gain + " thể lực (" + d.sta + "/" + d.maxSta + ").");
        m.refresh();
    }

    private void pet(Player p, Menu m) {
        if (busy(p)) return;
        ChickenData d = active(p);
        if (d == null) return;
        if (d.dead()) { Msg.send(p, "&cGà đã chết."); return; }
        if (onCooldown(p, "pet", 20)) return;
        d.mood = Math.min(100, d.mood + 10);
        st().save();
        p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 1.5, 0), 6, 0.3, 0.2, 0.3, 0);
        Msg.send(p, "&dBạn vuốt ve gà, tâm trạng: &f" + d.mood + "/100");
        m.refresh();
    }

    private void cure(Player p, Menu m) {
        if (busy(p)) return;
        ChickenData d = active(p);
        if (d == null) return;
        if (!d.injured()) { Msg.send(p, "&eGà không bị thương."); return; }
        if (!take(p, Material.GOLDEN_CARROT)) { Msg.send(p, "&cCần 1 cà rốt vàng trong túi."); return; }
        d.injuredUntil = 0;
        st().save();
        Msg.send(p, "&aĐã chữa khỏi cho gà.");
        m.refresh();
    }

    private void revive(Player p, Menu m) {
        if (busy(p)) return;
        ChickenData d = active(p);
        if (d == null) return;
        if (!d.dead()) { Msg.send(p, "&eGà vẫn còn sống."); return; }
        if (!take(p, Material.GOLDEN_APPLE)) { Msg.send(p, "&cCần 1 táo vàng trong túi để hồi sinh ngay."); return; }
        long weakMin = pl.getConfig().getLong("death.weak-minutes", 10);
        d.deadUntil = 0;
        d.weakUntil = System.currentTimeMillis() + weakMin * 60000L;
        d.sta = Math.max(d.sta, 20);
        d.hunger = Math.max(d.hunger, 30);
        st().save();
        Msg.send(p, "&aGà đã hồi sinh! Còn yếu " + weakMin + " phút (chỉ số giảm 20%).");
        m.refresh();
    }

    private void startRename(Player p) {
        renaming.add(p.getUniqueId());
        Msg.send(p, "&eGõ tên mới cho gà vào chat (tối đa 16 ký tự). Gõ &fhuy &eđể hủy.");
    }

    public boolean isRenaming(Player p) {
        return renaming.contains(p.getUniqueId());
    }

    public void finishRename(Player p, String raw) {
        renaming.remove(p.getUniqueId());
        String n = raw.replaceAll("[;=|&§]", "").trim();
        if (raw.trim().equalsIgnoreCase("huy")) { Msg.send(p, "&7Đã hủy đổi tên."); openCare(p); return; }
        ChickenData d = st().active(p.getUniqueId());
        if (d == null || n.isEmpty() || n.length() > 16) { Msg.send(p, "&cTên phải từ 1 đến 16 ký tự."); openCare(p); return; }
        d.name = n;
        st().save();
        Msg.send(p, "&aĐã đổi tên gà thành &f" + n);
        openCare(p);
    }

    // ================= LÒ ẤP =================
    public void openIncubator(Player p) {
        open(p, new Menu(p, 54, "&d&lLò ấp trứng", m -> fillIncubator(m, p)));
    }

    private void fillIncubator(Menu m, Player p) {
        m.background();
        List<DataStore.Slot> slots = st().slots(p.getUniqueId());
        int max = pl.getConfig().getInt("incubator-slots", 2);
        long now = System.currentTimeMillis();
        m.set(4, Ui.icon(Material.CAMPFIRE, "&6Mẹo", "&7Đứng gần lửa trại khi ấp: nhanh hơn 30%.", "&7Đêm trăng tròn: dễ đột biến hơn."), null);
        for (int i = 0; i < max && i < 7; i++) {
            final int idx = i;
            if (i < slots.size()) {
                DataStore.Slot sl = slots.get(i);
                if (sl.ready <= now) {
                    m.set(10 + i, Ui.glow(Ui.icon(Material.EGG, "&a&lĐÃ NỞ!", "&7Bấm để nhận gà mới.")), e -> collect(p, m, idx));
                } else {
                    m.set(10 + i, Ui.icon(Material.TURTLE_EGG, "&eĐang ấp...", "&7Còn khoảng &f" + ((sl.ready - now) / 60000L + 1) + " &7phút."), null);
                }
            } else {
                m.set(10 + i, Ui.icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&7Ô ấp trống", "&7Bấm vào một quả trứng bên dưới để ấp."), null);
            }
        }
        m.set(27, Ui.icon(Material.PAPER, "&e&lTRỨNG TRONG TÚI", "&7Bấm vào trứng để cho vào lò ấp."), null);
        PlayerInventory inv = p.getInventory();
        int shown = 0;
        for (int i = 0; i < 36 && shown < 18; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || Eggs.read(it) == null) continue;
            final int invSlot = i;
            m.set(36 + shown, Ui.withExtraLore(it, "", "&aBấm để ấp"), e -> incubate(p, m, invSlot));
            shown++;
        }
        back(m, 49, p);
    }

    private void incubate(Player p, Menu m, int invSlot) {
        ItemStack it = p.getInventory().getItem(invSlot);
        String raw = Eggs.read(it);
        if (raw == null) { m.refresh(); return; }
        List<DataStore.Slot> slots = st().slots(p.getUniqueId());
        int max = pl.getConfig().getInt("incubator-slots", 2);
        if (slots.size() >= max) { Msg.send(p, "&cLò ấp đã đầy, nhận gà đã nở trước."); return; }
        boolean fullMoon = (p.getWorld().getFullTime() / 24000L) % 8 == 0;
        ChickenData c = Eggs.hatch(raw, fullMoon, pl.getConfig(), rnd());
        double mult = pl.getConfig().getDouble("hatch-time-multiplier", 1.0);
        boolean fire = near(p.getLocation(), Material.CAMPFIRE, 5) || near(p.getLocation(), Material.SOUL_CAMPFIRE, 5);
        if (fire) mult *= (1.0 - pl.getConfig().getDouble("campfire-hatch-speedup", 0.3));
        long ms = (long) (c.rarity.hatchMinutes * 60000L * mult);
        DataStore.Slot sl = new DataStore.Slot();
        sl.chicken = c.serialize();
        sl.ready = System.currentTimeMillis() + ms;
        slots.add(sl);
        if (it.getAmount() <= 1) p.getInventory().setItem(invSlot, null);
        else it.setAmount(it.getAmount() - 1);
        st().save();
        Msg.send(p, "&aĐã cho trứng vào lò ấp" + (fire ? " &7(gần lửa trại, ấp nhanh hơn)" : "") + (fullMoon ? " &7(đêm trăng tròn!)" : "") + "&a.");
        m.refresh();
    }

    private void collect(Player p, Menu m, int idx) {
        List<DataStore.Slot> slots = st().slots(p.getUniqueId());
        if (idx >= slots.size()) { m.refresh(); return; }
        DataStore.Slot sl = slots.get(idx);
        if (sl.ready > System.currentTimeMillis()) { m.refresh(); return; }
        ChickenData c = ChickenData.parse(sl.chicken);
        slots.remove(idx);
        if (c != null) {
            long now = System.currentTimeMillis();
            c.staStamp = now; c.hungerStamp = now; c.moodStamp = now;
            pl.giveChicken(p, c);
            Msg.send(p, "&aMột chú gà đã nở: " + c.rarity.color + c.name + " &7(" + c.rarity.tag() + "&7)");
            p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.ENTITY_CHICKEN_EGG, 1f, 1f);
        }
        st().save();
        m.refresh();
    }

    // ================= GHÉP GEN =================
    public void openBreed(Player p) {
        breedSel.remove(p.getUniqueId());
        open(p, new Menu(p, 54, "&6&lGhép gen", m -> fillBreed(m, p)));
    }

    private void fillBreed(Menu m, Player p) {
        m.background();
        List<ChickenData> list = st().coop(p.getUniqueId());
        String sel = breedSel.get(p.getUniqueId());
        long now = System.currentTimeMillis();
        for (int i = 0; i < 45 && i < list.size(); i++) {
            final ChickenData d = list.get(i);
            d.refresh();
            String status;
            if (d.dead()) status = "&4Đã chết";
            else if (d.injured()) status = "&cĐang bị thương";
            else if (d.sta < 50 || d.hunger < 50) status = "&cCần thể lực và no từ 50";
            else if (d.breedReady > now) status = "&cNghỉ ghép " + ((d.breedReady - now) / 60000L + 1) + " phút";
            else status = "&aSẵn sàng ghép";
            boolean isSel = d.id.equals(sel);
            ItemStack it = Ui.withExtraLore(d.toItem(), "", status, isSel ? "&e★ Đã chọn làm gà bố/mẹ thứ nhất" : "&7Bấm để chọn");
            if (isSel) it = Ui.glow(it);
            m.set(i, it, e -> pickBreed(p, m, d));
        }
        m.set(45, Ui.icon(Material.PAPER, "&e&lCách ghép", "&71) Bấm chọn gà thứ nhất", "&72) Bấm chọn gà thứ hai", "&7Nhận Trứng Gà Lai, mang đi ấp.", "&7Mỗi gà cần nghỉ 30 phút trước khi ghép lại."), null);
        back(m, 49, p);
    }

    private void pickBreed(Player p, Menu m, ChickenData d) {
        if (busy(p)) return;
        String sel = breedSel.get(p.getUniqueId());
        if (sel == null) { breedSel.put(p.getUniqueId(), d.id); m.refresh(); return; }
        if (sel.equals(d.id)) { breedSel.remove(p.getUniqueId()); m.refresh(); return; }
        ChickenData a = null;
        for (ChickenData x : st().coop(p.getUniqueId())) if (x.id.equals(sel)) a = x;
        if (a == null) { breedSel.remove(p.getUniqueId()); m.refresh(); return; }
        ChickenData b = d;
        a.refresh(); b.refresh();
        long now = System.currentTimeMillis();
        for (ChickenData x : new ChickenData[]{a, b}) {
            if (x.dead()) { Msg.send(p, "&c" + x.name + " đã chết."); return; }
            if (x.injured()) { Msg.send(p, "&c" + x.name + " đang bị thương."); return; }
            if (x.sta < 50 || x.hunger < 50) { Msg.send(p, "&c" + x.name + " cần thể lực và độ no từ 50 trở lên."); return; }
            if (x.breedReady > now) { Msg.send(p, "&c" + x.name + " cần nghỉ " + ((x.breedReady - now) / 60000L + 1) + " phút nữa."); return; }
        }
        long cd = pl.getConfig().getLong("breed-cooldown-minutes", 30) * 60000L;
        for (ChickenData x : new ChickenData[]{a, b}) {
            x.sta -= 40;
            x.hunger -= 20;
            x.breedReady = now + cd;
        }
        DaGaPlugin.give(p, Eggs.bred(a, b));
        st().save();
        breedSel.remove(p.getUniqueId());
        Msg.send(p, "&aGhép gen thành công! Bạn nhận được &eTrứng Gà Lai&a. Mang vào Lò ấp.");
        m.refresh();
    }

    // ================= THÁCH ĐẤU =================
    public void openChallenge(Player p) {
        open(p, new Menu(p, 54, "&c&lChọn đối thủ", m -> fillChallenge(m, p)));
    }

    private void fillChallenge(Menu m, Player p) {
        m.background();
        int slot = 0;
        for (Player t : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (t.equals(p) || slot >= 45) continue;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            sm.setOwningPlayer(t);
            sm.displayName(Msg.c("&f" + t.getName()));
            ChickenData td = st().active(t.getUniqueId());
            sm.lore(List.of(Msg.c(td == null ? "&7Chưa có gà chiến" : "&7Gà: " + td.rarity.color + td.name), Msg.c("&aBấm để thách đấu")));
            head.setItemMeta(sm);
            m.set(slot++, head, e -> openBet(p, t));
        }
        if (slot == 0) m.set(22, Ui.icon(Material.BARRIER, "&cKhông có ai khác online"), null);
        back(m, 49, p);
    }

    private void openBet(Player p, Player target) {
        open(p, new Menu(p, 27, "&c&lMức cược", m -> {
            m.background();
            List<Double> presets = pl.getConfig().getDoubleList("bet.presets");
            if (presets.isEmpty()) presets = Arrays.asList(0.0, 100.0, 500.0, 1000.0, 5000.0, 10000.0);
            double min = pl.getConfig().getDouble("bet.min", 100);
            double max = pl.getConfig().getDouble("bet.max", 1000000);
            int s = 10;
            for (double bet : presets) {
                if (s > 16) break;
                if (bet > 0 && (pl.hook() == null || bet < min || bet > max)) continue;
                final double b = bet;
                m.set(s++, Ui.icon(bet == 0 ? Material.FEATHER : Material.GOLD_NUGGET,
                        bet == 0 ? "&fĐấu giao hữu (không cược)" : "&6Cược " + (long) bet,
                        "&7Đối thủ: &f" + target.getName(), "&aBấm để gửi thách đấu"), e -> {
                    p.closeInventory();
                    pl.fights().challenge(p, target, b);
                });
            }
            m.set(22, Ui.icon(Material.ARROW, "&e« Chọn đối thủ khác"), e -> openChallenge(p));
        }));
    }

    // ================= ĐẤU THỬ =================
    public void openSpar(Player p) {
        open(p, new Menu(p, 27, "&b&lĐấu thử gà máy", m -> {
            m.background();
            m.set(4, Ui.icon(Material.PAPER, "&eChọn độ mạnh của gà máy", "&7Không tốn tiền, gà bạn không chết,", "&7chỉ tốn 20 thể lực.", "&7Thắng: có cơ hội tăng chỉ số và nhặt hạt giống."), null);
            Material[] mats = {Material.WHITE_WOOL, Material.LIME_WOOL, Material.BLUE_WOOL, Material.ORANGE_WOOL};
            for (int i = 0; i < 4; i++) {
                final Rarity r = Rarity.values()[i];
                m.set(10 + i * 2, Ui.icon(mats[i], r.color + "Gà máy " + r.label, "&aBấm để bắt đầu"), e -> {
                    p.closeInventory();
                    pl.fights().spar(p, r);
                });
            }
            back(m, 22, p);
        }));
    }

    // ================= BẢNG XẾP HẠNG =================
    public void openTop(Player p) {
        open(p, new Menu(p, 27, "&6&lBảng xếp hạng", m -> {
            m.background();
            List<DataStore.Stat> top = st().top(10);
            if (top.isEmpty()) m.set(13, Ui.icon(Material.BARRIER, "&7Chưa có trận đấu nào"), null);
            int i = 0;
            for (DataStore.Stat s : top) {
                Material mat = i == 0 ? Material.GOLD_BLOCK : i == 1 ? Material.IRON_BLOCK : i == 2 ? Material.COPPER_BLOCK : Material.PAPER;
                m.set(i < 7 ? 9 + i : 11 + i, Ui.icon(mat, "&e#" + (i + 1) + " &f" + s.name,
                        "&aThắng: &f" + s.wins, "&cThua: &f" + s.losses, "&6Tiền thắng: &f" + (long) s.earned), null);
                i++;
            }
            DataStore.Stat me = st().statOf(p.getUniqueId());
            m.set(22, Ui.icon(Material.PLAYER_HEAD, "&bThành tích của bạn",
                    me == null ? "&7Chưa đấu trận nào" : "&aThắng: &f" + me.wins + " &8| &cThua: &f" + me.losses), null);
            back(m, 18, p);
        }));
    }

    // ================= CỬA HÀNG =================
    private record Shop(String name, Material icon, int amount, String key, double def, int kind) {}

    private static final Shop[] SHOP = {
            new Shop("Trứng Bí Ẩn", Material.EGG, 1, "mystery-egg", 1500, 1),
            new Shop("Trứng Cổ Đại", Material.EGG, 1, "ancient-egg", 8000, 2),
            new Shop("Hạt giống x16", Material.WHEAT_SEEDS, 16, "seeds", 50, 0),
            new Shop("Lúa mì x16", Material.WHEAT, 16, "wheat", 80, 0),
            new Shop("Bánh mì x8", Material.BREAD, 8, "bread", 100, 0),
            new Shop("Cà rốt vàng x4", Material.GOLDEN_CARROT, 4, "golden-carrot", 400, 0),
            new Shop("Táo vàng x1", Material.GOLDEN_APPLE, 1, "golden-apple", 1000, 0),
    };

    public void openShop(Player p) {
        if (pl.hook() == null) { Msg.send(p, "&cServer chưa có economy (Vault) nên chưa mở cửa hàng."); return; }
        open(p, new Menu(p, 27, "&a&lCửa hàng gà", m -> {
            m.background();
            for (int i = 0; i < SHOP.length; i++) {
                final Shop s = SHOP[i];
                final double price = pl.getConfig().getDouble("shop.prices." + s.key(), s.def());
                m.set(9 + i, Ui.icon(s.icon(), "&a" + s.name(), "&7Giá: &6" + (long) price, "&8Bấm để mua"), e -> buy(p, s, price));
            }
            back(m, 22, p);
        }));
    }

    private void buy(Player p, Shop s, double price) {
        VaultHook h = pl.hook();
        if (h == null) return;
        if (!h.has(p, price)) { Msg.send(p, "&cBạn không đủ tiền (" + (long) price + ")."); return; }
        if (!h.withdraw(p, price)) { Msg.send(p, "&cGiao dịch thất bại."); return; }
        switch (s.kind()) {
            case 1 -> DaGaPlugin.give(p, Eggs.wild(0));
            case 2 -> DaGaPlugin.give(p, Eggs.wild(2));
            default -> DaGaPlugin.give(p, new ItemStack(s.icon(), s.amount()));
        }
        Msg.send(p, "&aĐã mua &f" + s.name() + " &avới giá &6" + (long) price + "&a.");
    }

    // ================= HƯỚNG DẪN =================
    public void openHelp(Player p) {
        open(p, new Menu(p, 36, "&f&lHướng dẫn chơi", m -> {
            m.background();
            m.set(10, Ui.icon(Material.BOOK, "&e1. Bắt đầu", "&7Gõ &f/daga &7để mở menu chính.", "&7Mọi thứ đều bấm chuột, không cần nhớ lệnh.", "&7Cần có gà trước: xem mục Kiếm gà.", "&7Gà mới nở/thuần được sẽ vào Chuồng gà.", "&7Chọn 1 gà làm &agà chiến &7trong Chuồng."), null);
            m.set(11, Ui.icon(Material.FISHING_ROD, "&e2. Kiếm gà", "&7- Giết quái: rơi Trứng Bí Ẩn (hiếm).", "&7- Boss rơi Trứng Cổ Đại (xịn).", "&7- Câu cá có thể ra trứng.", "&7- Rương hầm ngục có Trứng Cổ Đại.", "&7- Gà hoang ở biome tuyết/sa mạc/nấm:", "&7  cầm hạt giống riêng, chuột phải vào gà.", "&7  (tuyết: củ dền, sa mạc: dưa hấu, nấm: bí ngô)", "&7- Mua ở Cửa hàng, hoặc thắng trận."), null);
            m.set(12, Ui.icon(Material.WHEAT, "&e3. Chăm gà", "&7Vào menu Chăm sóc gà:", "&7- Cho ăn: giữ độ no (đói quá gà đánh yếu).", "&7- Tập luyện: tăng chỉ số, tốn thể lực.", "&7- Nghỉ ngơi: hồi thể lực (gần bó rơm nhiều hơn).", "&7- Vuốt ve: tâm trạng cao đánh mạnh hơn.", "&7- Chữa thương / Hồi sinh khi cần."), null);
            m.set(13, Ui.icon(Material.EGG, "&e4. Ấp trứng", "&7Vào Lò ấp, bấm vào trứng trong túi.", "&7Trứng càng hiếm ấp càng lâu (5-40 phút).", "&7Đứng gần lửa trại: ấp nhanh hơn 30%.", "&7Đêm trăng tròn: dễ ra gà đột biến.", "&7Trứng nở xong bấm để nhận gà."), null);
            m.set(14, Ui.icon(Material.GOLDEN_CARROT, "&e5. Ghép gen", "&7Chọn 2 gà trong menu Ghép gen.", "&7Cần thể lực và no từ 50, nghỉ 30 phút/lần.", "&7Nhận Trứng Gà Lai, con thừa hưởng gen bố mẹ,", "&7có thể đột biến độ hiếm, màu lông, cựa, mào."), null);
            m.set(15, Ui.icon(Material.IRON_SWORD, "&e6. Đấu", "&7Thách đấu người chơi: chọn đối thủ + mức cược.", "&7Đối thủ bấm [CHẤP NHẬN] trong chat.", "&72 gà thật xuất hiện và đánh nhau.", "&7Thắng nhận tiền (trừ thuế sàn).", "&7Muốn tập một mình: Đấu thử gà máy,", "&7không mất gì, thắng có thưởng."), null);
            m.set(16, Ui.icon(Material.TOTEM_OF_UNDYING, "&e7. Chết & hồi sinh", "&7Thua mà hết máu thì gà chết 15 phút,", "&7sau đó còn yếu 10 phút (chỉ số -20%).", "&7Hồi sinh ngay: 1 táo vàng.", "&7Thắng nhưng còn ít máu / thua nhẹ: có thể", "&7bị thương, chữa bằng 1 cà rốt vàng."), null);
            m.set(22, Ui.icon(Material.NETHER_STAR, "&e8. Mẹo", "&7- Gà Huyền Thoại có chỉ số trần cao nhất.", "&7- Luôn để gà no và còn thể lực trước khi đấu.", "&7- Chuột phải vào vật phẩm gà để gửi vào chuồng.", "&7- Chuồng đầy thì gà nở ra sẽ vào túi."), null);
            back(m, 31, p);
        }));
    }

    // ================= ADMIN =================
    public void openAdmin(Player p) {
        if (!p.hasPermission("daga.admin")) { Msg.send(p, "&cBạn không có quyền."); return; }
        open(p, new Menu(p, 54, "&4&lBảng Admin &8- tự test một mình", m -> fillAdmin(m, p)));
    }

    private void fillAdmin(Menu m, Player p) {
        m.background();
        m.set(0, Ui.icon(Material.PAPER, "&e&lLẤY GÀ (vào chuồng)"), null);
        Material[] wool = {Material.WHITE_WOOL, Material.LIME_WOOL, Material.BLUE_WOOL, Material.ORANGE_WOOL};
        for (int i = 0; i < 4; i++) {
            final Rarity r = Rarity.values()[i];
            m.set(1 + i, Ui.icon(wool[i], r.color + "Gà " + r.label, "&aBấm để lấy 1 con"), e -> {
                pl.giveChicken(p, ChickenData.create(r, rnd()));
                m.refresh();
            });
        }
        m.set(9, Ui.icon(Material.PAPER, "&e&lLẤY TRỨNG"), null);
        m.set(10, Ui.icon(Material.EGG, "&dTrứng Bí Ẩn", "&7Bonus 0"), e -> { DaGaPlugin.give(p, Eggs.wild(0)); Msg.send(p, "&aĐã nhận trứng."); });
        m.set(11, Ui.icon(Material.EGG, "&5Trứng Cổ Đại (bonus 1)", "&7Dễ ra hiếm hơn"), e -> { DaGaPlugin.give(p, Eggs.wild(1)); Msg.send(p, "&aĐã nhận trứng."); });
        m.set(12, Ui.icon(Material.EGG, "&5Trứng Cổ Đại (bonus 3)", "&7Rất dễ ra Huyền Thoại"), e -> { DaGaPlugin.give(p, Eggs.wild(3)); Msg.send(p, "&aĐã nhận trứng."); });
        m.set(13, Ui.icon(Material.CHEST, "&6Bộ đồ ăn & chữa", "&7Hạt giống, lúa mì, bánh mì,", "&7cà rốt vàng, táo vàng"), e -> {
            for (Material mat : SEEDS) DaGaPlugin.give(p, new ItemStack(mat, 32));
            DaGaPlugin.give(p, new ItemStack(Material.WHEAT, 32));
            DaGaPlugin.give(p, new ItemStack(Material.BREAD, 16));
            DaGaPlugin.give(p, new ItemStack(Material.GOLDEN_CARROT, 16));
            DaGaPlugin.give(p, new ItemStack(Material.GOLDEN_APPLE, 8));
            Msg.send(p, "&aĐã nhận bộ đồ.");
        });

        m.set(18, Ui.icon(Material.PAPER, "&e&lCÔNG CỤ TEST"), null);
        m.set(19, Ui.icon(Material.GOLDEN_APPLE, "&aHồi đầy gà đang chọn", "&7Thể lực, no, tâm trạng 100,", "&7xóa chết/yếu/thương/cooldown ghép"), e -> {
            ChickenData d = st().active(p.getUniqueId());
            if (d == null) { Msg.send(p, "&cBạn chưa có gà chiến."); return; }
            d.sta = d.maxSta; d.hunger = 100; d.mood = 100;
            d.deadUntil = 0; d.weakUntil = 0; d.injuredUntil = 0; d.breedReady = 0;
            st().save();
            Msg.send(p, "&aĐã hồi đầy " + d.name + ".");
        });
        m.set(20, Ui.icon(Material.CLOCK, "&bẤp xong ngay", "&7Mọi trứng trong lò của bạn nở luôn"), e -> {
            for (DataStore.Slot s : st().slots(p.getUniqueId())) s.ready = 0;
            st().save();
            Msg.send(p, "&aTrứng sẽ nở ngay, vào Lò ấp để nhận.");
        });
        m.set(21, Ui.icon(Material.REDSTONE, "&cXóa cooldown", "&7Tập luyện, nghỉ, đấu thử"), e -> {
            cds.keySet().removeIf(k -> k.startsWith(p.getUniqueId().toString()));
            pl.fights().clearSparCooldown(p.getUniqueId());
            Msg.send(p, "&aĐã xóa cooldown (admin vốn không bị cooldown tập/nghỉ).");
        });
        m.set(22, Ui.icon(Material.BEACON, "&eĐặt sàn đấu tại đây", "&7Gà và người chơi sẽ dịch chuyển tới sàn", "&7khi đấu, xong tự về chỗ cũ."), e -> { setArena(p); });
        m.set(23, Ui.icon(Material.BARRIER, "&cXóa sàn đấu", "&7Về mặc định: gà đấu ngay trước mặt"), e -> {
            pl.getConfig().set("arena", null);
            pl.saveConfig();
            Msg.send(p, "&aĐã xóa sàn đấu.");
        });
        m.set(24, Ui.icon(Material.COMPARATOR, "&7Nạp lại config", "&7Giống /daga reload"), e -> { pl.reloadConfig(); Msg.send(p, "&aĐã tải lại config."); });

        m.set(27, Ui.icon(Material.PAPER, "&e&lĐẤU THỬ GÀ MÁY (không giới hạn)"), null);
        for (int i = 0; i < 4; i++) {
            final Rarity r = Rarity.values()[i];
            m.set(28 + i, Ui.icon(wool[i], r.color + "Đấu gà máy " + r.label, "&7Dùng gà đang chọn của bạn"), e -> { p.closeInventory(); pl.fights().spar(p, r); });
        }

        m.set(36, Ui.icon(Material.PAPER, "&e&lTRIỆU HỒI GÀ HOANG (test thuần hóa)"), null);
        m.set(37, Ui.icon(Material.SNOWBALL, "&bGà Tuyết", "&7Hạt củ dền"), e -> spawnWild(p, "snow"));
        m.set(38, Ui.icon(Material.SAND, "&eGà Sa Mạc", "&7Hạt dưa hấu"), e -> spawnWild(p, "desert"));
        m.set(39, Ui.icon(Material.RED_MUSHROOM, "&dGà Nấm", "&7Hạt bí ngô"), e -> spawnWild(p, "mushroom"));

        back(m, 49, p);
    }

    private void spawnWild(Player p, String type) {
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
        dir.normalize().multiply(2);
        Location l = p.getLocation().add(dir);
        p.getWorld().spawn(l, Chicken.class, ch -> WorldListener.markWild(ch, type));
        Msg.send(p, "&aĐã triệu hồi gà hoang, cầm hạt giống tương ứng rồi chuột phải vào nó.");
    }

    public void setArena(Player p) {
        if (!p.hasPermission("daga.admin")) { Msg.send(p, "&cBạn không có quyền."); return; }
        Location l = p.getLocation();
        pl.getConfig().set("arena.world", l.getWorld().getName());
        pl.getConfig().set("arena.x", l.getX());
        pl.getConfig().set("arena.y", l.getY());
        pl.getConfig().set("arena.z", l.getZ());
        pl.saveConfig();
        Msg.send(p, "&aĐã đặt tâm sàn đấu tại chỗ bạn đứng. Gà đứng cách nhau 3 block theo trục X; người chơi được dịch chuyển "
                + "tới cách tâm 4 block theo X và 5 block về phía Z âm, rồi tự về sau trận. Hãy xây/dọn sàn quanh đó.");
    }
}
