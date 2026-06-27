-- Tech-Box DB 初期化スキーマ

CREATE TABLE sources (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name       VARCHAR(50)  NOT NULL,
  type       VARCHAR(20)  NOT NULL CHECK (type IN ('api', 'rss', 'scraping')),
  base_url   TEXT         NOT NULL,
  is_active  BOOLEAN      NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

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

CREATE INDEX idx_articles_url        ON articles(url);
CREATE INDEX idx_articles_fetched_at ON articles(fetched_at DESC);
CREATE INDEX idx_articles_source_id  ON articles(source_id);

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

-- 初期ソースデータ
INSERT INTO sources (name, type, base_url) VALUES
  ('Hacker News',   'api',      'https://hacker-news.firebaseio.com/v0'),
  ('Zenn',          'rss',      'https://zenn.dev/feed'),
  ('GitHub Trending','scraping', 'https://github.com/trending'),
  ('dev.to',        'api',      'https://dev.to/api'),
  ('Qiita',         'api',      'https://qiita.com/api/v2');
