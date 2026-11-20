package vn.daga;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Random;
import java.util.UUID;

public class WorldListener implements Listener {
    private final DaGaPlugin pl;
    private final Random rnd;

    public WorldListener(DaGaPlugin pl) {
        this.pl = pl;
        this.rnd = pl.rnd();
    }

    // Chặn đặt trứng/gà chiến như vật phẩm thường
    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it == null || !it.hasItemMeta()) return;
        var pdc = it.getItemMeta().getPersistentDataContainer();
        if (pdc.has(ChickenData.KEY, PersistentDataType.STRING)) {
            e.setCancelled(true);
            ChickenData d = ChickenData.fromItem(it);
            Player p = e.getPlayer();
            if (d == null) return;
            if (pl.store().addChicken(p.getUniqueId(), d)) {
                if (e.getHand() == EquipmentSlot.OFF_HAND) p.getInventory().setItemInOffHand(null);
                else p.getInventory().setItemInMainHand(null);
                Msg.send(p, "&aĐã gửi &f" + d.name + " &avào chuồng gà. Mở menu: &e/daga");
            } else {
                Msg.send(p, "&cChuồng gà đã đầy!");
            }
        } else if (pdc.has(Eggs.KEY, PersistentDataType.STRING)) {
            e.setCancelled(true);
            Msg.send(e.getPlayer(), "&7Mang trứng vào Lò ấp: gõ &e/daga ap");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pl.fights().onQuit(e.getPlayer());
        pl.companions().forget(e.getPlayer());
    }

    // Gà đồng hành / gà đang đấu không đẻ trứng
    @EventHandler
    public void onChickenDrop(EntityDropItemEvent e) {
        var pdc = e.getEntity().getPersistentDataContainer();
        if (pdc.has(DaGaPlugin.COMPANION_KEY, PersistentDataType.STRING) || pdc.has(DaGaPlugin.FIGHT_KEY, PersistentDataType.BYTE)) {
            e.setCancelled(true);
        }
    }

    // Gà đồng hành đỡ sát thương rơi cho chủ
    @EventHandler
    public void onFall(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        double f = pl.companions().fallFactor(p);
        if (f >= 1.0) return;
        if (f <= 0.0) e.setCancelled(true);
        else e.setDamage(e.getDamage() * f);
    }

    // Tương tác với gà đồng hành / gà làng
    @EventHandler
    public void onChickenClick(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!(e.getRightClicked() instanceof Chicken ch)) return;
        Player p = e.getPlayer();
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
            ChickenData od = pl.store().active(owner);
            Msg.send(p, "&7Gà của &f" + op.getName() + "&7: " + (od == null ? "?" : od.rarity.color + od.name + " &7(" + od.rarity.tag() + "&7)"));
            if (p.isSneaking()) pl.fights().challenge(p, op, 0);
            else Msg.send(p, "&8Shift + chuột phải để thách đấu gà này.");
            return;
        }
        if (pdc.has(DaGaPlugin.WILD_KEY, PersistentDataType.STRING)) return;
        if (p.isSneaking()) {
            e.setCancelled(true);
            if (!pl.companions().isOut(p)) {
                Msg.send(p, "&7Hãy mang gà theo (menu &e/daga &7→ Mang gà đi theo bạn) rồi Shift + chuột phải vào gà khác để đấu.");
                return;
            }
            pl.fights().wildFight(p, ch);
        }
    }

    // Quái thường chết có tỉ lệ rơi trứng
    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        LivingEntity v = e.getEntity();
        if (v.getPersistentDataContainer().has(DaGaPlugin.FIGHT_KEY, PersistentDataType.BYTE)) {
            e.getDrops().clear();
            e.setDroppedExp(0);
            return;
        }
        if (v.getKiller() == null) return;
        if (v instanceof Monster && rnd.nextDouble() < pl.getConfig().getDouble("drops.mob-egg-chance", 0.015)) {
            e.getDrops().add(Eggs.wild(0));
        }
    }

    // Câu cá ra trứng bí ẩn
    @EventHandler
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (rnd.nextDouble() >= pl.getConfig().getDouble("drops.fishing-egg-chance", 0.03)) return;
        if (e.getCaught() instanceof Item item) {
            item.setItemStack(Eggs.wild(0));
            Msg.send(e.getPlayer(), "&dBạn câu được một Trứng Bí Ẩn!");
        }
    }

    // Gà hoang theo biome
    @EventHandler
    public void onSpawn(CreatureSpawnEvent e) {
        if (!(e.getEntity() instanceof Chicken ch)) return;
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        if (!ch.isAdult()) return;
        String biome = e.getLocation().getBlock().getBiome().getKey().getKey();
        String type = null;
        String label = null;
        if (biome.contains("snow") || biome.contains("frozen") || biome.contains("ice") || biome.contains("grove")) {
            type = "snow"; label = "&bGà Tuyết (hoang)";
        } else if (biome.contains("desert") || biome.contains("badlands")) {
            type = "desert"; label = "&eGà Sa Mạc (hoang)";
        } else if (biome.contains("mushroom")) {
            type = "mushroom"; label = "&dGà Nấm (hoang)";
        }
        if (type == null) return;
        if (rnd.nextDouble() >= pl.getConfig().getDouble("wild-chicken.variant-chance", 0.2)) return;
        ch.getPersistentDataContainer().set(DaGaPlugin.WILD_KEY, PersistentDataType.STRING, type);
        ch.customName(Msg.c(label));
        ch.setCustomNameVisible(true);
    }

    private Material seedFor(String type) {
        return switch (type) {
            case "snow" -> Material.BEETROOT_SEEDS;
            case "desert" -> Material.MELON_SEEDS;
            case "mushroom" -> Material.PUMPKIN_SEEDS;
            default -> Material.WHEAT_SEEDS;
        };
    }

    // Thuần hóa gà hoang bằng hạt giống riêng
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
            Msg.send(p, "&eGà hoang này chỉ chịu theo người có &f" + need.name().toLowerCase().replace('_', ' ') + "&e.");
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
