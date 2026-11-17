package vn.daga;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Tách riêng để plugin vẫn chạy được khi server không có Vault. */
public class VaultHook {
    private final Economy eco;

    private VaultHook(Economy eco) {
        this.eco = eco;
    }

    public static VaultHook create() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return null;
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : new VaultHook(rsp.getProvider());
    }

    public boolean has(OfflinePlayer p, double amount) {
        return eco.has(p, amount);
    }

    public boolean withdraw(OfflinePlayer p, double amount) {
        return eco.withdrawPlayer(p, amount).transactionSuccess();
    }

    public void deposit(OfflinePlayer p, double amount) {
        eco.depositPlayer(p, amount);
    }

    public String format(double amount) {
        return eco.format(amount);
    }
}
