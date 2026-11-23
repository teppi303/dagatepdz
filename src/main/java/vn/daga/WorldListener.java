package vn.daga;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class WorldListener implements Listener {
    private final DaGaPlugin pl;
    private final Random rnd;
    private final Map<UUID, Long> lastMobEgg = new HashMap<>();
    private final Map<UUID, Long> lastFishEgg = new HashMap<>();

    public WorldListener(DaGaPlugin pl) {
        this.pl = pl;
        this.rnd = pl.rnd();
    }

    private boolean isOurs(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        var pdc = it.getItemMeta().getPersistentDataContainer();
        return pdc.has(ChickenData.KEY, PersistentDataType.STRING) || pdc.has(Eggs.KEY, PersistentDataType.STRING);
    }

    // ===== vật phẩm gà / trứng của plugin =====
    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it == null || !it.hasItemMeta()) return;
        var pdc = it.getItemMeta().getPersistentDataContainer();
        boolean chicken = pdc.has(ChickenData.KEY, PersistentDataType.STRING);
        boolean egg = pdc.has(Eggs.KEY, PersistentDataType.STRING);
        if (!chicken && !egg) return;
        // không cho đặt gà / ném trứng, nhưng vẫn mở được rương, cửa... khi bấm vào khối
        e.setUseItemInHand(Event.Result.DENY);
        Player p = e.getPlayer();
        Block b = e.getClickedBlock();
        if (b != null && b.getType().isInteractable() && !p.isSneaking()) return;
        if (egg) {
            p.sendActionBar(Msg.c("&7Mang trứng vào Lò ấp: gõ &e/daga ap"));
            return;
        }
        ChickenData d = ChickenData.fromItem(it);
        if (d == null) return;
        if (pl.store().addChicken(p.getUniqueId(), d)) {
            if (e.getHand() == EquipmentSlot.OFF_HAND) p.getInventory().setItemInOffHand(null);
            else p.getInventory().setItemInMainHand(null);
            Msg.send(p, "&aĐã gửi &f" + d.name + " &avào chuồng gà. Mở menu: &e/daga");
        } else {
            Msg.send(p, "&cChuồng gà đã đầy!");
        }
    }

    // máy bắn không được bắn trứng / trứng gà của plugin ra
    @EventHandler
    public void onDispense(BlockDispenseEvent e) {
        if (isOurs(e.getItem())) e.setCancelled(true);
    }

    // ===== người chơi vào / ra =====
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        // thời gian offline không làm gà đói hay buồn
        for (ChickenData d : pl.store().coop(p.getUniqueId())) {
            d.hungerStamp = now;
            d.moodStamp = now;
        }
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (p.isOnline()) pl.careWatch().onJoin(p);
        }, 40L);
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline()) return;
            for (ChickenData d : pl.store().coop(p.getUniqueId())) {
                if (!d.grave() && d.hunger < 20) {
                    Msg.send(p, "&c⚠ &f" + d.name + " &cđang đói (no " + d.hunger + "/100). Cho ăn trong menu &e/daga &c→ Chăm sóc gà.");
                }
            }
        }, 80L);
        if (pl.store().coop(p.getUniqueId()).isEmpty() && pl.store().slots(p.getUniqueId()).isEmpty()) {
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (p.isOnline()) Msg.send(p, "&7Muốn nuôi và đá gà? Gõ &e/daga &7để bắt đầu.");
            }, 60L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        pl.fights().onQuit(p);
        pl.companions().forget(p);
        pl.gui().clearState(p);
        for (ChickenData d : pl.store().coop(p.getUniqueId())) d.refresh();
        pl.store().save();
        lastMobEgg.remove(p.getUniqueId());
        lastFishEgg.remove(p.getUniqueId());
    }

    // ===== gà đồng hành / gà đang đấu =====
    @EventHandler
    public void onChickenDrop(EntityDropItemEvent e) {
        var pdc = e.getEntity().getPersistentDataContainer();
        if (pdc.has(DaGaPlugin.COMPANION_KEY, PersistentDataType.STRING) || pdc.has(DaGaPlugin.FIGHT_KEY, PersistentDataType.BYTE)) {
            e.setCancelled(true);
        }
    }

    // Gà đồng hành không ai đánh được (kể cả người chơi chế độ sáng tạo, quái, nổ, lửa...)
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
    public void onPetDamage(EntityDamageEvent e) {
        var pdc = e.getEntity().getPersistentDataContainer();
        if (pdc.has(DaGaPlugin.COMPANION_KEY, PersistentDataType.STRING)) {
            EntityDamageEvent.DamageCause c = e.getCause();
            if (c == EntityDamageEvent.DamageCause.VOID || c == EntityDamageEvent.DamageCause.KILL) return;
            e.setCancelled(true);
            return;
        }
        // gà đang đấu: người chơi không đánh xen vào được (plugin vẫn tự "giết" để chạy hoạt ảnh)
        if (pdc.has(DaGaPlugin.FIGHT_KEY, PersistentDataType.BYTE)
                && e instanceof EntityDamageByEntityEvent be && be.getDamager() instanceof Player) {
            e.setCancelled(true);
        }
    }

    // Quái không nhắm vào gà đồng hành
    @EventHandler
    public void onPetTarget(EntityTargetLivingEntityEvent e) {
        LivingEntity t = e.getTarget();
        if (t != null && t.getPersistentDataContainer().has(DaGaPlugin.COMPANION_KEY, PersistentDataType.STRING)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onFall(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        double f = pl.companions().fallFactor(p);
        if (f >= 1.0) return;
        if (f <= 0.0) e.setCancelled(true);
        else e.setDamage(e.getDamage() * f);
    }

    @EventHandler
    public void onChickenClick(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        // cầm gà / trứng của plugin mà bấm vào sinh vật: không để trứng spawn hoặc mất vật phẩm
        ItemStack used = e.getHand() == EquipmentSlot.OFF_HAND ? p.getInventory().getItemInOffHand() : p.getInventory().getItemInMainHand();
        if (isOurs(used)) { e.setCancelled(true); return; }
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!(e.getRightClicked() instanceof Chicken ch)) return;
        var pdc = ch.getPersistentDataContainer();
        if (pdc.has(DaGaPlugin.FIGHT_KEY, PersistentDataType.BYTE)) { e.setCancelled(true); return; }
        UUID owner = pl.companions().ownerOf(ch);
        if (owner != null) {
            e.setCancelled(true);
            if (owner.equals(p.getUniqueId())) {
                if (p.isSneaking()) pl.gui().openCare(p);
                else pl.companions().interact(p, ch);
                return;
            }
            Player op = Bukkit.getPlayer(owner);
            if (op == null) return;
            pl.gui().openPetMenu(p, op);   // menu xem gà + thách đấu
            return;
        }
        if (pdc.has(DaGaPlugin.WILD_KEY, PersistentDataType.STRING)) return;   // để onTame xử lý
        if (p.isSneaking()) {
            e.setCancelled(true);
            if (!pl.companions().isOut(p)) {
                Msg.send(p, "&7Hãy mang gà theo (menu &e/daga &7→ Mang gà đi theo bạn) rồi Shift + chuột phải vào gà làng để đấu.");
                return;
            }
            if (ch.isDead()) return;
            pl.fights().wildFight(p, ch);
        }
    }

    // ===== nguồn trứng =====
    @EventHandler
    public void onSpawn(CreatureSpawnEvent e) {
        // quái từ lồng sinh quái / trứng sinh quái không rơi trứng (chống cày)
        if (e.getEntity() instanceof Monster m) {
            CreatureSpawnEvent.SpawnReason r = e.getSpawnReason();
            if (r == CreatureSpawnEvent.SpawnReason.SPAWNER || r == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG
                    || r == CreatureSpawnEvent.SpawnReason.DISPENSE_EGG) {
                m.getPersistentDataContainer().set(DaGaPlugin.NODROP_KEY, PersistentDataType.BYTE, (byte) 1);
            }
            return;
        }
        if (!(e.getEntity() instanceof Chicken ch)) return;
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        if (!ch.isAdult()) return;
        String biome = e.getLocation().getBlock().getBiome().getKey().getKey();
        String type = null;
        if (biome.contains("snow") || biome.contains("frozen") || biome.contains("ice") || biome.contains("grove")) type = "snow";
        else if (biome.contains("desert") || biome.contains("badlands")) type = "desert";
        else if (biome.contains("mushroom")) type = "mushroom";
        if (type == null) return;
        if (rnd.nextDouble() >= pl.getConfig().getDouble("wild-chicken.variant-chance", 0.2)) return;
        markWild(ch, type);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        LivingEntity v = e.getEntity();
        if (v.getPersistentDataContainer().has(DaGaPlugin.FIGHT_KEY, PersistentDataType.BYTE)) {
            e.getDrops().clear();
            e.setDroppedExp(0);
            return;
        }
        Player killer = v.getKiller();
        if (killer == null || !(v instanceof Monster)) return;
        if (v.getPersistentDataContainer().has(DaGaPlugin.NODROP_KEY, PersistentDataType.BYTE)) return;
        if (rnd.nextDouble() >= pl.getConfig().getDouble("drops.mob-egg-chance", 0.015)) return;
        long now = System.currentTimeMillis();
        long cd = pl.getConfig().getLong("drops.mob-egg-cooldown-seconds", 60) * 1000L;
        if (now - lastMobEgg.getOrDefault(killer.getUniqueId(), 0L) < cd) return;
        lastMobEgg.put(killer.getUniqueId(), now);
        e.getDrops().add(Eggs.wild(0));
    }

    @EventHandler
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (rnd.nextDouble() >= pl.getConfig().getDouble("drops.fishing-egg-chance", 0.03)) return;
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        long cd = pl.getConfig().getLong("drops.fishing-egg-cooldown-seconds", 60) * 1000L;
        if (now - lastFishEgg.getOrDefault(p.getUniqueId(), 0L) < cd) return;
        if (e.getCaught() instanceof Item item) {
            lastFishEgg.put(p.getUniqueId(), now);
            item.setItemStack(Eggs.wild(0));
            Msg.send(p, "&dBạn câu được một Trứng Bí Ẩn!");
        }
    }

    // ===== thuần hóa gà hoang =====
    private Material seedFor(String type) {
        return switch (type) {
            case "snow" -> Material.BEETROOT_SEEDS;
            case "desert" -> Material.MELON_SEEDS;
            case "mushroom" -> Material.PUMPKIN_SEEDS;
            default -> Material.WHEAT_SEEDS;
        };
    }

    @EventHandler
    public void onTame(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!(e.getRightClicked() instanceof Chicken ch)) return;
        String type = ch.getPersistentDataContainer().get(DaGaPlugin.WILD_KEY, PersistentDataType.STRING);
        if (type == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        Material need = seedFor(type);
        if (hand.getType() != need) {
            Msg.send(p, "&eGà hoang này chỉ chịu theo người cầm &f" + need.name().toLowerCase().replace('_', ' ') + "&e.");
            return;
        }
        if (hand.getAmount() <= 1) p.getInventory().setItemInMainHand(null);
        else hand.setAmount(hand.getAmount() - 1);
        if (rnd.nextDouble() >= pl.getConfig().getDouble("wild-chicken.tame-chance", 0.45)) {
            Msg.send(p, "&7Gà hoang giật mình, chưa chịu theo bạn. Thử lại!");
            return;
        }
        int bonus = type.equals("mushroom") ? 2 : 1;
        ChickenData c = ChickenData.create(Eggs.roll(pl.getConfig(), rnd, bonus), rnd);
        c.color = switch (type) {
            case "snow" -> 2;
            case "desert" -> 3;
            default -> 7;
        };
        ch.remove();
        pl.giveChicken(p, c);
        Msg.send(p, "&aThuần hóa thành công: " + c.rarity.color + c.name + " &7(" + c.rarity.tag() + "&7)");
    }

    public static void markWild(Chicken ch, String type) {
        String label = switch (type) {
            case "snow" -> "&bGà Tuyết (hoang)";
            case "desert" -> "&eGà Sa Mạc (hoang)";
            default -> "&dGà Nấm (hoang)";
        };
        ch.getPersistentDataContainer().set(DaGaPlugin.WILD_KEY, PersistentDataType.STRING, type);
        ch.customName(Msg.c(label));
        ch.setCustomNameVisible(true);
    }
}
