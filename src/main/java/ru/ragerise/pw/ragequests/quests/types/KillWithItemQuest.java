package ru.ragerise.pw.ragequests.quests.types;

import org.bukkit.Material;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import ru.ragerise.pw.ragequests.quests.Quest;
import ru.ragerise.pw.ragequests.quests.QuestType;

import java.util.List;

public class KillWithItemQuest extends Quest {
    private final Material item;

    public KillWithItemQuest(int id, String name, List<String> desc, Material icon, Material item, int amount, List<ItemStack> rewards) {
        super(id, name, desc, icon, amount, rewards);
        this.item = item;
    }

    @Override public QuestType getType() { return QuestType.KILL_WITH_ITEM; }

    @Override
    public boolean matchesEvent(org.bukkit.event.Event event, org.bukkit.entity.Player player) {
        if (!(event instanceof EntityDeathEvent e) || e.getEntity().getKiller() != player) return false;
        ItemStack weapon = player.getInventory().getItemInMainHand();
        return weapon.getType() == item;
    }

    @Override
    public int getIncrementAmount(org.bukkit.event.Event event) { return 1; }
}