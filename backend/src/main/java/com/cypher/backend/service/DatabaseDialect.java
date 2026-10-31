package com.cypher.backend.service;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseDialect {
    private final boolean mysql;

    public DatabaseDialect(JdbcTemplate jdbc) {
        mysql = Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection ->
                connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL")));
    }

    public String jsonType() {
        return mysql ? "JSON" : "jsonb";
    }

    public String auditField(String field) {
        return mysql ? "JSON_UNQUOTE(JSON_EXTRACT(a.new_values, '$." + field + "'))"
                : "a.new_values ->> '" + field + "'";
    }
}
