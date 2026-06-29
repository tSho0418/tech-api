@AGENTS.md

# ITニュース要約サービス（Tech-Box） — プロジェクト仕様書

Claude Code への引き渡し用ドキュメント。バックエンド・フロントエンドそれぞれの実装に使用すること。

---

## 1. サービス概要

毎朝決まった時間に複数のITニュースソースから記事を自動収集し、AIで日本語要約してダッシュボードに可視化する個人利用向けサービス。LINEへのプッシュ通知にも対応する。

---

## 2. リポジトリ構成

リポジトリは2つに分割する。本リポジトリはバックエンドであるため、フロントエンドの実装は行わない

- `tech-api` … Spring Boot アプリケーション（Render.com にデプロイ）
- `tech-front` … Next.js アプリケーション（Vercel にデプロイ）

---

## 3. 技術スタック

| レイヤー | 技術 | 備考 |
|---|---|---|
| バックエンド | Spring Boot 3.x (Java 21) | REST API + バッチ処理 |
| フロントエンド | Next.js 14 (App Router, TypeScript) | ダッシュボード |
| データベース | Supabase (PostgreSQL) | マネージド、無料枠 |
| AI要約 | Gemini API (gemini-2.5-flash) | 無料枠、クレジットカード不要 |
| 通知 | LINE Messaging API | Push通知、個人トークン |
| 認証 | NextAuth.js + Google OAuth | ダッシュボード保護用 |
| バックエンドホスティング | Render.com | 無料枠、Cron Jobs機能を使用 |
| フロントエンドホスティング | Vercel | 無料枠 |
| スケジューラ | Render Cron Jobs | 毎朝 07:00 JST |
| 開発環境 | Docker / Docker Compose | 3コンテナ構成 |

---

## 4. データソース


| ソース | 取得方法 | 件数 |
|---|---|---|
| Hacker News | 公式 API（無料・商用利用可） | 上位30件 |
| Zenn | RSS フィード | トレンド20件 |
| GitHub Trending | スクレイピング | 日次トレンド |
| dev.to / Qiita | 公式 API（無料） | 各20件 |

---

## 5. DB スキーマ（Supabase / PostgreSQL）

