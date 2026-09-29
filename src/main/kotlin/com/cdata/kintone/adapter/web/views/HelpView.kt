package com.cdata.kintone.adapter.web.views

import com.cdata.kintone.adapter.web.AppContext
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.code
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.h4
import kotlinx.html.li
import kotlinx.html.ol
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.small
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.html.ul

/**
 * `/help` で表示するヘルプドキュメントページ。
 * クイックスタート・主要用語・トラブルシューティング・関連リンクを 1 画面に集約。
 */
fun HTML.helpView(ctx: AppContext) {
    val activeCount = ctx.runner.listActive().size
    layout(
        pageTitle = "ヘルプ",
        activeCount = activeCount,
        currentPath = "/help",
    ) {
        div(classes = "hero") {
            h2 { +"ヘルプ・ガイド" }
            p(classes = "lead") {
                +"CData Kintone Adapter Console の使い方をまとめたページです。"
                +" 用語・クイックスタート・トラブルシューティングを順に確認できます。"
            }
        }

        article {
            h3 { +"このサービスでできること" }
            p {
                +"CData JDBC ドライバーを使って、外部データソース"
                +"（Salesforce / Google Sheets / Snowflake など）を kintone の"
                +"「外部 App」として参照可能にする Adapter Console です。"
            }
            p {
                +"kintone から見ると、外部データソースの 1 テーブルが"
                +"あたかも kintone 上の 1 アプリのように見え、レコードを参照できます。"
            }
        }

        article {
            h3 { +"主要な用語" }
            div(classes = "term-grid") {
                termCard(
                    "連携 (Sync)",
                    "「外部データソースの 1 テーブル」を kintone に見せるための単位。 " +
                        "内部的には Adapter (gRPC) と Agent (kintone と通信するコンテナ) のペアで動作します。",
                )
                termCard(
                    "データソース",
                    "JDBC 接続文字列の集まり。1 つのデータソース (例: 自社の Salesforce) を" +
                        "複数の連携から共有して使えます。",
                )
                termCard(
                    "ドライバー",
                    "CData JDBC Driver の jar ファイル。/drivers からアップロードし、" +
                        "ライセンス (トライアル) をアクティベートして利用します。",
                )
                termCard(
                    "Agent",
                    "kintone との通信を担当する常駐コンテナ。Adapter Console が Docker API " +
                        "経由で自動的に作成・起動します。",
                )
            }
        }

        article {
            h3 { +"クイックスタート (5 ステップ)" }
            ol(classes = "quickstart") {
                li {
                    strong { +"1. ドライバーをアップロード " }
                    span(classes = "muted") { +"— " }
                    a(href = "/drivers") { +"ドライバー画面" }
                    +" から CData JDBC Driver の jar をアップロードし、トライアルをアクティベートします。"
                }
                li {
                    strong { +"2. データソース接続を作成 " }
                    span(classes = "muted") { +"— " }
                    a(href = "/connections") { +"データソース画面" }
                    +" で接続文字列 (例: "
                    code { +"jdbc:salesforce:User=...;Password=..." }
                    +") を保存します。"
                }
                li {
                    strong { +"3. 新しい連携を追加 " }
                    span(classes = "muted") { +"— " }
                    a(href = "/syncs/new") { +"+ 新しい連携" }
                    +" を押し、データソースとテーブル名を選んでウィザードを進めます。"
                }
                li {
                    strong { +"4. kintone と接続 " }
                    span(classes = "muted") { +"— " }
                    +"連携の詳細画面から「kintone と接続」を選び、Step 1 で「鍵ペアを生成する」を押します。"
                    +" 表示された公開鍵を kintone 管理画面に登録 → 発行された JWT トークンを入力すると、"
                    +" Adapter と Agent コンテナが自動的に立ち上がります。"
                }
                li {
                    strong { +"5. kintone 側で外部 App を作成 " }
                    span(classes = "muted") { +"— " }
                    +"kintone の「外部 App」設定画面で Agent のホスト・ポートを指定すると、"
                    +"外部データがアプリとして参照できます。"
                }
            }
        }

        article {
            h3 { +"主要な画面" }
            ul(classes = "page-list") {
                pageRow("/", "ダッシュボード", "稼働状況の KPI と最近の連携を一覧表示します。")
                pageRow("/syncs", "連携", "登録済みの連携の一覧・開始・停止・削除・ログ閲覧を行います。")
                pageRow("/connections", "データソース", "JDBC 接続文字列の登録・編集・接続テストができます。")
                pageRow("/drivers", "ドライバー", "JDBC ドライバー jar のアップロードとアクティベーションを行います。")
            }
        }

        article {
            h3 { +"よくある問題 (Troubleshooting)" }
            section(classes = "faq") {
                h4 { +"ドライバーをアップロードしようとすると画面が真っ白になる" }
                p {
                    +"Ktor 3.x の API 互換性問題です。最新版でこの問題は修正済みなので、"
                    +"アプリケーションが古い場合は再ビルドしてください。"
                }
                h4 { +"連携を開始したら 500 エラーになる" }
                p {
                    +"原因として考えられるもの: "
                }
                ul {
                    li { +"Docker Desktop が起動していない / "; code { +"/var/run/docker.sock" }; +" が見えない" }
                    li {
                        +"Agent コンテナイメージ ("
                        code { +"kintone-data-connector-agent:0.9.2" }
                        +") がローカルビルドされていない"
                        +" — サイボウズ社から受領した Agent バイナリを "
                        code { +"agent/bin/linux_<arch>/" }
                        +" に配置し、"
                        code { +"docker compose -f agent/docker-compose.yml build" }
                        +" を実行してください"
                    }
                    li { +"既存の Agent コンテナ名と衝突している (一度 "; code { +"docker rm" }; +" で削除)" }
                }
                h4 { +"接続テストで「Login failed」になる" }
                p {
                    +"接続文字列の "; code { +"User" }; +" / "; code { +"Password" }; +" / "; code { +"SecurityToken" }
                    +" を確認してください。"
                }
                h4 { +"OAuth 認証のデータソースで「認可が必要」と出る" }
                p {
                    +"データソース接続の編集画面にある「OAuth 認可を行う」から認可してください。"
                    +"画面に表示された認可 URL を"
                    strong { +"ご自身の PC のブラウザ" }
                    +"で開き、リダイレクト後の URL に付く "
                    code { +"code=" }
                    +" の値を貼り戻すと完了します。サーバー側ではブラウザを開けないため、この手順になります。"
                }
                p {
                    small {
                        +"導線は OAuth 認証を使う接続にだけ表示されます。"
                        +"一度認可すれば、その接続を使う連携すべてでトークンが共有されます。"
                    }
                }
                h4 { +"連携を停止しても kintone 側でレコードが見える" }
                p {
                    +"kintone は最後に取得したスキーマをキャッシュしているため、"
                    +"連携を停止しても直近の参照結果が残ることがあります。新しいリクエストはエラーになります。"
                }
            }
        }

        article {
            h3 { +"関連リンク" }
            ul(classes = "link-list") {
                li {
                    strong { +"管理者向け: " }
                    +"Docker でこのサービスをセットアップする手順は "
                    code { +"docs/DOCKER-SETUP.md" }
                    +" を参照してください (リポジトリに同梱)。"
                }
                li {
                    strong { +"Agent バイナリの入手: " }
                    +"kintone-data-connector-agent はサイボウズ社から個別に受領するプログラムです。"
                    +" 公開レジストリでは配布されていません。受領後 "
                    code { +"agent/bin/linux_<arch>/" }
                    +" に配置し、同梱の "
                    code { +"agent/Dockerfile" }
                    +" で自前ビルドします。"
                }
                li {
                    a(href = "https://www.cdata.com/jdbc/", target = "_blank") {
                        +"CData JDBC Drivers (製品サイト)"
                    }
                }
                li {
                    a(href = "https://kintone.dev/", target = "_blank") {
                        +"kintone 開発者向けポータル"
                    }
                }
                li {
                    a(href = "https://picocss.com/", target = "_blank") {
                        +"Pico.css (UI フレームワーク)"
                    }
                }
            }
            small(classes = "muted") {
                +"バージョン v0.4.0 · 設定の保存先: "
                code { +ctx.configDir.resolve("config.db").toString() }
            }
        }
    }
}

private fun kotlinx.html.FlowContent.termCard(title: String, description: String) {
    div(classes = "term-card") {
        h4 { +title }
        p { +description }
    }
}

private fun kotlinx.html.UL.pageRow(href: String, label: String, description: String) {
    li {
        a(href = href) { strong { +label } }
        span(classes = "muted") { +" — $description" }
    }
}
