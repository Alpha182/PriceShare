package dev.priceshare.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Just enough JSON for this mod: writing the upload body, and reading the site's small replies.
 * Hand-written so the mod has no dependencies beyond the game itself.
 */
public final class Json {
    private Json() {}

    // ---- writing ----

    public static void string(StringBuilder out, String s) {
        if (s == null) {
            out.append("null");
            return;
        }
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    public static String quote(String s) {
        StringBuilder b = new StringBuilder();
        string(b, s);
        return b.toString();
    }

    // ---- reading ----

    /** Parses a JSON document into Map / List / String / Double / Boolean / null. */
    public static Object parse(String text) {
        Reader r = new Reader(text);
        r.ws();
        Object v = r.value();
        r.ws();
        if (r.i != text.length()) {
            throw new IllegalArgumentException("Trailing characters at " + r.i);
        }
        return v;
    }

    /** A field of a parsed object as a string, or null. */
    public static String str(Object obj, String key) {
        if (obj instanceof Map<?, ?> m && m.get(key) instanceof String s) {
            return s;
        }
        return null;
    }

    /** A field of a parsed object as a number, or the fallback. */
    public static double num(Object obj, String key, double fallback) {
        if (obj instanceof Map<?, ?> m && m.get(key) instanceof Double d) {
            return d;
        }
        return fallback;
    }

    public static boolean bool(Object obj, String key, boolean fallback) {
        if (obj instanceof Map<?, ?> m && m.get(key) instanceof Boolean b) {
            return b;
        }
        return fallback;
    }

    private static final class Reader {
        final String s;
        int i;
        int depth;

        Reader(String s) {
            this.s = s;
        }

        void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        char peek() {
            if (i >= s.length()) throw new IllegalArgumentException("Unexpected end");
            return s.charAt(i);
        }

        Object value() {
            if (++depth > 64) throw new IllegalArgumentException("Too deep");
            try {
                char c = peek();
                if (c == '{') return object();
                if (c == '[') return array();
                if (c == '"') return string();
                if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
                if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
                if (s.startsWith("null", i)) { i += 4; return null; }
                return number();
            } finally {
                depth--;
            }
        }

        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++;
            ws();
            if (peek() == '}') { i++; return m; }
            while (true) {
                ws();
                String k = string();
                ws();
                if (peek() != ':') throw new IllegalArgumentException("Expected : at " + i);
                i++;
                ws();
                m.put(k, value());
                ws();
                char c = peek();
                i++;
                if (c == '}') return m;
                if (c != ',') throw new IllegalArgumentException("Expected , at " + (i - 1));
            }
        }

        List<Object> array() {
            List<Object> l = new ArrayList<>();
            i++;
            ws();
            if (peek() == ']') { i++; return l; }
            while (true) {
                ws();
                l.add(value());
                ws();
                char c = peek();
                i++;
                if (c == ']') return l;
                if (c != ',') throw new IllegalArgumentException("Expected , at " + (i - 1));
            }
        }

        String string() {
            if (peek() != '"') throw new IllegalArgumentException("Expected string at " + i);
            i++;
            StringBuilder b = new StringBuilder();
            while (true) {
                char c = peek();
                i++;
                if (c == '"') return b.toString();
                if (c == '\\') {
                    char e = peek();
                    i++;
                    switch (e) {
                        case 'n' -> b.append('\n');
                        case 'r' -> b.append('\r');
                        case 't' -> b.append('\t');
                        case 'b' -> b.append('\b');
                        case 'f' -> b.append('\f');
                        case 'u' -> {
                            if (i + 4 > s.length()) throw new IllegalArgumentException("Bad escape");
                            b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> b.append(e);
                    }
                } else {
                    b.append(c);
                }
            }
        }

        Double number() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (start == i) throw new IllegalArgumentException("Unexpected character at " + i);
            return Double.parseDouble(s.substring(start, i));
        }
    }
}
