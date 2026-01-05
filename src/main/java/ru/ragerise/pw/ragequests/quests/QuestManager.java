package ru.ragerise.pw.ragequests.quests;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import ru.ragerise.pw.ragequests.Main;
import ru.ragerise.pw.ragequests.player.PlayerData;
import ru.ragerise.pw.ragequests.quests.types.*;
import ru.ragerise.pw.ragequests.util.ColorUtil;

import java.io.File;
import java.util.*;

public class QuestManager implements Listener {

    private final Main plugin;
    public final Map<Integer, Quest> questsById = new HashMap<>();
    private final Map<Integer, Reward> rewardsById = new HashMap<>();

    public final List<List<Integer>> stageItemIds = new ArrayList<>();
    private final List<List<Integer>> stageSlots = new ArrayList<>();

    public QuestManager(Main plugin) {
        this.plugin = plugin;
    }

    public void loadQuests() {
        questsById.clear();
        rewardsById.clear();
        stageItemIds.clear();
        stageSlots.clear();

        File file = new File(plugin.getDataFolder(), "quests.yml");
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        if (!cfg.contains("quests")) {
            plugin.getLogger().warning("quests.yml пустой или без секции 'quests'!");
            return;
        }

        for (String key : cfg.getConfigurationSection("quests").getKeys(false)) {
            var sec = cfg.getConfigurationSection("quests." + key);
            int id = Integer.parseInt(key);
            String typeStr = sec.getString("type", "").toUpperCase();
            QuestType type;
            try {
                type = QuestType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Неверный тип '" + typeStr + "' для ID " + id);
                continue;
            }

            String name = sec.getString("name", "Без имени");
            List<String> desc = sec.getStringList("description");
            Material icon = Material.matchMaterial(sec.getString("icon", "PAPER").toUpperCase());

            if (type == QuestType.REWARD) {
                List<String> commands = sec.getStringList("commands");
                int unlockAfter = sec.getInt("unlock-after", 0);
                Reward reward = new Reward(id, name, desc, icon, commands, unlockAfter);
                rewardsById.put(id, reward);
            } else {
                int amount = sec.getInt("amount", 1);
                List<ItemStack> itemRewards = new ArrayList<>();
                for (String rewardStr : sec.getStringList("rewards")) {
                    String[] parts = rewardStr.split(":");
                    Material mat = Material.matchMaterial(parts[0].toUpperCase());
                    if (mat == null) continue;
                    int amt = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                    itemRewards.add(new ItemStack(mat, amt));
                }

                Quest quest = null;
                switch (type) {
                    case COLLECT -> {
                        Material mat = Material.matchMaterial(sec.getString("material", "").toUpperCase());
                        if (mat != null) {
                            quest = new CollectQuest(id, name, desc, icon, mat, amount, itemRewards);
                        }
                    }
                    case KILL_MOB -> {
                        try {
                            EntityType entity = EntityType.valueOf(sec.getString("entity", "").toUpperCase());
                            quest = new KillMobQuest(id, name, desc, icon, entity, amount, itemRewards);
                        } catch (IllegalArgumentException ignored) {}
                    }
                    case KILL_WITH_ITEM -> {
                        Material item = Material.matchMaterial(sec.getString("item", "").toUpperCase());
                        if (item != null) {
                            quest = new KillWithItemQuest(id, name, desc, icon, item, amount, itemRewards);
                        }
                    }
                    case KILL_PLAYER_WITH_ITEM -> {
                        Material item = Material.matchMaterial(sec.getString("item", "").toUpperCase());
                        if (item != null) {
                            quest = new KillPlayerWithItemQuest(id, name, desc, icon, item, amount, itemRewards);
                        }
                    }
                    case CRAFT -> {
                        Material mat = Material.matchMaterial(sec.getString("material", "").toUpperCase());
                        if (mat != null) {
                            quest = new CraftQuest(id, name, desc, icon, mat, amount, itemRewards);
                        }
                    }
                }

                if (quest != null) {
                    questsById.put(id, quest);
                } else {
                    plugin.getLogger().warning("Ошибка создания квеста ID " + id);
                }
            }
        }

        // Загрузка этапов
        if (plugin.getConfig().contains("gui.stages")) {
            Set<String> stageKeys = plugin.getConfig().getConfigurationSection("gui.stages").getKeys(false);
            List<Integer> sortedStages = stageKeys.stream().map(Integer::parseInt).sorted().toList();

            for (int stageNum : sortedStages) {
                String path = "gui.stages." + stageNum;
                List<Integer> itemIds = plugin.getConfig().getIntegerList(path + ".item-ids");
                List<Integer> slots = plugin.getConfig().getIntegerList(path + ".slots");

                stageItemIds.add(itemIds);
                stageSlots.add(slots);
            }
        }

        plugin.debug("Загружено " + questsById.size() + " квестов и " + rewardsById.size() + " наград.");
    }

