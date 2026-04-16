package com.univent.util;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SynonymMapper {

    private static final Map<String, String> SYNONYMS = Map.of(
            "cs", "computer science",
            "cse", "computer science engineering",
            "it", "information technology",
            "ece", "electronics and communication",
            "mech", "mechanical engineering",
            "civil", "civil engineering",
            "ee", "electrical engineering",
            "ba", "business administration",
            "mba", "master of business administration"
    );

    public String expand(String query) {
        if (query == null) return null;
        
        String lowerQuery = query.toLowerCase().trim();
        return SYNONYMS.getOrDefault(lowerQuery, query);
    }
}
