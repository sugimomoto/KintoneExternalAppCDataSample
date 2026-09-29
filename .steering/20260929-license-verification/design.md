# ライセンス状態の実検証 — 設計

| 項目 | 内容 |
|---|---|
| 開発タイトル | license-verification |
| 対応 Issue | [#31](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/31) |
| 作成日 | 2026-09-29 |
| 要求定義 | [requirements.md](requirements.md) |

---

## 1. 設計方針

### 1.1 主要な設計判断

| # | 判断 | 理由 | 却下した代替案 |
|---|---|---|---|
| D-1 | 検証は **`sys_procedures` の SELECT** で行う | ライセンスチェックが走る操作のうち最も軽い。`Offline=true` で外部通信なしに実行でき（C-3）、接続先の資格情報も不要 | 実データのクエリで検証する案 → 資格情報が必要で、OAuth 未認可の接続では確認できない |
| D-2 | 結果は **有効 / 無効 の 2 値** + 生メッセージ | ライセンスエラーのメッセージはロケール依存で、分類のパースは壊れる（C-1, F-5）。#19 で同じ判断をしている | エラーコード（`[コード：I]` / `[コード：J]`）で分類する案 → 「コード」の表記自体がロケール依存 |
| D-3 | プローブ接続は **#15 の仕組みを再利用**する | `ConnectionPropertyProbe.candidateUrls` が 26.x 系の検証エラーまで面倒を見ている。同じ問題を二度解かない | 独自に接続文字列を組む案 |
| D-4 | 検証は**明示的な操作**（ボタン）で行う | ドライバー画面の表示ごとに全ドライバーへ接続すると重い（C-4, AC-7） | 一覧描画時に自動検証する案 |
| D-5 | `.lic` の有無判定は**残す** | 「ファイルが無い」と「ファイルはあるが使えない」は別の情報（C-5）。表示を 2 軸にする | `LicenseStatus` を実検証の結果で置き換える案 |
| D-6 | 検証結果は**保持しない** | 認証すれば変わる状態。キャッシュすると古い結果を見せる。押したときの結果をその場で出す | 結果を永続化する案 |

### 1.2 表示の 2 軸

| `.lic` の有無 | 実検証 | 表示 |
|---|---|---|
| あり | 未実施 | 「ライセンスファイルあり」＋検証ボタン |
| あり | 有効 | 「利用可能」（緑） |
| あり | 無効 | 「要アクティベーション」（赤）＋生メッセージ |
| なし | — | 「未有効化」＋アクティベーション導線（既存） |

---

## 2. 変更・追加するコンポーネント

| ファイル | 区分 | 内容 |
|---|---|---|
| `jdbc/LicenseVerifier.kt` | 新規 | プローブ接続で `sys_procedures` を読み、有効/無効を判定 |
| `web/routes/DriversRoutes.kt` | 変更 | 検証エンドポイントを追加 |
| `web/views/DriversView.kt` | 変更 | 検証ボタンと結果表示 |
| `web/AppContext.kt` | 変更 | `LicenseVerifier` を公開 |

テスト:

| ファイル | 区分 | 内容 |
|---|---|---|
| `jdbc/LicenseVerifierTest.kt` | 新規 | 判定（成功 / 例外 / IO 境界の差し替え） |

---

## 3. データ構造

```kotlin
/**
 * ライセンスが実際に使える状態かの検証結果。
 *
 * `.lic` ファイルの有無では分からない。CData のライセンスはマシン (nodeid) に
 * 紐づくため、別マシンで認証された `.lic` があっても使えない。
 */
sealed class LicenseVerification {
    data object Valid : LicenseVerification()

    /** 使えない。[rawMessage] はドライバーが返したメッセージ（ロケール依存）。 */
    data class Invalid(val rawMessage: String) : LicenseVerification()
}
```

分類しないのは C-1 のため。利用者には生メッセージをそのまま見せ、
判断材料を奪わない。

---

## 4. 実装

### 4.1 `LicenseVerifier`

```kotlin
class LicenseVerifier(
    private val libDir: Path = Path.of("./lib"),
    private val metadataSource: DriverMetadataSource = JdbcDriverMetadataSource(),
) {
    fun verify(driverClass: String, jarFilename: String): LicenseVerification
}
```

`DriverMetadataSource` を受け取るのは #15 と同じ理由（IO 境界を差し替えてテストする）。
`sys_procedures` を読むメソッドを同インターフェースに追加する。

```
verify():
  1. JAR の存在確認 → 無ければ Invalid
  2. jdbcPrefixOf → 非 CData なら Invalid
  3. loadDriver
  4. ConnectionPropertyProbe.candidateUrls を順に試し、sys_procedures を SELECT
     成功 → Valid
     全滅 → Invalid(最後の例外メッセージ)
```

### 4.2 画面

ドライバー画面の各行に「ライセンスを検証」ボタンを置く。
`POST /drivers/{filename}/verify-license` で検証し、結果を行内に差し替える（htmx）。

---

## 5. 影響範囲の分析

| 対象 | 影響 | 対応 |
|---|---|---|
| ドライバー画面の表示 | 列と操作が増える | 既存の `.lic` 有無バッジは残す（D-5） |
| ドライバー画面の表示速度 | 検証はボタン押下時のみ | D-4 |
| `.lic` ファイル | 検証では書き換えない | C-2 |
| 外部通信 | `Offline=true` を含むプローブ接続を使う | C-3 |
| `DriverMetadataSource` | `sys_procedures` を読むメソッドを追加 | 既存実装に 1 メソッド追加 |

---

## 6. テスト設計

### 6.1 `LicenseVerifierTest`

- `sys_procedures` が読めたら `Valid`
- ライセンスエラーで `Invalid` になり、生メッセージを保持する
- JAR が無ければ `Invalid`
- 非 CData ドライバークラスで `Invalid`
- 最初の候補が失敗しても次の候補で成功すれば `Valid`（26.x 系）
- メッセージをパースして分類しない（日本語・英語どちらでも `Invalid`）

### 6.2 実機確認

| 確認項目 | 期待 |
|---|---|
| コンテナ内で 4 本を検証 | すべて「要アクティベーション」＋生メッセージ |
| `.lic` が書き換わらない | 検証前後でタイムスタンプが変わらない |

---

## 7. 受け入れ条件との対応

| AC | 対応する設計 | 検証 |
|---|---|---|
| AC-1 | §4.1 | §6.1, §6.2 |
| AC-2 | §3 の `rawMessage` | §6.1 |
| AC-3 | D-2 | §6.1 |
| AC-4 | C-2 | §6.2 |
| AC-5 | C-3 | §6.2 |
| AC-6 | §1.2 の 2 軸 | §6.2 |
| AC-7 | D-4 | §6.2 |
| AC-8 | §3 の生メッセージ + 案内 | §6.2 |
| AC-9 / AC-10 / AC-11 | README | 目視 |
| AC-12 | §6.1 | — |
| AC-13 | — | `./gradlew test` |
