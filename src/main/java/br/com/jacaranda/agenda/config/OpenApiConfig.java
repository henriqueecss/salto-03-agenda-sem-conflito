package br.com.jacaranda.agenda.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("Agenda sem conflito - Clínica Jacarandá")
                .description("""
                        Reservas que nunca se sobrepõem para o mesmo profissional; a garantia é uma constraint de \
                        exclusão do PostgreSQL. Erros seguem Problem Details (RFC 9457) com o código no campo `codigo`. \
                        Horários de entrada são locais (America/Sao_Paulo), sem offset.""")
                .version("1.0"));
    }
}
