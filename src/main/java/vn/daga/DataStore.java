package vn.daga;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class DataStore {
    public static class Slot {
        public String chicken;
        public long ready;
    }

    public static class Stat {
        public String name = "";
        public int wins, losses;
        public double earned;
    }

    private final DaGaPlugin plugin;
    private final File file;
    private final Map<UUID, List<Slot>> inc = new HashMap<>();
    private final Map<UUID, Stat> stats = new HashMap<>();
    private final Map<UUID, List<ChickenData>> coops = new HashMap<>();
    private final Map<UUID, String> active = new HashMap<>();

    public DataStore(DaGaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        plugin.getDataFolder().mkdirs();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("inc");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                UUID u;
                try { u = UUID.fromString(k); } catch (IllegalArgumentException e) { continue; }
                List<Slot> list = new ArrayList<>();
                ConfigurationSection us = s.getConfigurationSection(k);
                if (us != null) {
                    for (String i : us.getKeys(false)) {
                        Slot sl = new Slot();
                        sl.chicken = us.getString(i + ".chicken");
                        sl.ready = us.getLong(i + ".ready");
                        if (sl.chicken != null) list.add(sl);
                    }
                }
                inc.put(u, list);
            }
        }
        ConfigurationSection t = y.getConfigurationSection("stats");
        if (t != null) {
            for (String k : t.getKeys(false)) {
                UUID u;
                try { u = UUID.fromString(k); } catch (IllegalArgumentException e) { continue; }
                Stat st = new Stat();
                st.name = t.getString(k + ".name", "?");
                st.wins = t.getInt(k + ".wins");
                st.losses = t.getInt(k + ".losses");
                st.earned = t.getDouble(k + ".earned");
                stats.put(u, st);
            }
        }
        ConfigurationSection c = y.getConfigurationSection("coop");
        if (c != null) {
            for (String k : c.getKeys(false)) {
                UUID u;
                try { u = UUID.fromString(k); } catch (IllegalArgumentException e) { continue; }
                List<ChickenData> list = new ArrayList<>();
                ConfigurationSection cs = c.getConfigurationSection(k);
                if (cs != null) {
                    for (String i : cs.getKeys(false)) {
                        ChickenData d = ChickenData.parse(cs.getString(i, ""));
                        if (d != null) list.add(d);
                    }
                }
                coops.put(u, list);
            }
        }
        ConfigurationSection a = y.getConfigurationSection("active");
        if (a != null) {
            for (String k : a.getKeys(false)) {
                try { active.put(UUID.fromString(k), a.getString(k)); } catch (IllegalArgumentException ignored) { }
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, List<Slot>> e : inc.entrySet()) {
            int i = 0;
            for (Slot sl : e.getValue()) {
                y.set("inc." + e.getKey() + "." + i + ".chicken", sl.chicken);
                y.set("inc." + e.getKey() + "." + i + ".ready", sl.ready);
                i++;
            }
        }
        for (Map.Entry<UUID, Stat> e : stats.entrySet()) {
            String b = "stats." + e.getKey();
            y.set(b + ".name", e.getValue().name);
            y.set(b + ".wins", e.getValue().wins);
            y.set(b + ".losses", e.getValue().losses);
            y.set(b + ".earned", e.getValue().earned);
        }
        for (Map.Entry<UUID, List<ChickenData>> e : coops.entrySet()) {
            int i = 0;
            for (ChickenData d : e.getValue()) {
                y.set("coop." + e.getKey() + "." + i, d.serialize());
                i++;
            }
        }
        for (Map.Entry<UUID, String> e : active.entrySet()) {
            y.set("active." + e.getKey(), e.getValue());
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Không lưu được data.yml: " + ex.getMessage());
        }
    }

    // ----- chuồng gà -----
    public List<ChickenData> coop(UUID u) {
        return coops.computeIfAbsent(u, k -> new ArrayList<>());
    }

    public int coopCap() {
        return Math.max(1, Math.min(45, plugin.getConfig().getInt("coop.slots", 18)));
    }

    public ChickenData active(UUID u) {
        List<ChickenData> l = coop(u);
        String id = active.get(u);
        if (id != null) {
            for (ChickenData d : l) if (d.id.equals(id)) return d;
        }
        if (!l.isEmpty()) {
            active.put(u, l.get(0).id);
            return l.get(0);
        }
        return null;
    }

    public void setActive(UUID u, String id) {
        active.put(u, id);
    }

    public boolean addChicken(UUID u, ChickenData d) {
        if (coop(u).size() >= coopCap()) return false;
        coop(u).add(d);
        if (!active.containsKey(u)) active.put(u, d.id);
        save();
        return true;
    }

    public void removeChicken(UUID u, ChickenData d) {
        coop(u).remove(d);
        if (d.id.equals(active.get(u))) active.remove(u);
        save();
    }

    // ----- lò ấp / thống kê -----
    public List<Slot> slots(UUID u) {
        return inc.computeIfAbsent(u, k -> new ArrayList<>());
    }

    public void addResult(UUID u, String name, boolean win, double earned) {
        Stat s = stats.computeIfAbsent(u, k -> new Stat());
        s.name = name;
        if (win) s.wins++; else s.losses++;
        s.earned += earned;
        save();
    }

    public Stat statOf(UUID u) {
        return stats.get(u);
    }

    public List<Stat> top(int n) {
        List<Stat> l = new ArrayList<>(stats.values());
        l.sort((a, b) -> b.wins != a.wins ? Integer.compare(b.wins, a.wins) : Double.compare(b.earned, a.earned));
        return l.subList(0, Math.min(n, l.size()));
    }
}
