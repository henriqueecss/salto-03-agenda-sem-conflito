package br.com.jacaranda.agenda.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    HORARIO_INVALIDO(HttpStatus.UNPROCESSABLE_CONTENT, "Horario invalido"),
    SERVICO_NAO_OFERECIDO(HttpStatus.UNPROCESSABLE_CONTENT, "Servico nao oferecido pelo profissional"),
    ANTECEDENCIA_INVALIDA(HttpStatus.UNPROCESSABLE_CONTENT, "Antecedencia invalida"),
    DIA_FECHADO(HttpStatus.UNPROCESSABLE_CONTENT, "Dia fechado"),
    FORA_DO_EXPEDIENTE(HttpStatus.UNPROCESSABLE_CONTENT, "Fora do expediente"),
    CONFLITO(HttpStatus.CONFLICT, "Horario indisponivel"),
    JA_CANCELADA(HttpStatus.CONFLICT, "Reserva ja cancelada"),
    RECURSO_NAO_ENCONTRADO(HttpStatus.NOT_FOUND, "Recurso nao encontrado");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
