package org.vpmq.dailyword.utils;

import java.util.AbstractMap;
import java.util.Map;

public class MarkdownEscaper {

    private static final Map<String, String> REPLACEMENTS = Map.ofEntries(
        new AbstractMap.SimpleEntry<>(".", "\\."),
        new AbstractMap.SimpleEntry<>("-", "\\-"),
        new AbstractMap.SimpleEntry<>("(", "\\("),
        new AbstractMap.SimpleEntry<>(")", "\\)"),
        new AbstractMap.SimpleEntry<>("{", "\\{"),
        new AbstractMap.SimpleEntry<>("}", "\\}"),
        new AbstractMap.SimpleEntry<>("[", "\\]"),
        new AbstractMap.SimpleEntry<>("]", "\\]"),
        new AbstractMap.SimpleEntry<>(":", "\\:"),
        new AbstractMap.SimpleEntry<>("\"", "\\\"")
    );

    public static String escape(String md) {
        String res = md;
        for (Map.Entry<String, String> entry : REPLACEMENTS.entrySet()) {
            res = res.replace(entry.getKey(), entry.getValue());
        }
        return res;
    }

    private MarkdownEscaper() {}
}
