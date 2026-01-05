package ru.ragerise.pw.ragequests;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import ru.ragerise.pw.ragequests.commands.AdminCommands;
import ru.ragerise.pw.ragequests.database.StorageManager;
import ru.ragerise.pw.ragequests.gui.QuestGUI;
import ru.ragerise.pw.ragequests.listeners.enablePlugin;
import ru.ragerise.pw.ragequests.quests.QuestManager;
import ru.ragerise.pw.ragequests.util.ColorUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.*;

public final class Main extends JavaPlugin implements Listener {

    private static Main instance;
    private StorageManager storageManager;
    private QuestManager questManager;
    private QuestGUI questGUI;
    private NamespacedKey questIdKey;

    // ← НОВОЕ: для мультипликатора
    public double progressMultiplier = 1.0;
    public long multiplierEndTime = 0;

    // ← НОВОЕ: для maintenance mode
    public boolean maintenanceMode = false;
    public final Set<String> maintenanceWhitelist = new HashSet<>();

    // ← НОВОЕ: для админ-лога
    private final List<String> adminLog = new ArrayList<>();
    private File logFile;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        saveResource("quests.yml", false);

        questIdKey = new NamespacedKey(this, "displayable_id");

        storageManager = new StorageManager(this);
        questManager = new QuestManager(this);
        questGUI = new QuestGUI(this);

        questManager.loadQuests();

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new enablePlugin(), this);
        getServer().getPluginManager().registerEvents(questManager, this);
        getServer().getPluginManager().registerEvents(questGUI, this);

        // ← НОВОЕ: загрузка кастомных настроек и таймер мультипликатора
        reloadCustomConfig();

        logFile = new File(getDataFolder(), "logs/admin.log");
        logFile.getParentFile().mkdirs();

        // ← НОВОЕ: таймер проверки окончания мультипликатора
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (System.currentTimeMillis() > multiplierEndTime && progressMultiplier != 1.0) {
                progressMultiplier = 1.0;
                multiplierEndTime = 0;
                saveConfig();
                Bukkit.broadcast(ColorUtil.color("&6[RageQuests] &eМультипликатор прогресса завершён."), "ragequests.admin");
            }
        }, 20L, 20L * 60); // каждую минуту

        // Команды для открытия конкретных этапов GUI
        getCommand("quest1").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(ColorUtil.color("&cТолько для игроков."));
                return true;
            }
            questGUI.open(p, 1);
            return true;
        });

        getCommand("quest2").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(ColorUtil.color("&cТолько для игроков."));
                return true;
            }
            questGUI.open(p, 2);
            return true;
        });

        getCommand("quest3").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(ColorUtil.color("&cТолько для игроков."));
                return true;
            }
            questGUI.open(p, 3);
            return true;
        });

// Основная админ-команда
        AdminCommands adminCommands = new AdminCommands(this);
        getCommand("ragequests").setExecutor(adminCommands);
        getCommand("ragequests").setTabCompleter(adminCommands);
    }

    // ← НОВОЕ: перезагрузка кастомных настроек
    public void reloadCustomConfig() {
        reloadConfig();

        progressMultiplier = getConfig().getDouble("event.multiplier", 1.0);
        multiplierEndTime = getConfig().getLong("event.end-time", 0);

        maintenanceMode = getConfig().getBoolean("maintenance.enabled", false);
        maintenanceWhitelist.clear();
        maintenanceWhitelist.addAll(getConfig().getStringList("maintenance.whitelist"));
    }

    // ← НОВОЕ: геттеры
    public double getProgressMultiplier() {
        return System.currentTimeMillis() > multiplierEndTime ? 1.0 : progressMultiplier;
    }

    public boolean isMaintenanceAllowed(Player player) {
        return !maintenanceMode || maintenanceWhitelist.contains(player.getName());
    }

    // ← НОВОЕ: логирование админ-действий
    public void logAdminAction(CommandSender sender, String action) {
        String entry = "[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "] " +
                (sender instanceof Player ? ((Player) sender).getName() : "CONSOLE") + ": " + action;
        adminLog.add(entry);

        try (BufferedWriter writer = Files.newBufferedWriter(logFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(entry);
            writer.newLine();
        } catch (Exception ignored) {}

        if (adminLog.size() > 1000) adminLog.remove(0);
    }

    public List<String> getAdminLog() {
        return new ArrayList<>(adminLog);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        storageManager.loadPlayerData(e.getPlayer());
        questManager.checkAndStartNext(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        storageManager.savePlayerData(e.getPlayer());
        questManager.unloadPlayerData(e.getPlayer());
    }

    @Override
    public void onDisable() {
        getServer().getOnlinePlayers().forEach(p -> storageManager.savePlayerData(p));
        storageManager.shutdown();

        // ← НОВОЕ: сохранение кастомных настроек
        getConfig().set("event.multiplier", progressMultiplier);
        getConfig().set("event.end-time", multiplierEndTime);
        getConfig().set("maintenance.enabled", maintenanceMode);
        getConfig().set("maintenance.whitelist", new ArrayList<>(maintenanceWhitelist));
        saveConfig();
    }

    public static Main getInstance() { return instance; }
    public StorageManager getStorageManager() { return storageManager; }
    public QuestManager getQuestManager() { return questManager; }
    public QuestGUI getQuestGUI() { return questGUI; }
    public NamespacedKey getQuestIdKey() { return questIdKey; }

    public void debug(String msg) {
        if (getConfig().getBoolean("debug", false)) {
            getLogger().info("[DEBUG] " + msg);
        }
    }
}