package com.jambuapp.api.comum;

import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(RegraNegocioException.class)
    ProblemDetail negocio(RegraNegocioException ex) {
        return problema(ex.getStatus(), ex.getCodigo(), ex.getMessage());
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail uploadExcedido(MaxUploadSizeExceededException ex) {
        return problema(HttpStatus.BAD_REQUEST, "dados_invalidos", "O arquivo excede o limite de 8 MiB.");
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validacao(MethodArgumentNotValidException ex) {
        var erro = problema(HttpStatus.BAD_REQUEST, "dados_invalidos", "Confira os campos informados.");
        List<CampoInvalido> campos = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new CampoInvalido(e.getField(), e.getDefaultMessage())).toList();
        erro.setProperty("campos", campos);
        return erro;
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class, MultipartException.class})
    ProblemDetail formato(Exception ex) {
        return problema(HttpStatus.BAD_REQUEST, "requisicao_invalida", "JSON, parâmetro ou cabeçalho inválido/ausente.");
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integridade(DataIntegrityViolationException ex) {
        return problema(HttpStatus.CONFLICT, "conflito", "Os dados conflitam com um registro ou uma restrição existente.");
    }
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail versao(OptimisticLockingFailureException ex) {
        return problema(HttpStatus.CONFLICT, "versao_desatualizada", "O registro mudou. Recarregue antes de editar.");
    }
    public static ProblemDetail problema(HttpStatus status, String codigo, String mensagem) {
        var erro = ProblemDetail.forStatusAndDetail(status, mensagem);
        erro.setProperty("codigo", codigo);
        return erro;
    }
    record CampoInvalido(String campo, String mensagem) {}
}
