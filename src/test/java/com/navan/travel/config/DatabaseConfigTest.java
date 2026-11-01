package com.navan.travel.config;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

public class DatabaseConfigTest {

    @Test
    void testDatabasePropertiesGettersAndSetters() {
        DatabaseProperties properties = new DatabaseProperties("jdbc:sqlite:test.db");
        assertThat(properties.getUrl()).isEqualTo("jdbc:sqlite:test.db");

        properties.setUrl("jdbc:sqlite:other.db");
        assertThat(properties.getUrl()).isEqualTo("jdbc:sqlite:other.db");
    }

    @Test
    void testInitDirectoryBranches() {
        DatabaseProperties properties = new DatabaseProperties();
        DatabaseConfig config = new DatabaseConfig(properties);

        // 1. URL with query params
        properties.setUrl("jdbc:sqlite:build/test-data/db.db?busy_timeout=5000");
        assertThatNoException().isThrownBy(config::initDirectory);

        // 2. URL without parent directory
        properties.setUrl("jdbc:sqlite:simple.db");
        assertThatNoException().isThrownBy(config::initDirectory);

        // 3. Non-sqlite URL
        properties.setUrl("jdbc:h2:mem:test");
        assertThatNoException().isThrownBy(config::initDirectory);

        // 4. Directory already exists
        File dir = new File("build/test-data");
        dir.mkdirs();
        properties.setUrl("jdbc:sqlite:build/test-data/db.db");
        assertThatNoException().isThrownBy(config::initDirectory);

        // 5. Null URL branch coverage
        properties.setUrl(null);
        assertThatNoException().isThrownBy(config::initDirectory);
    }
}
