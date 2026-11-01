package com.navan.travel.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThatNoException;

public class DatabaseConfigTest {

    @Test
    void testInitDirectoryBranches() {
        DatabaseConfig config = new DatabaseConfig();

        // 1. URL with query params
        ReflectionTestUtils.setField(config, "dbUrl", "jdbc:sqlite:build/test-data/db.db?busy_timeout=5000");
        assertThatNoException().isThrownBy(config::initDirectory);

        // 2. URL without parent directory
        ReflectionTestUtils.setField(config, "dbUrl", "jdbc:sqlite:simple.db");
        assertThatNoException().isThrownBy(config::initDirectory);

        // 3. Non-sqlite URL
        ReflectionTestUtils.setField(config, "dbUrl", "jdbc:h2:mem:test");
        assertThatNoException().isThrownBy(config::initDirectory);

        // 4. Directory already exists
        File dir = new File("build/test-data");
        dir.mkdirs();
        ReflectionTestUtils.setField(config, "dbUrl", "jdbc:sqlite:build/test-data/db.db");
        assertThatNoException().isThrownBy(config::initDirectory);
    }
}
