package ru.ragerise.pw.ragequests.gui;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.ragerise.pw.ragequests.Main;
import ru.ragerise.pw.ragequests.player.PlayerData;
import ru.ragerise.pw.ragequests.quests.Displayable;
import ru.ragerise.pw.ragequests.quests.Quest;
import ru.ragerise.pw.ragequests.quests.QuestType;
import ru.ragerise.pw.ragequests.quests.Reward;
import ru.ragerise.pw.ragequests.util.ColorUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class QuestGUI implements Listener {

    private final Main plugin;
    private final Map<UUID, Integer> playerOpenPage = new HashMap<>();

    public QuestGUI(Main plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, int page) {
        PlayerData data = PlayerData.get(player);
        if (data == null) {
            player.sendMessage(ColorUtil.color("&cОшибка загрузки данных. Перезайдите."));
            return;
        }

        int stageCount = plugin.getQuestManager().getStageCount();
        if (page < 1 || page > stageCount) {
            player.sendMessage(ColorUtil.color("&cТакого этапа нет!"));
            return;
        }

        int requiredQuests = 0;
        for (int i = 1; i < page; i++) {
            requiredQuests += plugin.getQuestManager().getQuestCountInStage(i);
        }

        if (data.getCompletedCount() < requiredQuests) {
            player.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.page-locked", "&cЗавершите предыдущие квесты!")));
            return;
        }

        String title = ColorUtil.color(plugin.getConfig().getString("gui.raw-title", "Квесты")
                .replace("%page%", String.valueOf(page)).trim());

        Inventory inv = Bukkit.createInventory(null, plugin.getConfig().getInt("gui.size", 54), title);

        List<Integer> itemIds = plugin.getQuestManager().getStageItemIds(page);
        List<Integer> slots = plugin.getQuestManager().getStageSlots(page);

        for (int i = 0; i < Math.min(itemIds.size(), slots.size()); i++) {
            int id = itemIds.get(i);
            int slot = slots.get(i);
            Displayable displayable = plugin.getQuestManager().getDisplayable(id);
            if (displayable == null) continue;

            boolean completed;
            boolean active;
            int progress = 0;

            if (displayable instanceof Quest q) {
                completed = q.getId() < data.getCurrentQuest();
                active = q.getId() == data.getCurrentQuest();
                progress = active ? data.getCurrentProgress() : (completed ? q.getRequiredAmount() : 0);
            } else if (displayable instanceof Reward r) {
                completed = data.isClaimed(r.getId());
                active = !completed && data.getCurrentQuest() > r.getUnlockAfter();
                progress = 0;
            } else {
                continue;
            }

            ItemStack item = displayable.createDisplayItem(progress, completed, active, plugin);
            inv.setItem(slot, item);
        }

        player.openInventory(inv);
        playerOpenPage.put(player.getUniqueId(), page);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getCurrentItem() == null) return;
        if (e.getView().title().contains(ColorUtil.color(plugin.getConfig().getString("gui.raw-title", "Квесты").trim()))) {

            e.setCancelled(true);

            ItemStack clicked = e.getCurrentItem();
            ItemMeta meta = clicked.getItemMeta();
            if (meta == null) return;

            Integer id = meta.getPersistentDataContainer().get(plugin.getQuestIdKey(), PersistentDataType.INTEGER);
            if (id == null) return;

            Displayable displayable = plugin.getQuestManager().getDisplayable(id);
            if (displayable == null) return;

            PlayerData data = PlayerData.get(p);
            if (data == null) return;

            if (displayable instanceof Reward reward) {
                boolean claimed = data.isClaimed(reward.getId());
                boolean available = data.getCurrentQuest() > reward.getUnlockAfter();

                if (claimed) {
                    p.sendMessage(ColorUtil.color("&cВы уже забрали эту награду."));
                    return;
                }

                if (!available) {
                    p.sendMessage(ColorUtil.color("&cЭта награда ещё недоступна."));
                    return;
                }

                data.claimReward(reward.getId());
                plugin.getStorageManager().savePlayerData(p);

                executeRewardCommands(p, reward.getCommands());

                p.sendMessage(ColorUtil.color("&aНаграда получена!"));

                Integer currentPage = playerOpenPage.get(p.getUniqueId());
                if (currentPage != null) {
                    open(p, currentPage);
                }

                return;
            }
        }
    }

    private void executeRewardCommands(Player player, List<String> commands) {
        if (commands == null || commands.isEmpty()) return;

        Location loc = player.getLocation();

        for (String raw : commands) {
            String line = raw.trim().replace("{player}", player.getName());
            if (line.isEmpty()) continue;

            try {
                if (line.startsWith("[MESSAGE] ")) {
                    player.sendMessage(ColorUtil.color(line.substring(10)));
                } else if (line.startsWith("[ACTIONBAR] ")) {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ColorUtil.color(line.substring(12))));
                } else if (line.startsWith("[BROADCAST] ")) {
                    Bukkit.broadcastMessage(ColorUtil.color(line.substring(13)));
                } else if (line.startsWith("[COMMAND] ")) {
                    String cmd = line.substring(10).replace("{player}", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                } else if (line.startsWith("[SOUND] ")) {
                    String[] parts = line.substring(8).split("\\s+");
                    if (parts.length >= 1) {
                        Sound sound = Sound.valueOf(parts[0].toUpperCase());
                        float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
                        float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
                        player.playSound(loc, sound, volume, pitch);
                    }
                } else if (line.startsWith("[PARTICLE] ")) {
                    String[] parts = line.substring(10).split("\\s+");
                    if (parts.length >= 9) {
                        Particle particle = Particle.valueOf(parts[0].toUpperCase());

                        double x = parseRelative(parts[1], loc.getX());
                        double y = parseRelative(parts[2], loc.getY());
                        double z = parseRelative(parts[3], loc.getZ());
                        Location target = new Location(loc.getWorld(), x, y, z);

                        double ox = Double.parseDouble(parts[4]);
                        double oy = Double.parseDouble(parts[5]);
                        double oz = Double.parseDouble(parts[6]);
                        double speed = Double.parseDouble(parts[7]);
                        int count = Integer.parseInt(parts[8]);

                        player.spawnParticle(particle, target, count, ox, oy, oz, speed);
                    }
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Ошибка выполнения команды награды '" + raw + "': " + ex.getMessage());
            }
        }
    }

    private double parseRelative(String coord, double base) {
        if ("~".equals(coord)) return base;
        if (coord.startsWith("~")) {
            String num = coord.substring(1);
            return num.isEmpty() ? base : base + Double.parseDouble(num);
        }
        return Double.parseDouble(coord);
    }
}