package ru.ragerise.pw.ragequests.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.ragerise.pw.ragequests.Main;
import ru.ragerise.pw.ragequests.player.PlayerData;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class StorageManager {

    private final Main plugin;

    private enum StorageType {
        YAML, SQLITE, MYSQL
    }

    private StorageType type;
    private HikariDataSource ds = null;
    private File yamlFile;
    private YamlConfiguration yaml;

    public StorageManager(Main plugin) {
        this.plugin = plugin;
        setupStorage();
    }

    private void setupStorage() {
        String configType = plugin.getConfig().getString("storage.type", "yaml").toLowerCase();
        try {
            type = StorageType.valueOf(configType.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Неверный тип хранилища '" + configType + "', используем YAML.");
            type = StorageType.YAML;
        }

        if (type == StorageType.YAML) {
            yamlFile = new File(plugin.getDataFolder(), "players.yml");
            yaml = YamlConfiguration.loadConfiguration(yamlFile);
            return;
        }

        // SQLITE или MYSQL — используем HikariCP
        HikariConfig hikari = new HikariConfig();

        if (type == StorageType.MYSQL) {
            String host = plugin.getConfig().getString("storage.mysql.host", "localhost");
            int port = plugin.getConfig().getInt("storage.mysql.port", 3306);
            String database = plugin.getConfig().getString("storage.mysql.database", "ragequests");
            boolean useSSL = plugin.getConfig().getBoolean("storage.mysql.useSSL", false);

            hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + useSSL);
            hikari.setUsername(plugin.getConfig().getString("storage.mysql.username", "root"));
            hikari.setPassword(plugin.getConfig().getString("storage.mysql.password", ""));
        } else { // SQLITE
            String fileName = plugin.getConfig().getString("storage.sqlite-file", "data.db");
            File dbFile = new File(plugin.getDataFolder(), fileName);
            hikari.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        }

        hikari.setMaximumPoolSize(10);
        hikari.setConnectionTimeout(5000);
        ds = new HikariDataSource(hikari);

        createTable();
    }

    private void createTable() {
        if (ds == null) return;

        String sql = """
            CREATE TABLE IF NOT EXISTS ragequests_players (
                uuid VARCHAR(36) PRIMARY KEY,
                current_quest INTEGER DEFAULT 1,
                current_progress INTEGER DEFAULT 0,
                claimed_rewards TEXT DEFAULT ''
            )
            """;

        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка создания таблицы: " + e.getMessage());
        }
    }

    public void loadPlayerData(Player player) {
        String uuid = player.getUniqueId().toString();
        PlayerData data = new PlayerData(player);

        try {
            if (type == StorageType.YAML) {
                String path = uuid + ".";
                data.setCurrentQuest(yaml.getInt(path + "current_quest", 1));
                data.setCurrentProgress(yaml.getInt(path + "current_progress", 0));

                // claimed_rewards для YAML
                Set<Integer> claimed = new HashSet<>();
                claimed.addAll(yaml.getIntegerList(path + "claimed_rewards"));
                data.setClaimedRewards(claimed);

            } else {
                // SQL
                try (Connection c = ds.getConnection();
                     PreparedStatement ps = c.prepareStatement("SELECT * FROM ragequests_players WHERE uuid = ?")) {
                    ps.setString(1, uuid);
                    ResultSet rs = ps.executeQuery();

                    if (rs.next()) {
                        data.setCurrentQuest(rs.getInt("current_quest"));
                        data.setCurrentProgress(rs.getInt("current_progress"));

                        // claimed_rewards для SQL
                        Set<Integer> claimed = new HashSet<>();
                        String claimedStr = rs.getString("claimed_rewards");
                        if (claimedStr != null && !claimedStr.isEmpty()) {
                            for (String s : claimedStr.split(",")) {
                                try {
                                    claimed.add(Integer.parseInt(s.trim()));
                                } catch (Exception ignored) {}
                            }
                        }
                        data.setClaimedRewards(claimed);
                    } else {
                        // Новый игрок
                        try (PreparedStatement insert = c.prepareStatement(
                                "INSERT INTO ragequests_players (uuid, current_quest, current_progress, claimed_rewards) VALUES (?, 1, 0, '')")) {
                            insert.setString(1, uuid);
                            insert.executeUpdate();
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка загрузки данных игрока " + player.getName() + ": " + e.getMessage());
            // Фолбэк на дефолт
            data.setCurrentQuest(1);
            data.setCurrentProgress(0);
            data.setClaimedRewards(new HashSet<>());
        }

        PlayerData.put(player, data);
    }

    public void savePlayerData(Player player) {
        PlayerData data = PlayerData.get(player);
        if (data == null) return;

        String uuid = player.getUniqueId().toString();

        // Выделяем логику сохранения в отдельный Runnable
        Runnable saveTask = () -> {
            try {
                if (type == StorageType.YAML) {
                    String path = uuid + ".";
                    yaml.set(path + "current_quest", data.getCurrentQuest());
                    yaml.set(path + "current_progress", data.getCurrentProgress());
                    yaml.set(path + "claimed_rewards", new ArrayList<>(data.getClaimedRewards()));
                    yaml.save(yamlFile);
                } else if (ds != null) {
                    String sql = "REPLACE INTO ragequests_players (uuid, current_quest, current_progress, claimed_rewards) VALUES (?, ?, ?, ?)";
                    try (Connection c = ds.getConnection();
                         PreparedStatement ps = c.prepareStatement(sql)) {
                        ps.setString(1, uuid);
                        ps.setInt(2, data.getCurrentQuest());
                        ps.setInt(3, data.getCurrentProgress());

                        String joined = data.getClaimedRewards().stream()
                                .map(String::valueOf)
                                .collect(java.util.stream.Collectors.joining(","));
                        ps.setString(4, joined);

                        ps.executeUpdate();
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка сохранения данных игрока " + player.getName() + ": " + e.getMessage());
            }
        };

        // Если плагин всё ещё enabled — сохраняем асинхронно
        // Если уже disabled (например, при /plugman reload или onDisable) — сохраняем синхронно
        if (plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, saveTask);
        } else {
            saveTask.run(); // синхронно, без ошибки IllegalPluginAccessException
        }
    }

    public void shutdown() {
        if (ds != null) {
            try {
                ds.close();
            } catch (Exception ignored) {}
        }
    }
}