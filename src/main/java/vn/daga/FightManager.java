package vn.daga;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class FightManager {
    private record Challenge(UUID from, double bet) {}

    private static final String[] VERBS = {"mổ", "đá", "quắp", "đạp cựa vào", "xông vào mổ"};

    private final DaGaPlugin plugin;
    private final Random rnd;
    private final Map<UUID, Challenge> pending = new HashMap<>();
    private final Map<UUID, Match> active = new HashMap<>();
    private final Map<UUID, Long> sparCd = new HashMap<>();

    public FightManager(DaGaPlugin plugin) {
        this.plugin = plugin;
        this.rnd = plugin.rnd();
    }

    public boolean inMatch(Player p) {
        return active.containsKey(p.getUniqueId());
    }

    public void clearSparCooldown(UUID u) {
        sparCd.remove(u);
    }

    private ChickenData chickenOf(Player p) {
        ChickenData d = plugin.store().active(p.getUniqueId());
        if (d != null) d.refresh();
        return d;
    }

    private String check(ChickenData d, String who) {
        if (d == null) return who + " chưa có gà chiến. Mở /daga → Chuồng gà để chọn.";
        if (d.dead()) return "Gà của " + who + " đã chết, còn ~" + ((d.deadUntil - System.currentTimeMillis()) / 60000L + 1) + " phút nữa hồi sinh.";
        if (d.injured()) return "Gà của " + who + " đang bị thương.";
        if (d.sta < 20) return "Gà của " + who + " quá mệt (cần ít nhất 20 thể lực).";
        if (d.hunger < 10) return "Gà của " + who + " đang quá đói.";
        return null;
    }

    private Location arenaLoc() {
        String w = plugin.getConfig().getString("arena.world");
        if (w == null || Bukkit.getWorld(w) == null) return null;
        return new Location(Bukkit.getWorld(w), plugin.getConfig().getDouble("arena.x"),
                plugin.getConfig().getDouble("arena.y"), plugin.getConfig().getDouble("arena.z"));
    }

    // ---------- đấu thử với gà máy ----------
    public void spar(Player p, Rarity r) {
        if (inMatch(p)) { Msg.send(p, "&cBạn đang trong trận."); return; }
        ChickenData ca = chickenOf(p);
        String err = check(ca, "bạn");
        if (err != null) { Msg.send(p, "&c" + err); return; }
        if (!p.hasPermission("daga.admin")) {
            long cd = plugin.getConfig().getLong("spar.cooldown-seconds", 120) * 1000L;
            long now = System.currentTimeMillis();
            long last = sparCd.getOrDefault(p.getUniqueId(), 0L);
            if (now - last < cd) { Msg.send(p, "&cĐợi thêm " + ((cd - (now - last)) / 1000 + 1) + " giây để đấu thử tiếp."); return; }
            sparCd.put(p.getUniqueId(), now);
        }
        ChickenData bot = ChickenData.create(r, rnd);
        bot.name = "Gà Máy " + r.label;
        Match m = new Match(p, null, ca, bot, 0, arenaLoc());
        active.put(p.getUniqueId(), m);
        m.start();
    }

    // ---------- thách đấu ----------
    public void challenge(Player from, Player to, double bet) {
        if (from.equals(to)) { Msg.send(from, "&cKhông thể tự thách đấu chính mình."); return; }
        if (!to.isOnline()) { Msg.send(from, "&cNgười đó đã thoát game."); return; }
        if (inMatch(from) || inMatch(to)) { Msg.send(from, "&cMột trong hai người đang trong trận."); return; }
        String err = check(chickenOf(from), "bạn");
        if (err != null) { Msg.send(from, "&c" + err); return; }
        if (bet > 0) {
            VaultHook h = plugin.hook();
            if (h == null) { Msg.send(from, "&cServer chưa có economy (Vault), chỉ đấu không cược được."); return; }
            double min = plugin.getConfig().getDouble("bet.min", 100);
            double max = plugin.getConfig().getDouble("bet.max", 1000000);
            if (bet < min || bet > max) { Msg.send(from, "&cTiền cược phải từ " + min + " đến " + max + "."); return; }
            if (!h.has(from, bet)) { Msg.send(from, "&cBạn không đủ tiền cược."); return; }
        }
        Challenge ch = new Challenge(from.getUniqueId(), bet);
        pending.put(to.getUniqueId(), ch);
        Msg.send(from, "&eĐã gửi thách đấu tới &f" + to.getName() + (bet > 0 ? " &evới cược &6" + bet : "") + "&e.");
        to.sendMessage(Msg.c(Msg.PREFIX + "&f" + from.getName() + " &ethách đấu gà" + (bet > 0 ? " với cược &6" + bet : "") + "&e! ")
                .append(Msg.c("&a&l[CHẤP NHẬN]").clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/daga chapnhan")))
                .append(Msg.c(" "))
                .append(Msg.c("&c&l[TỪ CHỐI]").clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/daga tuchoi"))));
        long timeout = plugin.getConfig().getLong("challenge-timeout-seconds", 30) * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pending.get(to.getUniqueId()) == ch) {
                pending.remove(to.getUniqueId());
                Msg.send(from, "&7Lời thách đấu tới " + to.getName() + " đã hết hạn.");
            }
        }, timeout);
    }

    public void decline(Player b) {
        Challenge ch = pending.remove(b.getUniqueId());
        if (ch == null) { Msg.send(b, "&cBạn không có lời thách đấu nào."); return; }
        Player a = Bukkit.getPlayer(ch.from());
        if (a != null) Msg.send(a, "&f" + b.getName() + " &cđã từ chối thách đấu.");
        Msg.send(b, "&7Đã từ chối.");
    }

    public void accept(Player b) {
        Challenge ch = pending.remove(b.getUniqueId());
        if (ch == null) { Msg.send(b, "&cBạn không có lời thách đấu nào."); return; }
        Player a = Bukkit.getPlayer(ch.from());
        if (a == null || !a.isOnline()) { Msg.send(b, "&cNgười thách đấu đã thoát game."); return; }
        if (inMatch(a) || inMatch(b)) { Msg.send(b, "&cMột trong hai người đang trong trận."); return; }
        Location arena = arenaLoc();
        if (arena == null && (!a.getWorld().equals(b.getWorld()) || a.getLocation().distance(b.getLocation()) > 24)) {
            Msg.send(b, "&cHai người phải đứng gần nhau (trong 24 block) để gà có chỗ đấu.");
            Msg.send(a, "&cHai người phải đứng gần nhau (trong 24 block) để gà có chỗ đấu.");
            return;
        }
        ChickenData ca = chickenOf(a), cb = chickenOf(b);
        String err = check(ca, a.getName());
        if (err == null) err = check(cb, "bạn");
        if (err != null) { Msg.send(b, "&c" + err); Msg.send(a, "&c" + err); return; }
        double bet = ch.bet();
        if (bet > 0) {
            VaultHook h = plugin.hook();
            if (h == null || !h.has(a, bet) || !h.has(b, bet)) {
                Msg.send(b, "&cMột trong hai người không đủ tiền cược."); Msg.send(a, "&cMột trong hai người không đủ tiền cược.");
                return;
            }
            if (!h.withdraw(a, bet)) { Msg.send(b, "&cKhông trừ được tiền của " + a.getName()); return; }
            if (!h.withdraw(b, bet)) { h.deposit(a, bet); Msg.send(b, "&cKhông trừ được tiền của bạn."); return; }
        }
        Match m = new Match(a, b, ca, cb, bet, arena);
        active.put(a.getUniqueId(), m);
        active.put(b.getUniqueId(), m);
        m.start();
    }

    public void onQuit(Player q) {
        pending.remove(q.getUniqueId());
        pending.values().removeIf(c -> c.from().equals(q.getUniqueId()));
        Match m = active.get(q.getUniqueId());
        if (m != null) m.finish(m.p[0].equals(q) ? 1 : 0, true);
    }

    public void shutdown() {
        Set<Match> ms = new HashSet<>(active.values());
        for (Match m : ms) m.abort();
        active.clear();
    }

    private Location ground(Location l) {
        World w = l.getWorld();
        int bx = l.getBlockX(), bz = l.getBlockZ(), y = l.getBlockY();
        for (int dy = 3; dy >= -4; dy--) {
            int yy = y + dy;
            if (w.getBlockAt(bx, yy, bz).isPassable() && w.getBlockAt(bx, yy + 1, bz).isPassable()
                    && !w.getBlockAt(bx, yy - 1, bz).isPassable()) {
                return new Location(w, l.getX(), yy, l.getZ());
            }
        }
        return l.clone();
    }

    private class Match {
        final Player[] p = new Player[2];   // p[1] == null nghĩa là đấu với gà máy
        final ChickenData[] c = new ChickenData[2];
        final double[] hp = new double[2];
        final double[] max = new double[2];
        final Chicken[] ents = new Chicken[2];
        final Location[] spots = new Location[2];
        final Location[] view = new Location[2];
        final Location[] back = new Location[2];
        final double bet;
        final boolean arenaMode;
        final boolean spar;
        int round = 0;
        boolean over = false;
        BukkitTask task;

        Match(Player a, Player b, ChickenData ca, ChickenData cb, double bet, Location arena) {
            p[0] = a; p[1] = b; c[0] = ca; c[1] = cb;
            this.bet = bet;
            this.spar = b == null;
            this.arenaMode = arena != null;
            for (int i = 0; i < 2; i++) { max[i] = c[i].fightHp(); hp[i] = max[i]; }
            if (arenaMode) {
                Location ctr = arena.clone();
                spots[0] = ctr.clone().add(-1.5, 0, 0);
                spots[1] = ctr.clone().add(1.5, 0, 0);
                view[0] = ctr.clone().add(-4, 0, -5);
                view[1] = ctr.clone().add(4, 0, -5);
                for (int i = 0; i < 2; i++) view[i].setDirection(ctr.toVector().subtract(view[i].toVector()));
            } else {
                Player ref = b != null ? b : a;
                Vector dir = ref.getLocation().getDirection().setY(0);
                if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
                dir.normalize();
                Location ctr = ref.getLocation().add(dir.clone().multiply(5));
                Vector perp = new Vector(-dir.getZ(), 0, dir.getX());
                spots[0] = ground(ctr.clone().add(perp.clone().multiply(-1.5)));
                spots[1] = ground(ctr.clone().add(perp.clone().multiply(1.5)));
            }
            Vector face = spots[1].toVector().subtract(spots[0].toVector()).setY(0);
            spots[0].setDirection(face);
            spots[1].setDirection(face.clone().multiply(-1));
        }

        void say(String m) {
            for (Player x : p) if (x != null) Msg.raw(x, m);
        }

        void sound(Sound s, float vol, float pitch) {
            for (Player x : p) if (x != null) x.playSound(x.getLocation(), s, vol, pitch);
        }

        void setup(Chicken ch, int i) {
            ch.setAI(false);
            ch.setInvulnerable(true);
            ch.setPersistent(false);
            ch.setRemoveWhenFarAway(true);
            ch.setCollidable(false);
            ch.setAdult();
            ch.setCustomNameVisible(true);
            ch.setGlowing(c[i].rarity == Rarity.HUYEN_THOAI);
            ch.getPersistentDataContainer().set(DaGaPlugin.FIGHT_KEY, PersistentDataType.BYTE, (byte) 1);
        }

        void spawnChickens() {
            for (int i = 0; i < 2; i++) {
                final int idx = i;
                try {
                    ents[i] = spots[i].getWorld().spawn(spots[i], Chicken.class, ch -> setup(ch, idx));
                } catch (Exception ex) {
                    ents[i] = null;
                }
            }
            if (!valid(0) || !valid(1)) {
                removeEnts();
                say("&7(Không triệu hồi được gà thật ở khu vực này, trận đấu diễn ra bằng chữ.)");
            } else {
                updateTag(0);
                updateTag(1);
            }
        }

        boolean valid(int i) {
            return ents[i] != null && ents[i].isValid();
        }

        void removeEnts() {
            for (int i = 0; i < 2; i++) {
                if (ents[i] != null && ents[i].isValid()) ents[i].remove();
                ents[i] = null;
            }
        }

        void returnPlayers() {
            if (!arenaMode) return;
            for (int i = 0; i < 2; i++) {
                if (back[i] != null && p[i] != null && p[i].isOnline()) p[i].teleport(back[i]);
            }
        }

        void updateTag(int i) {
            if (!valid(i)) return;
            ents[i].customName(Msg.c(c[i].rarity.color + c[i].name + " " + bar(i)));
        }

        void start() {
            if (arenaMode) {
                for (int i = 0; i < 2; i++) {
                    if (p[i] == null) continue;
                    back[i] = p[i].getLocation();
                    p[i].teleport(view[i]);
                }
            }
            spawnChickens();
            say("&6&l=== " + (spar ? "ĐẤU THỬ VỚI GÀ MÁY" : "TRẬN ĐÁ GÀ") + " ===");
            say(p[0].getName() + " &7(" + c[0].rarity.color + c[0].name + "&7) &cVS &f"
                    + (spar ? "" : p[1].getName() + " ") + "&7(" + c[1].rarity.color + c[1].name + "&7)");
            if (bet > 0) say("&7Tiền cược mỗi bên: &6" + bet);
            if (spar) say("&7Đấu thử: gà không chết, không bị thương, chỉ tốn thể lực.");
            sound(Sound.ENTITY_CHICKEN_AMBIENT, 1f, 0.8f);
            long delay = Math.max(40L, plugin.getConfig().getLong("fight.round-delay-ticks", 50L));
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, delay);
        }

        void tick() {
            if (over) return;
            for (Player x : p) {
                if (x != null && !x.isOnline()) { finish(x.equals(p[0]) ? 1 : 0, true); return; }
            }
            round++;
            say("&8—— &eHiệp " + round + " &8——");
            double sa = c[0].spd() * (0.8 + rnd.nextDouble() * 0.4);
            double sb = c[1].spd() * (0.8 + rnd.nextDouble() * 0.4);
            final int first = sa >= sb ? 0 : 1;
            strike(first);
            if (checkDeath()) return;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (over) return;
                strike(1 - first);
                if (checkDeath()) return;
                say("&7" + c[0].name + " " + bar(0) + " &8| &7" + c[1].name + " " + bar(1));
                if (round >= plugin.getConfig().getInt("fight.max-rounds", 30)) {
                    finish(hp[0] / max[0] >= hp[1] / max[1] ? 0 : 1, false);
                }
            }, 20L);
        }

        boolean checkDeath() {
            if (hp[0] <= 0 || hp[1] <= 0) {
                say("&7" + c[0].name + " " + bar(0) + " &8| &7" + c[1].name + " " + bar(1));
                finish(hp[0] <= 0 ? 1 : 0, false);
                return true;
            }
            return false;
        }

        void lunge(int i) {
            if (!valid(i) || !valid(1 - i)) return;
            final Chicken a = ents[i];
            Location dl = ents[1 - i].getLocation();
            Vector dir = dl.toVector().subtract(spots[i].toVector()).setY(0);
            if (dir.lengthSquared() < 0.01) return;
            dir.normalize();
            Location hit = dl.clone().subtract(dir.clone().multiply(0.9));
            hit.setDirection(dir);
            a.teleport(hit);
            final Location home = spots[i].clone();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (a.isValid()) a.teleport(home);
            }, 8L);
        }

        void hurtFx(int j, boolean crit) {
            if (!valid(j)) return;
            ents[j].playHurtAnimation(0f);
            Location at = ents[j].getLocation().add(0, 0.5, 0);
            ents[j].getWorld().spawnParticle(crit ? Particle.DAMAGE_INDICATOR : Particle.CRIT, at, 12, 0.2, 0.2, 0.2, 0.1);
        }

        void strike(int i) {
            int j = 1 - i;
            ChickenData at = c[i], df = c[j];
            double dodge = clamp(0.06 + (df.spd() - at.spd()) * 0.008 + df.luck() * 0.002, 0.03, 0.35);
            if (rnd.nextDouble() < dodge) {
                lunge(i);
                if (valid(j)) ents[j].getWorld().spawnParticle(Particle.CLOUD, ents[j].getLocation().add(0, 0.3, 0), 8, 0.2, 0.1, 0.2, 0.02);
                say("&e" + at.name + " &7ra đòn trượt, &e" + df.name + " &7né được!");
                return;
            }
            double dmg = at.effAtk() * (0.8 + rnd.nextDouble() * 0.4) - df.effDef() * 0.4;
            boolean crit = rnd.nextDouble() < clamp(0.05 + at.luck() * 0.004, 0.05, 0.4);
            if (crit) dmg *= 1.6;
            long d = Math.max(1, Math.round(dmg));
            hp[j] = Math.max(0, hp[j] - d);
            lunge(i);
            hurtFx(j, crit);
            updateTag(j);
            say("&e" + at.name + " &7" + VERBS[rnd.nextInt(VERBS.length)] + " &e" + df.name + " &c-" + d + (crit ? " &6&lCHÍ MẠNG!" : ""));
            sound(Sound.ENTITY_CHICKEN_HURT, 1f, crit ? 0.7f : 1.1f);
        }

        String bar(int i) {
            int filled = (int) Math.round(10 * hp[i] / max[i]);
            StringBuilder sb = new StringBuilder("&a");
            for (int k = 0; k < 10; k++) {
                if (k == filled) sb.append("&8");
                sb.append("█");
            }
            return sb + " &f" + (int) hp[i];
        }

        void kill(int i) {
            if (!valid(i)) return;
            Chicken e = ents[i];
            e.setInvulnerable(false);
            e.getWorld().spawnParticle(Particle.CLOUD, e.getLocation().add(0, 0.4, 0), 25, 0.3, 0.3, 0.3, 0.05);
            e.damage(1000);
        }

        void cleanupLater() {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                removeEnts();
                returnPlayers();
            }, 60L);
        }

        void finish(int w, boolean forfeit) {
            if (over) return;
            over = true;
            if (task != null) task.cancel();
            active.remove(p[0].getUniqueId());
            if (p[1] != null) active.remove(p[1].getUniqueId());
            int l = 1 - w;
            if (spar) { finishSpar(w, forfeit); return; }
            boolean died = hp[l] <= 0 || forfeit;
            double prize = 0;
            VaultHook h = plugin.hook();
            if (bet > 0 && h != null) {
                double pot = bet * 2;
                double tax = pot * plugin.getConfig().getDouble("tax-percent", 5) / 100.0;
                prize = pot - tax;
                h.deposit(p[w], prize);
            }
            say("&6&l★ " + p[w].getName() + " THẮNG" + (forfeit ? " &7(đối thủ bỏ cuộc)" : "") + " &6&l★");
            if (died) say("&4☠ &c" + c[l].name + " đã gục ngã!");
            if (prize > 0) Msg.raw(p[w], Msg.PREFIX + "&aBạn nhận được &6" + prize + " &7(đã trừ thuế sàn).");
            if (died) kill(l);
            if (valid(w)) {
                ents[w].getWorld().spawnParticle(Particle.HEART, ents[w].getLocation().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0);
            }
            apply(p[w], c[w], true, false, hp[w] / max[w]);
            apply(p[l], c[l], false, died, hp[l] / max[l]);
            plugin.store().save();
            plugin.store().addResult(p[w].getUniqueId(), p[w].getName(), true, prize);
            plugin.store().addResult(p[l].getUniqueId(), p[l].getName(), false, 0);
            p[w].playSound(p[w].getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            if (rnd.nextDouble() < plugin.getConfig().getDouble("win-egg-chance", 0.08)) {
                DaGaPlugin.give(p[w], Eggs.wild(1));
                Msg.send(p[w], "&5Phần thưởng hiếm: bạn nhận được 1 Trứng Cổ Đại!");
            }
            cleanupLater();
        }

        void finishSpar(int w, boolean forfeit) {
            Player pl = p[0];
            ChickenData d = c[0];
            boolean win = w == 0;
            say(win ? "&6&l★ BẠN THẮNG GÀ MÁY ★" : "&c&lBạn thua gà máy" + (forfeit ? " (bỏ cuộc)" : "") + " &7- không mất gì, thử lại nhé!");
            if (win) kill(1); else kill(0);
            d.refresh();
            d.sta = Math.max(0, d.sta - 20);
            d.hunger = Math.max(0, d.hunger - 5);
            d.mood = Math.max(0, Math.min(100, d.mood + (win ? 5 : -3)));
            if (win && !forfeit) {
                if (rnd.nextDouble() < 0.35) {
                    int s = rnd.nextInt(5);
                    if (d.train(s)) Msg.send(pl, "&dRút được kinh nghiệm! &f" + ChickenData.STAT_NAMES[s] + " &dtăng lên &f" + d.st[s]);
                }
                if (rnd.nextDouble() < 0.25) {
                    DaGaPlugin.give(pl, new ItemStack(Material.WHEAT_SEEDS, 3));
                    Msg.send(pl, "&aNhặt được 3 hạt giống từ gà máy.");
                }
            }
            plugin.store().save();
            cleanupLater();
        }

        void abort() {
            if (over) return;
            over = true;
            if (task != null) task.cancel();
            if (bet > 0 && plugin.hook() != null && p[1] != null) {
                plugin.hook().deposit(p[0], bet);
                plugin.hook().deposit(p[1], bet);
            }
            say("&cTrận đấu bị hủy do server dừng" + (bet > 0 ? ", đã hoàn tiền cược." : "."));
            removeEnts();
            returnPlayers();
        }
    }

    private void apply(Player pl, ChickenData d, boolean win, boolean died, double hpPct) {
        d.refresh();
        long now = System.currentTimeMillis();
        d.sta = Math.max(0, d.sta - (win ? 15 : 35));
        d.hunger = Math.max(0, d.hunger - 10);
        if (win) {
            d.wins++;
            d.mood = Math.min(100, d.mood + 10);
            if (hpPct < 0.25) {
                d.injuredUntil = now + plugin.getConfig().getLong("injury.minutes", 10) * 60000L;
                Msg.send(pl, "&cGà thắng nhưng bị thương nặng! Chữa trong menu Chăm sóc (cần 1 cà rốt vàng).");
            }
        } else {
            d.losses++;
            d.mood = Math.max(0, d.mood - 15);
            if (died) {
                long respawn = plugin.getConfig().getLong("death.respawn-minutes", 15);
                long weak = plugin.getConfig().getLong("death.weak-minutes", 10);
                d.deadUntil = now + respawn * 60000L;
                d.weakUntil = d.deadUntil + weak * 60000L;
                d.injuredUntil = 0;
                Msg.send(pl, "&4☠ &cGà của bạn đã chết! Hồi sinh sau &f" + respawn + " &cphút, sau đó còn yếu &f" + weak
                        + " &cphút (chỉ số giảm 20%). Hồi sinh ngay trong menu Chăm sóc (1 táo vàng).");
            } else if (rnd.nextDouble() < plugin.getConfig().getDouble("injury.chance", 0.35)) {
                d.injuredUntil = now + plugin.getConfig().getLong("injury.minutes", 10) * 60000L;
                Msg.send(pl, "&cGà của bạn bị thương! Chữa trong menu Chăm sóc (1 cà rốt vàng).");
            }
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
