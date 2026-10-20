package com.jambuapp.api.comum;

import org.springframework.http.HttpStatus;

public class RegraNegocioException extends RuntimeException {
    private final HttpStatus status;
    private final String codigo;

    public RegraNegocioException(HttpStatus status, String codigo, String mensagem) {
        super(mensagem);
        this.status = status;
        this.codigo = codigo;
    }
    public HttpStatus getStatus() { return status; }
    public String getCodigo() { return codigo; }
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
