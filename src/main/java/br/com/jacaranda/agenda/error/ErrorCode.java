package br.com.jacaranda.agenda.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    REQUISICAO_INVALIDA(HttpStatus.BAD_REQUEST, "Requisição inválida"),
    HORARIO_INVALIDO(HttpStatus.UNPROCESSABLE_CONTENT, "Horário inválido"),
    SERVICO_NAO_OFERECIDO(HttpStatus.UNPROCESSABLE_CONTENT, "Serviço não oferecido pelo profissional"),
    ANTECEDENCIA_INVALIDA(HttpStatus.UNPROCESSABLE_CONTENT, "Antecedência inválida"),
    DIA_FECHADO(HttpStatus.UNPROCESSABLE_CONTENT, "Dia fechado"),
    FORA_DO_EXPEDIENTE(HttpStatus.UNPROCESSABLE_CONTENT, "Fora do expediente"),
    CONFLITO(HttpStatus.CONFLICT, "Horário indisponível"),
    JA_CANCELADA(HttpStatus.CONFLICT, "Reserva já cancelada"),
    RECURSO_NAO_ENCONTRADO(HttpStatus.NOT_FOUND, "Recurso não encontrado");

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
