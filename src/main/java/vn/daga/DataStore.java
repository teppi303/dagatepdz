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
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Không lưu được data.yml: " + ex.getMessage());
        }
    }

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

    public List<Stat> top(int n) {
        List<Stat> l = new ArrayList<>(stats.values());
        l.sort((a, b) -> b.wins != a.wins ? Integer.compare(b.wins, a.wins) : Double.compare(b.earned, a.earned));
        return l.subList(0, Math.min(n, l.size()));
    }
}
