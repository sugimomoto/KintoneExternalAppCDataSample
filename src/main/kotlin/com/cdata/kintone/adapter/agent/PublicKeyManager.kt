package com.cdata.kintone.adapter.agent

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.exists

/**
 * kintone コネクター登録用の公開鍵 (`agent/public-key.pem`) を読み出すヘルパ。
 * Web UI の「kintone と接続」セクションで「コピー」「ダウンロード」「フィンガープリント表示」に使う。
 */
class PublicKeyManager(private val keyPath: Path = Path.of("./agent/public-key.pem")) {

    fun exists(): Boolean = keyPath.exists()

    fun read(): String? = if (exists()) Files.readString(keyPath) else null

    fun bytes(): ByteArray? = if (exists()) Files.readAllBytes(keyPath) else null

    /** SHA-256 ハッシュをコロン区切り 16 進で返す (例: `a1:b2:c3:...`) 。 */
    fun fingerprint(): String? {
        val content = bytes() ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(content)
        return digest.joinToString(":") { "%02x".format(it) }
    }

    fun pathString(): String = keyPath.toString()
}
