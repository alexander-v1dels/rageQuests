package ru.ragerise.pw.ragequests.quests;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.persistence.PersistentDataType;
import ru.ragerise.pw.ragequests.Main;
import ru.ragerise.pw.ragequests.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;

public class Reward implements Displayable {

    private final int id;
    private final String name;
    private final List<String> description;
    private final Material icon;
    private final List<String> commands;
    private final int unlockAfter;

    public Reward(int id, String name, List<String> description, Material icon, List<String> commands, int unlockAfter) {
        this.id = id;
        this.name = ColorUtil.color(name);
        this.description = description.stream().map(ColorUtil::color).toList();
        this.icon = icon;
        this.commands = commands;
        this.unlockAfter = unlockAfter;
    }

    @Override
    public int getId() { return id; }

    public List<String> getCommands() { return commands; }

    public int getUnlockAfter() { return unlockAfter; }

    @Override
    public ItemStack createDisplayItem(int progress, boolean completed, boolean active, Main plugin) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);

        String status = completed ? plugin.getConfig().getString("gui.reward.status-claimed", "&7✔ Забрано")
                : active ? plugin.getConfig().getString("gui.reward.status-available", "&a▶ Кликните, чтобы забрать")
                : plugin.getConfig().getString("gui.reward.status-locked", "&c🔒 Заблокировано");

        String requirement = "";
        if (!active && !completed && unlockAfter > 0) {
            Quest reqQuest = plugin.getQuestManager().getQuestById(unlockAfter);
            String reqName = reqQuest != null ? reqQuest.getName() : "предыдущий квест";
            requirement = plugin.getConfig().getString("gui.reward.requirement-line", "&cТребуется завершить квест: &e%quest_name%")
                    .replace("%quest_name%", reqName);
        }

        List<String> lore = new ArrayList<>();
        for (String rawLine : description) {
            String processed = rawLine
                    .replace("%reward_status%", status)
                    .replace("%reward_requirement%", requirement);
            String colored = ColorUtil.color(processed);
            if (!colored.trim().isEmpty()) {
                lore.add(colored);
            }
        }

        if (active) {
            meta.addEnchant(Enchantment.LUCK, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        meta.getPersistentDataContainer().set(plugin.getQuestIdKey(), PersistentDataType.INTEGER, id);

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}