### sources テーブル
```sql
CREATE TABLE sources (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name       VARCHAR(50)  NOT NULL,
  type       VARCHAR(20)  NOT NULL CHECK (type IN ('api', 'rss', 'scraping')),
  base_url   TEXT         NOT NULL,
  is_active  BOOLEAN      NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

### articles テーブル
```sql
CREATE TABLE articles (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  source_id    UUID         NOT NULL REFERENCES sources(id),
  title        TEXT         NOT NULL,
  url          TEXT         NOT NULL UNIQUE,
  author       VARCHAR(100),
  score        INTEGER,
  tags         TEXT[],
  language     VARCHAR(5)   NOT NULL DEFAULT 'en' CHECK (language IN ('ja', 'en')),
  published_at TIMESTAMPTZ,
  fetched_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  is_notified  BOOLEAN      NOT NULL DEFAULT false,
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_articles_url         ON articles(url);
CREATE INDEX idx_articles_fetched_at  ON articles(fetched_at DESC);
CREATE INDEX idx_articles_source_id   ON articles(source_id);
```

### summaries テーブル
```sql
CREATE TABLE summaries (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  article_id        UUID         NOT NULL UNIQUE REFERENCES articles(id),
  summary_ja        TEXT         NOT NULL,
  reason_ja         TEXT,
  quality_score     FLOAT,
  model_used        VARCHAR(50)  NOT NULL,
  prompt_tokens     INTEGER,
  completion_tokens INTEGER,
  created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

### trending_keywords テーブル
```sql
CREATE TABLE trending_keywords (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  keyword     VARCHAR(100) NOT NULL,
  count       INTEGER      NOT NULL DEFAULT 0,
  prev_count  INTEGER      NOT NULL DEFAULT 0,
  growth_rate FLOAT,
  source_ids  UUID[],
  date        DATE         NOT NULL,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  UNIQUE (keyword, date)
);

CREATE INDEX idx_trending_date_keyword ON trending_keywords(date DESC, keyword);
```

### batch_logs テーブル
```sql
CREATE TABLE batch_logs (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  batch_type     VARCHAR(20)  NOT NULL CHECK (batch_type IN ('collect', 'summarize', 'full')),
  status         VARCHAR(10)  NOT NULL CHECK (status IN ('running', 'success', 'partial', 'fail')),
  fetched_count  INTEGER      NOT NULL DEFAULT 0,
  skipped_count  INTEGER      NOT NULL DEFAULT 0,
  error_count    INTEGER      NOT NULL DEFAULT 0,
  error_detail   TEXT,
  started_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  finished_at    TIMESTAMPTZ,
  created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

---

## 6. バックエンド（Spring Boot）仕様

### 6-1. プロジェクト構成

```
tech-api/
├── src/main/java/com/example/itnews/
│   ├── batch/
│   │   ├── collector/          # ソース別収集クラス
│   │   │   ├── HackerNewsCollector.java
│   │   │   ├── ZennCollector.java
│   │   │   ├── GitHubTrendingCollector.java
│   │   │   └── DevToQiitaCollector.java
│   │   ├── BatchJobService.java      # バッチ全体の制御
│   │   ├── SummarizerService.java    # Gemini API 呼び出し
│   │   ├── TrendAnalyzer.java        # trending_keywords 集計
│   │   └── NotifierService.java      # LINE 通知
│   ├── api/
│   │   ├── ArticleController.java
│   │   ├── TrendingController.java
│   │   └── BatchController.java
│   ├── domain/
│   │   ├── Article.java
│   │   ├── Summary.java
│   │   ├── TrendingKeyword.java
│   │   └── BatchLog.java
│   ├── repository/             # Spring Data JPA
│   └── config/
│       ├── SecurityConfig.java       # API キー認証
│       └── CorsConfig.java           # Next.js からのアクセス許可
└── src/main/resources/
    └── application.yml
```

### 6-2. 環境変数

```
SUPABASE_URL=
SUPABASE_DB_PASSWORD=
GEMINI_API_KEY=
LINE_CHANNEL_ACCESS_TOKEN=
LINE_USER_ID=
API_SECRET_KEY=          # Next.js → Spring Boot の認証キー
FRONTEND_ORIGIN=         # CORS 許可オリジン（Vercel の URL）
```

### 6-3. API エンドポイント一覧

すべてのエンドポイントは `X-API-Key: {API_SECRET_KEY}` ヘッダーによる認証が必要。

#### 記事系 `/api/articles`

| メソッド | パス | 説明 |
|---|---|---|
| GET | `/api/articles` | 記事一覧。クエリ: `source`, `tag`, `date`, `page`, `size` |
| GET | `/api/articles/{id}` | 記事詳細（要約・原文URL含む） |
| GET | `/api/articles/today` | 本日取得分をスコア降順で返す |
| PATCH | `/api/articles/{id}/read` | 既読フラグ更新 |

#### トレンド系 `/api/trending`

| メソッド | パス | 説明 |
|---|---|---|
| GET | `/api/trending/keywords` | 急上昇キーワード一覧。クエリ: `days=7`, `limit=20` |
| GET | `/api/trending/keywords/{keyword}/history` | キーワードの時系列データ（グラフ用） |
| GET | `/api/trending/sources` | ソース別取得件数サマリー（本日/7日） |
| GET | `/api/trending/stats` | ダッシュボード用サマリー（総記事数・本日数・ソース数） |

#### バッチ管理 `/api/batch`

| メソッド | パス | 説明 |
|---|---|---|
| GET | `/api/batch/status` | 最終実行日時・件数・ステータス |
| GET | `/api/batch/logs` | 実行履歴一覧。クエリ: `limit=30` |
| POST | `/api/batch/run` | バッチ手動実行トリガー |
| POST | `/api/batch/notify` | LINE 通知手動トリガー（テスト用） |

### 6-4. バッチ処理フロー

Render Cron Jobs が毎朝 07:00 JST に `POST /api/batch/run` を呼び出す。

```
1. batch_logs に status=running で開始記録
2. 4ソースを CompletableFuture で並列収集
3. url UNIQUE 制約で重複排除（INSERT ... ON CONFLICT DO NOTHING）
4. 新規記事を1件ずつ Gemini API に投げて日本語要約
   - RPM制限対策として1リクエストごとに Thread.sleep(4000)
   - 失敗時は exponential backoff で最大3回リトライ
   - 3回失敗したらスキップして error_count をインクリメント
5. articles + summaries を Supabase に保存
6. trending_keywords を集計・更新（タグ・タイトルから抽出、前日比 growth_rate 計算）
7. スコア上位3〜5件を LINE に Push 通知（要約 + 原文URL）
8. batch_logs を success / partial / fail で完了記録
   - success  : エラー件数 = 0
   - partial  : 1件以上スキップあり
   - fail     : 収集自体が全滅
エラー発生時: batch_logs に fail 記録 → LINE にエラー通知 → 終了
```

### 6-5. Gemini API プロンプト仕様

```
システムプロンプト:
  あなたはITエンジニア向けのニュース要約AIです。
  与えられた記事を日本語で簡潔に要約してください。

ユーザープロンプト:
  タイトル: {title}
  本文（または概要）: {content}

  以下のJSON形式のみで回答してください:
  {
    "summary_ja": "3〜5行の日本語要約",
    "reason_ja": "なぜ今注目されているかを1行で",
    "quality_score": 0.0〜1.0の信頼度スコア
  }
```

---

## 7. フロントエンド（Next.js）仕様

### 7-1. プロジェクト構成

```
tech-front/
├── app/
│   ├── (auth)/
│   │   └── login/page.tsx
│   ├── dashboard/
│   │   ├── page.tsx             # トップ（本日の記事一覧）
│   │   ├── trending/page.tsx    # トレンド・急上昇キーワード
│   │   └── settings/page.tsx   # バッチ実行・ログ確認
│   ├── api/
│   │   └── auth/[...nextauth]/route.ts
│   └── layout.tsx
├── components/
│   ├── ArticleCard.tsx
│   ├── TrendingChart.tsx
│   ├── KeywordRanking.tsx
│   ├── BatchStatus.tsx
│   └── SourceFilter.tsx
└── lib/
    └── api.ts                   # Spring Boot API クライアント
```

### 7-2. 環境変数

```
NEXTAUTH_SECRET=
NEXTAUTH_URL=
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
BACKEND_URL=             
BACKEND_API_KEY=   
```

### 7-3. ページ仕様

#### `/dashboard`（トップ）
- 本日の記事一覧（`GET /api/articles/today`）
- ソース・タグのフィルタリング
- 各記事に要約・原文リンク・既読ボタン
- バッチ最終実行状態のサマリーバー（`GET /api/batch/status`）

#### `/dashboard/trending`
- 急上昇キーワードランキング（`GET /api/trending/keywords`）
- キーワード推移グラフ（`GET /api/trending/keywords/{kw}/history`）
- ソース別取得件数グラフ（`GET /api/trending/sources`）

#### `/dashboard/settings`
- バッチ実行履歴テーブル（`GET /api/batch/logs`）
- 手動バッチ実行ボタン（`POST /api/batch/run`）
- LINE通知テストボタン（`POST /api/batch/notify`）

### 7-4. API クライアント仕様（`lib/api.ts`）

Next.js の Route Handlers は使用しない。`lib/api.ts` に Spring Boot への fetch ラッパーを実装し、Server Components から直接呼び出す。`X-API-Key` ヘッダーは `BACKEND_API_KEY` 環境変数から付与する。

---

## 8. セキュリティ方針

- Spring Boot の全エンドポイントを `X-API-Key` ヘッダーで保護
- Next.js ダッシュボードを NextAuth.js（Google OAuth）で保護
- Supabase への接続は Spring Boot のみが行う（Next.js から直接アクセスしない）
- 環境変数は各プラットフォームのシークレット管理機能を使用（コードに含めない）

---

## 9. 著作権・利用規約の方針

- 記事の全文保存は行わない。タイトル・URL・メタ情報のみ保存
- 要約文は AI が生成した独自コンテンツとして扱う
- 各記事には必ず原文 URL を付与し、ユーザーを元サイトへ誘導する

---

## 10. 実装優先順位

1. Docker 開発環境の構築（`docker compose up` で3コンテナ起動確認）
2. DB スキーマ作成（ローカル PostgreSQL コンテナに対して初期化 SQL を実行）
3. Spring Boot バッチ処理（収集 → 要約 → 保存）
4. Spring Boot REST API
5. Next.js ダッシュボード基本画面
6. LINE 通知
7. NextAuth.js 認証

---

## 11. Docker 開発環境

### 11-1. 概要

ローカル開発環境は Docker Compose で構築する。以下の3コンテナを起動する。

| コンテナ名 | イメージ | ポート | 役割 |
|---|---|---|---|
| `backend` | `eclipse-temurin:21-jdk` (マルチステージビルド) | 8080:8080 | Spring Boot API + バッチ |
| `frontend` | `node:20-alpine` | 3000:3000 | Next.js 開発サーバー |
| `db` | `postgres:16-alpine` | 5432:5432 | ローカル PostgreSQL |

本番（Render.com + Vercel + Supabase）と開発（Docker）で接続先 DB が異なる。環境変数で切り替える。

### 11-2. ディレクトリ構成

`docker-compose.yml` は `tech-api` リポジトリに置き、`tech-front` は相対パスで参照する。上位ディレクトリに置くとどのリポジトリにも属さず git で追跡できないためこの方式を採用する。

```
~/projects/
├── tech-api/             ← git リポジトリ
│   ├── docker-compose.yml       ← ここで一元管理
│   ├── Dockerfile
│   ├── docker/
│   │   └── init.sql             ← DB 初期化 SQL
│   └── src/
└── tech-front/            ← git リポジトリ
    ├── Dockerfile
    └── src/
```

**前提**: `tech-api` と `tech-front` が同じ親ディレクトリに並んでいること。README にクローン手順として明記する。

```bash
# クローン手順（README に記載する）
git clone https://github.com/tSho0418/tech-api
git clone https://github.com/yourname/tech-front
cd tech-api
docker compose up --build
```

### 11-3. docker-compose.yml

```yaml
services:
  db:
    image: postgres:16-alpine
    container_name: tech-db
    environment:
      POSTGRES_DB: itnews
      POSTGRES_USER: itnews
      POSTGRES_PASSWORD: itnews
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./tech-api/docker/init.sql:/docker-entrypoint-initdb.d/init.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U itnews"]
      interval: 5s
      timeout: 5s
      retries: 5

  backend:
    build:
      context: ./tech-api
      dockerfile: Dockerfile
    container_name: tech-api
    ports:
      - "8080:8080"
    env_file:
      - .env
    depends_on:
      db:
        condition: service_healthy
    volumes:
      - ./tech-api/src:/app/src   # ホットリロード用（devtools使用時）

  frontend:
    build:
      context: ../tech-front
      dockerfile: Dockerfile
    container_name: tech-front
    ports:
      - "3000:3000"
    env_file:
      - ../tech-front/.env.local
    depends_on:
      - backend
    volumes:
      - ../tech-front:/app         # ホットリロード用
      - /app/node_modules                # node_modules はコンテナ内を使用

volumes:
  postgres_data:
```

### 11-4. tech-api/Dockerfile

```dockerfile
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle .
RUN ./gradlew dependencies --no-daemon
COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 11-5. tech-front/Dockerfile

```dockerfile
FROM node:20-alpine
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
EXPOSE 3000
CMD ["npm", "run", "dev"]
```

### 11-6. 環境変数ファイル（.env）

各リポジトリのルートに`.env` を作成する。`.gitignore` に追加して絶対にコミットしない。

#### tech-apiの環境変数ファイル
```
# DB接続
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/itnews
SPRING_DATASOURCE_USERNAME=itnews
SPRING_DATASOURCE_PASSWORD=itnews

# Gemini API
GEMINI_API_KEY=your_gemini_api_key

# LINE
LINE_CHANNEL_ACCESS_TOKEN=your_line_token
LINE_USER_ID=your_line_user_id

# セキュリティ
API_SECRET_KEY=your_random_secret_key
FRONTEND_ORIGIN=http://localhost:3000
```

#### tech-frontの環境変数ファイル
```
# バックエンド接続
BACKEND_URL=http://localhost:8080
BACKEND_API_KEY=your_random_secret_key

# NextAuth
NEXTAUTH_SECRET=your_nextauth_secret
NEXTAUTH_URL=http://localhost:3000
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret
```

### 11-7. DB 初期化 SQL

`tech-api/docker/init.sql` に Section 5 のスキーマ DDL をすべて記述する。コンテナ初回起動時に自動実行される。

### 11-8. 本番との接続先の違い

| 設定項目 | ローカル（Docker） | 本番 |
|---|---|---|
| DB 接続先 | `db` コンテナ（PostgreSQL 16） | Supabase |
| バックエンド URL | `http://backend:8080` | Render.com の公開 URL |
| フロントエンド URL | `http://localhost:3000` | Vercel の公開 URL |
| CORS 許可オリジン | `http://localhost:3000` | Vercel の公開 URL |

Spring Boot の `application.yml` は環境変数で上書きする設計にする。本番固有の設定は `application-prod.yml` に分離してもよい。

### 11-9. 起動コマンド

```bash
# 初回 or イメージ再ビルド時
docker compose up --build

# 通常起動
docker compose up

# バックグラウンド起動
docker compose up -d

# 停止
docker compose down

# DB データも含めてリセット
docker compose down -v
```

---

## 12. API レスポンス形式

フロントエンド実装の参照用。全フィールドは **camelCase**（Jackson デフォルト）。日時は ISO 8601 形式（例: `"2026-06-28T07:00:00Z"`）。

### 12-1. 共通

#### エラーレスポンス（4xx / 5xx）

```json
{
  "code": "NOT_FOUND",
  "message": "Article not found: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
}
```

| code | HTTP | 発生条件 |
|---|---|---|
| `FORBIDDEN` | 403 | `X-API-Key` ヘッダーが不正または欠落 |
| `NOT_FOUND` | 404 | 指定 ID のリソースが存在しない |
| `VALIDATION_ERROR` | 400 | クエリパラメータのバリデーション失敗 |
| `INTERNAL_ERROR` | 500 | サーバー内部エラー |

#### ページネーションレスポンス（`PageResponse<T>`）

`GET /api/articles` のみ使用。

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 87,
  "totalPages": 5,
  "last": false
}
```

---

### 12-2. 記事系

#### `ArticleResponse`（記事1件）

`GET /api/articles`・`GET /api/articles/today`・`GET /api/articles/{id}` で使用。

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "sourceName": "Hacker News",
  "title": "Why does kinetic energy increase quadratically with speed?",
  "url": "https://physics.stackexchange.com/questions/535/...",
  "author": "username",
  "score": 342,
  "tags": ["physics", "energy"],
  "language": "en",
  "publishedAt": "2026-06-28T10:00:00Z",
  "fetchedAt": "2026-06-28T07:00:00Z",
  "notified": false,
  "summary": {
    "id": "660e8400-e29b-41d4-a716-446655440001",
    "summaryJa": "運動エネルギーが速度の2乗に比例する理由を解説...",
    "reasonJa": "物理学の基礎概念への関心が高まっている",
    "qualityScore": 0.85,
    "modelUsed": "gemini-2.5-flash"
  }
}
```

- `summary` はまだ要約されていない場合 `null`
- `author` / `score` / `tags` / `publishedAt` はソースによって `null` の場合あり
- `language`: `"en"` または `"ja"`

#### `GET /api/articles` クエリパラメータ

| パラメータ | デフォルト | 説明 |
|---|---|---|
| `source` | なし | ソース名でフィルタ（例: `"Hacker News"`） |
| `tag` | なし | タグでフィルタ |
| `date` | 当日 | 対象日（ISO 8601 date: `2026-06-28`） |
| `page` | `0` | ページ番号（0始まり） |
| `size` | `20` | 1ページあたり件数 |

#### `GET /api/articles/today` レスポンス

ページネーションなし。スコア降順の配列を返す。

```json
[
  { "id": "...", "sourceName": "Hacker News", ... },
  { "id": "...", "sourceName": "Zenn", ... }
]
```

#### `PATCH /api/articles/{id}/read` レスポンス

`204 No Content`（ボディなし）

---

### 12-3. トレンド系

#### `GET /api/trending/keywords`

急上昇キーワードのランキング。

```json
[
  { "keyword": "AI", "totalCount": 15 },
  { "keyword": "Rust", "totalCount": 9 },
  { "keyword": "WebAssembly", "totalCount": 6 }
]
```

| クエリ | デフォルト | 説明 |
|---|---|---|
| `days` | `7` | 集計対象の日数 |
| `limit` | `20` | 返却件数上限 |

#### `GET /api/trending/keywords/{keyword}/history`

キーワードの日別推移（グラフ用）。

```json
[
  {
    "id": "770e8400-e29b-41d4-a716-446655440002",
    "keyword": "AI",
    "count": 8,
    "prevCount": 5,
    "growthRate": 0.6,
    "date": "2026-06-28"
  },
  {
    "id": "880e8400-e29b-41d4-a716-446655440003",
    "keyword": "AI",
    "count": 5,
    "prevCount": 3,
    "growthRate": 0.67,
    "date": "2026-06-27"
  }
]
```

- `growthRate`: `(count - prevCount) / prevCount`。初日など前日データがない場合は `null`
- 日付降順で返却

#### `GET /api/trending/sources`

ソース別取得件数（本日 / 直近7日）。週合計の多い順にソートされる。

```json
[
  { "sourceName": "Hacker News", "todayCount": 30, "weekCount": 187 },
  { "sourceName": "dev.to",      "todayCount": 20, "weekCount": 134 },
  { "sourceName": "Qiita",       "todayCount": 20, "weekCount": 130 },
  { "sourceName": "Zenn",        "todayCount": 20, "weekCount": 128 },
  { "sourceName": "GitHub Trending", "todayCount": 0, "weekCount": 87 }
]
```

#### `GET /api/trending/stats`

ダッシュボード上部のサマリーカード用。

```json
{
  "totalArticles": 523,
  "todayArticles": 87,
  "sourceCount": 5,
  "summaryCount": 450
}
```

---

### 12-4. バッチ管理系

#### `BatchLogResponse`（バッチログ1件）

`GET /api/batch/status`・`GET /api/batch/logs` で使用。

```json
{
  "id": "990e8400-e29b-41d4-a716-446655440004",
  "batchType": "full",
  "status": "success",
  "fetchedCount": 45,
  "skippedCount": 42,
  "errorCount": 0,
  "errorDetail": null,
  "startedAt": "2026-06-28T07:00:00Z",
  "finishedAt": "2026-06-28T07:05:32Z"
}
```

| フィールド | 説明 |
|---|---|
| `batchType` | `"full"` のみ（現在） |
| `status` | `"running"` / `"success"` / `"partial"` / `"fail"` |
| `fetchedCount` | 新規保存した記事数 |
| `skippedCount` | URL重複でスキップした記事数 |
| `errorCount` | 要約失敗・スキップした記事数 |
| `errorDetail` | `"fail"` 時のエラーメッセージ。それ以外は `null` |
| `finishedAt` | `"running"` 中は `null` |

#### `GET /api/batch/logs` レスポンス

`BatchLogResponse` の配列（デフォルト最新30件）。

```json
[
  { "id": "...", "status": "success", ... },
  { "id": "...", "status": "partial", ... }
]
```

| クエリ | デフォルト | 説明 |
|---|---|---|
| `limit` | `30` | 返却件数上限 |

#### `POST /api/batch/run` / `POST /api/batch/notify` / `POST /api/batch/notify/test`

`202 Accepted`（ボディなし）。バッチは非同期実行のため即時リターン。

