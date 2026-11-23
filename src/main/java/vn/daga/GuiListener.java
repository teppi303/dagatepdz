package vn.daga;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.function.Consumer;

public class GuiListener implements Listener {
    private final DaGaPlugin pl;

    public GuiListener(DaGaPlugin pl) {
        this.pl = pl;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        // getHolder(false): không tạo snapshot khối (tránh lag khi người chơi bấm rương/lò...)
        if (!(e.getInventory().getHolder(false) instanceof Menu m)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) return;
        Consumer<InventoryClickEvent> a = m.actions.get(e.getRawSlot());
        if (a != null) a.accept(e);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder(false) instanceof Menu) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder(false) instanceof Menu && e.getPlayer() instanceof Player p) {
            pl.gui().onMenuClose(p);
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (!pl.gui().isRenaming(p)) return;
        e.setCancelled(true);
        String raw = PlainTextComponentSerializer.plainText().serialize(e.message());
        Bukkit.getScheduler().runTask(pl, () -> pl.gui().finishRename(p, raw));
    }
}
