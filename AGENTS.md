# AGENTS.md — Tech-API エージェント作業ルール

## パッケージ規約
- ルートパッケージ: `com.example.techbox`
- CLAUDE.md に `itnews` と記載があっても `techbox` で統一する

## コーディング規約
- Lombok を積極的に使用する (`@Data`, `@Builder`, `@RequiredArgsConstructor` など)
- `@Entity` クラスは `domain/` パッケージに配置し、Lombok `@Builder` + JPA アノテーションを組み合わせる
- UUID 主キーは `@GeneratedValue(strategy = GenerationType.UUID)` で生成する
- 日時は `OffsetDateTime` を使用する（`TIMESTAMPTZ` に対応）
- null 安全のため `Optional` を積極的に返す (Repository レイヤー)

## テスト方針
- 単体テスト: JUnit 5 + Mockito
- 統合テスト: Testcontainers (PostgreSQL) を使用。H2 は使わない
- テストクラス命名: `*Test.java` (単体), `*IntegrationTest.java` (統合)

## 環境変数
- アプリケーション設定は必ず環境変数経由で注入する
- ハードコードされた接続情報・APIキーは絶対に書かない
- ローカル開発: `.env` ファイル (`.gitignore` 済み)
- CI: GitHub Actions の `env:` ブロック
- 本番: Render.com の Environment Variables

## DBスキーマ管理
- DDL は `docker/init.sql` で管理する（ローカル Docker 用）
- `spring.jpa.hibernate.ddl-auto` は `none` に固定（スキーマ変更は手動 SQL で行う）

## 外部 API
- Gemini API: `gemini-2.5-flash` を使用。レートリミット対策で1リクエストごとに4秒待機
- LINE Messaging API: Push通知のみ使用
- Hacker News / dev.to / Qiita は公式 API、Zenn は RSS、GitHub Trending はスクレイピング
