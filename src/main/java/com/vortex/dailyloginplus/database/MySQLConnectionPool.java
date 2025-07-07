package com.vortex.dailyloginplus.database;

import com.vortex.dailyloginplus.DailyLoginPlus;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.SQLException;

public class MySQLConnectionPool {

    private final DailyLoginPlus plugin;
    private HikariDataSource dataSource;
    private final FileConfiguration config;

    public MySQLConnectionPool(DailyLoginPlus plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager().getConfig();
    }

    /**
     * Initializes the HikariCP connection pool.
     */
    public void initializePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            plugin.getLogger().warning("HikariCP pool is already initialized or not closed. Closing existing pool.");
            shutdownPool();
        }

        HikariConfig hikariConfig = new HikariConfig();

        // Basic connection properties
        hikariConfig.setJdbcUrl("jdbc:mysql://" + config.getString("mysql.host") + ":" + config.getInt("mysql.port") + "/" + config.getString("mysql.database"));
        hikariConfig.setUsername(config.getString("mysql.username"));
        hikariConfig.setPassword(config.getString("mysql.password"));

        // HikariCP Specific properties
        hikariConfig.setMinimumIdle(config.getInt("mysql.pool-settings.minimum-idle", 5));
        hikariConfig.setMaximumPoolSize(config.getInt("mysql.pool-settings.maximum-pool-size", 10));
        hikariConfig.setConnectionTimeout(config.getLong("mysql.pool-settings.connection-timeout", 30000));
        hikariConfig.setIdleTimeout(config.getLong("mysql.pool-settings.idle-timeout", 600000));
        hikariConfig.setMaxLifetime(config.getLong("mysql.pool-settings.max-lifetime", 1800000));
        hikariConfig.setLeakDetectionThreshold(config.getLong("mysql.pool-settings.leak-detection-threshold", 0));

        // Other JDBC properties (if necessary)
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
        hikariConfig.addDataSourceProperty("useSSL", config.getBoolean("mysql.use-ssl", false));
        hikariConfig.addDataSourceProperty("allowPublicKeyRetrieval", "true"); // Important for MySQL 8+ if not using SSL or specific authentication

        try {
            dataSource = new HikariDataSource(hikariConfig);
            plugin.getLogger().info("HikariCP connection pool initialized.");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize HikariCP connection pool: " + e.getMessage());
            dataSource = null; // Ensure dataSource is null if init fails
        }
    }

    /**
     * Retrieves a connection from the pool.
     * @return A database connection.
     * @throws SQLException if a connection cannot be obtained.
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("HikariCP DataSource is not initialized or is closed.");
        }
        return dataSource.getConnection();
    }

    /**
     * Shuts down the connection pool gracefully.
     */
    public void shutdownPool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("HikariCP connection pool shut down.");
        }
    }
}
