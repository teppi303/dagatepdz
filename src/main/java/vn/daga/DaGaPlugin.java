package vn.daga;

import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

public class DaGaPlugin extends JavaPlugin {
    public static NamespacedKey WILD_KEY;

    private VaultHook hook;
    private DataStore store;
    private FightManager fights;
    private final Random rnd = new Random();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ChickenData.KEY = new NamespacedKey(this, "chicken");
        Eggs.KEY = new NamespacedKey(this, "egg");
        WILD_KEY = new NamespacedKey(this, "wild");

        hook = VaultHook.create();
        if (hook == null) getLogger().warning("Không thấy Vault/economy: tính năng cược tiền sẽ tắt.");

        store = new DataStore(this);
        store.load();
        fights = new FightManager(this);

        PluginCommand cmd = getCommand("daga");
        DaGaCommand ex = new DaGaCommand(this);
        if (cmd != null) {
            cmd.setExecutor(ex);
            cmd.setTabCompleter(ex);
        }
        getServer().getPluginManager().registerEvents(new WorldListener(this), this);
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

    public static void give(Player p, ItemStack it) {
        for (ItemStack left : p.getInventory().addItem(it).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
    }
}
