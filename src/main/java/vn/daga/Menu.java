package vn.daga;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Một menu GUI: mỗi lần refresh sẽ vẽ lại toàn bộ nút và hành động. */
public class Menu implements InventoryHolder {
    public final Player player;
    public final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();
    private final Consumer<Menu> filler;
    private final Inventory inv;

    public Menu(Player player, int size, String title, Consumer<Menu> filler) {
        this.player = player;
        this.filler = filler;
        this.inv = Bukkit.createInventory(this, size, Msg.c(title));
        refresh();
    }

    public void refresh() {
        inv.clear();
        actions.clear();
        filler.accept(this);
    }

    public void background() {
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, Ui.pane());
    }

    public void set(int slot, ItemStack it, Consumer<InventoryClickEvent> action) {
        inv.setItem(slot, it);
        if (action != null) actions.put(slot, action);
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
