package com.cdata.kintone.adapter.agent

import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.KeyPairGenerator
import java.util.Base64
import kotlin.io.path.exists

/**
 * Agent 用の RSA 鍵ペアを生成して PEM ファイルとしてホストに書き出すサービス。
 *
 * - 秘密鍵 (`private-key.pem`) は PKCS#8 形式 (`-----BEGIN PRIVATE KEY-----`)
 *   既存の `openssl genrsa | openssl pkcs8 -topk8` 出力と互換
 * - 公開鍵 (`public-key.pem`) は X.509 SubjectPublicKeyInfo 形式
 *   `-----BEGIN PUBLIC KEY-----`
 * - 秘密鍵はファイルパーミッション 600 (POSIX 対応 FS のみ)
 *
 * 誤上書き防止のため、既存ファイルが存在する場合は [GenerationResult.AlreadyExists] を返す。
 */
class KeyPairGeneratorService(
    private val privateKeyPath: Path,
    private val publicKeyPath: Path,
    private val keySizeBits: Int = 2048,
) {

    private val log = KotlinLogging.logger {}

    sealed class GenerationResult {
        data object Success : GenerationResult()
        data object AlreadyExists : GenerationResult()
        data class Failed(val message: String, val cause: Throwable?) : GenerationResult()
    }

    fun generate(): GenerationResult {
        if (privateKeyPath.exists() || publicKeyPath.exists()) {
            return GenerationResult.AlreadyExists
        }
        return try {
            val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(keySizeBits) }.generateKeyPair()
            Files.createDirectories(privateKeyPath.parent)

            writePem(privateKeyPath, "PRIVATE KEY", keyPair.private.encoded)
            writePem(publicKeyPath, "PUBLIC KEY", keyPair.public.encoded)
            applyPrivateKeyPermissions(privateKeyPath)

            log.info { "Generated RSA $keySizeBits-bit keypair: $privateKeyPath / $publicKeyPath" }
            GenerationResult.Success
        } catch (e: Exception) {
            log.error(e) { "Failed to generate keypair" }
            GenerationResult.Failed(e.message ?: e.javaClass.simpleName, e)
        }
    }

    private fun writePem(path: Path, type: String, der: ByteArray) {
        val base64 = Base64.getEncoder().encodeToString(der)
        val body = base64.chunked(64).joinToString("\n")
        val pem = "-----BEGIN $type-----\n$body\n-----END $type-----\n"
        Files.write(
            path,
            pem.toByteArray(),
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
        )
    }

    private fun applyPrivateKeyPermissions(path: Path) {
        if (!path.fileSystem.supportedFileAttributeViews().contains("posix")) return
        runCatching {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"))
        }.onFailure { log.warn(it) { "Failed to chmod 600 on $path" } }
    }
}
