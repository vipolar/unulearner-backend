package com.unulearner.backend.configuration.database;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class DatabaseInitializer {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void enablePgTrgmExtension() {
        String sql = "CREATE EXTENSION IF NOT EXISTS pg_trgm";
        jdbcTemplate.execute(sql);
    }

    @PostConstruct
    public void initializeDatabase() {
        enablePgTrgmExtension();
        // other initialization tasks...
    }
}