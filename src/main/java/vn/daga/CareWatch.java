package vn.daga;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;

/**
 * Theo dõi việc chăm gà: gà hết no (no = 0) mà suốt thời hạn (mặc định 1 tuần, tính theo giờ thật,
 * kể cả lúc chủ offline) không được cho ăn thì chết hẳn và để lại mộ.
 * Lưu ý: lúc chủ offline gà không bị đói thêm; chỉ gà đã đói lả sẵn mới tiếp tục bị đếm.
 */
public class CareWatch {
    private final DaGaPlugin pl;
    private final BukkitTask task;

    public CareWatch(DaGaPlugin pl) {
        this.pl = pl;
        this.task = Bukkit.getScheduler().runTaskTimer(pl, this::tick, 1200L, 1200L);   // mỗi phút
    }

    public void stop() {
        task.cancel();
    }

    private boolean enabled() {
        return pl.getConfig().getBoolean("neglect.enabled", true);
    }

    private long limit() {
        return (long) (pl.getConfig().getDouble("neglect.starve-hours", 168) * 3600000L);
    }

    private void tick() {
        if (!enabled()) return;
        long limit = limit();
        if (limit <= 0) return;
        for (Player p : Bukkit.getOnlinePlayers()) check(p, limit, false);
    }

    /** Khi người chơi vào game: kiểm tra những con gà đã đói lả trong lúc họ vắng mặt. */
    public void onJoin(Player p) {
        if (!enabled()) return;
        long limit = limit();
        if (limit <= 0) return;
        check(p, limit, true);
    }

    private void check(Player p, long limit, boolean joining) {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (ChickenData d : new ArrayList<>(pl.store().coop(p.getUniqueId()))) {
            if (d.grave()) continue;
            long before = d.starveSince;
            d.refresh();
            if (d.starveSince != before) changed = true;
            if (d.starveSince <= 0) { d.warned = 0; continue; }
            double ratio = (double) (now - d.starveSince) / limit;
            if (ratio >= 1.0) {
                bury(p, d, joining ? "bị bỏ đói trong lúc bạn vắng mặt" : "bị bỏ đói quá lâu");
                changed = true;
            } else if (ratio >= 0.9 && d.warned < 2) {
                d.warned = 2;
                warn(p, d, limit, now, true);
            } else if (ratio >= 0.5 && d.warned < 1) {
                d.warned = 1;
                warn(p, d, limit, now, false);
            }
        }
        if (changed) pl.store().save();
    }

    private void warn(Player p, ChickenData d, long limit, long now, boolean urgent) {
        String left = ChickenData.fmtHours(Math.max(0, limit - (now - d.starveSince)) / 3600000.0);
        Msg.send(p, (urgent ? "&4&l⚠ KHẨN CẤP: " : "&c⚠ ") + "&f" + d.name + " &cđang đói lả! Còn khoảng &f" + left
                + " &c(tính cả lúc bạn offline) trước khi chết. Cho nó ăn ngay: menu &e/daga &c→ Chăm sóc gà.");
        p.playSound(p.getLocation(), Sound.ENTITY_CHICKEN_HURT, 1f, 0.7f);
    }

    private void bury(Player p, ChickenData d, String cause) {
        d.bury(cause);
        pl.companions().dismissIfChicken(p.getUniqueId(), d.id);
        p.showTitle(Title.title(Msg.c("&4☠ Gà đã chết"), Msg.c("&7" + d.name + " chết vì bị bỏ đói")));
        Msg.send(p, "&4☠ &c" + d.name + " đã chết vì " + cause
                + ". Một ngôi mộ được dựng ở đúng ô của nó trong chuồng (menu → Chuồng gà).");
        p.playSound(p.getLocation(), Sound.ENTITY_CHICKEN_DEATH, 1f, 0.6f);
    }
}
