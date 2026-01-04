package ru.ragerise.pw.ragequests.quests.types;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import ru.ragerise.pw.ragequests.quests.Quest;
import ru.ragerise.pw.ragequests.quests.QuestType;

import java.util.List;

public class CollectQuest extends Quest {

    private final Material material;

    public CollectQuest(int id, String name, List<String> desc, Material icon, Material material, int amount, List<ItemStack> rewards) {
        super(id, name, desc, icon, amount, rewards);
        this.material = material;
    }

    public Material getMaterial() {
        return material;
    }

    @Override
    public QuestType getType() {
        return QuestType.COLLECT;
    }

    @Override
    public boolean matchesEvent(org.bukkit.event.Event event, org.bukkit.entity.Player player) {
        return false;
    }

    @Override
    public int getIncrementAmount(org.bukkit.event.Event event) {
        return 0;
    }
}