    public Displayable getDisplayable(int id) {
        Quest q = questsById.get(id);
        if (q != null) return q;
        return rewardsById.get(id);
    }

    public Quest getQuestById(int id) {
        return questsById.get(id);
    }

    public int getStageCount() {
        return stageItemIds.size();
    }

    public List<Integer> getStageItemIds(int stage) {
        if (stage < 1 || stage > stageItemIds.size()) return Collections.emptyList();
        return stageItemIds.get(stage - 1);
    }

    public List<Integer> getStageSlots(int stage) {
        if (stage < 1 || stage > stageSlots.size()) return Collections.emptyList();
        return stageSlots.get(stage - 1);
    }

    public int getQuestCountInStage(int stage) {
        if (stage < 1 || stage > stageItemIds.size()) return 0;
        return (int) stageItemIds.get(stage - 1).stream().filter(id -> questsById.containsKey(id)).count();
    }

    public boolean isLastQuestInStage(Quest quest) {
        for (int s = 0; s < stageItemIds.size(); s++) {
            List<Integer> ids = stageItemIds.get(s);
            int lastQuestId = -1;
            for (int id : ids) {
                if (questsById.containsKey(id)) {
                    lastQuestId = id;
                }
            }
            if (lastQuestId == quest.getId()) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onPickup(PlayerPickupItemEvent e) {
        if (e.isCancelled()) return;
        handleProgress(e.getPlayer(), e);
    }

    @EventHandler
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        handleProgress(player, e);
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        handleProgress(killer, e);
    }

    private void handleProgress(Player player, org.bukkit.event.Event event) {

        if (!plugin.isMaintenanceAllowed(player)) {
            return; // не засчитываем прогресс
        }


        PlayerData data = PlayerData.get(player);
        if (data == null) return;

        Quest currentQuest = questsById.get(data.getCurrentQuest());
        if (currentQuest == null) return;

        if (currentQuest.matchesEvent(event, player)) {
            int increment = currentQuest.getIncrementAmount(event);
            int newProgress = data.getCurrentProgress() + increment;
            data.setCurrentProgress(newProgress);

            if (newProgress >= currentQuest.getRequiredAmount()) {
                completeQuest(player, currentQuest);
            }


            plugin.getStorageManager().savePlayerData(player);
        }
    }

    private void completeQuest(Player player, Quest quest) {
        PlayerData data = PlayerData.get(player);

        List<String> list = plugin.getConfig().getStringList("messages.quest-completed");
        list.forEach(string -> {
            player.sendMessage(ColorUtil.color(string.replaceAll("%quest%", quest.getName())));
        });

        data.setCurrentQuest(quest.getId() + 1);
        data.setCurrentProgress(0);
        plugin.getStorageManager().savePlayerData(player);

        if (isLastQuestInStage(quest)) {
            int stageIndex = -1;
            for (int i = 0; i < stageItemIds.size(); i++) {
                if (stageItemIds.get(i).contains(quest.getId())) {
                    stageIndex = i;
                    break;
                }
            }
            if (stageIndex != -1) {
//                player.sendMessage(ColorUtil.color("&a✔ Этап завершён! Получаете награды за этап:"));

                for (int id : stageItemIds.get(stageIndex)) {
                    Quest q = questsById.get(id);
                    if (q != null) {
                        for (ItemStack reward : q.getRewards()) {
                            player.getInventory().addItem(reward.clone());
                        }
                    }
                }
            }
        }

        checkAndStartNext(player);
    }

    public void checkAndStartNext(Player player) {
        PlayerData data = PlayerData.get(player);
        Quest next = questsById.get(data.getCurrentQuest());
        if (next != null) {
//            String msg = plugin.getConfig().getString("messages.quest-started", "&aНачат квест: %quest%")
//                    .replace("%quest%", next.getName());
//            player.sendMessage(ColorUtil.color(msg));
        }
    }

    public void resetPlayer(Player player) {
        PlayerData data = PlayerData.get(player);
        if (data == null) return;
        data.setCurrentQuest(1);
        data.setCurrentProgress(0);
        data.clearClaimedRewards();
        plugin.getStorageManager().savePlayerData(player);
        player.sendMessage(ColorUtil.color("&eВаш прогресс по квестам сброшен."));
    }

    public void forceCompleteCurrent(Player player) {
        PlayerData data = PlayerData.get(player);
        if (data == null) return;
        Quest quest = questsById.get(data.getCurrentQuest());
        if (quest != null) {
            completeQuest(player, quest);
        }
    }

    public void setCurrentQuest(Player player, int id) {
        PlayerData data = PlayerData.get(player);
        if (data == null) return;
        data.setCurrentQuest(id);
        data.setCurrentProgress(0);
        plugin.getStorageManager().savePlayerData(player);
        checkAndStartNext(player);
    }

    public void unloadPlayerData(Player player) {
        PlayerData.remove(player);
    }
}