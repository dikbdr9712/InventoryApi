package com.api.inventory.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Fixes column types that Hibernate's automatic update cannot change by itself (ddl-auto=update adds tables and
 * columns, but never changes the type of a column that already exists).
 *
 * Runs first on start-up, only on MySQL, and only when a column is still too small. Safe to run every time.
 *
 *  - legal_terms.body was first created as TINYTEXT (255 characters); agreements need MEDIUMTEXT.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaRepair implements ApplicationRunner {

    private final DataSource dataSource;

    public SchemaRepair(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            if (!c.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql")) {
                return; // H2 in the tests, or another database: nothing to repair
            }
            widenText(c, "legal_terms", "body", "MEDIUMTEXT NOT NULL");
        }
    }

    /** Changes a text column to the wanted type when it is TINYTEXT, TEXT or VARCHAR (too small). */
    private static void widenText(Connection c, String table, String column, String wanted) throws Exception {
        String sql = "SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        String type = null;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    type = rs.getString(1).toLowerCase();
                }
            }
        }
        if (type == null || type.equals("mediumtext") || type.equals("longtext")) {
            return; // the table does not exist yet (Hibernate creates it right) or it is already big enough
        }
        try (Statement st = c.createStatement()) {
            st.executeUpdate("ALTER TABLE " + table + " MODIFY COLUMN " + column + " " + wanted);
        }
        System.out.println("Schema repair: " + table + "." + column + " changed from " + type.toUpperCase() + " to " + wanted + ".");
    }
}
