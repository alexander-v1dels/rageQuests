package ru.ragerise.pw.ragequests.quests.types;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import ru.ragerise.pw.ragequests.quests.Quest;
import ru.ragerise.pw.ragequests.quests.QuestType;

import java.util.List;

public class KillMobQuest extends Quest {
    private final EntityType entity;

    public KillMobQuest(int id, String name, List<String> desc, Material icon, EntityType entity, int amount, List<ItemStack> rewards) {
        super(id, name, desc, icon, amount, rewards);
        this.entity = entity;
    }

    @Override public QuestType getType() { return QuestType.KILL_MOB; }

    @Override
    public boolean matchesEvent(org.bukkit.event.Event event, org.bukkit.entity.Player player) {
        return event instanceof EntityDeathEvent e && e.getEntity().getType() == entity && e.getEntity().getKiller() == player;
    }

    @Override
    public int getIncrementAmount(org.bukkit.event.Event event) { return 1; }
}