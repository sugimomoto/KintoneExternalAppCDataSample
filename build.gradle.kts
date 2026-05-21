import io.gitlab.arturbosch.detekt.Detekt

plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
    id("com.gradleup.shadow") version "8.3.5"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
    jacoco
    application
}

group = "com.cdata.kintone"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven { url = uri("https://buf.build/gen/maven") }
}

configurations.all {
    resolutionStrategy {
        // cybozu アーティファクトが参照する古い protovalidate を最新版に強制置換
        force(
            "build.buf.gen:bufbuild_protovalidate_protocolbuffers_java:34.1.0.1.20260415201107.50325440f8f2",
            "build.buf.gen:bufbuild_protovalidate_protocolbuffers_kotlin:34.1.0.2.20260415201107.50325440f8f2",
        )
    }
}

// BSR (Buf Schema Registry) で公開されている cybozu/kintone-data-connector の生成済みアーティファクト
val bsrCommit = "86857a1d93f8"  // 最新コミット (2026-03-09)
val protoKotlinGenVersion = "34.1.0.2.20260309044725.$bsrCommit"
val protoJavaGenVersion = "34.1.0.1.20260309044725.$bsrCommit"
val grpcJavaGenVersion = "1.81.0.1.20260309044725.$bsrCommit"
val grpcKotlinGenVersion = "1.5.0.3.20260309044725.$bsrCommit"

// bufbuild/protovalidate の transitive 依存：cybozu リポジトリが参照する古い版が
// BSR から削除されているため最新版に上書き
val protovalidateJavaVersion = "34.1.0.1.20260415201107.50325440f8f2"
val protovalidateKotlinVersion = "34.1.0.2.20260415201107.50325440f8f2"

dependencies {
    // BSR 公開: kintone-data-connector の protobuf + gRPC サーバスタブ生成コード
    implementation("build.buf.gen:cybozu_kintone-data-connector_protocolbuffers_kotlin:$protoKotlinGenVersion")
    implementation("build.buf.gen:cybozu_kintone-data-connector_protocolbuffers_java:$protoJavaGenVersion")
    implementation("build.buf.gen:cybozu_kintone-data-connector_grpc_java:$grpcJavaGenVersion")
    implementation("build.buf.gen:cybozu_kintone-data-connector_grpc_kotlin:$grpcKotlinGenVersion")

    // protovalidate transitive 上書き
    implementation("build.buf.gen:bufbuild_protovalidate_protocolbuffers_java:$protovalidateJavaVersion")
    implementation("build.buf.gen:bufbuild_protovalidate_protocolbuffers_kotlin:$protovalidateKotlinVersion")

    // gRPC ランタイム
    // BSR gen の grpc_java 1.81.0 とランタイムを合わせる必要がある（API 差異で AbstractMethodError）
    val grpcVersion = "1.81.0"
    implementation("io.grpc:grpc-netty:$grpcVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    implementation("io.grpc:grpc-services:$grpcVersion")
    implementation("io.grpc:grpc-kotlin-stub:1.4.3")

    // Kotlin / Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3")

    // YAML 設定
    implementation("com.charleskorn.kaml:kaml:0.61.0")

    // CLI
    implementation("com.github.ajalt.clikt:clikt:4.4.0")

    // 接続プール
    implementation("com.zaxxer:HikariCP:5.1.0")

    // CData JDBC Driver（ローカル参照）
    // lib/ 配下に配置済みの JAR を参照する。compileOnly にして fat jar に同梱せず、
    // 実行時は JdbcConnectionProvider が URLClassLoader 経由で動的ロードする。
    // lic ファイル (lib/*.lic) は JAR と同じディレクトリに置く必要がある。
    compileOnly(fileTree("lib") { include("*.jar") })

    // ロギング
    implementation("ch.qos.logback:logback-classic:1.5.12")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")

    // テスト
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("io.mockk:mockk:1.13.13")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.testcontainers:postgresql:1.20.3")
    testImplementation("org.testcontainers:junit-jupiter:1.20.3")
    testImplementation("org.postgresql:postgresql:42.7.4")
    testImplementation("com.h2database:h2:2.3.232")
}

application {
    mainClass.set("com.cdata.kintone.adapter.ApplicationKt")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

jacoco {
    toolVersion = "0.8.12"
}

ktlint {
    version.set("1.3.1")
    verbose.set(true)
    outputToConsole.set(true)
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
    }
}

detekt {
    config.setFrom("detekt.yml")
    buildUponDefaultConfig = true
    autoCorrect = false
}

tasks.withType<Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(true)
    }
    exclude("**/generated/**")
}

tasks.shadowJar {
    archiveBaseName.set("adapter")
    archiveClassifier.set("all")
    archiveVersion.set(version.toString())
    manifest {
        attributes["Main-Class"] = "com.cdata.kintone.adapter.ApplicationKt"
    }
    mergeServiceFiles()
}
