package com.cdata.kintone.adapter.jdbc

import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * CData JDBC Driver のトライアルライセンスを取得するヘルパ。
 *
 * `java -jar driver.jar -l` を実行して、対話プロンプトに stdin から
 * 名前・メール・"TRIAL" を流し込む。
 *
 * Phase 2-A での実機検証フローを自動化したもの。
 */
class DriverActivator(
    private val javaCommand: String = "java",
    private val timeoutSec: Long = 60,
) {

    /** アクティベーション結果。 */
    sealed class Result {
        data object Success : Result()
        data class Failure(val message: String, val stdout: String) : Result()
    }

    fun activateTrial(jarPath: Path, name: String, email: String): Result {
        require(jarPath.toFile().exists()) { "JAR が見つかりません: $jarPath" }

        val pb = ProcessBuilder(javaCommand, "-jar", jarPath.fileName.toString(), "-l")
            .directory(jarPath.parent.toFile())
            .redirectErrorStream(true)
        val proc = pb.start()
        proc.outputStream.bufferedWriter().use { w ->
            w.write("$name\n")
            w.write("$email\n")
            w.write("TRIAL\n")
            w.write("\n")  // "Press any key to exit"
            w.flush()
        }
        val finished = proc.waitFor(timeoutSec, TimeUnit.SECONDS)
        if (!finished) {
            proc.destroyForcibly()
            return Result.Failure(
                message = "Timeout (${timeoutSec}s)",
                stdout = "(timeout)",
            )
        }
        val stdout = proc.inputStream.readAllBytes().toString(Charsets.UTF_8)
        return when {
            stdout.contains(SUCCESS_MARKER) -> Result.Success
            stdout.contains(INVALID_KEY_MARKER) -> Result.Failure(
                message = "Invalid product key. Trial が許可されていない可能性があります。",
                stdout = stdout,
            )
            else -> Result.Failure(
                message = "Activation failed. Check stdout.",
                stdout = stdout,
            )
        }
    }

    companion object {
        private const val SUCCESS_MARKER = "License installation succeeded."
        private const val INVALID_KEY_MARKER = "Invalid product key"
    }
}
