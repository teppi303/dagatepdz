package vn.daga;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class DaGaCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = List.of("menu", "mang", "giup", "chuong", "cham", "ap", "ghep", "dauthu", "cuahang",
            "top", "thachdau", "chapnhan", "tuchoi", "admin");

    private final DaGaPlugin pl;

    public DaGaCommand(DaGaPlugin pl) {
        this.pl = pl;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "menu" : a[0].toLowerCase();
        if (sub.equals("reload") || sub.equals("give")) {
            if (!s.hasPermission("daga.admin")) { Msg.send(s, "&cBạn không có quyền."); return true; }
            if (sub.equals("reload")) {
                pl.reloadConfig();
                Msg.send(s, "&aĐã tải lại config.");
            } else {
                give(s, a);
            }
            return true;
        }
        if (!(s instanceof Player p)) { Msg.send(s, "&cChỉ người chơi mới dùng được lệnh này."); return true; }
        Gui g = pl.gui();
        switch (sub) {
            case "menu" -> g.openMain(p);
            case "mang" -> pl.companions().toggle(p);
            case "giup", "help" -> g.openHelp(p);
            case "chuong" -> g.openCoop(p);
            case "cham" -> g.openCare(p);
            case "ap" -> g.openIncubator(p);
            case "ghep" -> g.openBreed(p);
            case "dauthu" -> g.openSpar(p);
            case "cuahang" -> g.openShop(p);
            case "top" -> g.openTop(p);
            case "admin" -> g.openAdmin(p);
            case "setarena" -> g.setArena(p);
            case "thachdau" -> challenge(p, a);
            case "chapnhan" -> pl.fights().accept(p);
            case "tuchoi" -> pl.fights().decline(p);
            default -> g.openMain(p);
        }
        return true;
    }

    private void challenge(Player p, String[] a) {
        if (a.length < 2) { pl.gui().openChallenge(p); return; }
        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) { Msg.send(p, "&cKhông thấy người chơi &f" + a[1] + "&c đang online."); return; }
        double bet = 0;
        if (a.length >= 3) {
            try { bet = Double.parseDouble(a[2]); } catch (NumberFormatException e) { Msg.send(p, "&cSố tiền không hợp lệ."); return; }
            if (bet < 0 || Double.isNaN(bet) || Double.isInfinite(bet)) { Msg.send(p, "&cSố tiền không hợp lệ."); return; }
        }
        pl.fights().challenge(p, t, bet);
    }

    private void give(CommandSender s, String[] a) {
        if (a.length < 3) { Msg.send(s, "&cDùng: /daga give <người> trung [bonus] | ga <thuong|kha|hiem|huyenthoai>"); return; }
        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) { Msg.send(s, "&cKhông thấy người chơi đó."); return; }
        if (a[2].equalsIgnoreCase("trung")) {
            int bonus = 0;
            if (a.length >= 4) { try { bonus = Integer.parseInt(a[3]); } catch (NumberFormatException ignored) { } }
            DaGaPlugin.give(t, Eggs.wild(bonus));
            Msg.send(s, "&aĐã đưa trứng cho " + t.getName());
        } else if (a[2].equalsIgnoreCase("ga") && a.length >= 4) {
            Rarity r = Rarity.parse(a[3]);
            if (r == null) { Msg.send(s, "&cĐộ hiếm: thuong, kha, hiem, huyenthoai"); return; }
            pl.giveChicken(t, ChickenData.create(r, pl.rnd()));
            Msg.send(s, "&aĐã đưa gà " + r.label + " cho " + t.getName());
        } else {
            Msg.send(s, "&cDùng: /daga give <người> trung [bonus] | ga <độ hiếm>");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String x : SUBS) if (x.startsWith(a[0].toLowerCase())) out.add(x);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("thachdau")) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase().startsWith(a[1].toLowerCase())) out.add(p.getName());
        }
        return out;
    }
}
