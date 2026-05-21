# 07. SDK オプション（言語別）

Adapter 実装のための言語別 SDK 取得方法。Buf Schema Registry (BSR) から自動生成済みの SDK を利用するのが基本。

公開リポジトリ：https://buf.build/cybozu/kintone-data-connector

ローカルコピー：[reference/kintone-data-connector/*.proto](../../../../reference/kintone-data-connector/)

---

## 選択肢の整理

| 言語 | 推奨ライブラリ | 取得方法 | サンプル一致 |
|---|---|---|---|
| TypeScript / Node.js | `@bufbuild/protobuf` + `@connectrpc/connect-node` | npm（事前ビルド済みSDK） | ○ |
| Java | `protoc-gen-java` + `connect-java` | buf generate | × |
| Kotlin | `protoc-gen-kotlin` + `connect-kotlin` | buf generate | × |
| Go | `protoc-gen-go` + `connect-go` | buf generate / go module | × |
| Python | `protobuf` + `connect-python` | pip + buf generate | × |
| Swift | `swift-protobuf` + `connect-swift` | swift package | × |
| .NET (C#) | `Grpc.Net.Client` + `Grpc.Tools` | NuGet | × |

---

## TypeScript / Node.js（サンプルと同じ）

### npm registry 設定
```bash
# .npmrc に追記
@buf:registry=https://buf.build/gen/npm/v1/
```

または以下のコマンドで設定:
```bash
npm config set @buf:registry https://buf.build/gen/npm/v1/
```

### インストール
```bash
npm install @connectrpc/connect @connectrpc/connect-node @bufbuild/protobuf
npm install @buf/cybozu_kintone-data-connector.bufbuild_es@latest
```

### 利用例
サンプルの [`src/index.ts`](../../../../reference/db-connector-sample/db-connector-sample/adapter/src/index.ts) 参照。

---

## Java（CData JDBC 親和性が最も高い）

### 方法1: buf CLI で生成

`buf.gen.yaml`:
```yaml
version: v2
plugins:
  - remote: buf.build/protocolbuffers/java
    out: gen/java
  - remote: buf.build/connectrpc/java
    out: gen/java
```

```bash
buf generate buf.build/cybozu/kintone-data-connector
```

### 方法2: Gradle で buf プラグイン

```kotlin
plugins {
  id("build.buf") version "..."
}

buf {
  generate {
    plugin("buf.build/protocolbuffers/java")
    plugin("buf.build/connectrpc/java")
  }
}
```

### 依存追加
```kotlin
// build.gradle.kts
dependencies {
  implementation("com.google.protobuf:protobuf-java:...")
  implementation("com.connectrpc:connect-kotlin-google-java-ext:...")
  implementation("com.connectrpc:connect-kotlin:...")
  implementation("io.netty:netty-codec-http2:...")
}
```

### サーバ実装

Connect-Java / Connect-Kotlin が `connect-kotlin-http-okhttp` などのモジュールでサーバサポートを提供。
あるいは、より定番の `grpc-java` + `armeria-grpc` で gRPC サーバ実装。

**注意**：Connect プロトコルは Connect 公式実装が安定。grpc-java で立てる場合は gRPC バイナリのみで JSON は対応外（curl テストはできない）。

---

## Kotlin（Spring Boot/Ktor との親和性）

Java と同じ buf 経由でコード生成。`connect-kotlin` ライブラリを使う。

```kotlin
// Ktor + Connect-Kotlin の例
embeddedServer(Netty, port = 8083) {
    install(ConnectRpc) {
        service(AdapterService) { req ->
            // 実装
        }
    }
}.start(wait = true)
```

実装言語選定で **Kotlin + connect-kotlin** は有力候補：
- JDBC ネイティブ呼出
- Connect プロトコルがファーストクラスでサポートされている
- サンプルの TypeScript 構造と類似（async/await が coroutine に相当）

---

## Go（軽量・配布容易）

```bash
buf generate buf.build/cybozu/kintone-data-connector
```

`buf.gen.yaml`:
```yaml
version: v2
plugins:
  - remote: buf.build/protocolbuffers/go
    out: gen/go
  - remote: buf.build/connectrpc/go
    out: gen/go
```

依存:
```bash
go get connectrpc.com/connect
go get golang.org/x/net/http2
```

JDBC は Java の世界なので、Go では CData ODBC Driver を使う形になる（CData は ODBC 版も提供）。

---

## Buf CLI のインストール

```bash
# macOS
brew install bufbuild/buf/buf

# その他
curl -sSL "https://github.com/bufbuild/buf/releases/latest/download/buf-$(uname -s)-$(uname -m)" -o /usr/local/bin/buf
chmod +x /usr/local/bin/buf
```

### よく使うコマンド

```bash
# BSR からスキーマをダウンロード
buf export buf.build/cybozu/kintone-data-connector -o ./proto

# コード生成
buf generate buf.build/cybozu/kintone-data-connector

# プッシュ（公式向け、本プロジェクトでは使わない）
buf push
```

---

## ローカル proto ファイルからの生成

本プロジェクトでは [reference/kintone-data-connector/](../../../../reference/kintone-data-connector/) に proto ファイル7本がダウンロード済み。

`buf.work.yaml`（v1）または `buf.yaml` の `modules` 設定でローカル参照可能。

```yaml
# buf.yaml (v2)
version: v2
modules:
  - path: reference/kintone-data-connector
    name: buf.build/cybozu/kintone-data-connector
```

ただし、proto に `import "buf/validate/validate.proto"` があるため、`protovalidate` モジュールへの依存を `deps` に追加する必要あり。

---

## Connect プロトコル vs 純粋 gRPC

| 観点 | Connect プロトコル | 純粋 gRPC |
|---|---|---|
| HTTP | HTTP/1.1 + HTTP/2 両対応 | HTTP/2 必須 |
| Content-Type | `application/json` / `application/proto` / gRPC バイナリ | gRPC バイナリのみ |
| ブラウザ対応 | ○（gRPC-Web 上位互換） | × |
| curl 動作確認 | ○ | × |
| Java/Kotlin サーバ | connect-kotlin | grpc-java |
| Node.js サーバ | connect-node | @grpc/grpc-js |

**Agent ↔ Adapter は Connect プロトコル** なので、サーバ側も Connect 対応ライブラリを使うのが互換性的に安全。純粋 gRPC サーバでも基本動作はするが、JSON エンドポイントが使えない（curl デバッグできない）。

---

## 本プロジェクトの推奨 SDK 構成（**確定**）

**Kotlin + Ktor + connect-kotlin + CData JDBC** を採用：

```kotlin
// build.gradle.kts
plugins {
  kotlin("jvm") version "1.9.x"
  kotlin("plugin.serialization") version "1.9.x"
  id("com.github.johnrengelman.shadow") version "8.x"  // Fat JAR
  id("build.buf") version "0.10.x"                     // protobuf 生成
  id("org.jlleitschuh.gradle.ktlint") version "12.x"
  id("io.gitlab.arturbosch.detekt") version "1.23.x"
}

dependencies {
  // Connect RPC
  implementation("com.connectrpc:connect-kotlin:0.5.0")
  implementation("com.connectrpc:connect-kotlin-google-java-ext:0.5.0")
  implementation("com.google.protobuf:protobuf-kotlin:3.25.1")

  // Ktor server
  implementation("io.ktor:ktor-server-core:2.3.7")
  implementation("io.ktor:ktor-server-netty:2.3.7")

  // CData JDBC（ローカル参照）
  implementation(files("lib/cdata.jdbc.salesforce.jar"))
  implementation("com.zaxxer:HikariCP:5.1.0")

  // YAML 設定 + CLI
  implementation("com.charleskorn.kaml:kaml:0.55.0")
  implementation("com.github.ajalt.clikt:clikt:4.2.1")

  // ロギング
  implementation("ch.qos.logback:logback-classic:1.4.14")

  // テスト
  testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
  testImplementation("io.mockk:mockk:1.13.8")
  testImplementation("org.testcontainers:postgresql:1.19.3")
}
```

詳細は対応方針ドラフト [.steering/20260515-initial-implementation/approach-draft.md](../../../../.steering/20260515-initial-implementation/approach-draft.md) および [docs/architecture.md](../../../../docs/architecture.md) 参照。動作確認は [09-curl-test-snippets.md](09-curl-test-snippets.md)。
