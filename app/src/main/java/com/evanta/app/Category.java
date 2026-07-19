package com.evanta.app;

public class Category {

    private final String label;
    private final String value; // null = "All" (no filter)
    private final int iconRes;
    private final int color;

    public Category(String label, String value, int iconRes, int color) {
        this.label = label;
        this.value = value;
        this.iconRes = iconRes;
        this.color = color;
    }

    public String getLabel() { return label; }
    public String getValue() { return value; }
    public int getIconRes() { return iconRes; }
    public int getColor() { return color; }
}
