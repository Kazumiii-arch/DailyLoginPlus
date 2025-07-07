package com.vortex.dailyloginplus.config;

import com.vortex.dailyloginplus.DailyLoginPlus;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;

public class ConfigManager {

    private final DailyLoginPlus plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private File configFile;
    private File messagesFile;
    private ZoneId serverTimeZone;

    public ConfigManager(DailyLoginPlus plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        this.messagesFile = new File(plugin.getDataFolder(), "messages.yml");
    }

    /**
     * Loads/reloads config.yml and messages.yml.
     */
    public void loadConfigs() {
        // Ensure data folder exists
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        // Load config.yml
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false); // Save default if not exists
        }
        this.config = YamlConfiguration.loadConfiguration(configFile);
        // Add defaults from plugin's JAR if new keys are added in updates
        InputStream configStream = plugin.getResource("config.yml");
        if (configStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(configStream, StandardCharsets.UTF_8));
            config.setDefaults(defaultConfig);
            config.options().copyDefaults(true); // Copy new defaults to existing config
        }


        // Load messages.yml
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false); // Save default if not exists
        }
        this.messages = YamlConfiguration.loadConfiguration(messagesFile);
        // Add defaults from plugin's JAR if new keys are added in updates
        InputStream messagesStream = plugin.getResource("messages.yml");
        if (messagesStream != null) {
            YamlConfiguration defaultMessages = YamlConfiguration.loadConfiguration(new InputStreamReader(messagesStream, StandardCharsets.UTF_8));
            messages.setDefaults(defaultMessages);
            messages.options().copyDefaults(true); // Copy new defaults to existing messages
        }

        try {
            config.save(configFile); // Save config to apply new defaults
            messages.save(messagesFile); // Save messages to apply new defaults
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save configuration files: " + e.getMessage());
        }

        // Set server timezone after loading config
        try {
            this.serverTimeZone = ZoneId.of(config.getString("server-timezone", "GMT"));
        } catch (java.time.zone.ZoneRulesException e) {
            plugin.getLogger().warning("Invalid server-timezone configured: " + config.getString("server-timezone") + ". Falling back to GMT. Error: " + e.getMessage());
            this.serverTimeZone = ZoneId.of("GMT");
        }

        plugin.getLogger().info("Configuration files loaded successfully.");
    }

    /**
     * Saves the current config.yml to disk.
     */
    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save config.yml: " + e.getMessage());
        }
    }

    /**
     * Saves the current messages.yml to disk.
     */
    public void saveMessages() {
        try {
            messages.save(messagesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save messages.yml: " + e.getMessage());
        }
    }

    /**
     * Gets the main plugin configuration.
     * @return FileConfiguration object for config.yml
     */
    public FileConfiguration getConfig() {
        return config;
    }

    /**
     * Gets the messages configuration.
     * @return FileConfiguration object for messages.yml
     */
    public FileConfiguration getMessages() {
        return messages;
    }

    /**
     * Helper to get a formatted message from messages.yml.
     * Adds prefix and color codes.
     * @param path The path to the message in messages.yml
     * @return Formatted message string
     */
    public String getFormattedMessage(String path) {
        String message = messages.getString(path, "Message not found: " + path);
        String prefix = messages.getString("prefix", "&e&lDailyLogin+ &8» &r");
        return net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', prefix + message);
    }
    
    /**
     * Helper to get a raw message from messages.yml without prefix.
     * @param path The path to the message in messages.yml
     * @return Raw message string with color codes translated
     */
    public String getRawMessage(String path) {
        String message = messages.getString(path, "Raw message not found: " + path);
        return net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Gets the configured server time zone.
     * @return ZoneId for the server's time zone.
     */
    public ZoneId getServerTimeZone() {
        return serverTimeZone;
    }
}
