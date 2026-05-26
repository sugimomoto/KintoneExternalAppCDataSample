package com.cdata.kintone.adapter.agent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlin.io.path.exists
import kotlin.io.path.readText

class KeyPairGeneratorServiceTest {

    @Test
    fun `generate writes PKCS#8 private and SPKI public PEM with 2048 bit RSA`(@TempDir dir: Path) {
        val priv = dir.resolve("private-key.pem")
        val pub = dir.resolve("public-key.pem")
        val service = KeyPairGeneratorService(priv, pub)

        val result = service.generate()

        assertEquals(KeyPairGeneratorService.GenerationResult.Success, result)
        assertTrue(priv.exists())
        assertTrue(pub.exists())

        val privPem = priv.readText()
        assertTrue(privPem.startsWith("-----BEGIN PRIVATE KEY-----"))
        assertTrue(privPem.trimEnd().endsWith("-----END PRIVATE KEY-----"))

        val pubPem = pub.readText()
        assertTrue(pubPem.startsWith("-----BEGIN PUBLIC KEY-----"))
        assertTrue(pubPem.trimEnd().endsWith("-----END PUBLIC KEY-----"))

        val der = Base64.getDecoder().decode(
            privPem.lines()
                .filterNot { it.startsWith("-----") || it.isBlank() }
                .joinToString(""),
        )
        val rsaKey = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(der)) as RSAPrivateKey
        assertEquals(2048, rsaKey.modulus.bitLength())
    }

    @Test
    fun `generate returns AlreadyExists if either file already exists`(@TempDir dir: Path) {
        val priv = dir.resolve("private-key.pem")
        val pub = dir.resolve("public-key.pem")
        Files.writeString(pub, "already there")

        val result = KeyPairGeneratorService(priv, pub).generate()

        assertEquals(KeyPairGeneratorService.GenerationResult.AlreadyExists, result)
        assertEquals("already there", pub.readText())
        assertTrue(!priv.exists())
    }

    @Test
    fun `generated public key parses as SPKI RSA and matches private key modulus`(@TempDir dir: Path) {
        val priv = dir.resolve("private-key.pem")
        val pub = dir.resolve("public-key.pem")
        KeyPairGeneratorService(priv, pub).generate()

        val rsaPriv = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(decodePem(priv.readText()))) as RSAPrivateKey
        val rsaPub = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(decodePem(pub.readText()))) as RSAPublicKey

        assertEquals(2048, rsaPub.modulus.bitLength())
        assertEquals(rsaPriv.modulus, rsaPub.modulus)
    }

    private fun decodePem(pem: String): ByteArray = Base64.getDecoder().decode(
        pem.lines().filterNot { it.startsWith("-----") || it.isBlank() }.joinToString(""),
    )

    @Test
    fun `private key receives POSIX 0600 permissions on supporting filesystem`(@TempDir dir: Path) {
        if (!dir.fileSystem.supportedFileAttributeViews().contains("posix")) return
        val priv = dir.resolve("private-key.pem")
        val pub = dir.resolve("public-key.pem")

        KeyPairGeneratorService(priv, pub).generate()

        val perms = Files.getPosixFilePermissions(priv)
        assertNotNull(perms)
        assertEquals(PosixFilePermissions.fromString("rw-------"), perms)
    }
}
