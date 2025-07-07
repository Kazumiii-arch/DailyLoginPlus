package com.vortex.dailyloginplus.listeners;

import com.vortex.dailyloginplus.DailyLoginPlus;
import com.vortex.dailyloginplus.config.ConfigManager;
import com.vortex.dailyloginplus.data.DataManager;
import com.vortex.dailyloginplus.data.PlayerData;
import com.vortex.dailyloginplus.gui.GUIManager;
import com.vortex.dailyloginplus.rewards.RewardManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

public class InventoryClickListener implements Listener {

    private final DailyLoginPlus plugin;
    private final GUIManager guiManager;
    private final DataManager dataManager;
    private final RewardManager rewardManager;
    private final ConfigManager configManager;
    private final ZoneId serverTimeZone;

    public InventoryClickListener(DailyLoginPlus plugin, GUIManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.dataManager = plugin.getDataManager();
        this.rewardManager = plugin.getRewardManager();
        this.configManager = plugin.getConfigManager();
        
        this.serverTimeZone = configManager.getServerTimeZone();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getClickedInventory();
        ItemStack clickedItem = event.getCurrentItem();

        String mainMenuTitle = configManager.getRawMessage("gui-settings.main-menu-title");
        String monthlyTitle = configManager.getRawMessage("gui-settings.monthly-title");
        String weeklyTitle = configManager.getRawMessage("gui-settings.weekly-title");
        String playtimeTitle = configManager.getRawMessage("gui-settings.playtime-title");
        String donatorTitle = configManager.getRawMessage("gui-settings.donator-title");


        if (event.getView().getTitle().equals(mainMenuTitle) ||
            event.getView().getTitle().equals(monthlyTitle) ||
            event.getView().getTitle().equals(weeklyTitle) ||
            event.getView().getTitle().equals(playtimeTitle) ||
            event.getView().getTitle().equals(donatorTitle) ) {
            
            event.setCancelled(true); // Always cancel clicks in our GUIs to prevent item dragging

            if (clickedItem == null || clickedItem.getType().isAir()) return;

            // Handle Back Button (common to all sub-GUIs)
            if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasDisplayName() &&
                clickedItem.getItemMeta().getDisplayName().equals(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', "&7Back to Main Menu"))) {
                guiManager.openMainMenu(player);
                return; // Stop processing further for back button
            }

            // Handle Main Menu Clicks
            if (event.getView().getTitle().equals(mainMenuTitle)) {
                if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasDisplayName()) {
                    String itemName = clickedItem.getItemMeta().getDisplayName();

                    if (itemName.equals(configManager.getRawMessage("monthly-calendar.gui-icon.name"))) {
                        guiManager.openMonthlyCalendar(player);
                    } else if (itemName.equals(configManager.getRawMessage("weekly-login.gui-icon.name"))) {
                        guiManager.openWeeklyCalendar(player);
                    } else if (itemName.equals(configManager.getRawMessage("playtime-rewards.gui-icon.name"))) {
                        guiManager.openPlaytimeRewardsGUI(player);
                    } else if (itemName.equals(configManager.getRawMessage("donator-rewards.gui-icon.name"))) {
                        guiManager.openDonatorRewardsGUI(player);
                    }
                }
            }
            // Handle Monthly Calendar Clicks
            else if (event.getView().getTitle().equals(monthlyTitle)) {
                PlayerData playerData = dataManager.getPlayerData(player.getUniqueId());
                LocalDate today = LocalDate.now(serverTimeZone);
                LocalDate lastClaimDateMonthly = playerData.getLastClaimDateMonthly();

                int clickedSlot = event.getSlot();
                int dayClicked = clickedSlot + 1;

                if (dayClicked > today.lengthOfMonth() || dayClicked < 1) { // Prevent out of bounds clicks
                    player.sendMessage(configManager.getFormattedMessage("reward-not-available")); // Or specific "invalid slot" message
                    return;
                }

                // Check if the clicked day is the current day and not yet claimed in the current month
                if (dayClicked == today.getDayOfMonth() && (lastClaimDateMonthly == null || !lastClaimDateMonthly.isEqual(today))) {
                    List<String> rewards = configManager.getConfig().getStringList("monthly-calendar.rewards." + dayClicked);
                    if (!rewards.isEmpty()) {
                        rewardManager.giveRewards(player, rewards);
                        playerData.setLastClaimDateMonthly(today);
                        dataManager.savePlayerData(playerData);

                        player.sendMessage(configManager.getFormattedMessage("reward-claimed")
                                            .replace("%day_number%", String.valueOf(dayClicked)));
                        
                        guiManager.openMonthlyCalendar(player); // Re-open to show updated status
                    } else {
                        player.sendMessage(configManager.getFormattedMessage("no-available-rewards"));
                    }
                } else if (lastClaimDateMonthly != null && lastClaimDateMonthly.isEqual(today) && dayClicked == today.getDayOfMonth()) {
                     player.sendMessage(configManager.getFormattedMessage("reward-already-claimed"));
                } else {
                    player.sendMessage(configManager.getFormattedMessage("reward-not-available")); // Missed or future day
                }
            }
            // Handle Weekly Calendar Clicks
            else if (event.getView().getTitle().equals(weeklyTitle)) {
                PlayerData playerData = dataManager.getPlayerData(player.getUniqueId());
                LocalDate today = LocalDate.now(serverTimeZone);
                LocalDate lastClaimDateWeekly = playerData.getLastClaimDateWeekly();

                // Weekly claim is a single claim per weekly cycle, not per day
                // Assuming week starts Monday (DayOfWeek.MONDAY.getValue() is 1)
                LocalDate startOfWeek = today.minusDays(today.getDayOfWeek().getValue() - 1); 
                boolean hasClaimedWeeklyThisCycle = (lastClaimDateWeekly != null && !lastClaimDateWeekly.isBefore(startOfWeek));

                if (!hasClaimedWeeklyThisCycle) {
                    // If the player hasn't claimed the weekly reward this cycle
                    // The reward given is for the current day of the week based on config
                    List<String> rewards = configManager.getConfig().getStringList("weekly-login.rewards." + today.getDayOfWeek().getValue()); 
                    if (!rewards.isEmpty()) {
                        rewardManager.giveRewards(player, rewards);
                        playerData.setLastClaimDateWeekly(today); // Mark as claimed for this cycle
                        dataManager.savePlayerData(playerData);

                        player.sendMessage(configManager.getFormattedMessage("reward-claimed")
                                            .replace("%day_number%", "weekly")); // Use 'weekly' for message
                        guiManager.openWeeklyCalendar(player); // Re-open to show updated status
                    } else {
                        player.sendMessage(configManager.getFormattedMessage("no-available-rewards"));
                    }
                } else {
                    player.sendMessage(configManager.getFormattedMessage("reward-already-claimed"));
                }
            }
            // Handle Playtime Rewards GUI Clicks
            else if (event.getView().getTitle().equals(playtimeTitle)) {
                PlayerData playerData = dataManager.getPlayerData(player.getUniqueId());
                long totalPlaytime = playerData.getTotalPlaytimeMinutes();
                
                if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasDisplayName()) {
                    String rawName = clickedItem.getItemMeta().getDisplayName();
                    try {
                        // Attempt to parse "100" from "&b100 Min. Playtime Reward..."
                        // This relies on specific naming convention, which is fragile.
                        // A more robust solution involves custom NBT tags or mapping slot to milestone.
                        String namePart = net.md_5.bungee.api.ChatColor.stripColor(rawName);
                        int startIndex = namePart.indexOf("Min. Playtime Reward");
                        int milestone = -1;
                        if (startIndex != -1) {
                            String numStr = namePart.substring(0, startIndex).trim();
                            String[] numParts = numStr.split(" ");
                            if (numParts.length > 0) {
                                milestone = Integer.parseInt(numParts[0]);
                            }
                        }

                        if (milestone == -1) { // If parsing failed
                            plugin.getLogger().warning("Failed to parse playtime milestone from GUI item: " + rawName);
                            player.sendMessage(configManager.getFormattedMessage("reward-invalid-value").replace("%reward%", rawName));
                            return;
                        }

                        if (totalPlaytime >= milestone && !playerData.hasClaimedPlaytimeMilestone(milestone)) {
                             List<String> rewards = configManager.getConfig().getStringList("playtime-rewards.rewards." + milestone);
                             if (!rewards.isEmpty()) {
                                rewardManager.giveRewards(player, rewards);
                                playerData.markPlaytimeMilestoneClaimed(milestone);
                                dataManager.savePlayerData(playerData);
                                player.sendMessage(configManager.getFormattedMessage("playtime-reward-claimed")
                                                    .replace("%time_threshold%", String.valueOf(milestone)));
                                guiManager.openPlaytimeRewardsGUI(player); // Re-open to update
                            } else {
                                player.sendMessage(configManager.getFormattedMessage("no-available-rewards"));
                            }
                        } else if (playerData.hasClaimedPlaytimeMilestone(milestone)) {
                            player.sendMessage(configManager.getFormattedMessage("playtime-reward-already-claimed"));
                        } else {
                             player.sendMessage(configManager.getFormattedMessage("playtime-not-reached")
                                .replace("%time_remaining%", String.valueOf(milestone - totalPlaytime)));
                        }
                    } catch (NumberFormatException e) {
                        plugin.getLogger().severe("Error parsing playtime milestone number from item: " + rawName + " - " + e.getMessage());
                        player.sendMessage(configManager.getFormattedMessage("reward-parse-error").replace("%reward%", rawName));
                    } catch (Exception e) {
                        plugin.getLogger().severe("Unexpected error in playtime click handling for item: " + rawName + " - " + e.getMessage());
                        player.sendMessage(configManager.getFormattedMessage("reward-general-error").replace("%reward%", rawName));
                    }
                }
            }
            // Handle Donator Rewards GUI Clicks
            else if (event.getView().getTitle().equals(donatorTitle)) {
                PlayerData playerData = dataManager.getPlayerData(player.getUniqueId());
                LocalDate today = LocalDate.now(serverTimeZone);
                
                if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasDisplayName()) {
                    String rawName = clickedItem.getItemMeta().getDisplayName();
                    // Extract tier name from item name. Still fragile if names change too much.
                    // A more robust solution would be to use NBT tags to store the tier name.
                    String tierName = null;
                    if (configManager.getConfig().contains("donator-rewards.rewards")) {
                        for (String configTier : Objects.requireNonNull(configManager.getConfig().getConfigurationSection("donator-rewards.rewards")).getKeys(false)) {
                            // Check for a substring match, e.g., "VIP Rewards"
                            if (rawName.contains(configTier + " Rewards")) {
                                tierName = configTier;
                                break;
                            }
                        }
                    }

                    if (tierName == null) {
                        plugin.getLogger().warning("Could not identify donator tier from clicked item: " + rawName);
                        player.sendMessage(configManager.getFormattedMessage("reward-invalid-value").replace("%reward%", rawName));
                        return;
                    }

                    String permissionNode = configManager.getConfig().getString("donator-rewards.rewards." + tierName + ".permission");
                    boolean hasPermission = player.hasPermission(permissionNode);
                    boolean claimedToday = playerData.hasClaimedDonatorRewardToday(tierName, today);

                    if (hasPermission) {
                        if (!claimedToday) {
                            List<String> rewards = configManager.getConfig().getStringList("donator-rewards.rewards." + tierName + ".commands");
                            if (!rewards.isEmpty()) {
                                rewardManager.giveRewards(player, rewards);
                                playerData.setLastClaimDateDonatorTier(tierName, today); // Mark tier as claimed today
                                dataManager.savePlayerData(playerData);

                                player.sendMessage(configManager.getFormattedMessage("reward-claimed")
                                                    .replace("%day_number%", tierName)); // Use tier name for message
                                guiManager.openDonatorRewardsGUI(player); // Re-open to update
                            } else {
                                player.sendMessage(configManager.getFormattedMessage("no-available-rewards"));
                            }
                        } else {
                            player.sendMessage(configManager.getFormattedMessage("reward-already-claimed"));
                        }
                    } else {
                        player.sendMessage(configManager.getFormattedMessage("no-permission"));
                    }
                }
            }
        }
    }
                                                      }
