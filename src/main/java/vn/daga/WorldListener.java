package vn.daga;

import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Random;

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
        if (pdc.has(ChickenData.KEY, PersistentDataType.STRING) || pdc.has(Eggs.KEY, PersistentDataType.STRING)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pl.fights().onQuit(e.getPlayer());
    }

    // Quái chết / boss rơi trứng
    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        LivingEntity v = e.getEntity();
        if (v.getKiller() == null) return;
        boolean boss = v instanceof EnderDragon || v instanceof Wither || v instanceof ElderGuardian || v instanceof Warden;
        if (boss) {
            e.getDrops().add(Eggs.wild(pl.getConfig().getInt("drops.boss-egg-bonus", 2)));
            Msg.send(v.getKiller(), "&5Boss đánh rơi một Trứng Cổ Đại!");
        } else if (v instanceof Monster && rnd.nextDouble() < pl.getConfig().getDouble("drops.mob-egg-chance", 0.015)) {
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

    // Rương hầm ngục / kho báu
    @EventHandler
    public void onLoot(LootGenerateEvent e) {
        if (rnd.nextDouble() < pl.getConfig().getDouble("drops.loot-egg-chance", 0.25)) {
            e.getLoot().add(Eggs.wild(1));
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
        DaGaPlugin.give(p, c.toItem());
        Msg.send(p, "&aThuần hóa thành công: " + c.rarity.color + c.name + " &7(" + c.rarity.tag() + "&7)");
    }
}
