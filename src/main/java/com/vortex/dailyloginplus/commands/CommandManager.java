package com.vortex.dailyloginplus.commands;

import com.vortex.dailyloginplus.DailyLoginPlus;
import com.vortex.dailyloginplus.config.ConfigManager;
import com.vortex.dailyloginplus.data.DataManager;
import com.vortex.dailyloginplus.data.PlayerData;
import com.vortex.dailyloginplus.gui.GUIManager;
import com.vortex.dailyloginplus.rewards.RewardManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class CommandManager implements CommandExecutor, TabCompleter {

    private final DailyLoginPlus plugin;
    private final DataManager dataManager;
    private final GUIManager guiManager;
    private final RewardManager rewardManager;
    private final ConfigManager configManager;

    public CommandManager(DailyLoginPlus plugin, DataManager dataManager, GUIManager guiManager, RewardManager rewardManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.guiManager = guiManager;
        this.rewardManager = rewardManager;
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("daily")) {
            return handleDailyCommand(sender, args);
        } else if (cmd.getName().equalsIgnoreCase("dailystats")) {
            return handleDailyStatsCommand(sender, args);
        } else if (cmd.getName().equalsIgnoreCase("dailyadmin")) {
            return handleDailyAdminCommand(sender, args);
        }
        return false;
    }

    private boolean handleDailyCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(configManager.getFormattedMessage("player-only-command"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("dailyloginplus.command")) {
            player.sendMessage(configManager.getFormattedMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            guiManager.openMainMenu(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "monthly":
                guiManager.openMonthlyCalendar(player);
                break;
            case "weekly":
                guiManager.openWeeklyCalendar(player);
                break;
            case "playtime":
                guiManager.openPlaytimeRewardsGUI(player);
                break;
            case "donator":
                guiManager.openDonatorRewardsGUI(player);
                break;
            case "help":
                sendDailyHelp(player);
                break;
            default:
                player.sendMessage(configManager.getFormattedMessage("invalid-command-usage").replace("%usage%", "/daily [monthly|weekly|playtime|donator|help]"));
                break;
        }
        return true;
    }

    private boolean handleDailyStatsCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(configManager.getFormattedMessage("player-only-command"));
            return true;
        }

        Player player = (Player) sender;

        new BukkitRunnable() {
            @Override
            public void run() {
                PlayerData playerData = dataManager.getPlayerData(player.getUniqueId());
                LocalDate today = LocalDate.now(plugin.getConfigManager().getServerTimeZone());

                // Get last claimed date (monthly)
                String lastMonthlyClaimed = playerData.getLastClaimDateMonthly() != null ?
                                    playerData.getLastClaimDateMonthly().format(DateTimeFormatter.ofPattern("MMM dd,yyyy")) :
                                    "&7Never";
                boolean claimedMonthlyToday = (playerData.getLastClaimDateMonthly() != null && playerData.getLastClaimDateMonthly().isEqual(today));
                String claimedMonthlyTodayStatus = claimedMonthlyToday ? "&aYes" : "&cNo";

                // Get last claimed date (weekly)
                String lastWeeklyClaimed = playerData.getLastClaimDateWeekly() != null ?
                                    playerPlayer.getLastClaimDateWeekly().format(DateTimeFormatter.ofPattern("MMM dd,yyyy")) :
                                    "&7Never";
                // For weekly, check if claimed *this* cycle (since Monday)
                LocalDate startOfWeek = today.minusDays(today.getDayOfWeek().getValue() - 1);
                boolean claimedWeeklyThisCycle = (playerData.getLastClaimDateWeekly() != null && !playerData.getLastClaimDateWeekly().isBefore(startOfWeek));
                String claimedWeeklyThisCycleStatus = claimedWeeklyThisCycle ? "&aYes" : "&cNo";

                // Get claimed playtime milestones
                StringBuilder claimedPlaytimeSummary = new StringBuilder();
                boolean firstMilestone = true;
                if (!playerData.getClaimedPlaytimeMilestones().isEmpty()) {
                    // Sort milestones for consistent output
                    List<Integer> sortedMilestones = new ArrayList<>(playerData.getClaimedPlaytimeMilestones().keySet());
                    Collections.sort(sortedMilestones);

                    for (int milestone : sortedMilestones) {
                        if (playerData.getClaimedPlaytimeMilestones().getOrDefault(milestone, false)) { // If claimed
                            if (!firstMilestone) claimedPlaytimeSummary.append(", ");
                            claimedPlaytimeSummary.append("&a").append(milestone).append("m");
                            firstMilestone = false;
                        }
                    }
                }
                String playtimeMilestonesStatus = claimedPlaytimeSummary.length() > 0 ? claimedPlaytimeSummary.toString() : "&7None";

                // Get donator claim statuses
                StringBuilder donatorClaimsSummary = new StringBuilder();
                boolean firstTier = true;
                if (!playerData.getLastClaimDateDonatorTiers().isEmpty()) {
                     // Sort tiers for consistent output (optional)
                    List<String> sortedTiers = new ArrayList<>(playerData.getLastClaimDateDonatorTiers().keySet());
                    Collections.sort(sortedTiers); // Sort alphabetically by tier name

                     for (String tier : sortedTiers) {
                        if (!firstTier) donatorClaimsSummary.append(", ");
                        donatorClaimsSummary.append("&b").append(tier).append(": ");
                        if (playerData.hasClaimedDonatorRewardToday(tier, today)) {
                            donatorClaimsSummary.append("&aClaimed Today");
                        } else {
                            donatorClaimsSummary.append("&cNot Claimed Today");
                        }
                        firstTier = false;
                    }
                }
                String donatorClaimsStatus = donatorClaimsSummary.length() > 0 ? donatorClaimsSummary.toString() : "&7None";


                player.sendMessage(configManager.getFormattedMessage("stats.header"));
                player.sendMessage(configManager.getFormattedMessage("stats.current-streak").replace("%streak%", String.valueOf(playerData.getCurrentStreak())));
                player.sendMessage(configManager.getFormattedMessage("stats.highest-streak").replace("%highest_streak%", String.valueOf(playerData.getHighestStreak())));
                player.sendMessage(configManager.getFormattedMessage("stats.total-playtime").replace("%playtime%", String.valueOf(playerData.getTotalPlaytimeMinutes())));
                player.sendMessage(configManager.getFormattedMessage("stats.monthly-last-claimed").replace("%date%", lastMonthlyClaimed));
                player.sendMessage(configManager.getFormattedMessage("stats.monthly-claimed-today").replace("%status%", claimedMonthlyTodayStatus));
                player.sendMessage(configManager.getFormattedMessage("stats.weekly-last-claimed").replace("%date%", lastWeeklyClaimed));
                player.sendMessage(configManager.getFormattedMessage("stats.weekly-claimed-this-cycle").replace("%status%", claimedWeeklyThisCycleStatus));
                player.sendMessage(configManager.getFormattedMessage("stats.playtime-milestones").replace("%milestones%", playtimeMilestonesStatus));
                player.sendMessage(configManager.getFormattedMessage("stats.donator-claims").replace("%donator_status%", donatorClaimsStatus));
                player.sendMessage(configManager.getFormattedMessage("stats.footer"));

            }
        }.runTaskAsynchronously(plugin);
        return true;
    }

    private boolean handleDailyAdminCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dailyloginplus.admin.*")) {
            sender.sendMessage(configManager.getFormattedMessage("no-permission"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendDailyAdminHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                if (!sender.hasPermission("dailyloginplus.admin.reload")) {
                    sender.sendMessage(configManager.getFormattedMessage("no-permission"));
                    return true;
                }
                plugin.getConfigManager().loadConfigs();
                if (plugin.getConfigManager().getConfig().getString("data-storage-type", "YAML").equalsIgnoreCase("MYSQL") && plugin.getConnectionPool() != null) {
                    plugin.getConnectionPool().initializePool(); // Re-initialize pool on reload
                }
                plugin.getLogger().info("DailyLogin+ has been reloaded by " + sender.getName() + ".");
                sender.sendMessage(configManager.getFormattedMessage("plugin-reloaded"));
                break;
            case "reset":
                if (!sender.hasPermission("dailyloginplus.admin.reset")) {
                    sender.sendMessage(configManager.getFormattedMessage("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(configManager.getFormattedMessage("invalid-command-usage").replace("%usage%", "/dailyadmin reset <player>"));
                    return true;
                }
                String targetPlayerNameReset = args[1];
                
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        OfflinePlayer targetOfflinePlayer = Bukkit.getOfflinePlayer(targetPlayerNameReset); // Safer for offline players
                        UUID targetUUID = targetOfflinePlayer.getUniqueId();
                        
                        // Ensure data exists before trying to reset specific fields
                        if (!dataManager.getPlayerDataMap().containsKey(targetUUID)) {
                            sender.sendMessage(configManager.getFormattedMessage("player-not-found").replace("%player_name%", targetPlayerNameReset));
                            return; // Exit async task
                        }

                        PlayerData dataToReset = dataManager.getPlayerData(targetUUID); // This ensures PlayerData object exists
                        
                        dataToReset.setLastClaimDateMonthly(null);
                        dataToReset.setLastClaimDateWeekly(null);
                        dataToReset.getLastClaimDateDonatorTiers().clear();
                        dataToReset.setCurrentStreak(0);
                        dataToReset.setHighestStreak(0);
                        dataToReset.setLastLoginDateTime(null); // Reset last login for streak
                        dataToReset.getClaimedPlaytimeMilestones().clear(); // Reset claimed playtime milestones
                        dataToReset.setTotalPlaytimeMinutes(0); // Reset total playtime as well on full reset

                        dataManager.savePlayerData(dataToReset);

                        sender.sendMessage(configManager.getFormattedMessage("admin-reset-confirm").replace("%target_player%", targetPlayerNameReset));
                        plugin.getLogger().info("DailyLogin+ data for " + targetPlayerNameReset + " (" + targetUUID + ") has been reset by " + sender.getName() + ".");
                    }
                }.runTaskAsynchronously(plugin);
                break;
            case "give":
                if (!sender.hasPermission("dailyloginplus.admin.give")) {
                    sender.sendMessage(configManager.getFormattedMessage("no-permission"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(configManager.getFormattedMessage("invalid-command-usage").replace("%usage%", "/dailyadmin give <player> <monthly|weekly|playtime <minutes>|donator <tier>>"));
                    return true;
                }
                String targetPlayerNameGive = args[1];
                String rewardType = args[2].toLowerCase();
                Player targetPlayerGive = Bukkit.getPlayer(targetPlayerNameGive);

                if (targetPlayerGive == null) {
                    sender.sendMessage(configManager.getFormattedMessage("player-not-found").replace("%player_name%", targetPlayerNameGive));
                    return true;
                }

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        List<String> rewards = new ArrayList<>();
                        String rewardName = "";
                        
                        switch (rewardType) {
                            case "monthly":
                                LocalDate today = LocalDate.now(plugin.getConfigManager().getServerTimeZone());
                                int dayOfMonth = today.getDayOfMonth();
                                rewards = configManager.getConfig().getStringList("monthly-calendar.rewards." + dayOfMonth);
                                rewardName = "Monthly Day " + dayOfMonth;
                                if (rewards.isEmpty()) { sender.sendMessage(configManager.getFormattedMessage("admin-give-no-rewards-configured").replace("%reward_type%", rewardType)); return; }
                                PlayerData dataMonthly = dataManager.getPlayerData(targetPlayerGive.getUniqueId());
                                dataMonthly.setLastClaimDateMonthly(today); // Force claim
                                dataManager.savePlayerData(dataMonthly);
                                break;
                            case "weekly":
                                LocalDate currentWeekDay = LocalDate.now(plugin.getConfigManager().getServerTimeZone());
                                rewards = configManager.getConfig().getStringList("weekly-login.rewards." + currentWeekDay.getDayOfWeek().getValue());
                                rewardName = "Weekly Reward";
                                if (rewards.isEmpty()) { sender.sendMessage(configManager.getFormattedMessage("admin-give-no-rewards-configured").replace("%reward_type%", rewardType)); return; }
                                PlayerData dataWeekly = dataManager.getPlayerData(targetPlayerGive.getUniqueId());
                                dataWeekly.setLastClaimDateWeekly(currentWeekDay); // Force claim
                                dataManager.savePlayerData(dataWeekly);
                                break;
                            case "playtime":
                                if (args.length < 4) {
                                    sender.sendMessage(configManager.getFormattedMessage("invalid-command-usage").replace("%usage%", "/dailyadmin give <player> playtime <minutes>"));
                                    return;
                                }
                                try {
                                    int playtimeMilestone = Integer.parseInt(args[3]);
                                    rewards = configManager.getConfig().getStringList("playtime-rewards.rewards." + playtimeMilestone);
                                    rewardName = "Playtime " + playtimeMilestone + " Minutes";
                                    if (rewards.isEmpty()) { sender.sendMessage(configManager.getFormattedMessage("admin-give-no-rewards-configured").replace("%reward_type%", rewardType)); return; }
                                    PlayerData dataPlaytime = dataManager.getPlayerData(targetPlayerGive.getUniqueId());
                                    dataPlaytime.markPlaytimeMilestoneClaimed(playtimeMilestone);
                                    dataManager.savePlayerData(dataPlaytime);
                                } catch (NumberFormatException e) {
                                    sender.sendMessage(configManager.getFormattedMessage("invalid-command-usage").replace("%usage%", "/dailyadmin give <player> playtime <minutes>"));
                                    return;
                                }
                                break;
                            case "donator":
                                if (args.length < 4) {
                                    sender.sendMessage(configManager.getFormattedMessage("invalid-command-usage").replace("%usage%", "/dailyadmin give <player> donator <tier>"));
                                    return;
                                }
                                String donatorTier = args[3];
                                if (!configManager.getConfig().contains("donator-rewards.rewards." + donatorTier)) {
                                    sender.sendMessage(configManager.getFormattedMessage("admin-give-invalid-tier").replace("%tier_name%", donatorTier));
                                    return;
                                }
                                rewards = configManager.getConfig().getStringList("donator-rewards.rewards." + donatorTier + ".commands");
                                rewardName = "Donator Tier: " + donatorTier;
                                if (rewards.isEmpty()) { sender.sendMessage(configManager.getFormattedMessage("admin-give-no-rewards-configured").replace("%reward_type%", rewardType)); return; }
                                PlayerData dataDonator = dataManager.getPlayerData(targetPlayerGive.getUniqueId());
                                dataDonator.setLastClaimDateDonatorTier(donatorTier, LocalDate.now(plugin.getConfigManager().getServerTimeZone())); // Force claim
                                dataManager.savePlayerData(dataDonator);
                                break;
                            default:
                                sender.sendMessage(configManager.getFormattedMessage("invalid-command-usage").replace("%usage%", "/dailyadmin give <player> <monthly|weekly|playtime <minutes>|donator <tier>>"));
                                return;
                        }

                        if (!rewards.isEmpty()) {
                            final List<String> finalRewards = rewards;
                            final String finalRewardName = rewardName;
                            new BukkitRunnable() {
                                @Override
              
