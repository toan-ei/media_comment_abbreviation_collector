package com.social_comment_abbreviation_collector.support;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Connection settings for the isolated QA schema used by DB integration tests.
 * <p>
 * Credentials are never stored in the repository. They are read from, in order:
 * <ol>
 *   <li>env {@code MYSQL_TEST_USERNAME} / {@code MYSQL_TEST_PASSWORD} (optional {@code MYSQL_TEST_HOST_PORT})</li>
 *   <li>file {@code ~/.day7-qa-db.properties} (or {@code .properties.txt}) with keys {@code username}, {@code password} (optional {@code hostPort})</li>
 * </ol>
 * Tests always use the dedicated schema {@value #SCHEMA}; they never touch the application schema.
 * When no credentials are found, DB tests are skipped (not failed).
 */
public final class QaTestDatabase {

    public static final String SCHEMA = "social_comment_collector_qa_test";
    private static final Path FILE = locateFile();

    private static final Properties CONFIG = load();

    private QaTestDatabase() {
    }

    /** Windows Notepad often appends ".txt", so accept that variant too. */
    private static Path locateFile() {
        Path plain = Path.of(System.getProperty("user.home"), ".day7-qa-db.properties");
        Path txt = Path.of(System.getProperty("user.home"), ".day7-qa-db.properties.txt");
        return Files.isReadable(plain) ? plain : txt;
    }

    private static Properties load() {
        Properties p = new Properties();
        if (Files.isReadable(FILE)) {
            try {
                for (String line : Files.readAllLines(FILE)) {
                    parseLine(p, line);
                }
            } catch (IOException ignored) {
                // treated as "not configured"
            }
        }
        putIfPresent(p, "username", System.getenv("MYSQL_TEST_USERNAME"));
        putIfPresent(p, "password", System.getenv("MYSQL_TEST_PASSWORD"));
        putIfPresent(p, "hostPort", System.getenv("MYSQL_TEST_HOST_PORT"));
        return p;
    }

    /**
     * Accepts {@code username=...} as well as PowerShell style {@code $env:MYSQL_USERNAME="..."}.
     * Only the MySQL username/password/host entries are used; any other line is ignored.
     */
    private static void parseLine(Properties p, String raw) {
        String line = raw.strip().replace("﻿", "");
        int eq = line.indexOf('=');
        if (line.startsWith("#") || eq < 0) return;
        String key = line.substring(0, eq).strip().replaceFirst("(?i)^\\$env:", "");
        String value = line.substring(eq + 1).strip();
        if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }
        switch (key.toUpperCase()) {
            case "USERNAME", "MYSQL_USERNAME", "MYSQL_TEST_USERNAME" -> p.setProperty("username", value);
            case "PASSWORD", "MYSQL_PASSWORD", "MYSQL_TEST_PASSWORD" -> p.setProperty("password", value);
            case "HOSTPORT", "MYSQL_TEST_HOST_PORT" -> p.setProperty("hostPort", value);
            default -> { }
        }
    }

    private static void putIfPresent(Properties p, String key, String value) {
        if (value != null && !value.isBlank()) p.setProperty(key, value);
    }

    /** Used by {@code @EnabledIf}. */
    public static boolean available() {
        return CONFIG.getProperty("username") != null;
    }

    /** Same JDBC parameters as application.yaml, but pointing at the QA schema. */
    public static String url() {
        return "jdbc:mysql://" + CONFIG.getProperty("hostPort", "127.0.0.1:3306") + "/" + SCHEMA
                + "?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=UTC";
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", QaTestDatabase::url);
        registry.add("spring.datasource.username", () -> CONFIG.getProperty("username"));
        registry.add("spring.datasource.password", () -> CONFIG.getProperty("password", ""));
        // Fresh schema built from the production entities on every test run.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("crawl.worker.enabled", () -> "false");
        registry.add("crawler.recovery.delay-ms", () -> "3600000");
    }
}
