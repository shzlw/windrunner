package com.windrunner.server.search;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Single source of truth for search text normalization. Both the write path
 * (indexing entity text into search_vec) and the query path (normalizing user
 * queries) must go through this class so they can never drift apart.
 */
@Component
public class SearchNormalizer {

    private final Analyzer analyzer = new EnglishAnalyzer();

    /**
     * Lowercased, stopword-filtered, stemmed tokens joined by spaces.
     * Intended to be indexed via to_tsvector('simple', :normalized) and
     * queried via websearch_to_tsquery('simple', :normalized).
     */
    public String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        try (TokenStream stream = analyzer.tokenStream("", text)) {
            CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                out.append(term.toString()).append(' ');
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString().trim();
    }
}
