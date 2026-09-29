package com.cdata.kintone.adapter.error

/**
 * 内部例外メッセージをエンドユーザー向けの日本語文言に翻訳する。
 * Phase 2-C UX-03 要件: Adapter/Agent/Port 等の技術用語を見せず、
 * 操作可能なアクションボタンを提示する。
 */
object ErrorMessageTranslator {

    enum class Severity { INFO, WARN, ERROR }

    enum class UserAction(val label: String, val href: String) {
        RECONNECT("再接続を試す", "javascript:location.reload()"),
        GO_TO_DRIVERS("ドライバー画面へ", "/drivers"),
        GO_TO_CONNECTIONS("接続設定へ", "/connections"),
        VIEW_LOGS("ログを見る", "javascript:history.back()"),
    }

    data class UserMessage(
        val text: String,
        val severity: Severity,
        val actions: List<UserAction>,
        val rawMessage: String? = null,
    )

    private data class TranslationRule(
        val pattern: Regex,
        val userMessage: String,
        val severity: Severity,
        val actions: List<UserAction> = emptyList(),
    )

    private val rules: List<TranslationRule> = listOf(
        TranslationRule(
            pattern = Regex("rpc error.*keepalive ping failed", RegexOption.IGNORE_CASE),
            userMessage = "kintone との接続が一時的に切れています。再接続をお試しください。",
            severity = Severity.WARN,
            actions = listOf(UserAction.RECONNECT),
        ),
        TranslationRule(
            pattern = Regex("rpc error.*code = Unavailable", RegexOption.IGNORE_CASE),
            userMessage = "Sync が応答していません。「ログを見る」で詳細を確認してください。",
            severity = Severity.ERROR,
            actions = listOf(UserAction.VIEW_LOGS, UserAction.RECONNECT),
        ),
        TranslationRule(
            pattern = Regex("Connection refused", RegexOption.IGNORE_CASE),
            userMessage = "データソースへの接続に失敗しました。接続設定が正しいか確認してください。",
            severity = Severity.ERROR,
            actions = listOf(UserAction.GO_TO_CONNECTIONS),
        ),
        TranslationRule(
            pattern = Regex("(JAR not found|driver.*not found)", RegexOption.IGNORE_CASE),
            userMessage = "データソース用のドライバーが見つかりません。ドライバー画面で追加してください。",
            severity = Severity.ERROR,
            actions = listOf(UserAction.GO_TO_DRIVERS),
        ),
        // --- OAuth 認可ウィザード (Issue #12) ---
        TranslationRule(
            pattern = Regex("is not a valid stored procedure", RegexOption.IGNORE_CASE),
            userMessage = "このドライバーは Web UI からの OAuth 認可に対応していません。" +
                "取得済みのトークン（リフレッシュトークン / JWT 等）を接続設定に入力してください。",
            severity = Severity.ERROR,
            actions = listOf(UserAction.GO_TO_CONNECTIONS),
        ),
        TranslationRule(
            pattern = Regex("(invalid_client|invalid_client_id|OAUTH \\[30004\\])", RegexOption.IGNORE_CASE),
            userMessage = "OAuth クライアント ID またはシークレットが正しくありません。" +
                "データソース側で発行した値を接続設定で確認してください。",
            severity = Severity.ERROR,
            actions = listOf(UserAction.GO_TO_CONNECTIONS),
        ),
        TranslationRule(
            pattern = Regex("(invalid_grant|OAUTH \\[30003\\])", RegexOption.IGNORE_CASE),
            userMessage = "認可コードが無効か期限切れです。認可 URL を開き直して、" +
                "新しいコードを取得してください。",
            severity = Severity.ERROR,
            actions = listOf(UserAction.RECONNECT),
        ),
        TranslationRule(
            pattern = Regex("OAUTH \\[50001\\]", RegexOption.IGNORE_CASE),
            userMessage = "OAuth の初回認可が完了していません。" +
                "データソース接続の編集画面から「OAuth 認可」を実行してください。",
            severity = Severity.ERROR,
            actions = listOf(UserAction.GO_TO_CONNECTIONS),
        ),
        TranslationRule(
            pattern = Regex("(license|lic).*(invalid|expired|not.*found)", RegexOption.IGNORE_CASE),
            userMessage = "データソース用ドライバーのライセンスが有効ではありません。ドライバー画面でアクティベーションしてください。",
            severity = Severity.ERROR,
            actions = listOf(UserAction.GO_TO_DRIVERS),
        ),
        TranslationRule(
            pattern = Regex("session.*(not found|expired)", RegexOption.IGNORE_CASE),
            userMessage = "kintone セッションが切れました。再接続をお試しください。",
            severity = Severity.WARN,
            actions = listOf(UserAction.RECONNECT),
        ),
        TranslationRule(
            pattern = Regex("(INVALID_LOGIN|authentication failed|UNAUTHORIZED)", RegexOption.IGNORE_CASE),
            userMessage = "データソースへの認証に失敗しました。ユーザー名・パスワード・トークン等を確認してください。",
            severity = Severity.ERROR,
            actions = listOf(UserAction.GO_TO_CONNECTIONS),
        ),
        TranslationRule(
            pattern = Regex("OAuth.*timeout|OAuth.*not.*authorized", RegexOption.IGNORE_CASE),
            userMessage = "OAuth 認証が完了していません。データソース接続を再確認してください。",
            severity = Severity.WARN,
            actions = listOf(UserAction.GO_TO_CONNECTIONS),
        ),
        TranslationRule(
            pattern = Regex("Address already in use|port.*already in use", RegexOption.IGNORE_CASE),
            userMessage = "ポートが既に使用されています。別の連携が同じポートを使っていないか確認してください。",
            severity = Severity.ERROR,
        ),
        TranslationRule(
            pattern = Regex("docker.*not.*available|docker.*socket", RegexOption.IGNORE_CASE),
            userMessage = "Docker サービスに接続できません。Docker Desktop または Docker daemon が起動しているか確認してください。",
            severity = Severity.ERROR,
        ),
        TranslationRule(
            pattern = Regex("container.*not.*found", RegexOption.IGNORE_CASE),
            userMessage = "Agent コンテナが見つかりません。「kintone と接続」から再作成してください。",
            severity = Severity.WARN,
            actions = listOf(UserAction.RECONNECT),
        ),
        TranslationRule(
            pattern = Regex("(NoClassDefFoundError|ClassNotFoundException)", RegexOption.IGNORE_CASE),
            userMessage = "サービスが正しく起動していません。Web UI を再起動してください。",
            severity = Severity.ERROR,
        ),
    )

    fun translate(rawMessage: String?): UserMessage {
        val raw = rawMessage ?: return UserMessage(
            text = "不明なエラーが発生しました。",
            severity = Severity.ERROR,
            actions = emptyList(),
        )
        val rule = rules.firstOrNull { it.pattern.containsMatchIn(raw) }
        return if (rule != null) {
            UserMessage(rule.userMessage, rule.severity, rule.actions, raw)
        } else {
            UserMessage(raw, Severity.ERROR, emptyList(), raw)
        }
    }
}
