package ru.ragerise.pw.ragequests;

import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import ru.ragerise.pw.ragequests.database.StorageManager;
import ru.ragerise.pw.ragequests.gui.QuestGUI;
import ru.ragerise.pw.ragequests.listeners.enablePlugin;
import ru.ragerise.pw.ragequests.quests.QuestManager;
import ru.ragerise.pw.ragequests.util.ColorUtil;

public final class Main extends JavaPlugin implements Listener {

    private static Main instance;
    private StorageManager storageManager;
    private QuestManager questManager;
    private QuestGUI questGUI;
    private NamespacedKey questIdKey;

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

        getCommand("quest1").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player p)) return true;
            questGUI.open(p, 1);
            return true;
        });
        getCommand("quest2").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player p)) return true;
            questGUI.open(p, 2);
            return true;
        });
        getCommand("quest3").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player p)) return true;
            questGUI.open(p, 3);
            return true;
        });

        getCommand("ragequests").setExecutor(this::onAdminCommand);

        getServer().getOnlinePlayers().forEach(player -> {
            storageManager.loadPlayerData(player);
            questManager.checkAndStartNext(player);
        });
    }

    private boolean onAdminCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("ragequests.admin")) {
            sender.sendMessage(ColorUtil.color("&cНет прав."));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(ColorUtil.color("&cИспользование: /ragequests <reload|reset|complete|setquest>"));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                reloadConfig();
                questManager.loadQuests();
                sender.sendMessage(ColorUtil.color("&aКонфиги перезагружены."));
            }
            case "reset" -> {
                if (args.length < 2) return true;
                Player target = getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ColorUtil.color("&cИгрок не онлайн."));
                    return true;
                }
                questManager.resetPlayer(target);
                sender.sendMessage(ColorUtil.color("&aПрогресс " + target.getName() + " сброшен."));
            }
            case "complete" -> {
                if (args.length < 2) return true;
                Player target = getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ColorUtil.color("&cИгрок не онлайн."));
                    return true;
                }
                questManager.forceCompleteCurrent(target);
                sender.sendMessage(ColorUtil.color("&aТекущий квест " + target.getName() + " завершён принудительно."));
            }
            case "setquest" -> {
                if (args.length < 3) return true;
                Player target = getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ColorUtil.color("&cИгрок не онлайн."));
                    return true;
                }
                try {
                    int id = Integer.parseInt(args[2]);
                    questManager.setCurrentQuest(target, id);
                    sender.sendMessage(ColorUtil.color("&aКвест " + target.getName() + " установлен на " + id));
                } catch (NumberFormatException e) {
                    sender.sendMessage(ColorUtil.color("&cНеверный ID квеста."));
                }
            }
            default -> sender.sendMessage(ColorUtil.color("&cНеизвестная подкоманда."));
        }
        return true;
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