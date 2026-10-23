package com.jambuapp.api.autenticacao;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.springframework.stereotype.Component;

/** A chave permanece fora do banco; a resposta é recuperável por poucos segundos. */
@Component
public class ProtecaoRepeticao {
    private final SecretKeySpec chave;
    private final SecureRandom random = new SecureRandom();
    public ProtecaoRepeticao(AuthProperties config) {
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(config.chaveRepeticao()); }
        catch (Exception ex) { throw new IllegalArgumentException("Configure JAMBU_AUTH_CHAVE_REPETICAO com 32 bytes em Base64."); }
        if (bytes.length != 32) throw new IllegalArgumentException("Configure JAMBU_AUTH_CHAVE_REPETICAO com 32 bytes em Base64.");
        chave = new SecretKeySpec(bytes, "AES");
    }
    public String cifrar(String texto, UUID tokenId, UUID operacao) {
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(128, iv));
            cipher.updateAAD((tokenId + ":" + operacao).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(cipher.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException("Não foi possível proteger a resposta de renovação."); }
    }
    public String decifrar(String texto, UUID tokenId, UUID operacao) {
        try {
            String[] partes = texto.split("\\.");
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(128, Base64.getDecoder().decode(partes[0])));
            cipher.updateAAD((tokenId + ":" + operacao).getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(Base64.getDecoder().decode(partes[1])), StandardCharsets.UTF_8);
        } catch (Exception ex) { throw new IllegalStateException("Resposta de renovação indisponível."); }
    }
}
