package com.jambuapp.api.foto;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import com.jambuapp.api.comum.RegraNegocioException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class SupabaseStorageAdapter implements ArmazenamentoFotos {
    private final URI baseUrl;
    private final String apiKey;
    private final String bucket;
    private final Duration timeout;
    private final HttpClient http;

    public SupabaseStorageAdapter(URI baseUrl, String apiKey, String bucket, Duration timeout) {
        if (baseUrl == null || apiKey == null || apiKey.isBlank() || bucket == null || !bucket.matches("[a-z0-9_-]{1,63}"))
            throw new IllegalArgumentException("Configuração do Supabase Storage privado incompleta ou inválida.");
        boolean httpsRemoto = "https".equals(baseUrl.getScheme()) && baseUrl.getHost() != null;
        boolean simuladorLocal = "http".equals(baseUrl.getScheme()) && "127.0.0.1".equals(baseUrl.getHost());
        if ((!httpsRemoto && !simuladorLocal) || baseUrl.getUserInfo() != null || baseUrl.getQuery() != null || baseUrl.getFragment() != null)
            throw new IllegalArgumentException("Supabase Storage exige HTTPS ou simulador local em 127.0.0.1.");
        this.baseUrl = baseUrl.toString().endsWith("/") ? baseUrl : URI.create(baseUrl + "/");
        this.apiKey = apiKey;
        this.bucket = bucket;
        this.timeout = timeout == null ? Duration.ofSeconds(5) : timeout;
        this.http = HttpClient.newBuilder().connectTimeout(this.timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public void gravar(String chave, byte[] conteudo) {
        exigirForaDeTransacao();
        ArmazenamentoFotos.validarChave(chave);
        if (conteudo == null || conteudo.length == 0)
            throw RegraNegocioException.invalido("Conteúdo da foto ausente.");
        var req = request(chave)
                .header("Content-Type", "image/jpeg")
                .header("Cache-Control", "private, no-store")
                .header("x-upsert", "false")
                .POST(HttpRequest.BodyPublishers.ofByteArray(conteudo))
                .build();
        var res = enviar(req);
        if (res.statusCode() == 409)
            throw RegraNegocioException.conflito("Chave de foto já existente no storage privado.");
        if (res.statusCode() != 200 && res.statusCode() != 201)
            throw new RegraNegocioException(HttpStatus.SERVICE_UNAVAILABLE, "storage_indisponivel", "Armazenamento privado de fotos indisponível.");
    }

    @Override
    public byte[] ler(String chave) {
        exigirForaDeTransacao();
        ArmazenamentoFotos.validarChave(chave);
        var res = enviar(request(chave).GET().build());
        if (res.statusCode() == 404 || res.statusCode() == 400) throw RegraNegocioException.inexistente();
        if (res.statusCode() != 200 || res.body() == null || res.body().length == 0)
            throw new RegraNegocioException(HttpStatus.SERVICE_UNAVAILABLE, "storage_indisponivel", "Armazenamento privado de fotos indisponível.");
        return res.body();
    }

    @Override
    public void apagar(String chave) {
        exigirForaDeTransacao();
        ArmazenamentoFotos.validarChave(chave);
        var res = enviar(request(chave).DELETE().build());
        if (res.statusCode() != 200 && res.statusCode() != 204 && res.statusCode() != 404)
            throw new RegraNegocioException(HttpStatus.SERVICE_UNAVAILABLE, "storage_indisponivel", "Falha ao remover objeto do armazenamento privado.");
    }

    @Override
    public boolean existe(String chave) {
        exigirForaDeTransacao();
        ArmazenamentoFotos.validarChave(chave);
        var res = enviar(request(chave).GET().build());
        return res.statusCode() == 200 && res.body() != null && res.body().length > 0;
    }

    private HttpRequest.Builder request(String chave) {
        URI uri = baseUrl.resolve("storage/v1/object/" + bucket + "/" + chave);
        return HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("apikey", apiKey);
    }

    private HttpResponse<byte[]> enviar(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RegraNegocioException(HttpStatus.SERVICE_UNAVAILABLE, "storage_indisponivel", "Armazenamento privado de fotos interrompido.");
        } catch (Exception ex) {
            throw new RegraNegocioException(HttpStatus.SERVICE_UNAVAILABLE, "storage_indisponivel", "Armazenamento privado de fotos indisponível.");
        }
    }

    private static void exigirForaDeTransacao() {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Operação HTTP no Supabase Storage não pode ocorrer dentro de transação de banco.");
    }
}
