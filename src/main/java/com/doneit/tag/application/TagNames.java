package com.doneit.tag.application;

import java.text.Normalizer;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class TagNames {
    public static final int MAX_TAGS = 20;
    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N}_-][\\p{L}\\p{N}\\p{M} _-]*");
    private TagNames() {}

    public static String normalize(String value) {
        if (value == null) throw new IllegalArgumentException("Invalid tag");
        String name = Normalizer.normalize(value, Normalizer.Form.NFKC).strip();
        if (name.startsWith("#")) name = name.substring(1).strip();
        name = name.replaceAll("(?U)\\s+", " ");
        if (name.codePointCount(0, name.length()) > 40 || !NAME.matcher(name).matches())
            throw new IllegalArgumentException("Invalid tag");
        return name;
    }

    public static String key(String name) { return name.toLowerCase(Locale.ROOT); }

    public static List<String> normalize(Collection<String> values) {
        if (values == null) return List.of();
        if (values.size() > MAX_TAGS) throw new IllegalArgumentException("Too many tags");
        LinkedHashMap<String, String> names = new LinkedHashMap<>();
        for (String value : values) {
            String name = normalize(value);
            names.putIfAbsent(key(name), name);
        }
        return List.copyOf(names.values());
    }

    public static boolean valid(Collection<String> values) {
        try { normalize(values); return true; }
        catch (IllegalArgumentException exception) { return false; }
    }
}
