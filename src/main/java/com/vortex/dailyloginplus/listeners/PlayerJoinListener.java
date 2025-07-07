package com.vortex.dailyloginplus.listeners;

import com.vortex.dailyloginplus.DailyLoginPlus;
import com.vortex.dailyloginplus.config.ConfigManager;
import com.vortex.dailyloginplus.data.DataManager;
import com.vortex.dailyloginplus.data.PlayerData;
import com.vortex.dailyloginplus.gui.GUIManager;
import com.vortex.dailyloginplus.rewards.RewardManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Duration;
import java.util.UUID;

public class PlayerJoinListener implements Listener {

    private final DailyLoginPlus plugin;
    private final DataManager dataManager;
    private final GUIManager guiManager;
    private final RewardManager rewardManager;
    private final ConfigManager configManager;
    private final ZoneId serverTimeZone;

    public PlayerJoinListener(DailyLoginPlus plugin, DataManager dataManager, GUIManager guiManager, RewardManager rewardManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.guiManager = guiManager;
        this.rewardManager = rewardManager;
        this.configManager = plugin.getConfigManager();
        
        this.serverTimeZone = configManager.getServerTimeZone();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Asynchronously load/get player data to prevent lag on join
        new BukkitRunnable() {
            @Override
            public void run() {
                PlayerData playerData = dataManager.getPlayerData(playerUUID); // This will load or create data

                LocalDateTime now = LocalDateTime.now(serverTimeZone);
                LocalDate today = now.toLocalDate();
                LocalDate lastClaimDateMonthly = playerData.getLastClaimDateMonthly();
                LocalDateTime lastLoginDateTime = playerData.getLastLoginDateTime();

                boolean claimedTodayMonthly = (lastClaimDateMonthly != null && lastClaimDateMonthly.isEqual(today));

                // --- Streak System Logic ---
                handleLoginStreak(player, playerData, now, lastLoginDateTime);

                // --- Daily Monthly Reward Check ---
                if (configManager.getConfig().getBoolean("monthly-calendar.enabled", true)) {
                    if (!claimedTodayMonthly) {
                        plugin.getLogger().info(player.getName() + " has not claimed monthly reward today.");

                        // Optional: Open GUI or send reminder message
                        boolean openGui = configManager.getConfig().getBoolean("open-gui-on-join-if-unclaimed", true);
                        if (openGui && player.isOnline()) { // Check if player is still online before showing GUI
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    if (player.isOnline()) { // Double check if player is still online
                                        guiManager.openMainMenu(player); // We'll implement this later
                                        player.sendMessage(configManager.getFormattedMessage("daily-gui-reminder-on-join"));
                                    }
                                }
                            }.runTaskLater(plugin, 20L); // Delay by 1 second to ensure player fully loads
                        } else if (player.isOnline()) {
                            player.sendMessage(configManager.getFormattedMessage("daily-gui-reminder-on-join"));
                        }
                    }
                }

                // --- Playtime Tracking Start ---
                // The PlaytimeTracker scheduler handles continuous tracking.
                // This listener ensures data is loaded and updated at login.
                plugin.getLogger().fine("Data loaded for " + player.getName() + ", playtime tracking assumed active.");

                // Save player data (even if only streak or last login was updated)
                dataManager.savePlayerData(playerData);

            }
        }.runTaskAsynchronously(plugin); // Run asynchronously to not block main thread
    }

    private void handleLoginStreak(Player player, PlayerData playerData, LocalDateTime now, LocalDateTime lastLoginDateTime) {
        if (!configManager.getConfig().getBoolean("streak-system.enabled", true)) {
            return; // Streak system disabled
        }

        int currentStreak = playerData.getCurrentStreak();
        // int highestStreak = playerData.getHighestStreak(); // Highest streak updated in PlayerData.setHighestStreak

        LocalDate today = now.toLocalDate();
        LocalDate lastLoginDate = (lastLoginDateTime != null) ? lastLoginDateTime.toLocalDate() : null;

        // Initial login or consecutive day login
        if (lastLoginDateTime == null) {
            // First login ever, start streak
            currentStreak = 1;
            player.sendMessage(configManager.getFormattedMessage("streak-updated")
                                .replace("%current_streak%", String.valueOf(currentStreak)));
        } else if (lastLoginDate.isEqual(today)) {
            // Player logged in multiple times today, streak doesn't change
            // No message needed for this case normally.
            playerData.setLastLoginDateTime(now); // Update last login time even for same-day logins
            playerData.setCurrentStreak(currentStreak); // Ensure current streak doesn't change
            playerData.setHighestStreak(currentStreak); // Update highest if necessary
            return;
        } else {
            // Logged in on a different day, check streak continuation or break
            boolean streakBroken = true; // Assume broken unless proven otherwise
            
            // Check if last login was yesterday
            if (lastLoginDate.isEqual(today.minusDays(1))) {
                currentStreak++;
                player.sendMessage(configManager.getFormattedMessage("streak-updated")
                                    .replace("%current_streak%", String.valueOf(currentStreak)));
                streakBroken = false;
            } else {
                // Player missed a day (or more), check grace period
                long gracePeriodHours = configManager.getConfig().getLong("streak-system.grace-period-hours", 6);

                if (gracePeriodHours > 0) {
                     // Calculate the end of the grace period from the last login time
                     // The grace period typically applies if they missed *one* full calendar day.
                     // Example: Last login Mon 10 PM. If they login Wed 2 AM, it's 2 days missed.
                     // Grace period applies from Mon 10 PM -> Tue 10 PM + grace.
                     // More accurately: grace period applies from midnight after missed day.
                     // If last login was yesterday.minusDays(1) (i.e. 2 days ago)
                     // If today is Wednesday, last login was Monday. Grace applies from Tuesday 00:00 to Tuesday 00:00 + grace
                     
                     // Simpler approach for grace: If lastLoginDateTime is within 24 hours + grace.
                     // Or, if lastLoginDateTime's date is today.minusDays(2) AND (lastLoginDateTime + 1 day + grace) > now
                     LocalDateTime lastLoginDayEndPlusGrace = lastLoginDateTime.toLocalDate().atStartOfDay(serverTimeZone).plusDays(1).plusHours(gracePeriodHours).toLocalDateTime();
                     
                     if (now.isBefore(lastLoginDayEndPlusGrace)) {
                         // Still within grace period, allow streak to continue
                         currentStreak++;
                         player.sendMessage(configManager.getFormattedMessage("streak-updated")
                                             .replace("%current_streak%", String.valueOf(currentStreak)));
                         player.sendMessage(configManager.getFormattedMessage("streak-saved")); // Notify they were in grace
                         streakBroken = false;
                     }
                }

                // --- Streak Saver Check (if streak is still considered broken) ---
                if (streakBroken && configManager.getConfig().getBoolean("streak-system.streak-saver.enabled", false)) {
                    String saverPermission = configManager.getConfig().getString("streak-system.streak-saver.permission");
                    boolean hasPermission = (saverPermission != null && player.hasPermission(saverPermission));

                    String itemMaterialName = configManager.getConfig().getString("streak-system.streak-saver.item-cost.material");
                    int itemAmount = configManager.getConfig().getInt("streak-system.streak-saver.item-cost.amount", 0);
                    boolean hasItem = false;
                    Material itemMat = null; // Declare here for wider scope
                    if (itemMaterialName != null && !itemMaterialName.isEmpty() && itemAmount > 0) {
                        try {
                            itemMat = Material.valueOf(itemMaterialName.toUpperCase());
                            hasItem = player.getInventory().contains(itemMat, itemAmount);
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Invalid streak-saver item material in config: " + itemMaterialName + " for player " + player.getName());
                            // Do not set hasItem to true if material is invalid
                        }
                    }

                    if (hasPermission || hasItem) {
                        // Apply streak saver
                        if (hasItem) {
                            final Material finalItemMat = itemMat; // Need final for lambda
                            new BukkitRunnable() { // Remove item on main thread
                                @Override
                                public void run() {
                                    if (player.isOnline() && finalItemMat != null) { // Double check online
                                        player.getInventory().removeItem(new ItemStack(finalItemMat, itemAmount));
                                        player.updateInventory();
                                        player.sendMessage(configManager.getFormattedMessage("streak-saver-used-item").replace("%amount%", String.valueOf(itemAmount)).replace("%item%", finalItemMat.name().replace("_", " ").toLowerCase()));
                                    }
                                }
                            }.runTask(plugin);
                        } else if (hasPermission) {
                            player.sendMessage(configManager.getFormattedMessage("streak-saver-used-perm"));
                        }
                        
                        currentStreak = (lastLoginDate != null && lastLoginDate.isEqual(today.minusDays(2))) ? currentStreak + 1 : 1; // If 2 days ago, add 1. Else, just start new streak.
                        // Correct logic should be to continue the previous streak as if no day was missed
                        currentStreak = (currentStreak == 0) ? 1 : currentStreak + 1; // If initial streak was 0, set to 1. Else, increment. This might be flawed.
                        // Simpler: if streakBroken == true and saver applied, then currentStreak should increase by 1,
                        // assuming the saver "fills" the missed day.
                        currentStreak = playerData.getCurrentStreak() + 1; // Assume saver allows continuation from last streak
                        
                        usedStreakSaver = true; // Flag for external check if needed
                        streakBroken = false;
                        player.sendMessage(configManager.getFormattedMessage("streak-updated")
                                            .replace("%current_streak%", String.valueOf(currentStreak)));
                        player.sendMessage(configManager.getFormattedMessage("streak-saved"));
                    } else {
                        // Player needs a saver but doesn't have it
                        player.sendMessage(configManager.getFormattedMessage("streak-saver-not-available"));
                    }
                }
            }

            if (streakBroken) { // If still broken after grace and saver checks
                player.sendMessage(configManager.getFormattedMessage("streak-broken")
                                .replace("%last_streak%", String.valueOf(currentStreak)));
                currentStreak = 1; // Reset streak
            }
        }

        playerData.setLastLoginDateTime(now); // Always update to current login time
        playerData.setCurrentStreak(currentStreak);
        playerData.setHighestStreak(currentStreak); // Update highest streak if current is higher
        // Data will be saved by DataManager at the end of onPlayerJoin logic
    }
}
