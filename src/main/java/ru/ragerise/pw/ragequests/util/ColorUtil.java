package ru.ragerise.pw.ragequests.util;

import net.md_5.bungee.api.ChatColor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtil {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public static String color(String s) {
        if (s == null) return null;

        Matcher matcher = HEX_PATTERN.matcher(s);
        StringBuffer buffer = new StringBuffer(s.length() + 32);
        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(buffer, ChatColor.of("#" + hex).toString());
        }
        matcher.appendTail(buffer);

        return org.bukkit.ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}