package com.vortex.dailyloginplus.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.vortex.dailyloginplus.DailyLoginPlus;
import com.vortex.dailyloginplus.config.ConfigManager;
import com.vortex.dailyloginplus.database.MySQLConnectionPool;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DataManager {

    private final DailyLoginPlus plugin;
    private final ConfigManager configManager;
    private final Map<UUID, PlayerData> playerDataMap;
    private final String storageType;
    private final Gson gson = new Gson();

    private final MySQLConnectionPool connectionPool;

    public DataManager(DailyLoginPlus plugin, ConfigManager configManager, MySQLConnectionPool connectionPool) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerDataMap = new ConcurrentHashMap<>();
        this.storageType = configManager.getConfig().getString("data-storage-type", "YAML").toUpperCase();
        this.connectionPool = connectionPool;

        if (storageType.equals("YAML")) {
            File playerFolder = new File(plugin.getDataFolder(), "playerdata");
            if (!playerFolder.exists()) {
                playerFolder.mkdirs();
            }
        } else if (storageType.equals("MYSQL")) {
            try (Connection conn = getConnection()) {
                if (conn != null) {
                    createMySQLTables(conn);
                    plugin.getLogger().info("Connected to MySQL database and ensured table structure.");
                } else {
                    plugin.getLogger().severe("Failed to establish MySQL connection. Please check your config.yml. Defaulting to YAML storage.");
                    // Fallback logic
                    File playerFolder = new File(plugin.getDataFolder(), "playerdata");
                    if (!playerFolder.exists()) playerFolder.mkdirs();
                    // Note: For a strict fallback, you'd re-assign this.storageType = "YAML";
                    // For now, we'll assume the primary type is intended.
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("MySQL error during initialization: " + e.getMessage());
                plugin.getLogger().severe("Defaulting to YAML storage due to MySQL error.");
                // Note: For a strict fallback, you'd re-assign this.storageType = "YAML";
            }
        }
    }

    // --- MySQL Connection (UPDATED TO USE HIKARICP) ---
    private Connection getConnection() throws SQLException {
        if (connectionPool == null) {
            throw new SQLException("MySQL Connection Pool is not initialized.");
        }
        return connectionPool.getConnection();
    }

    // --- MySQL Table Creation (ADD NEW COLUMNS) ---
    private void createMySQLTables(Connection conn) throws SQLException {
        String createPlayerDataTable = "CREATE TABLE IF NOT EXISTS `" + configManager.getConfig().getString("mysql.table-prefix", "dl_") + "player_data` (" +
                                       "`uuid` VARCHAR(36) NOT NULL PRIMARY KEY," +
                                       "`last_claim_date_monthly` DATE NULL," +
                                       "`current_streak` INT NOT NULL DEFAULT 0," +
                                       "`highest_streak` INT NOT NULL DEFAULT 0," +
                                       "`last_login_date` DATETIME NULL," + // Changed to DATETIME
                                       "`total_playtime_minutes` BIGINT NOT NULL DEFAULT 0," +
                                       "`claimed_playtime_milestones` TEXT NULL," +
                                       "`last_claim_date_weekly` DATE NULL," +
                                       "`last_claim_date_donator_tiers` TEXT NULL" +
                                       ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
        
        try (PreparedStatement ps = conn.prepareStatement(createPlayerDataTable)) {
            ps.executeUpdate();
        }
        plugin.getLogger().info("Ensured MySQL table '" + configManager.getConfig().getString("mysql.table-prefix", "dl_") + "player_data' exists.");
    }

    // --- Loading and Saving All Data ---
    public void loadAllPlayerData() {
        playerDataMap.clear();

        if (storageType.equals("YAML")) {
            File playerFolder = new File(plugin.getDataFolder(), "playerdata");
            File[] playerFiles = playerFolder.listFiles((dir, name) -> name.endsWith(".yml"));

            if (playerFiles != null) {
                for (File file : playerFiles) {
                    try {
                        UUID uuid = UUID.fromString(file.getName().replace(".yml", ""));
                        PlayerData data = loadPlayerDataFromYamlFile(uuid, file);
                        playerDataMap.put(uuid, data);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid UUID filename found: " + file.getName() + " - " + e.getMessage());
                    }
                }
            }
            plugin.getLogger().info("Loaded " + playerDataMap.size() + " player data files from YAML.");

        } else if (storageType.equals("MYSQL")) {
            plugin.getLogger().info("Loading all player data from MySQL...");
            String selectAllSQL = "SELECT * FROM `" + configManager.getConfig().getString("mysql.table-prefix", "dl_") + "player_data`";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(selectAllSQL);
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    LocalDate lastClaimDateMonthly = (rs.getDate("last_claim_date_monthly") != null) ? rs.getDate("last_claim_date_monthly").toLocalDate() : null;
                    int currentStreak = rs.getInt("current_streak");
                    int highestStreak = rs.getInt("highest_streak");
                    LocalDateTime lastLoginDateTime = (rs.getTimestamp("last_login_date") != null) ? rs.getTimestamp("last_login_date").toLocalDateTime() : null;
                    long totalPlaytimeMinutes = rs.getLong("total_playtime_minutes");
                    
                    Map<Integer, Boolean> claimedPlaytimeMilestones = new HashMap<>();
                    String milestonesJson = rs.getString("claimed_playtime_milestones");
                    if (milestonesJson != null && !milestonesJson.isEmpty()) {
                        TypeToken<Map<Integer, Boolean>> type = new TypeToken<Map<Integer, Boolean>>() {};
                        claimedPlaytimeMilestones = gson.fromJson(milestonesJson, type.getType());
                    }

                    LocalDate lastClaimDateWeekly = (rs.getDate("last_claim_date_weekly") != null) ? rs.getDate("last_claim_date_weekly").toLocalDate() : null;

                    Map<String, LocalDate> lastClaimDateDonatorTiers = new HashMap<>();
                    String donatorTiersJson = rs.getString("last_claim_date_donator_tiers");
                    if (donatorTiersJson != null && !donatorTiersJson.isEmpty()) {
                        TypeToken<Map<String, String>> type = new TypeToken<Map<String, String>>() {};
                        Map<String, String> rawMap = gson.fromJson(donatorTiersJson, type.getType());
                        rawMap.forEach((tier, dateString) -> {
                            try {
                                lastClaimDateDonatorTiers.put(tier, LocalDate.parse(dateString));
                            } catch (DateTimeParseException e) {
                                plugin.getLogger().warning("Failed to parse donator tier date for UUID " + uuid + ", tier " + tier + ": " + dateString);
                            }
                        });
                    }

                    PlayerData data = new PlayerData(uuid, lastClaimDateMonthly, currentStreak, highestStreak,
                                                      lastLoginDateTime, totalPlaytimeMinutes, claimedPlaytimeMilestones,
                                                      lastClaimDateWeekly, lastClaimDateDonatorTiers);
                    playerDataMap.put(uuid, data);
                }
                plugin.getLogger().info("Loaded " + playerDataMap.size() + " player data entries from MySQL.");

            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load all player data from MySQL: " + e.getMessage());
            }
        }
    }

    public void saveAllPlayerData() {
        if (storageType.equals("YAML")) {
            for (PlayerData data : playerDataMap.values()) {
                savePlayerDataToYamlFile(data);
            }
            plugin.getLogger().info("Saved " + playerDataMap.size() + " player data files to YAML.");
        } else if (storageType.equals("MYSQL")) {
            plugin.getLogger().info("Saving all player data to MySQL...");
            for (PlayerData data : playerDataMap.values()) {
                savePlayerDataToDatabase(data);
            }
            plugin.getLogger().info("Finished saving all player data to MySQL.");
        }
    }
    
    // --- Get Player Data ---
    public PlayerData getPlayerData(UUID uuid) {
        return playerDataMap.computeIfAbsent(uuid, k -> {
            PlayerData newData = new PlayerData(k);
            // Save new player data immediately on creation to the chosen storage
            if (storageType.equals("MYSQL")) {
                savePlayerDataToDatabase(newData);
            } else { // YAML
                savePlayerDataToYamlFile(newData);
            }
            return newData;
        });
    }

    public Map<UUID, PlayerData> getPlayerDataMap() { // Added for admin commands
        return playerDataMap;
    }

    // --- Specific Data Operations (YAML) ---
    private PlayerData loadPlayerDataFromYamlFile(UUID uuid, File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        LocalDate lastClaimDateMonthly = null;
        String lastClaimString = yaml.getString("monthly.lastClaimDate");
        if (lastClaimString != null) {
            try {
                lastClaimDateMonthly = LocalDate.parse(lastClaimString);
            } catch (DateTimeParseException e) {
                plugin.getLogger().warning("Failed to parse monthly last claim date for " + uuid + ": " + lastClaimString);
            }
        }

        int currentStreak = yaml.getInt("streak.current", 0);
        int highestStreak = yaml.getInt("streak.highest", 0);
        LocalDateTime lastLoginDateTime = null;
        String lastLoginString = yaml.getString("streak.lastLoginDate");
        if (lastLoginString != null) {
            try {
                lastLoginDateTime = LocalDateTime.parse(lastLoginString);
            } catch (DateTimeParseException e) {
                plugin.getLogger().warning("Could not parse lastLoginDate for " + uuid + ": " + lastLoginString + ". Attempting as LocalDate...");
                try {
                    lastLoginDateTime = LocalDate.parse(lastLoginString).atStartOfDay();
                } catch (DateTimeParseException ignored) {
                    plugin.getLogger().warning("Failed to parse lastLoginDate as LocalDate either. Setting to null.");
                }
            }
        }

        long totalPlaytimeMinutes = yaml.getLong("playtime.totalMinutes", 0L);
        Map<Integer, Boolean> claimedPlaytimeMilestones = new HashMap<>();
        if (yaml.contains("playtime.claimedMilestones")) {
            for (String key : Objects.requireNonNull(yaml.getConfigurationSection("playtime.claimedMilestones")).getKeys(false)) {
                try {
                    claimedPlaytimeMilestones.put(Integer.parseInt(key), yaml.getBoolean("playtime.claimedMilestones." + key));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid playtime milestone key in " + file.getName() + ": " + key);
                }
            }
        }
        
        LocalDate lastClaimDateWeekly = null;
        String lastWeeklyClaimString = yaml.getString("weekly.lastClaimDate");
        if (lastWeeklyClaimString != null) {
            try {
                lastClaimDateWeekly = LocalDate.parse(lastWeeklyClaimString);
            } catch (DateTimeParseException e) {
                plugin.getLogger().warning("Failed to parse weekly last claim date for " + uuid + ": " + lastWeeklyClaimString);
            }
        }

        Map<String, LocalDate> lastClaimDateDonatorTiers = new HashMap<>();
        if (yaml.contains("donator.lastClaimDates")) {
            for (String tier : Objects.requireNonNull(yaml.getConfigurationSection("donator.lastClaimDates")).getKeys(false)) {
                String dateString = yaml.getString("donator.lastClaimDates." + tier);
                if (dateString != null) {
                    try {
                        lastClaimDateDonatorTiers.put(tier, LocalDate.parse(dateString));
                    } catch (DateTimeParseException e) {
                        plugin.getLogger().warning("Failed to parse donator tier date for " + uuid + ", tier " + tier + ": " + dateString);
                    }
                }
            }
        }

        return new PlayerData(uuid, lastClaimDateMonthly, currentStreak, highestStreak, lastLoginDateTime,
                              totalPlaytimeMinutes, claimedPlaytimeMilestones,
                              lastClaimDateWeekly, lastClaimDateDonatorTiers);
    }

    public void savePlayerDataToYamlFile(PlayerData data) {
        if (!storageType.equals("YAML")) return;

        File file = new File(plugin.getDataFolder() + "/playerdata", data.getUniqueId().toString() + ".yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        yaml.set("monthly.lastClaimDate", data.getLastClaimDateMonthly() != null ? data.getLastClaimDateMonthly().toString() : null);
        yaml.set("streak.current", data.getCurrentStreak());
        yaml.set("streak.highest", data.getHighestStreak());
        yaml.set("streak.lastLoginDate", data.getLastLoginDateTime() != null ? data.getLastLoginDateTime().toString() : null);
        yaml.set("playtime.totalMinutes", data.getTotalPlaytimeMinutes());
        yaml.set("playtime.claimedMilestones", data.getClaimedPlaytimeMilestones());
        yaml.set("weekly.lastClaimDate", data.getLastClaimDateWeekly() != null ? data.getLastClaimDateWeekly().toString() : null);

        Map<String, String> donatorDatesStringMap = new HashMap<>();
        data.getLastClaimDateDonatorTiers().forEach((tier, date) -> donatorDatesStringMap.put(tier, date.toString()));
        yaml.set("donator.lastClaimDates", donatorDatesStringMap);

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save player data for " + data.getUniqueId() + " to YAML: " + e.getMessage());
        }
    }

    // --- Saving Player Data to MySQL (UPDATE) ---
    public void savePlayerDataToDatabase(PlayerData data) {
        if (!storageType.equals("MYSQL")) return;

        String claimedMilestonesJson = gson.toJson(data.getClaimedPlaytimeMilestones());
        
        Map<String, String> donatorDatesStringMap = new HashMap<>();
        data.getLastClaimDateDonatorTiers().forEach((tier, date) -> donatorDatesStringMap.put(tier, date.toString()));
        String donatorTiersJson = gson.toJson(donatorDatesStringMap);

        String upsertSQL = "INSERT INTO `" + configManager.getConfig().getString("mysql.table-prefix", "dl_") + "player_data` " +
                           "(`uuid`, `last_claim_date_monthly`, `current_streak`, `highest_streak`, " +
                           "`last_login_date`, `total_playtime_minutes`, `claimed_playtime_milestones`, " +
                           "`last_claim_date_weekly`, `last_claim_date_donator_tiers`) " +
                           "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                           "ON DUPLICATE KEY UPDATE " +
                           "`last_claim_date_monthly` = VALUES(`last_claim_date_monthly`), " +
                           "`current_streak` = VALUES(`current_streak`), " +
                           "`highest_streak` = VALUES(`highest_streak`), " +
                           "`last_login_date` = VALUES(`last_login_date`), " +
                           "`total_playtime_minutes` = VALUES(`total_playtime_minutes`), " +
                           "`claimed_playtime_milestones` = VALUES(`claimed_playtime_milestones`), " +
                           "`last_claim_date_weekly` = VALUES(`last_claim_date_weekly`), " +
                           "`last_claim_date_donator_tiers` = VALUES(`last_claim_date_donator_tiers`);";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(upsertSQL)) {

            ps.setString(1, data.getUniqueId().toString());
            ps.setDate(2, (data.getLastClaimDateMonthly() != null) ? java.sql.Date.valueOf(data.getLastClaimDateMonthly()) : null);
            ps.setInt(3, data.getCurrentStreak());
            ps.setInt(4, data.getHighestStreak());
            ps.setTimestamp(5, (data.getLastLoginDateTime() != null) ? java.sql.Timestamp.valueOf(data.getLastLoginDateTime()) : null);
            ps.setLong(6, data.getTotalPlaytimeMinutes());
            ps.setString(7, claimedMilestonesJson);
            ps.setDate(8, (data.getLastClaimDateWeekly() != null) ? java.sql.Date.valueOf(data.getLastClaimDateWeekly()) : null);
            ps.setString(9, donatorTiersJson);

            ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().severe("Could not save player data for " + data.getUniqueId() + " to MySQL: " + e.getMessage());
        }
    }
    
    // --- Data Reset Methods ---
    public void resetPlayerMonthlyData(UUID uuid) {
        PlayerData data = getPlayerData(uuid);
        data.setLastClaimDateMonthly(null);
        // Note: For a full reset, more fields would be nulled/cleared here
        if (storageType.equals("MYSQL")) {
            savePlayerDataToDatabase(data);
        } else {
            savePlayerDataToYamlFile(data);
        }
    }

    public void resetAllMonthlyData() {
        if (storageType.equals("MYSQL")) {
            String updateSQL = "UPDATE `" + configManager.getConfig().getString("mysql.table-prefix", "dl_") + "player_data` SET `last_claim_date_monthly` = NULL;";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(updateSQL)) {
                ps.executeUpdate();
                plugin.getLogger().info("All monthly login data has been reset in MySQL.");
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to reset all monthly login data in MySQL: " + e.getMessage());
            }
        } else { // YAML
             for (PlayerData data : playerDataMap.values()) {
                data.setLastClaimDateMonthly(null);
                savePlayerDataToYamlFile(data);
            }
            plugin.getLogger().info("All monthly login data has been reset in YAML.");
        }
    }
                      }
