package com.cdata.kintone.adapter.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ConfigSourceFactoryTest {

    @Test
    fun `何もない configDir では YAML モード`(@TempDir tempDir: Path) {
        val mode = ConfigSourceFactory.detectMode(configDir = tempDir, envResolver = { null })
        assertEquals(ConfigSourceFactory.Mode.YAML, mode)
    }

    @Test
    fun `config_db があれば SQLITE モード`(@TempDir tempDir: Path) {
        Files.createFile(tempDir.resolve("config.db"))
        val mode = ConfigSourceFactory.detectMode(configDir = tempDir, envResolver = { null })
        assertEquals(ConfigSourceFactory.Mode.SQLITE, mode)
    }

    @Test
    fun `環境変数 CONFIG_SOURCE=sqlite が最優先`(@TempDir tempDir: Path) {
        val env = mapOf("CONFIG_SOURCE" to "sqlite")
        val mode = ConfigSourceFactory.detectMode(configDir = tempDir, envResolver = env::get)
        assertEquals(ConfigSourceFactory.Mode.SQLITE, mode)
    }

    @Test
    fun `環境変数 CONFIG_SOURCE=yaml が config_db より優先`(@TempDir tempDir: Path) {
        Files.createFile(tempDir.resolve("config.db"))
        val env = mapOf("CONFIG_SOURCE" to "yaml")
        val mode = ConfigSourceFactory.detectMode(configDir = tempDir, envResolver = env::get)
        assertEquals(ConfigSourceFactory.Mode.YAML, mode)
    }

    @Test
    fun `create で YAML モードなら YamlConfigSource を返す`(@TempDir tempDir: Path) {
        val source = ConfigSourceFactory.create(
            mode = ConfigSourceFactory.Mode.YAML,
            configDir = tempDir,
        )
        assertTrue(source is YamlConfigSource)
    }

    @Test
    fun `create で SQLITE モードなら SqliteConfigSource を返す`(@TempDir tempDir: Path) {
        val source = ConfigSourceFactory.create(
            mode = ConfigSourceFactory.Mode.SQLITE,
            configDir = tempDir,
        )
        assertTrue(source is SqliteConfigSource)
    }
}
