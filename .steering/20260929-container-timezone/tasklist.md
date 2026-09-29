# コンテナのタイムゾーン — タスクリスト

| 項目 | 内容 |
|---|---|
| 開発タイトル | container-timezone |
| 対応 Issue | [#25](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/25) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

---

## Phase 1. TZ の引き継ぎ

- [x] **T-10** 🔴 `AgentContainerManagerTest` に `timeZoneEnv` のテストを追加
- [x] **T-11** 🟢 `timeZoneEnv` を実装し、`ensureCreated` で使う

## Phase 2. compose と表示

- [x] **T-20** `docker-compose.yml` の adapter-console に `TZ` を追加
- [x] **T-21** `agent/docker-compose.yml` に `TZ` を追加
- [x] **T-22** `DashboardView` / `ListActiveCommand` の時刻表示にタイムゾーンを併記

## Phase 3. 検証

- [x] **T-30** `./gradlew test` / detekt（新規指摘が無いこと）
- [x] **T-31** `src/browserTest` が時刻表示に依存していないか確認
- [x] **T-32** `docker compose build adapter-console && docker compose up -d adapter-console`（CLAUDE.md 手順 7）
- [x] **T-33** コンテナの `date`・ログ・画面の時刻を確認
- [x] **T-34** Agent コンテナを作り直して `TZ` が入ることを確認

## Phase 4. 仕上げ

- [x] **T-40** README に `TZ` を記載（既存コンテナは作り直すまで反映されない点も明記）
- [x] **T-41** コミット
- [x] **T-42** PR 作成・マージ、Issue #25 に結果を記録

---

## 結果

| AC | 状態 | 確認方法 |
|---|---|---|
| AC-1 画面の時刻がローカル時刻と一致 | ✅ | 実機: 連携詳細 `2026-09-29 15:43 JST` / ダッシュボード `15:45:55 JST` |
| AC-2 `TZ` で上書きできる | ✅ | `TZ: ${TZ:-Asia/Tokyo}` |
| AC-3 ログの時刻も揃う | ✅ | 実機: `2026-09-29 15:45:58.191 [main] INFO ... Web UI 起動` |
| AC-4 Agent コンテナも揃う | ⚠️ | `timeZoneEnv` のユニットテストで担保。実機は**既存コンテナを作り直していないため未確認** |
| AC-5 タイムゾーンが併記される | ✅ | 画面・CLI ともに `JST` が付く |
| AC-6 `TZ` 未設定でも動く | ✅ | `timeZoneEnv(null)` が空リストを返す |
| AC-7 テストと lint | ✅ | `test` 成功 (354 件)。detekt は 84 件で変更前後とも同数 |

### 実機での確認

```
$ docker exec adapter-console sh -c 'date; echo TZ=$TZ'
Tue Sep 29 03:46:02 PM JST 2026
TZ=Asia/Tokyo

$ docker exec adapter-console java -jar /app/adapter.jar list-active
  - ProductPlant    port=18008  status=SERVING  started=2026-09-29 15:45:55 JST
```

### 補足

AC-4 は `ensureCreated` が既存コンテナに手を出さない仕様のため、
**既存の Agent コンテナは作り直すまで UTC のまま**。README に明記した。
今日壊した Product の agent.json を使って実機確認することもできたが、
連携の状態をこれ以上動かさない判断で見送り、ユニットテストで担保している。

### 実装で変わった判断

`ListActiveCommand` の時刻表示にもタイムゾーンを併記した。設計では画面 2 箇所の
想定だったが、CLI もコンテナ内で実行すれば同じずれが起きるため対象に含めた。
