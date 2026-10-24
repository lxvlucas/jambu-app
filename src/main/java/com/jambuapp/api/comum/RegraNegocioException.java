package com.jambuapp.api.comum;

import org.springframework.http.HttpStatus;

public class RegraNegocioException extends RuntimeException {
    private final HttpStatus status;
    private final String codigo;
    private final Long retryAfterSegundos;

    public RegraNegocioException(HttpStatus status, String codigo, String mensagem) {
        this(status, codigo, mensagem, null);
    }
    private RegraNegocioException(HttpStatus status, String codigo, String mensagem, Long retryAfterSegundos) {
        super(mensagem);
        this.status = status;
        this.codigo = codigo;
        this.retryAfterSegundos = retryAfterSegundos;
    }
    public HttpStatus getStatus() { return status; }
    public String getCodigo() { return codigo; }
    /** Segundos para o cabeçalho Retry-After das respostas 429; nulo nos demais erros. */
    public Long getRetryAfterSegundos() { return retryAfterSegundos; }
    public static RegraNegocioException limite(String codigo, String mensagem, long retryAfterSegundos) {
        return new RegraNegocioException(HttpStatus.TOO_MANY_REQUESTS, codigo, mensagem, Math.max(1, retryAfterSegundos));
    }
    public static RegraNegocioException invalido(String mensagem) {
        return new RegraNegocioException(HttpStatus.BAD_REQUEST, "dados_invalidos", mensagem);
    }
    public static RegraNegocioException inexistente() {
        return new RegraNegocioException(HttpStatus.NOT_FOUND, "recurso_inexistente", "Recurso não encontrado.");
    }
    public static RegraNegocioException conflito(String mensagem) {
        return new RegraNegocioException(HttpStatus.CONFLICT, "conflito", mensagem);
    }
    public static RegraNegocioException proibido() {
        return new RegraNegocioException(HttpStatus.FORBIDDEN, "acesso_negado", "Operação não permitida.");
    }
}
