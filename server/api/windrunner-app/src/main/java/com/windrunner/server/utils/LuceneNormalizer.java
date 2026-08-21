package com.windrunner.server.utils;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import java.io.IOException;
import java.io.StringReader;

public class LuceneNormalizer {

    // EnglishAnalyzer: Removes stop words, lowercases, and stems (e.g., "running" -> "run")
    private static final Analyzer ENGLISH_ANALYZER = new EnglishAnalyzer();

    // StandardAnalyzer: Removes stop words and lowercases (no stemming)
    private static final Analyzer STANDARD_ANALYZER = new StandardAnalyzer();

    /**
     * Normalizes text for LLM embeddings using Lucene's production pipeline.
     */
    public static String normalize(String text, boolean useStemming) {
        if (text == null || text.isBlank()) return "";

        Analyzer analyzer = useStemming ? ENGLISH_ANALYZER : STANDARD_ANALYZER;
        StringBuilder result = new StringBuilder();

        // The "fieldName" is dummy here; Lucene requires it but we aren't indexing.
        try (TokenStream ts = analyzer.tokenStream("content", new StringReader(text))) {
            CharTermAttribute termAttr = ts.addAttribute(CharTermAttribute.class);
            ts.reset(); // Mandatory call in Lucene

            while (ts.incrementToken()) {
                if (result.length() > 0) result.append(" ");
                result.append(termAttr.toString());
            }

            ts.end();   // Mandatory call to perform end-of-stream operations
        } catch (IOException e) {
            return text;
        }
        return result.toString();
    }
}
