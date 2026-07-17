package com.example.evanta;

import java.util.HashMap;
import java.util.Map;

public class CategoryColors {

    private static final Map<String, Integer> COLORS = new HashMap<>();

    static {
        COLORS.put("Tech", 0xFF2F80ED);
        COLORS.put("Cultural", 0xFFD6449C);
        COLORS.put("Sports", 0xFF27AE60);
        COLORS.put("Workshop", 0xFFF2994A);
        COLORS.put("Music", 0xFF9B59F6);
    }

    public static int forCategory(String category) {
        Integer color = COLORS.get(category);
        return color != null ? color : 0xFF7C4DFF;
    }
}