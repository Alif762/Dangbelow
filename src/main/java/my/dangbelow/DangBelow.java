package my.dangbelow;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

public class DangBelow extends JavaPlugin implements Listener {

    private static final String OBJ = "dang_below";

    // viewer -> (target -> teks terakhir yang dihantar)
    private final Map<UUID, Map<UUID, String>> sent = new HashMap<>();
    private Object eco;
    private Method getBalance;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        // Refresh setiap 1 saat (20 tick)
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
    }

    @Override
    public void onDisable() {
        for (Player v : Bukkit.getOnlinePlayers()) {
            try {
                Objective o = v.getScoreboard().getObjective(OBJ);
                if (o != null) o.unregister();
            } catch (Throwable ignored) {
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        String name = e.getPlayer().getName();
        sent.remove(id);
        for (Player v : Bukkit.getOnlinePlayers()) {
            Map<UUID, String> m = sent.get(v.getUniqueId());
            if (m != null) m.remove(id);
            Objective o = v.getScoreboard().getObjective(OBJ);
            if (o != null) o.getScore(name).resetScore();
        }
    }

    private void tick() {
        File f = new File(getDataFolder().getParentFile(), "DangBoard/shards.yml");
        YamlConfiguration shards = f.exists()
                ? YamlConfiguration.loadConfiguration(f)
                : new YamlConfiguration();

        Map<UUID, String> texts = new HashMap<>();
        for (Player t : Bukkit.getOnlinePlayers()) {
            long shard = shards.getLong("shards." + t.getUniqueId(), 0);
            texts.put(t.getUniqueId(),
                    "\u00a7a$ " + fmt(balance(t))
                            + " \u00a78| \u00a7d\u2726 " + fmt(shard));
        }

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            apply(viewer, texts);
        }
    }

    private void apply(Player viewer, Map<UUID, String> texts) {
        Scoreboard sb = viewer.getScoreboard();
        Objective obj = sb.getObjective(OBJ);
        if (obj == null) {
            obj = sb.registerNewObjective(OBJ, Criteria.DUMMY, Component.empty());
            obj.setDisplaySlot(DisplaySlot.BELOW_NAME);
            sent.remove(viewer.getUniqueId());
        }

        Map<UUID, String> cache = sent.computeIfAbsent(viewer.getUniqueId(), k -> new HashMap<>());
        for (Player t : Bukkit.getOnlinePlayers()) {
            String text = texts.get(t.getUniqueId());
            if (text == null || text.equals(cache.get(t.getUniqueId()))) continue;

            org.bukkit.scoreboard.Score s = obj.getScore(t.getName());
            s.setScore(1);
            s.numberFormat(NumberFormat.fixed(
                    LegacyComponentSerializer.legacySection().deserialize(text)));
            cache.put(t.getUniqueId(), text);
        }
    }

    // ---------- Duit (Vault) ----------
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void hook() {
        try {
            Class c = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider rsp = Bukkit.getServicesManager().getRegistration(c);
            if (rsp == null) return;
            eco = rsp.getProvider();
            getBalance = c.getMethod("getBalance", OfflinePlayer.class);
        } catch (Throwable ignored) {
        }
    }

    private double balance(Player p) {
        try {
            if (eco == null) hook();
            if (eco != null) return ((Number) getBalance.invoke(eco, p)).doubleValue();
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private String fmt(double v) {
        if (v >= 1_000_000_000) return String.format(Locale.US, "%.1fB", v / 1_000_000_000);
        if (v >= 1_000_000) return String.format(Locale.US, "%.1fM", v / 1_000_000);
        if (v >= 1_000) return String.format(Locale.US, "%.1fK", v / 1_000);
        return String.valueOf((long) v);
    }
              }
