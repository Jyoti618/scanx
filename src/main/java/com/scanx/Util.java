package com.scanx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Small helpers that older Java versions do not have built in. */
public final class Util {

    private Util() {
    }

    public static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    public static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /** Throws IllegalArgumentException if the text is not valid hex. */
    public static byte[] unhex(String text) {
        if (text.length() % 2 != 0) {
            throw new IllegalArgumentException("Odd length");
        }
        byte[] out = new byte[text.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int high = Character.digit(text.charAt(2 * i), 16);
            int low = Character.digit(text.charAt(2 * i + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Not hex");
            }
            out[i] = (byte) ((high << 4) | low);
        }
        return out;
    }

    /** Reads at most max + 1 bytes (so the caller can tell the body was too big). */
    public static byte[] readLimited(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while (total <= max && (read = in.read(buffer, 0, Math.min(buffer.length, max + 1 - total))) != -1) {
            out.write(buffer, 0, read);
            total += read;
        }
        return out.toByteArray();
    }

}
