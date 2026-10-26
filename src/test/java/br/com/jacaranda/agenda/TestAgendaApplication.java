package br.com.jacaranda.agenda;

import org.springframework.boot.SpringApplication;

public class TestAgendaApplication {

    public static void main(String[] args) {
        SpringApplication.from(AgendaApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
