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

    public void setDefaultUuidForIdColumns() {
        String sql = """
            DO $$ 
            DECLARE 
                r RECORD;
            BEGIN
                FOR r IN (SELECT table_name 
                          FROM information_schema.columns 
                          WHERE column_name = 'id' 
                            AND data_type = 'uuid'
                            AND column_default IS NULL) 
                LOOP
                    EXECUTE format('ALTER TABLE %I ALTER COLUMN id SET DEFAULT gen_random_uuid()', r.table_name);
                END LOOP;
            END $$;
        """;
        jdbcTemplate.execute(sql);
    }

    @PostConstruct
    public void initializeDatabase() {
        enablePgTrgmExtension();
        setDefaultUuidForIdColumns();
    }
}