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
import ru.ragerise.pw.ragequests.quests.types.CollectQuest;
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

        if (!plugin.isMaintenanceAllowed(player)) {
            player.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.maintenance-denied",
                    "&cКвесты временно недоступны — техническое обслуживание.")));
            return;
        }

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
            Displayable displayable = plugin.getQuestManager().getDisplayable(id);
            if (displayable == null) continue;

            boolean completed;
            boolean active;
            int progress = 0;

            if (displayable instanceof Quest q) {
                completed = q.getId() < data.getCurrentQuest();
                active = q.getId() == data.getCurrentQuest();
                progress = active ? data.getCurrentProgress() : (completed ? q.getRequiredAmount() : 0);

                if (!active && !completed) {
                    Material mat = Material.matchMaterial(plugin.getConfig().getString("gui.locked-quest.material", "BARRIER").toUpperCase());
                    if (mat == null) mat = Material.BARRIER;

                    ItemStack lockedItem = new ItemStack(mat);
                    ItemMeta meta = lockedItem.getItemMeta();
                    meta.setDisplayName(ColorUtil.color(plugin.getConfig().getString("gui.locked-quest.name", "&cКвест заблокирован")));
                    meta.setLore(plugin.getConfig().getStringList("gui.locked-quest.lore").stream().map(ColorUtil::color).toList());
                    lockedItem.setItemMeta(meta);

                    inv.setItem(slots.get(i), lockedItem);
                    continue;
                }
            } else {
                // Reward
                Reward r = (Reward) displayable;
                completed = data.isClaimed(r.getId());
                active = data.getCurrentQuest() > r.getUnlockAfter();
                progress = 0;
            }

            inv.setItem(slots.get(i), displayable.createDisplayItem(progress, completed, active && !completed, plugin));
        }

        playerOpenPage.put(player.getUniqueId(), page);
        player.openInventory(inv);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getCurrentItem() == null) return;

        String title = ColorUtil.color(plugin.getConfig().getString("gui.raw-title", "Квесты").replace("%page%", ""));
        if (!e.getView().getTitle().startsWith(title)) return;

        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        Integer id = meta.getPersistentDataContainer().get(plugin.getQuestIdKey(), PersistentDataType.INTEGER);
        if (id == null) return;

        PlayerData data = PlayerData.get(p);
        if (data == null) return;

        Displayable displayable = plugin.getQuestManager().getDisplayable(id);

        // === Обработка наград (ваш существующий код) ===
        if (displayable instanceof Reward reward) {
            if (data.isClaimed(id)) {
                p.sendMessage(ColorUtil.color("&cЭта награда уже забрана!"));
                return;
            }

            if (!(reward.getUnlockAfter() == 0 || data.getCurrentQuest() > reward.getUnlockAfter())) {
                p.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.reward-locked", "&cНаграда ещё заблокирована!")));
                return;
            }

            executeRewardCommands(p, reward.getCommands());

            data.claimReward(id);
            plugin.getStorageManager().savePlayerData(p);

            p.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.reward-claimed", "&aНаграда успешно получена!")));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

            e.getInventory().setItem(e.getSlot(), reward.createDisplayItem(0, true, false, plugin));
            return;
        }

        // === Новая логика для CollectQuest ===
        if (displayable instanceof Quest quest && quest.getId() == data.getCurrentQuest() && quest.getType() == QuestType.COLLECT) {
            CollectQuest collectQuest = (CollectQuest) quest;
            Material requiredMaterial = collectQuest.getMaterial();
            int currentProgress = data.getCurrentProgress();
            int required = collectQuest.getRequiredAmount();
            int needed = required - currentProgress;

            if (needed <= 0) {
                p.sendMessage(ColorUtil.color("&cКвест уже завершён."));
                return;
            }

            // Подсчёт доступных предметов
            int available = 0;
            for (ItemStack stack : p.getInventory().getStorageContents()) {
                if (stack != null && stack.getType() == requiredMaterial) {
                    available += stack.getAmount();
                }
            }

            int toTake = Math.min(needed, available);

            if (toTake <= 0) {
//                p.sendMessage(ColorUtil.color("&cУ вас нет требуемых предметов (&7" + requiredMaterial.name().toLowerCase() + "&c)."));
                return;
            }

            // Забираем предметы
            int remaining = toTake;
            for (ItemStack stack : p.getInventory().getStorageContents()) {
                if (remaining > 0 && stack != null && stack.getType() == requiredMaterial) {
                    int remove = Math.min(remaining, stack.getAmount());
                    stack.setAmount(stack.getAmount() - remove);
                    remaining -= remove;
                }
            }

            // Обновляем прогресс
            data.setCurrentProgress(currentProgress + toTake);
            plugin.getStorageManager().savePlayerData(p);

//            p.sendMessage(ColorUtil.color("&aСдано &e" + toTake + " &a× &l" + requiredMaterial.name().toLowerCase() + "&a. Прогресс: &e" + data.getCurrentProgress() + "&7/&e" + required));
//            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.5f);

            // Если квест завершён — завершаем его
            boolean completedNow = data.getCurrentProgress() >= required;
            if (completedNow) {
                plugin.getQuestManager().forceCompleteCurrent(p);
            }

            // Переоткрываем тот же этап для актуального отображения
            Integer currentPage = playerOpenPage.get(p.getUniqueId());
            if (currentPage != null) {
                open(p, currentPage);
            }

            return;
        }

        // Для остальных квестов — просто информация (по желанию)
//        if (displayable instanceof Quest quest) {
//            p.sendMessage(ColorUtil.color("&eКвест: &f" + quest.getName()));
//        }
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

                        boolean force = parts.length > 9 && "force".equalsIgnoreCase(parts[9]);

                        player.spawnParticle(particle, target, count, ox, oy, oz, speed, force);
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