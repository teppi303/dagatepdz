package vn.daga;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

/** Gà đồng hành (pet): đi theo chủ, có cấp độ và kỹ năng. */
public class Companions {
    public static final int[] PERK_LV = {2, 4, 6, 8, 10, 15};
    public static final String[] PERK_TXT = {
            "Bới đất ra hạt giống cho bạn",
            "Giảm 50% sát thương rơi của bạn",
            "Mổ quái xung quanh giúp bạn",
            "Khi bới đất có thể ra Trứng Bí Ẩn",
            "Miễn nhiễm sát thương rơi",
            "Hào quang may mắn + mổ quái mạnh gấp đôi"
    };
    private static final Material[] SEEDS = {Material.WHEAT_SEEDS, Material.BEETROOT_SEEDS, Material.MELON_SEEDS, Material.PUMPKIN_SEEDS};

    private final DaGaPlugin pl;
    private final Random rnd;
    private final Map<UUID, Chicken> out = new HashMap<>();
    private final Map<UUID, String> ids = new HashMap<>();
    private final Map<UUID, Long> nextDig = new HashMap<>();
    private final Map<UUID, Long> nextPeck = new HashMap<>();
    private final Map<UUID, Long> lastPet = new HashMap<>();
    private final Map<UUID, Location> lastPos = new HashMap<>();
    private final Set<UUID> paused = new HashSet<>();
    private final BukkitTask task;
    private int ticks = 0;

    public Companions(DaGaPlugin pl) {
        this.pl = pl;
        this.rnd = pl.rnd();
        this.task = Bukkit.getScheduler().runTaskTimer(pl, this::tick, 20L, 10L);
    }

    public static String perkAt(int level) {
        for (int i = 0; i < PERK_LV.length; i++) if (PERK_LV[i] == level) return PERK_TXT[i];
        return null;
    }

    public boolean isOut(Player p) {
        Chicken c = out.get(p.getUniqueId());
        return c != null && c.isValid();
    }

    public Chicken get(UUID owner) {
        Chicken c = out.get(owner);
        return c != null && c.isValid() ? c : null;
    }

    public UUID ownerOf(Entity e) {
        String s = e.getPersistentDataContainer().get(DaGaPlugin.COMPANION_KEY, PersistentDataType.STRING);
        if (s == null) return null;
        try { return UUID.fromString(s); } catch (IllegalArgumentException ex) { return null; }
    }

    private String label(ChickenData d, Player owner) {
        return d.rarity.color + d.name + " &eLv." + d.level + (d.hunger < 30 ? " &c(đói)" : "") + " &8[&7" + owner.getName() + "&8]";
    }

    private long digMs() {
        return pl.getConfig().getLong("companion.dig-minutes", 3) * 60000L;
    }

    public void toggle(Player p) {
        if (isOut(p)) {
            dismiss(p.getUniqueId());
            Msg.send(p, "&7Gà đã về nghỉ.");
        } else if (summon(p)) {
            Msg.send(p, "&aGà đã đi theo bạn! &7Chuột phải tay không: vuốt ve. Cầm hạt/lúa/bánh: cho ăn. "
                    + "Shift + chuột phải: chăm sóc. Shift + chuột phải vào gà khác: đấu để lên cấp.");
        }
    }

    public boolean summon(Player p) {
        if (isOut(p)) return true;
        if (pl.fights().inMatch(p)) { Msg.send(p, "&cBạn đang trong trận."); return false; }
        final ChickenData d = pl.store().active(p.getUniqueId());
        if (d == null) { Msg.send(p, "&cBạn chưa có gà chiến. Chọn gà trong Chuồng gà trước."); return false; }
        d.refresh();
        if (d.dead()) { Msg.send(p, "&cGà đã chết, chưa thể đi theo bạn. Hồi sinh trong menu Chăm sóc."); return false; }
        try {
            Chicken ch = p.getWorld().spawn(p.getLocation(), Chicken.class, c -> {
                c.setInvulnerable(true);
                c.setPersistent(false);
                c.setRemoveWhenFarAway(false);
                c.setCollidable(false);
                c.setAdult();
                c.setCustomNameVisible(true);
                c.setGlowing(d.rarity == Rarity.HUYEN_THOAI);
                c.customName(Msg.c(label(d, p)));
                c.getPersistentDataContainer().set(DaGaPlugin.COMPANION_KEY, PersistentDataType.STRING, p.getUniqueId().toString());
            });
            if (!ch.isValid()) {
                Msg.send(p, "&cKhông triệu hồi được gà ở đây (có thể plugin khác đang chặn spawn gà).");
                return false;
            }
            out.put(p.getUniqueId(), ch);
            ids.put(p.getUniqueId(), d.id);
            nextDig.put(p.getUniqueId(), System.currentTimeMillis() + digMs());
            lastPos.put(p.getUniqueId(), p.getLocation().clone());
            return true;
        } catch (Exception ex) {
            Msg.send(p, "&cKhông triệu hồi được gà ở đây (có thể plugin khác đang chặn spawn gà).");
            return false;
        }
    }

