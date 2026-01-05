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
            "globalmessage", "saveall", "debug", "event", "maintenance", "adminlog", "changelog", "queststatus"
            // Добавь сюда новые, если будут
    );

    public AdminCommands(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("ragequests.admin")) {
            sender.sendMessage(ColorUtil.color("&cНет прав."));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ColorUtil.color("&c/rq <подкоманда>"));
            sender.sendMessage(ColorUtil.color("&aПодкоманды: &f" + String.join(", ", subCommands)));
            return true;
        }

        QuestManager qm = plugin.getQuestManager();

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadCustomConfig();
                qm.loadQuests();
                sender.sendMessage(ColorUtil.color("&aПерезагружено."));
                plugin.logAdminAction(sender, "reload");
            }

            case "reset" -> {
                if (args.length < 2) {
                    sender.sendMessage(ColorUtil.color("&c/rq reset <игрок>"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ColorUtil.color("&cИгрок не онлайн."));
                    return true;
                }
                qm.resetPlayer(target);
                sender.sendMessage(ColorUtil.color("&aПрогресс сброшен для &e" + target.getName()));
                plugin.logAdminAction(sender, "reset " + target.getName());
            }

            case "complete" -> {
                if (args.length < 2) {
                    sender.sendMessage(ColorUtil.color("&c/rq complete <игрок>"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ColorUtil.color("&cИгрок не онлайн."));
                    return true;
                }
                qm.forceCompleteCurrent(target);
                sender.sendMessage(ColorUtil.color("&aТекущий квест завершён для &e" + target.getName()));
                plugin.logAdminAction(sender, "complete " + target.getName());
            }

            case "setquest" -> {
                if (args.length < 3) {
                    sender.sendMessage(ColorUtil.color("&c/rq setquest <игрок> <id>"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ColorUtil.color("&cИгрок не онлайн."));
                    return true;
                }
                int id;
                try {
                    id = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ColorUtil.color("&cНеверный ID."));
                    return true;
                }
                qm.setCurrentQuest(target, id);
                sender.sendMessage(ColorUtil.color("&aКвест установлен на &e" + id + " &aдля &e" + target.getName()));
                plugin.logAdminAction(sender, "setquest " + target.getName() + " " + id);
            }

            case "progress" -> {
                if (args.length < 4) {
                    sender.sendMessage(ColorUtil.color("&c/rq progress <игрок> <set|add|sub> <кол-во>"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ColorUtil.color("&cИгрок не онлайн."));
                    return true;
                }
                PlayerData data = PlayerData.get(target);
                if (data == null) return true;
                int amount;
                try {
                    amount = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ColorUtil.color("&cНеверное количество."));
                    return true;
                }
                int current = data.getCurrentProgress();
                switch (args[2].toLowerCase()) {
                    case "set" -> data.setCurrentProgress(amount);
                    case "add" -> data.setCurrentProgress(current + amount);
                    case "sub" -> data.setCurrentProgress(Math.max(0, current - amount));
                    default -> {
                        sender.sendMessage(ColorUtil.color("&c<set|add|sub>"));
                        return true;
                    }
                }
                plugin.getStorageManager().savePlayerData(target);
                sender.sendMessage(ColorUtil.color("&aПрогресс обновлён для &e" + target.getName()));
                plugin.logAdminAction(sender, "progress " + target.getName() + " " + args[2] + " " + amount);
            }

            case "info" -> {
                if (args.length < 2) {
                    sender.sendMessage(ColorUtil.color("&c/rq info <игрок>"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ColorUtil.color("&cИгрок не онлайн."));
                    return true;
                }
                PlayerData data = PlayerData.get(target);
                if (data == null) return true;
                Quest quest = qm.questsById.get(data.getCurrentQuest());
                sender.sendMessage(ColorUtil.color("&aИнфо о &e" + target.getName() + "&a:"));
                sender.sendMessage(ColorUtil.color("&7Квест ID: &f" + data.getCurrentQuest() + (quest != null ? " (&f" + quest.getName() + "&7)" : "")));
                sender.sendMessage(ColorUtil.color("&7Прогресс: &f" + data.getCurrentProgress() + "/" + (quest != null ? quest.getRequiredAmount() : "?")));
                sender.sendMessage(ColorUtil.color("&7Забрано ревардов: &f" + data.getClaimedRewards().size()));
            }

            case "event" -> {
                if (args.length < 3 || !args[1].equalsIgnoreCase("multiplier")) {
                    sender.sendMessage(ColorUtil.color("&c/rq event multiplier <x2..x10> <минуты> или off"));
                    return true;
                }
                if (args[2].equalsIgnoreCase("off")) {
                    plugin.progressMultiplier = 1.0;
                    plugin.multiplierEndTime = 0;
                    plugin.saveConfig();
                    sender.sendMessage(ColorUtil.color("&aМультипликатор выключен."));
                    plugin.logAdminAction(sender, "event multiplier off");
                    return true;
                }
                double mult;
                try {
                    mult = Double.parseDouble(args[2]);
                    if (mult < 1.1 || mult > 10.0) throw new Exception();
                } catch (Exception e) {
                    sender.sendMessage(ColorUtil.color("&cМножитель 1.1–10.0"));
                    return true;
                }
                int minutes = Integer.parseInt(args[3]);
                plugin.progressMultiplier = mult;
                plugin.multiplierEndTime = System.currentTimeMillis() + (minutes * 60L * 1000L);
                plugin.saveConfig();
                Bukkit.broadcastMessage(ColorUtil.color("&6[Ивент] &aПрогресс x" + mult + " на " + minutes + " мин!"));
                sender.sendMessage(ColorUtil.color("&aЗапущен."));
                plugin.logAdminAction(sender, "event multiplier x" + mult + " на " + minutes + " мин");
            }

            case "maintenance" -> {
                if (args.length < 2) {
                    sender.sendMessage(ColorUtil.color("&aMaintenance: " + (plugin.maintenanceMode ? "&aвкл" : "&cвыкл")));
                    sender.sendMessage(ColorUtil.color("&7Whitelist: " + plugin.maintenanceWhitelist));
                    return true;
                }
                switch (args[1].toLowerCase()) {
                    case "on" -> { plugin.maintenanceMode = true; plugin.saveConfig(); sender.sendMessage(ColorUtil.color("&aВключён.")); plugin.logAdminAction(sender, "maintenance on"); }
                    case "off" -> { plugin.maintenanceMode = false; plugin.saveConfig(); sender.sendMessage(ColorUtil.color("&aВыключен.")); plugin.logAdminAction(sender, "maintenance off"); }
                    case "add" -> { plugin.maintenanceWhitelist.add(args[2]); plugin.saveConfig(); sender.sendMessage(ColorUtil.color("&aДобавлен " + args[2])); plugin.logAdminAction(sender, "maintenance add " + args[2]); }
                    case "remove" -> { plugin.maintenanceWhitelist.remove(args[2]); plugin.saveConfig(); sender.sendMessage(ColorUtil.color("&aУдалён " + args[2])); plugin.logAdminAction(sender, "maintenance remove " + args[2]); }
                    case "clear" -> { plugin.maintenanceWhitelist.clear(); plugin.saveConfig(); sender.sendMessage(ColorUtil.color("&aОчищен.")); plugin.logAdminAction(sender, "maintenance clear"); }
                    default -> sender.sendMessage(ColorUtil.color("&c/rq maintenance <on|off|add|remove|clear> [ник]"));
                }
            }

            case "adminlog", "changelog" -> {
                sender.sendMessage(ColorUtil.color("&aПоследние 50 действий:"));
                List<String> log = plugin.getAdminLog();
                int start = Math.max(0, log.size() - 50);
                for (int i = start; i < log.size(); i++) {
                    sender.sendMessage(ColorUtil.color("&7" + log.get(i)));
                }
            }

            case "queststatus" -> {
                sender.sendMessage(ColorUtil.color("&aСтатус квестов:"));
                Map<Integer, Integer> countPerStage = new HashMap<>();
                int total = Bukkit.getOnlinePlayers().size();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    PlayerData data = PlayerData.get(p);
                    if (data != null) {
                        int stage = (data.getCurrentQuest() - 1) / qm.stageItemIds.get(0).size() + 1; // простая логика по этапам
                        countPerStage.merge(stage, 1, Integer::sum);
                    }
                }
                for (int i = 1; i <= qm.getStageCount(); i++) {
                    int cnt = countPerStage.getOrDefault(i, 0);
                    double perc = total == 0 ? 0 : (cnt * 100.0 / total);
                    sender.sendMessage(ColorUtil.color("&aЭтап " + i + ": &f" + cnt + " (&e" + String.format("%.1f", perc) + "%&a)"));
                }
            }
            default -> sender.sendMessage(ColorUtil.color("&cНеизвестная подкоманда."));
        }
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return subCommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .sorted()
                    .toList();
        }
        return null;
    }
}