package com.navan.travel.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.Statement;

@Configuration
public class DatabaseConfig {

    @Value("${spring.datasource.url:jdbc:sqlite:data/travel_invoice.db}")
    private String dbUrl;

    @PostConstruct
    public void initDirectory() {
        // Ensure data directory exists if local file is used
        if (dbUrl.startsWith("jdbc:sqlite:")) {
            String path = dbUrl.substring("jdbc:sqlite:".length());
            int queryIdx = path.indexOf('?');
            if (queryIdx != -1) {
                path = path.substring(0, queryIdx);
            }
            File file = new File(path);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
        }
    }

    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties properties) {
        HikariDataSource dataSource = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();

        // Initialize pragmas directly on the newly created database
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode = WAL;");
            stmt.execute("PRAGMA busy_timeout = 5000;");
            stmt.execute("PRAGMA foreign_keys = ON;");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize SQLite pragmas", e);
        }

        // Ensure every pooled connection enforces foreign keys and busy timeout
        dataSource.setConnectionInitSql("PRAGMA busy_timeout = 5000; PRAGMA foreign_keys = ON;");

        return dataSource;
    }
}