    public void dismiss(UUID u) {
        Chicken c = out.remove(u);
        ids.remove(u);
        nextDig.remove(u);
        lastPos.remove(u);
        if (c != null && c.isValid()) c.remove();
    }

    /** Cho gà đồng hành về nếu đó đúng là con gà có id này (ví dụ gà vừa chết). */
    public void dismissIfChicken(UUID owner, String chickenId) {
        if (chickenId != null && chickenId.equals(ids.get(owner))) dismiss(owner);
    }

    public void refresh(Player p) {
        if (isOut(p)) {
            dismiss(p.getUniqueId());
            summon(p);
        }
    }

    public void pause(Player p) {
        if (isOut(p)) {
            paused.add(p.getUniqueId());
            dismiss(p.getUniqueId());
        }
    }

    public void resume(Player p) {
        if (paused.remove(p.getUniqueId()) && p.isOnline()) summon(p);
    }

    public void forget(Player p) {
        paused.remove(p.getUniqueId());
        dismiss(p.getUniqueId());
        nextPeck.remove(p.getUniqueId());
        lastPet.remove(p.getUniqueId());
    }

    public void dismissAll() {
        task.cancel();
        for (UUID u : new ArrayList<>(out.keySet())) dismiss(u);
    }

    // ----- hệ số giảm sát thương rơi nhờ gà -----
    public double fallFactor(Player p) {
        if (!isOut(p)) return 1.0;
        ChickenData d = pl.store().active(p.getUniqueId());
        if (d == null || d.hunger < 30) return 1.0;
        return d.level >= 10 ? 0.0 : d.level >= 4 ? 0.5 : 1.0;
    }

    // ----- chuột phải vào gà của mình: cho ăn nếu cầm thức ăn, không thì vuốt ve -----
    private int foodValue(Material m) {
        return switch (m) {
            case WHEAT_SEEDS, BEETROOT_SEEDS, MELON_SEEDS, PUMPKIN_SEEDS -> 10;
            case WHEAT, BREAD -> 25;
            case GOLDEN_CARROT -> 35;
            default -> 0;
        };
    }

    public void interact(Player p, Chicken c) {
        if (pl.fights().inMatch(p)) return;
        ChickenData d = pl.store().active(p.getUniqueId());
        if (d == null) return;
        d.refresh();
        ItemStack hand = p.getInventory().getItemInMainHand();
        int food = hand == null ? 0 : foodValue(hand.getType());
        Location head = c.getLocation().add(0, 0.8, 0);
        if (food > 0) {
            if (d.hunger >= 100) { p.sendActionBar(Msg.c("&e" + d.name + " đã no căng rồi!")); return; }
            boolean hungry = d.hunger < 70;
            if (hand.getAmount() <= 1) p.getInventory().setItemInMainHand(null);
            else hand.setAmount(hand.getAmount() - 1);
            d.hunger = Math.min(100, d.hunger + food);
            d.starveSince = 0;
            d.mood = Math.min(100, d.mood + 2);
            if (hungry) pl.giveXp(p, d, 1);
            pl.store().save();
            c.getWorld().spawnParticle(Particle.HEART, head, 2, 0.2, 0.1, 0.2, 0);
            c.getWorld().playSound(c.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.8f, 1.3f);
            c.customName(Msg.c(label(d, p)));
            p.sendActionBar(Msg.c("&a" + d.name + " ăn ngon lành! Độ no: &f" + d.hunger + "/100"));
            return;
        }
        // vuốt ve: hồi chiêu dài, hiệu quả nhỏ, không có kinh nghiệm
        long now = System.currentTimeMillis();
        long cd = pl.getConfig().getLong("pet.cooldown-seconds", 45) * 1000L;
        long left = cd - (now - lastPet.getOrDefault(p.getUniqueId(), 0L));
        if (left > 0) {
            p.sendActionBar(Msg.c("&7" + d.name + " đang thư giãn, đợi &f" + (left / 1000 + 1) + "s &7nữa nhé."));
            return;
        }
        lastPet.put(p.getUniqueId(), now);
        d.mood = Math.min(100, d.mood + 4);
        pl.store().save();
        c.getWorld().spawnParticle(Particle.HEART, head, 3, 0.3, 0.2, 0.3, 0);
        c.getWorld().playSound(c.getLocation(), Sound.ENTITY_CHICKEN_AMBIENT, 1f, 1.5f);
        p.sendActionBar(Msg.c("&d" + d.name + " thích thú! Tâm trạng: &f" + d.mood + "/100"));
    }

