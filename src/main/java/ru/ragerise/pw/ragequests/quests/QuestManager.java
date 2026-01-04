package ru.ragerise.pw.ragequests.quests;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
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
    private final Map<Integer, Quest> questsById = new HashMap<>();
    private final Map<Integer, Reward> rewardsById = new HashMap<>();

    private final List<List<Integer>> stageItemIds = new ArrayList<>();
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

        List<Integer> allIds = new ArrayList<>();
        for (String key : cfg.getConfigurationSection("quests").getKeys(false)) {
            allIds.add(Integer.parseInt(key));
        }
        allIds.sort(Integer::compareTo);

        int stage = 1;
        List<Integer> currentStageIds = new ArrayList<>();
        List<Integer> currentStageSlots = new ArrayList<>();

        for (int id : allIds) {
            currentStageIds.add(id);
            currentStageSlots.add(currentStageIds.size() - 1);

            var sec = cfg.getConfigurationSection("quests." + id);
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
                        } catch (Exception ignored) {}
                    }
                    case KILL_WITH_ITEM -> {
                        Material mat = Material.matchMaterial(sec.getString("item", "").toUpperCase());
                        if (mat != null) {
                            quest = new KillWithItemQuest(id, name, desc, icon, mat, amount, itemRewards);
                        }
                    }
                    case KILL_PLAYER_WITH_ITEM -> {
                        Material mat = Material.matchMaterial(sec.getString("item", "").toUpperCase());
                        if (mat != null) {
                            quest = new KillPlayerWithItemQuest(id, name, desc, icon, mat, amount, itemRewards);
                        }
                    }
                }

                if (quest != null) {
                    questsById.put(id, quest);
                }
            }

            if (sec.getBoolean("end-of-stage", false)) {
                stageItemIds.add(new ArrayList<>(currentStageIds));
                stageSlots.add(new ArrayList<>(currentStageSlots));
                currentStageIds.clear();
                currentStageSlots.clear();
                stage++;
            }
        }

        if (!currentStageIds.isEmpty()) {
            stageItemIds.add(currentStageIds);
            stageSlots.add(currentStageSlots);
        }
    }

    public int getStageCount() {
        return stageItemIds.size();
    }

    public int getQuestCountInStage(int stage) {
        if (stage < 1 || stage > stageItemIds.size()) return 0;
        return stageItemIds.get(stage - 1).size();
    }

    public List<Integer> getStageItemIds(int stage) {
        if (stage < 1 || stage > stageItemIds.size()) return Collections.emptyList();
        return stageItemIds.get(stage - 1);
    }

    public List<Integer> getStageSlots(int stage) {
        if (stage < 1 || stage > stageSlots.size()) return Collections.emptyList();
        return stageSlots.get(stage - 1);
    }

    public Displayable getDisplayable(int id) {
        if (questsById.containsKey(id)) return questsById.get(id);
        if (rewardsById.containsKey(id)) return rewardsById.get(id);
        return null;
    }

    public Quest getQuestById(int id) {
        return questsById.get(id);
    }

    @EventHandler
    public void onPickup(PlayerPickupItemEvent e) {
        handleProgress(e.getPlayer(), e);
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        handleProgress(killer, e);
    }

    private void handleProgress(Player player, org.bukkit.event.Event event) {
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
                player.sendMessage(ColorUtil.color("&a✔ Этап завершён! Получаете награды за этап:"));

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

    private boolean isLastQuestInStage(Quest quest) {
        for (List<Integer> stage : stageItemIds) {
            if (stage.contains(quest.getId()) && stage.indexOf(quest.getId()) == stage.size() - 1) {
                return true;
            }
        }
        return false;
    }

    public void checkAndStartNext(Player player) {
        PlayerData data = PlayerData.get(player);
        Quest next = questsById.get(data.getCurrentQuest());
        if (next != null) {
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