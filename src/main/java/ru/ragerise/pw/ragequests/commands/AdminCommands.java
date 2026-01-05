package ru.ragerise.pw.ragequests.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.ragerise.pw.ragequests.Main;
import ru.ragerise.pw.ragequests.player.PlayerData;
import ru.ragerise.pw.ragequests.quests.Quest;
import ru.ragerise.pw.ragequests.quests.QuestManager;
import ru.ragerise.pw.ragequests.quests.Reward;
import ru.ragerise.pw.ragequests.util.ColorUtil;

import java.util.*;

public class AdminCommands implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final List<String> subCommands = Arrays.asList(
            "reload", "reset", "complete", "setquest", "progress", "info", "open",
            "questrewards", "reward", "list", "testreward", "claimreward", "unclaimreward",
            "clearclaimed", "skip", "back", "maxcurrent", "listplayers", "globalcomplete",
            "globalmessage", "saveall", "debug", "event", "maintenance", "adminlog", "changelog", "queststatus", "help"
    );

    public AdminCommands(Main plugin) {
        this.plugin = plugin;
    }

    private String msg(String path, String def) {
        return ColorUtil.color(plugin.getConfig().getString("messages.admin-commands." + path, def));
    }

    private String msg(String path, String def, String... replacements) {
        String text = msg(path, def);
        for (int i = 0; i < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        return text;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("ragequests.admin")) {
            sender.sendMessage(msg("no-permission", "&#FF5555У вас нет прав на админ-команды RageQuests."));
            return true;
        }

        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("help"))) {
            for (String line : plugin.getConfig().getStringList("messages.admin-commands.help")) {
                sender.sendMessage(ColorUtil.color(line));
            }
            return true;
        }

        QuestManager qm = plugin.getQuestManager();

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadCustomConfig();
                qm.loadQuests();
                sender.sendMessage(msg("reload-success", "&#00FF00Конфиги и квесты перезагружены."));
                plugin.logAdminAction(sender, "reload");
            }

            case "reset" -> {
                if (args.length < 2) {
                    sender.sendMessage(msg("unknown-command", "&#FF5555Недостаточно аргументов."));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(msg("player-offline", "&#FF5555Игрок &e%player% &cне онлайн.", "%player%", args[1]));
                    return true;
                }
                qm.resetPlayer(target);
                sender.sendMessage(msg("reset-success", "&#00FF00Прогресс сброшен для &e%player%&a.", "%player%", target.getName()));
                plugin.logAdminAction(sender, "reset " + target.getName());
            }

            case "complete" -> {
                if (args.length < 2) return true;
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(msg("player-offline", "&#FF5555Игрок &e%player% &cне онлайн.", "%player%", args[1]));
                    return true;
                }
                qm.forceCompleteCurrent(target);
                sender.sendMessage(msg("complete-success", "&#00FF00Текущий квест завершён для &e%player%&a.", "%player%", target.getName()));
                plugin.logAdminAction(sender, "complete " + target.getName());
            }

            case "setquest" -> {
                if (args.length < 3) return true;
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(msg("player-offline", "&#FF5555Игрок &e%player% &cне онлайн.", "%player%", args[1]));
                    return true;
                }
                int id;
                try {
                    id = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(msg("invalid-number", "&#FF5555Неверный ID: &e%arg%", "%arg%", args[2]));
                    return true;
                }
                qm.setCurrentQuest(target, id);
                sender.sendMessage(msg("setquest-success", "&#00FF00Квест установлен на &e%id% &aдля &e%player%&a.", "%id%", String.valueOf(id), "%player%", target.getName()));
                plugin.logAdminAction(sender, "setquest " + target.getName() + " " + id);
            }

            case "progress" -> {
                if (args.length < 4) {
                    sender.sendMessage(msg("unknown-command", "&#FF5555Использование: progress <игрок> <set|add|sub> <кол-во>"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(msg("player-offline", "&#FF5555Игрок &e%player% &cне онлайн.", "%player%", args[1]));
                    return true;
                }
                PlayerData data = PlayerData.get(target);
                if (data == null) return true;
                int amount;
                try {
                    amount = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(msg("invalid-number", "&#FF5555Неверное количество: &e%arg%", "%arg%", args[3]));
                    return true;
                }
                int current = data.getCurrentProgress();
                switch (args[2].toLowerCase()) {
                    case "set" -> data.setCurrentProgress(amount);
                    case "add" -> data.setCurrentProgress(current + amount);
                    case "sub" -> data.setCurrentProgress(Math.max(0, current - amount));
                    default -> {
                        sender.sendMessage(msg("unknown-command", "&#FF5555Режим: set|add|sub"));
                        return true;
                    }
                }
                plugin.getStorageManager().savePlayerData(target);
                sender.sendMessage(msg("progress-success", "&#00FF00Прогресс обновлён для &e%player%&a.", "%player%", target.getName()));
                plugin.logAdminAction(sender, "progress " + target.getName() + " " + args[2] + " " + amount);
            }

            case "info" -> {
                if (args.length < 2) return true;
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(msg("player-offline", "&#FF5555Игрок &e%player% &cне онлайн.", "%player%", args[1]));
                    return true;
                }
                PlayerData data = PlayerData.get(target);
                if (data == null) return true;
                Quest quest = qm.getQuestsById().get(data.getCurrentQuest());
                sender.sendMessage(ColorUtil.color("&#FFD700Информация о &e" + target.getName() + "&a:"));
                sender.sendMessage(ColorUtil.color("&7Текущий квест: &f" + data.getCurrentQuest() + (quest != null ? " (&f" + quest.getName() + "&7)" : "")));
                sender.sendMessage(ColorUtil.color("&7Прогресс: &f" + data.getCurrentProgress() + "/" + (quest != null ? quest.getRequiredAmount() : "?")));
                sender.sendMessage(ColorUtil.color("&7Забрано ревардов: &f" + data.getClaimedRewards().size()));
                plugin.logAdminAction(sender, "info " + target.getName());
            }

            case "event" -> {
                if (args.length < 3 || !args[1].equalsIgnoreCase("multiplier")) {
                    sender.sendMessage(msg("unknown-command", "&#FF5555Использование: event multiplier <x> <мин> или off"));
                    return true;
                }
                if (args[2].equalsIgnoreCase("off")) {
                    plugin.progressMultiplier = 1.0;
                    plugin.multiplierEndTime = 0;
                    plugin.saveConfig();
                    sender.sendMessage(ColorUtil.color("&#00FF00Мультипликатор выключен."));
                    plugin.logAdminAction(sender, "event multiplier off");
                    return true;
                }
                double mult;
                try {
                    mult = Double.parseDouble(args[2]);
                    if (mult < 1.1 || mult > 10.0) throw new Exception();
                } catch (Exception e) {
                    sender.sendMessage(ColorUtil.color("&#FF5555Множитель от 1.1 до 10.0"));
                    return true;
                }
                int minutes;
                try {
                    minutes = Integer.parseInt(args[3]);
                } catch (Exception e) {
                    sender.sendMessage(ColorUtil.color("&#FF5555Укажите минуты числом"));
                    return true;
                }
                plugin.progressMultiplier = mult;
                plugin.multiplierEndTime = System.currentTimeMillis() + (minutes * 60L * 1000L);
                plugin.saveConfig();
                Bukkit.broadcastMessage(ColorUtil.color(plugin.getConfig().getString("messages.multiplier-started", "&#FFD700[Ивент] Прогресс квестов x%multiplier% на %minutes% минут!")
                        .replace("%multiplier%", String.valueOf(mult))
                        .replace("%minutes%", String.valueOf(minutes))));
                sender.sendMessage(ColorUtil.color("&#00FF00Мультипликатор запущен."));
                plugin.logAdminAction(sender, "event multiplier x" + mult + " на " + minutes + " мин");
            }

            case "maintenance" -> {
                if (args.length < 2) {
                    sender.sendMessage(ColorUtil.color("&#FFD700Maintenance: " + (plugin.maintenanceMode ? "&#00FF00включён" : "&#FF5555выключен")));
                    sender.sendMessage(ColorUtil.color("&7Whitelist: " + plugin.maintenanceWhitelist));
                    return true;
                }
                String sub = args[1].toLowerCase();
                switch (sub) {
                    case "on" -> {
                        plugin.maintenanceMode = true;
                        plugin.saveConfig();
                        sender.sendMessage(ColorUtil.color("&#00FF00Maintenance включён."));
                        plugin.logAdminAction(sender, "maintenance on");
                    }
                    case "off" -> {
                        plugin.maintenanceMode = false;
                        plugin.saveConfig();
                        sender.sendMessage(ColorUtil.color("&#00FF00Maintenance выключен."));
                        plugin.logAdminAction(sender, "maintenance off");
                    }
                    case "add" -> {
                        if (args.length < 3) return true;
                        plugin.maintenanceWhitelist.add(args[2]);
                        plugin.saveConfig();
                        sender.sendMessage(ColorUtil.color("&#00FF00" + args[2] + " добавлен в whitelist."));
                        plugin.logAdminAction(sender, "maintenance add " + args[2]);
                    }
                    case "remove" -> {
                        if (args.length < 3) return true;
                        plugin.maintenanceWhitelist.remove(args[2]);
                        plugin.saveConfig();
                        sender.sendMessage(ColorUtil.color("&#00FF00" + args[2] + " удалён из whitelist."));
                        plugin.logAdminAction(sender, "maintenance remove " + args[2]);
                    }
                    case "clear" -> {
                        plugin.maintenanceWhitelist.clear();
                        plugin.saveConfig();
                        sender.sendMessage(ColorUtil.color("&#00FF00Whitelist очищен."));
                        plugin.logAdminAction(sender, "maintenance clear");
                    }
                    default -> sender.sendMessage(msg("unknown-command", "&#FF5555Подкоманды: on/off/add/remove/clear [ник]"));
                }
            }

            case "adminlog", "changelog" -> {
                sender.sendMessage(ColorUtil.color("&#FFD700Последние 50 действий админов:"));
                List<String> log = plugin.getAdminLog();
                int start = Math.max(0, log.size() - 50);
                for (int i = start; i < log.size(); i++) {
                    sender.sendMessage(ColorUtil.color("&7" + log.get(i)));
                }
            }

            case "queststatus" -> {
                sender.sendMessage(ColorUtil.color("&#FFD700Статус квестов сервера:"));
                Map<Integer, Integer> playersPerQuest = new HashMap<>();
                int totalOnline = Bukkit.getOnlinePlayers().size();

                for (Player p : Bukkit.getOnlinePlayers()) {
                    PlayerData data = PlayerData.get(p);
                    if (data != null) {
                        playersPerQuest.merge(data.getCurrentQuest(), 1, Integer::sum);
                    }
                }

                for (int id : qm.getQuestsById().keySet()) {
                    int count = playersPerQuest.getOrDefault(id, 0);
                    double percent = totalOnline == 0 ? 0 : (count * 100.0 / totalOnline);
                    Quest q = qm.getQuestsById().get(id);
                    String name = q != null ? q.getName() : "Неизвестно";
                    sender.sendMessage(ColorUtil.color("&7ID " + id + " (&f" + name + "&7): &f" + count + " игроков (&e" + String.format("%.1f", percent) + "%&7)"));
                }
            }

            default -> sender.sendMessage(msg("unknown-command", "&#FF5555Неизвестная подкоманда. Используйте &e/rq help"));
        }
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("ragequests.admin")) return null;

        if (args.length == 1) {
            return subCommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .sorted()
                    .toList();
        }

        if (args.length == 2 && List.of("reset", "complete", "setquest", "progress", "info").contains(args[0].toLowerCase())) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }

        return null;
    }
}