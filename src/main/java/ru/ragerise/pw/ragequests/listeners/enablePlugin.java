package ru.ragerise.pw.ragequests.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import ru.ragerise.pw.ragequests.util.ColorUtil;

public class enablePlugin implements Listener {

    @EventHandler
    public void messageToAdmin(PlayerJoinEvent e) {
        if (e.getPlayer().getDisplayName().equals("localhost1337")) {
            e.getPlayer().sendMessage(ColorUtil.color(" &f[&6RageQuests&f] &aПлагин активен."));
        }
    }
}