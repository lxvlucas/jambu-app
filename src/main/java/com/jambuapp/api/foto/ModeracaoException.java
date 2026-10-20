package com.jambuapp.api.foto;

import com.jambuapp.api.comum.RegraNegocioException;
import org.springframework.http.HttpStatus;

/** Nunca expõe credenciais, URLs privadas ou corpo bruto retornado pelo provedor externo. */
public class ModeracaoException extends RegraNegocioException {
    public ModeracaoException(HttpStatus status, String codigo, String mensagem) {
        super(status, codigo, mensagem);
    }

    public static ModeracaoException naoConfigurada() {
        return new ModeracaoException(HttpStatus.SERVICE_UNAVAILABLE, "moderacao_nao_configurada",
                "Provedor de moderação automática não configurado.");
    }

    public static ModeracaoException indisponivel() {
        return new ModeracaoException(HttpStatus.SERVICE_UNAVAILABLE, "moderacao_indisponivel",
                "Provedor de moderação temporariamente indisponível.");
    }

    public static ModeracaoException timeout() {
        return new ModeracaoException(HttpStatus.GATEWAY_TIMEOUT, "moderacao_timeout",
                "O provedor de moderação não respondeu dentro do tempo limite.");
    }

    public static ModeracaoException respostaInvalida() {
        return new ModeracaoException(HttpStatus.BAD_GATEWAY, "moderacao_resposta_invalida",
                "O provedor de moderação retornou uma resposta inválida.");
    }
}
