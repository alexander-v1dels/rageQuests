package ru.ragerise.pw.ragequests.player;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerData {
    private static final Map<UUID, PlayerData> cache = new HashMap<>();

    private final Player player;
    private int currentQuest = 1;
    private int currentProgress = 0;
    private Set<Integer> claimedRewards = new HashSet<>();

    public PlayerData(Player player) {
        this.player = player;
    }

    public static PlayerData get(Player player) {
        return cache.get(player.getUniqueId());
    }

    public static void put(Player player, PlayerData data) {
        cache.put(player.getUniqueId(), data);
    }

    public static void remove(Player player) {
        cache.remove(player.getUniqueId());
    }

    public int getCurrentQuest() { return currentQuest; }
    public void setCurrentQuest(int currentQuest) { this.currentQuest = currentQuest; }

    public int getCurrentProgress() { return currentProgress; }
    public void setCurrentProgress(int currentProgress) { this.currentProgress = currentProgress; }

    public int getCompletedCount() { return currentQuest - 1; }

    public boolean isClaimed(int rewardId) { return claimedRewards.contains(rewardId); }
    public void claimReward(int rewardId) { claimedRewards.add(rewardId); }
    public void clearClaimedRewards() { claimedRewards.clear(); }
    public Set<Integer> getClaimedRewards() { return claimedRewards; }
    public void setClaimedRewards(Set<Integer> set) { this.claimedRewards = set != null ? set : new HashSet<>(); }
}