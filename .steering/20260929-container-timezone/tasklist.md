# コンテナのタイムゾーン — タスクリスト

| 項目 | 内容 |
|---|---|
| 開発タイトル | container-timezone |
| 対応 Issue | [#25](https://github.com/sugimomoto/KintoneExternalAppCDataSample/issues/25) |
| 要求定義 | [requirements.md](requirements.md) |
| 設計 | [design.md](design.md) |

---

## Phase 1. TZ の引き継ぎ

- [ ] **T-10** 🔴 `AgentContainerManagerTest` に `timeZoneEnv` のテストを追加
- [ ] **T-11** 🟢 `timeZoneEnv` を実装し、`ensureCreated` で使う

## Phase 2. compose と表示

- [ ] **T-20** `docker-compose.yml` の adapter-console に `TZ` を追加
- [ ] **T-21** `agent/docker-compose.yml` に `TZ` を追加
- [ ] **T-22** `DashboardView` / `ListActiveCommand` の時刻表示にタイムゾーンを併記

## Phase 3. 検証

- [ ] **T-30** `./gradlew test` / detekt（新規指摘が無いこと）
- [ ] **T-31** `src/browserTest` が時刻表示に依存していないか確認
- [ ] **T-32** `docker compose build adapter-console && docker compose up -d adapter-console`（CLAUDE.md 手順 7）
- [ ] **T-33** コンテナの `date`・ログ・画面の時刻を確認
- [ ] **T-34** Agent コンテナを作り直して `TZ` が入ることを確認

## Phase 4. 仕上げ

- [ ] **T-40** README に `TZ` を記載（既存コンテナは作り直すまで反映されない点も明記）
- [ ] **T-41** コミット
- [ ] **T-42** PR 作成・マージ、Issue #25 に結果を記録
