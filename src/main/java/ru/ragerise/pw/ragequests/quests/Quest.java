package ru.ragerise.pw.ragequests.quests;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.ragerise.pw.ragequests.Main;
import ru.ragerise.pw.ragequests.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;

public abstract class Quest implements Displayable {
    protected final int id;
    protected final String name;
    protected final List<String> description;
    protected final Material icon;
    protected final int requiredAmount;
    protected final List<ItemStack> rewards;

    public Quest(int id, String name, List<String> description, Material icon, int requiredAmount, List<ItemStack> rewards) {
        this.id = id;
        this.name = ColorUtil.color(name);
        this.description = description.stream().map(ColorUtil::color).toList();
        this.icon = icon;
        this.requiredAmount = requiredAmount;
        this.rewards = rewards;
    }

    public abstract QuestType getType();

    public abstract boolean matchesEvent(org.bukkit.event.Event event, org.bukkit.entity.Player player);

    public abstract int getIncrementAmount(org.bukkit.event.Event event);

    public int getId() { return id; }
    public String getName() { return name; }
    public List<String> getDescription() { return description; }
    public Material getIcon() { return icon; }
    public int getRequiredAmount() { return requiredAmount; }
    public List<ItemStack> getRewards() { return rewards; }

    @Override
    public ItemStack createDisplayItem(int currentProgress, boolean completed, boolean active, Main plugin) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);

        List<String> lore = new ArrayList<>(description);
        lore.add("");

        if (requiredAmount > 0) {
            String progressLine = plugin.getConfig().getString("messages.progress", "&7Прогресс: %progress%&7/&%required%")
                    .replace("%progress%", String.valueOf(currentProgress))
                    .replace("%required%", String.valueOf(requiredAmount));
            lore.add(ColorUtil.color(progressLine));
        }

        String status = completed ? plugin.getConfig().getString("messages.completed", "&a✔ Завершён")
                : active ? plugin.getConfig().getString("messages.active", "&eАктивен")
                : plugin.getConfig().getString("messages.locked", "&cЗаблокировано");
        lore.add(ColorUtil.color(status));

        meta.getPersistentDataContainer().set(plugin.getQuestIdKey(), PersistentDataType.INTEGER, id);

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}