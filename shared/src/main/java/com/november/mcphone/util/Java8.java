package com.november.mcphone.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Arrays;

/** Bounded I/O and text methods available on Java 8. */
public final class Java8 {
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private Java8() {}

    public static boolean isBlank(String value) {
        return leading(value) == value.length();
    }

    public static String strip(String value) {
        int start = leading(value);
        return start == value.length() ? "" : value.substring(start, trailing(value));
    }

    public static String stripLeading(String value) {
        return value.substring(leading(value));
    }

    public static String stripTrailing(String value) {
        return value.substring(0, trailing(value));
    }

    private static int leading(String value) {
        int index = 0;
        while (index < value.length()) {
            int codePoint = value.codePointAt(index);
            if (!Character.isWhitespace(codePoint)) break;
            index += Character.charCount(codePoint);
        }
        return index;
    }

    private static int trailing(String value) {
        int index = value.length();
        while (index > 0) {
            int codePoint = value.codePointBefore(index);
            if (!Character.isWhitespace(codePoint)) break;
            index -= Character.charCount(codePoint);
        }
        return index;
    }

    public static String repeat(String value, int count) {
        if (count < 0) throw new IllegalArgumentException("Negative repeat count");
        if (value.isEmpty() || count == 0) return "";
        StringBuilder result = new StringBuilder(Math.multiplyExact(value.length(), count));
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }

    public static String formatHex(byte[] bytes) { return formatHex(bytes, 0, bytes.length); }

    public static String formatHex(byte[] bytes, int from, int to) {
        if (from < 0 || to > bytes.length || from > to) throw new IndexOutOfBoundsException();
        char[] text = new char[(to - from) * 2];
        for (int i = from, j = 0; i < to; i++) {
            text[j++] = HEX[(bytes[i] >>> 4) & 15];
            text[j++] = HEX[bytes[i] & 15];
        }
        return new String(text);
    }

    public static byte[] readNBytes(InputStream input, int limit) throws IOException {
        if (limit < 0) throw new IllegalArgumentException("Negative read limit");
        if (limit == 0) return new byte[0];
        byte[] buffer = new byte[Math.min(8192, limit)];
        int count = readUpTo(input, buffer, buffer.length);
        if (count == limit || count < buffer.length) return Arrays.copyOf(buffer, count);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(buffer.length);
        bytes.write(buffer, 0, count);
        while (bytes.size() < limit) {
            int want = Math.min(buffer.length, limit - bytes.size());
            int read = readUpTo(input, buffer, want);
            bytes.write(buffer, 0, read);
            if (read < want) break;
        }
        return bytes.toByteArray();
    }

    private static int readUpTo(InputStream input, byte[] buffer, int limit) throws IOException {
        int offset = 0;
        while (offset < limit) {
            int count = input.read(buffer, offset, limit - offset);
            if (count < 0) break;
            if (count == 0) {
                int next = input.read();
                if (next < 0) break;
                buffer[offset++] = (byte) next;
            } else offset += count;
        }
        return offset;
    }

    public static String readString(Path path) throws IOException {
        return readString(path, StandardCharsets.UTF_8);
    }

    public static String readString(Path path, Charset charset) throws IOException {
        return charset.newDecoder().decode(ByteBuffer.wrap(Files.readAllBytes(path))).toString();
    }

    public static Path writeString(Path path, CharSequence text, OpenOption... options) throws IOException {
        return writeString(path, text, StandardCharsets.UTF_8, options);
    }

    public static Path writeString(Path path, CharSequence text, Charset charset, OpenOption... options) throws IOException {
        ByteBuffer encoded = charset.newEncoder().encode(CharBuffer.wrap(text));
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        return Files.write(path, bytes, options);
    }

    public static <T> T requireNonNullElse(T value, T fallback) {
        return value != null ? value : java.util.Objects.requireNonNull(fallback, "fallback");
    }

    public static void skipNBytes(InputStream input, long remaining) throws IOException {
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
            } else {
                if (input.read() < 0) throw new java.io.EOFException();
                remaining--;
            }
        }
    }

    public static String formEncode(String value, Charset charset) {
        try {
            return java.net.URLEncoder.encode(value, charset.name());
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalArgumentException("Unsupported form encoding: " + charset, e);
        }
    }
}
