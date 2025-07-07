package com.vortex.dailyloginplus.tasks;

import com.vortex.dailyloginplus.DailyLoginPlus;
import com.vortex.dailyloginplus.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

import me.clip.placeholderapi.PlaceholderAPI; // Optional: PlaceholderAPI for dynamic messages

public class ChatTipScheduler {

    private final DailyLoginPlus plugin;
    private final ConfigManager configManager;
    private BukkitRunnable task;
    private final Random random = new Random();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a"); // e.g., 07:30 PM

    public ChatTipScheduler(DailyLoginPlus plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    public void startScheduler() {
        if (!configManager.getMessages().getBoolean("chat-tips.enabled", false)) {
            plugin.getLogger().info("Chat tips are disabled in messages.yml. Not starting chat tip scheduler.");
            return;
        }

        long intervalMinutes = configManager.getMessages().getLong("chat-tips.interval-minutes", 15);
        if (intervalMinutes <= 0) {
            plugin.getLogger().warning("Chat tips interval is set to 0 or less. Not starting scheduler.");
            return;
        }

        long intervalTicks = intervalMinutes * 60 * 20; // Convert minutes to ticks

        task = new BukkitRunnable() {
            @Override
            public void run() {
                List<String> tips = configManager.getMessages().getStringList("chat-tips.messages");
                if (tips.isEmpty()) {
                    plugin.getLogger().warning("Chat tips list is empty. No tips to broadcast.");
                    return;
                }
                
                // Ensure there are online players before broadcasting
                if (Bukkit.getOnlinePlayers().isEmpty()) {
                    return;
                }

                String randomTip = tips.get(random.nextInt(tips.size()));
                
                // Replace dynamic placeholders like time
                randomTip = randomTip.replace("%current_time%", LocalDateTime.now(configManager.getServerTimeZone()).format(timeFormatter));

                // Process PlaceholderAPI placeholders if the plugin is enabled
                if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                    randomTip = PlaceholderAPI.setPlaceholders(null, randomTip); // Use null for global context
                }

                // Send the message to all online players
                Bukkit.broadcastMessage(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', randomTip));
            }
        };
        // Schedule to run async to avoid blocking main thread, as broadcast is safe
        task.runTaskTimerAsynchronously(plugin, intervalTicks, intervalTicks);
        plugin.getLogger().info("Chat tip scheduler started, broadcasting every " + intervalMinutes + " minutes.");
    }

    public void stopScheduler() {
        if (task != null) {
            task.cancel();
            task = null;
            plugin.getLogger().info("Chat tip scheduler stopped.");
        }
    }
}
