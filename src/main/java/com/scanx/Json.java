package com.scanx;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader and writer (the JDK has none built in).
 * Objects become Map, arrays become List, numbers become Long or Double.
 */
public final class Json {

    private Json() {
    }

    // =====================================================
    //  Reading
    // =====================================================

    public static Object parse(String text) {
        Parser parser = new Parser(text);
        parser.skipSpace();
        Object value = parser.value(0);
        parser.skipSpace();
        if (parser.pos != text.length()) {
            throw parser.error("Unexpected text after the JSON value");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("A JSON object was expected");
        }
        return (Map<String, Object>) value;
    }

    private static final class Parser {

        private final String text;
        private int pos = 0;

        Parser(String text) {
            this.text = text;
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " (at position " + pos + ")");
        }

        void skipSpace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        Object value(int depth) {
            if (depth > 40) {
                throw error("JSON is nested too deeply");
            }
            if (pos >= text.length()) {
                throw error("Unexpected end of JSON");
            }
            char c = text.charAt(pos);
            switch (c) {
                case '{':
                    return object(depth);
                case '[':
                    return array(depth);
                case '"':
                    return string();
                case 't':
                    expect("true");
                    return Boolean.TRUE;
                case 'f':
                    expect("false");
                    return Boolean.FALSE;
                case 'n':
                    expect("null");
                    return null;
                default:
                    return number();
            }
        }

        void expect(String word) {
            if (!text.startsWith(word, pos)) {
                throw error("Unexpected text");
            }
            pos += word.length();
        }

        Map<String, Object> object(int depth) {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // {
            skipSpace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipSpace();
                if (peek() != '"') {
                    throw error("A property name was expected");
                }
                String key = string();
                skipSpace();
                if (peek() != ':') {
                    throw error("A colon was expected");
                }
                pos++;
                skipSpace();
                map.put(key, value(depth + 1));
                skipSpace();
                char c = peek();
                pos++;
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    throw error("A comma or } was expected");
                }
            }
        }

        List<Object> array(int depth) {
            List<Object> list = new ArrayList<>();
            pos++; // [
            skipSpace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                skipSpace();
                list.add(value(depth + 1));
                skipSpace();
                char c = peek();
                pos++;
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw error("A comma or ] was expected");
                }
            }
        }

        char peek() {
            if (pos >= text.length()) {
                throw error("Unexpected end of JSON");
            }
            return text.charAt(pos);
        }

        String string() {
            StringBuilder out = new StringBuilder();
            pos++; // opening quote
            while (true) {
                if (pos >= text.length()) {
                    throw error("A string was not closed");
                }
                char c = text.charAt(pos++);
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (pos >= text.length()) {
                    throw error("A string was not closed");
                }
                char e = text.charAt(pos++);
                switch (e) {
                    case '"': out.append('"'); break;
                    case '\\': out.append('\\'); break;
                    case '/': out.append('/'); break;
                    case 'b': out.append('\b'); break;
                    case 'f': out.append('\f'); break;
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    case 'u':
                        if (pos + 4 > text.length()) {
                            throw error("A \\u escape is too short");
                        }
                        try {
                            out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw error("A \\u escape is not valid");
                        }
                        pos += 4;
                        break;
                    default:
                        throw error("An unknown escape");
                }
            }
        }

        Object number() {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            while (pos < text.length() && "0123456789.eE+-".indexOf(text.charAt(pos)) >= 0) {
                pos++;
            }
            String token = text.substring(start, pos);
            if (token.isEmpty() || token.equals("-")) {
                throw error("A value was expected");
            }
            try {
                if (token.indexOf('.') >= 0 || token.indexOf('e') >= 0 || token.indexOf('E') >= 0) {
                    return Double.valueOf(token);
                }
                return Long.valueOf(token);
            } catch (NumberFormatException e) {
                throw error("A number is not valid");
            }
        }

    }

    // =====================================================
    //  Writing
    // =====================================================

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value);
        return out.toString();
    }

    private static void write(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String) {
            quote(out, (String) value);
        } else if (value instanceof Boolean) {
            out.append(value.toString());
        } else if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            out.append(Double.isNaN(d) || Double.isInfinite(d) ? "null" : value.toString());
        } else if (value instanceof Number) {
            out.append(value.toString());
        } else if (value instanceof Map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                quote(out, String.valueOf(entry.getKey()));
                out.append(':');
                write(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof Iterable) {
            out.append('[');
            boolean first = true;
            for (Object item : (Iterable<?>) value) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                write(out, item);
            }
            out.append(']');
        } else {
            quote(out, value.toString());
        }
    }

    private static void quote(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }

    // =====================================================
    //  Reading values out of a parsed object
    // =====================================================

    /** A text value, or null if missing. Numbers are turned into text. */
    public static String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        return value instanceof String ? (String) value : String.valueOf(value);
    }

    /** A whole number, or the fallback if missing or not a number. */
    public static int integer(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    public static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return fallback;
    }

    /** A list of numbers, or null if the value is missing or not a list of numbers. */
    public static List<Double> doubles(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof List)) {
            return null;
        }
        List<Double> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (!(item instanceof Number)) {
                return null;
            }
            result.add(((Number) item).doubleValue());
        }
        return result;
    }

}
