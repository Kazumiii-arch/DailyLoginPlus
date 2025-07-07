package com.vortex.dailyloginplus.rewards;

import com.vortex.dailyloginplus.DailyLoginPlus;
import com.vortex.dailyloginplus.config.ConfigManager;
import com.vortex.dailyloginplus.data.DataManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class RewardManager {

    private final DailyLoginPlus plugin;
    private final ConfigManager configManager;
    private final DataManager dataManager; 
    private static Economy econ = null;

    public RewardManager(DailyLoginPlus plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.dataManager = dataManager;
        setupEconomy();
    }

    // --- Vault Economy Setup ---
    private boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    /**
     * Processes and gives a list of rewards to a player.
     * Each string in the list represents a different reward type.
     *
     * @param player The player to give rewards to.
     * @param rewards The list of reward strings from config.
     */
    public void giveRewards(Player player, List<String> rewards) {
        if (rewards == null || rewards.isEmpty()) {
            return;
        }

        // Bukkit API calls (like giveItem, runCommand, playSound) MUST be on main thread.
        // So, we schedule tasks to run sync.
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) { // Player might have logged out
                    plugin.getLogger().warning("Attempted to give rewards to offline player " + player.getName() + ".");
                    return;
                }

                StringBuilder rewardSummary = new StringBuilder();
                boolean firstReward = true;
                PlayerInventory inv = player.getInventory();

                for (String rewardString : rewards) {
                    if (rewardString == null || rewardString.trim().isEmpty()) continue;

                    String[] parts = rewardString.trim().split(":", 2); // Split once for type and value
                    String type = parts[0].toLowerCase();
                    String value = parts.length > 1 ? parts[1].trim() : "";

                    try {
                        switch (type) {
                            case "command":
                                // Execute a command as console (or player if specified)
                                String commandToExecute = value.replace("%player_name%", player.getName());
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToExecute);
                                rewardSummary.append(firstReward ? "" : ", ").append("Command Executed");
                                break;
                            case "item":
                                // Format: "item: MATERIAL_NAME, AMOUNT, NAME_COLOR_CODE, LORE_COLOR_CODE"
                                String[] itemParts = value.split(",", 4);
                                if (itemParts.length >= 2) {
                                    Material material = Material.valueOf(itemParts[0].toUpperCase());
                                    int amount = Integer.parseInt(itemParts[1]);
                                    ItemStack item = new ItemStack(material, amount);

                                    ItemMeta meta = item.getItemMeta();
                                    if (meta != null) {
                                        if (itemParts.length >= 3 && !itemParts[2].trim().isEmpty()) {
                                            meta.setDisplayName(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', itemParts[2].trim()));
                                        }
                                        if (itemParts.length >= 4 && !itemParts[3].trim().isEmpty()) {
                                            List<String> lore = List.of(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', itemParts[3].trim()).split("\\|"));
                                            meta.setLore(lore);
                                        }
                                        item.setItemMeta(meta);
                                    }
                                    
                                    // --- Handle full inventory for items ---
                                    if (inv.firstEmpty() == -1) { // If inventory is full
                                        player.sendMessage(configManager.getFormattedMessage("inventory-full-item-drop")
                                                            .replace("%item_name%", item.getType().name().replace("_", " "))
                                                            .replace("%amount%", String.valueOf(item.getAmount())));
                                        player.getWorld().dropItemNaturally(player.getLocation(), item); // Drop item at player's location
                                    } else {
                                        inv.addItem(item);
                                    }
                                    rewardSummary.append(firstReward ? "" : ", ").append(amount).append(" ").append(material.name().replace("_", " "));
                                } else {
                                    plugin.getLogger().warning("Invalid item format in config: '" + rewardString + "' for player " + player.getName());
                                    player.sendMessage(configManager.getFormattedMessage("reward-invalid-value").replace("%reward%", rewardString));
                                }
                                break;
                            case "eco": // Vault money
                                if (econ != null && econ.isEnabled()) {
                                    double amount = Double.parseDouble(value);
                                    econ.depositPlayer(player, amount);
                                    rewardSummary.append(firstReward ? "" : ", ").append(econ.format(amount));
                                } else {
                                    plugin.getLogger().warning("Vault/Economy not found or enabled for 'eco' reward type for player " + player.getName() + ".");
                                    player.sendMessage(configManager.getFormattedMessage("reward-vault-missing")); // New message for missing Vault
                                }
                                break;
                            case "xp": // Experience or levels
                                if (value.endsWith("L")) { // Levels
                                    int levels = Integer.parseInt(value.substring(0, value.length() - 1));
                                    player.giveExpLevels(levels);
                                    rewardSummary.append(firstReward ? "" : ", ").append(levels).append(" Levels");
                                } else { // Raw XP
                                    int xp = Integer.parseInt(value);
                                    player.giveExp(xp);
                                    rewardSummary.append(firstReward ? "" : ", ").append(xp).append(" XP");
                                }
                                break;
                            case "key": // Integration with other crate plugins (assumes command like "crate give")
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "crate give " + player.getName() + " " + value);
                                rewardSummary.append(firstReward ? "" : ", ").append(value).append(" Key");
                                break;
                            case "perm": // Permissions (temporary or permanent)
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), value.replace("%player_name%", player.getName()));
                                rewardSummary.append(firstReward ? "" : ", ").append(value).append(" Perm");
                                break;
                            case "sound": // Custom sound
                                String[] soundParts = value.split(",", 3);
                                if (soundParts.length >= 1) {
                                    Sound sound = Sound.valueOf(soundParts[0].toUpperCase());
                                    float volume = (soundParts.length >= 2) ? Float.parseFloat(soundParts[1]) : 1.0f;
                                    float pitch = (soundParts.length >= 3) ? Float.parseFloat(soundParts[2]) : 1.0f;
                                    player.playSound(player.getLocation(), sound, volume, pitch);
                                } else {
                                    plugin.getLogger().warning("Invalid sound format in config: '" + rewardString + "'");
                                }
                                break;
                            case "particle": // Custom particle
                                String[] particleParts = value.split(",", 2);
                                if (particleParts.length >= 1) {
                                    Particle particle = Particle.valueOf(particleParts[0].toUpperCase());
                                    int count = (particleParts.length >= 2) ? Integer.parseInt(particleParts[1]) : 10;
                                    player.spawnParticle(particle, player.getLocation().add(0, 1, 0), count); // Offset slightly above player
                                } else {
                                    plugin.getLogger().warning("Invalid particle format in config: '" + rewardString + "'");
                                }
                                break;
                            default:
                                plugin.getLogger().warning("Unknown reward type in config: '" + type + "'. Full reward string: '" + rewardString + "' for player " + player.getName());
                                player.sendMessage(configManager.getFormattedMessage("unknown-reward-type"));
                                break;
                        }
                        firstReward = false;
                    } catch (NumberFormatException e) {
                        plugin.getLogger().severe("Error parsing number for reward '" + rewardString + "' for player " + player.getName() + ": " + e.getMessage());
                        player.sendMessage(configManager.getFormattedMessage("reward-parse-error").replace("%reward%", rewardString));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().severe("Invalid material/sound/particle name for reward '" + rewardString + "' for player " + player.getName() + ": " + e.getMessage());
                        player.sendMessage(configManager.getFormattedMessage("reward-invalid-value").replace("%reward%", rewardString));
                    } catch (Exception e) {
                        plugin.getLogger().severe("General error processing reward '" + rewardString + "' for player " + player.getName() + ": " + e.getMessage());
                        player.sendMessage(configManager.getFormattedMessage("reward-general-error").replace("%reward%", rewardString));
                    }
                }

                // Send notification to player
                if (configManager.getConfig().getBoolean("notification-on-claim.enabled", true)) {
                    String title = configManager.getRawMessage("notification-on-claim.title");
                    String subtitle = configManager.getRawMessage("notification-on-claim.subtitle")
                                        .replace("%day_number%", String.valueOf(LocalDate.now(configManager.getServerTimeZone()).getDayOfMonth())); 
                    String chatMessage = configManager.getFormattedMessage("notification-on-claim.chat-message")
                                            .replace("%reward_summary%", rewardSummary.toString().trim());

                    player.sendTitle(title, subtitle, 10, 70, 20);
                    player.sendMessage(chatMessage);
                }

                // Play custom claim sound and particle effects (if configured)
                String soundName = configManager.getConfig().getString("gui-settings.claim-sound");
                if (soundName != null && !soundName.isEmpty()) {
                    try {
                        Sound sound = Sound.valueOf(soundName.toUpperCase());
                        player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid claim sound in config: " + soundName + " for player " + player.getName());
                    }
                }
                String particleName = configManager.getConfig().getString("gui-settings.claim-particle");
                int particleCount = configManager.getConfig().getInt("gui-settings.claim-particle-count", 20);
                if (particleName != null && !particleName.isEmpty()) {
                    try {
                        Particle particle = Particle.valueOf(particleName.toUpperCase());
                        player.spawnParticle(particle, player.getLocation().add(0, 1, 0), particleCount);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid claim particle in config: " + particleName + " for player " + player.getName());
                    }
                }

                plugin.getLogger().info("Player " + player.getName() + " claimed rewards: " + rewardSummary.toString().trim());

            }
        }.runTask(plugin); // Run on the main thread because Bukkit API calls
    }
  }
