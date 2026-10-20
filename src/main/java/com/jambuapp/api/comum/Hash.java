package com.jambuapp.api.comum;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

public final class Hash {
    private Hash() {}
    public static String sha256(String texto) {
        return sha256Bytes(texto.getBytes(StandardCharsets.UTF_8));
    }
    public static String sha256Bytes(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
