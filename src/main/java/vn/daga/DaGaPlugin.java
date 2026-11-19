package vn.daga;

import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

public class DaGaPlugin extends JavaPlugin {
    public static NamespacedKey WILD_KEY;
    public static NamespacedKey FIGHT_KEY;

    private VaultHook hook;
    private DataStore store;
    private FightManager fights;
    private Gui gui;
    private final Random rnd = new Random();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ChickenData.KEY = new NamespacedKey(this, "chicken");
        Eggs.KEY = new NamespacedKey(this, "egg");
        WILD_KEY = new NamespacedKey(this, "wild");
        FIGHT_KEY = new NamespacedKey(this, "fight");

        hook = VaultHook.create();
        if (hook == null) getLogger().warning("Không thấy Vault/economy: tính năng cược tiền sẽ tắt.");

        store = new DataStore(this);
        store.load();
        fights = new FightManager(this);
        gui = new Gui(this);

        PluginCommand cmd = getCommand("daga");
        DaGaCommand ex = new DaGaCommand(this);
        if (cmd != null) {
            cmd.setExecutor(ex);
            cmd.setTabCompleter(ex);
        }
        getServer().getPluginManager().registerEvents(new WorldListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
    }

    @Override
    public void onDisable() {
        if (fights != null) fights.shutdown();
        if (store != null) store.save();
    }

    public VaultHook hook() { return hook; }
    public DataStore store() { return store; }
    public FightManager fights() { return fights; }
    public Random rnd() { return rnd; }
    public Gui gui() { return gui; }

    /** Đưa gà vào chuồng; chuồng đầy thì đưa vào túi dưới dạng vật phẩm. */
    public void giveChicken(Player p, ChickenData d) {
        if (store.addChicken(p.getUniqueId(), d)) {
            Msg.send(p, "&aGà " + d.rarity.color + d.name + " &ađã vào chuồng. Mở menu &e/daga &ađể chăm sóc.");
        } else {
            give(p, d.toItem());
            Msg.send(p, "&eChuồng đã đầy! Gà được đưa vào túi. Dọn chỗ rồi chuột phải vào gà để gửi vào chuồng.");
        }
    }

    public static void give(Player p, ItemStack it) {
        for (ItemStack left : p.getInventory().addItem(it).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
    }
}
