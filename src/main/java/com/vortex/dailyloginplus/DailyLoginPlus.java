package com.vortex.dailyloginplus;

import com.vortex.dailyloginplus.commands.CommandManager;
import com.vortex.dailyloginplus.config.ConfigManager;
import com.vortex.dailyloginplus.data.DataManager;
import com.vortex.dailyloginplus.database.MySQLConnectionPool;
import com.vortex.dailyloginplus.gui.GUIManager;
import com.vortex.dailyloginplus.listeners.InventoryClickListener;
import com.vortex.dailyloginplus.listeners.PlayerJoinListener;
import com.vortex.dailyloginplus.placeholder.DailyLoginPlusExpansion;
import com.vortex.dailyloginplus.rewards.RewardManager;
import com.vortex.dailyloginplus.tasks.ChatTipScheduler;
import com.vortex.dailyloginplus.data.PlaytimeTracker; // Corrected import

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class DailyLoginPlus extends JavaPlugin {

    private static DailyLoginPlus instance;
    private ConfigManager configManager;
    private DataManager dataManager;
    private GUIManager guiManager;
    private CommandManager commandManager;
    private RewardManager rewardManager;
    private PlaytimeTracker playtimeTracker;
    private ChatTipScheduler chatTipScheduler;
    private MySQLConnectionPool connectionPool;

    @Override
    public void onEnable() {
        instance = this; // Set static instance for easy access

        // 1. Load Configurations
        saveDefaultConfig(); // Creates config.yml if it doesn't exist (from JAR)
        // Make sure messages.yml is also saved if it doesn't exist
        if (!new File(getDataFolder(), "messages.yml").exists()) {
            saveResource("messages.yml", false);
        }

        this.configManager = new ConfigManager(this);
        this.configManager.loadConfigs(); // This method now handles copying defaults as well

        // NEW: Initialize MySQL Connection Pool if MySQL is chosen
        if (configManager.getConfig().getString("data-storage-type", "YAML").equalsIgnoreCase("MYSQL")) {
            this.connectionPool = new MySQLConnectionPool(this);
            this.connectionPool.initializePool();
        }

        // 2. Initialize Data Manager (pass connectionPool if MySQL is active)
        this.dataManager = new DataManager(this, configManager, connectionPool); // Pass connectionPool
        this.dataManager.loadAllPlayerData();

        // 3. Initialize other managers
        this.rewardManager = new RewardManager(this, dataManager);
        this.guiManager = new GUIManager(this, dataManager, rewardManager);
        this.playtimeTracker = new PlaytimeTracker(this, dataManager, rewardManager);
        this.chatTipScheduler = new ChatTipScheduler(this);

        // 4. Register Listeners
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, dataManager, guiManager, rewardManager), this);
        getServer().getPluginManager().registerEvents(new InventoryClickListener(this, guiManager), this);

        // 5. Register Commands
        this.commandManager = new CommandManager(this, dataManager, guiManager, rewardManager);
        getCommand("daily").setExecutor(commandManager);
        getCommand("daily").setTabCompleter(commandManager);
        getCommand("dailystats").setExecutor(commandManager);
        getCommand("dailystats").setTabCompleter(commandManager);
        getCommand("dailyadmin").setExecutor(commandManager);
        getCommand("dailyadmin").setTabCompleter(commandManager);
        
        // 6. Start Schedulers
        playtimeTracker.startTracking();
        chatTipScheduler.startScheduler();

        // Register PlaceholderAPI Expansion
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new DailyLoginPlusExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion registered.");
        } else {
            getLogger().warning("PlaceholderAPI not found. DailyLogin+ placeholders will not work.");
        }

        getLogger().info("DailyLogin+ v" + getDescription().getVersion() + " has been enabled!");
    }

    @Override
    public void onDisable() {
        if (connectionPool != null) {
            connectionPool.shutdownPool(); // Shutdown pool gracefully
        }
        if (chatTipScheduler != null) {
            chatTipScheduler.stopScheduler(); // Stop chat tip scheduler gracefully
        }
        if (playtimeTracker != null) {
            playtimeTracker.stopTracking(); // Stop the scheduler gracefully
        }
        if (dataManager != null) {
            dataManager.saveAllPlayerData(); // Save all data on shutdown
        }
        getLogger().info("DailyLogin+ v" + getDescription().getVersion() + " has been disabled!");
    }

    // Static getter for the plugin instance (convenience for other classes)
    public static DailyLoginPlus getInstance() {
        return instance;
    }

    // Public getters for managers (so other classes can access them)
    public ConfigManager getConfigManager() { return configManager; }
    public DataManager getDataManager() { return dataManager; }
    public GUIManager getGuiManager() { return guiManager; }
    public RewardManager getRewardManager() { return rewardManager; }
    public PlaytimeTracker getPlaytimeTracker() { return playtimeTracker; }
    public ChatTipScheduler getChatTipScheduler() { return chatTipScheduler; }
    public MySQLConnectionPool getConnectionPool() { return connectionPool; }
          }
