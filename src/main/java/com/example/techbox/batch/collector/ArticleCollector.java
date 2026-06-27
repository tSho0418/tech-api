package com.example.techbox.batch.collector;

import com.example.techbox.domain.Article;

import java.util.List;

public interface ArticleCollector {

    List<Article> collect();

    String getSourceName();
}
