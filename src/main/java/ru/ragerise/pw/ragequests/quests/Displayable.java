package ru.ragerise.pw.ragequests.quests;

import org.bukkit.inventory.ItemStack;
import ru.ragerise.pw.ragequests.Main;

public interface Displayable {
    int getId();
    ItemStack createDisplayItem(int progress, boolean completed, boolean active, Main plugin);
}