    // ----- vòng lặp chính -----
    private void tick() {
        ticks++;
        long now = System.currentTimeMillis();
        double follow = pl.getConfig().getDouble("companion.follow-distance", 5);
        double tp = pl.getConfig().getDouble("companion.teleport-distance", 16);
        for (UUID u : new ArrayList<>(out.keySet())) {
            Player owner = Bukkit.getPlayer(u);
            Chicken c = out.get(u);
            if (owner == null || !owner.isOnline() || c == null || !c.isValid()) { dismiss(u); continue; }
            ChickenData d = pl.store().active(u);
            if (d == null || d.dead() || !d.id.equals(ids.get(u))) {
                dismiss(u);
                Msg.send(owner, "&7Gà đồng hành đã về chuồng.");
                continue;
            }
            if (pl.fights().inMatch(owner)) continue;
            boolean healthy = d.hunger >= 30;

            // mổ quái giúp chủ
            Monster target = null;
            if (d.level >= 6 && healthy && d.sta >= 10) {
                double best = 1e9;
                for (Entity en : c.getNearbyEntities(7, 4, 7)) {
                    if (en instanceof Monster mo && !mo.isDead() && mo.getWorld().equals(owner.getWorld())
                            && mo.getLocation().distance(owner.getLocation()) < 12) {
                        double dd = mo.getLocation().distance(c.getLocation());
                        if (dd < best) { best = dd; target = mo; }
                    }
                }
                if (target != null) {
                    if (best <= 2.5) {
                        if (now >= nextPeck.getOrDefault(u, 0L)) {
                            nextPeck.put(u, now + 1500L);
                            double dmg = Math.max(1.0, d.effAtk() * 0.25 * (d.level >= 15 ? 2 : 1));
                            target.damage(dmg, c);
                            d.sta = Math.max(0, d.sta - 1);
                            c.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 8, 0.2, 0.2, 0.2, 0.1);
                            c.getWorld().playSound(c.getLocation(), Sound.ENTITY_CHICKEN_HURT, 0.8f, 1.4f);
                            c.setVelocity(new Vector(0, 0.3, 0));
                            if (rnd.nextDouble() < 0.5) pl.giveXp(owner, d, 1);
                        }
                    } else {
                        c.getPathfinder().moveTo(target.getLocation(), 1.6);
                    }
                }
            }

            // bám theo chủ
            double dist = c.getWorld().equals(owner.getWorld()) ? c.getLocation().distance(owner.getLocation()) : 1e9;
            if (dist > tp) c.teleport(owner.getLocation());
            else if (target == null && dist > follow) c.getPathfinder().moveTo(owner.getLocation(), 1.3);

            // bới đất
            if (d.level >= 2 && healthy && d.mood >= 40) {
                long nd = nextDig.getOrDefault(u, 0L);
                if (nd > 0 && now >= nd) {
                    nextDig.put(u, now + digMs());
                    dig(owner, c, d);
                }
            }

            // hào quang may mắn
            if (d.level >= 15 && healthy && ticks % 4 == 0) {
                owner.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 100, 0, true, false, false));
            }

            // hiệu ứng vui
            if (ticks % 30 == 0 && d.mood >= 90) {
                c.getWorld().spawnParticle(Particle.HEART, c.getLocation().add(0, 0.9, 0), 1, 0.2, 0.1, 0.2, 0);
            }
            if (d.rarity == Rarity.HUYEN_THOAI && ticks % 2 == 0) {
                c.getWorld().spawnParticle(Particle.END_ROD, c.getLocation().add(0, 0.6, 0), 1, 0.2, 0.2, 0.2, 0.01);
            }

            // kêu đói
            if (ticks % 120 == 0 && d.hunger < 30) {
                owner.sendActionBar(Msg.c("&c" + d.name + " đang đói bụng, cho nó ăn đi!"));
                c.getWorld().playSound(c.getLocation(), Sound.ENTITY_CHICKEN_HURT, 0.8f, 1.6f);
            }

            if (ticks % 5 == 0) {
                d.refresh();
                c.customName(Msg.c(label(d, owner)));
            }
            if (ticks % 600 == 0) {
                Location lp = lastPos.get(u);
                Location cur = owner.getLocation();
                if (lp != null && lp.getWorld() != null && lp.getWorld().equals(cur.getWorld()) && lp.distance(cur) >= 30) {
                    d.mood = Math.min(100, d.mood + 2);
                    pl.giveXp(owner, d, 3);
                }
                lastPos.put(u, cur.clone());
                pl.store().save();
            }
        }
    }

    private void dig(Player owner, Chicken c, ChickenData d) {
        Location l = c.getLocation();
        Material seed = SEEDS[rnd.nextInt(SEEDS.length)];
        int n = 1 + rnd.nextInt(3);
        l.getWorld().dropItemNaturally(l, new ItemStack(seed, n));
        String extra = "";
        double eggChance = d.level >= 8 ? 0.03 + (d.level - 8) * 0.005 : 0;
        if (rnd.nextDouble() < eggChance) {
            l.getWorld().dropItemNaturally(l, Eggs.wild(0));
            extra = " &d+ 1 Trứng Bí Ẩn!";
        }
        l.getWorld().spawnParticle(Particle.CLOUD, l.clone().add(0, 0.2, 0), 8, 0.3, 0.1, 0.3, 0.02);
        owner.sendActionBar(Msg.c("&e" + d.name + " &7bới đất được &f" + n + " hạt giống" + extra));
        pl.giveXp(owner, d, 2);
        pl.store().save();
    }
}
