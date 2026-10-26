# Agenda sem conflito de horário

[![CI](https://github.com/henriqueecss/salto-03-agenda-sem-conflito/actions/workflows/ci.yml/badge.svg)](https://github.com/henriqueecss/salto-03-agenda-sem-conflito/actions/workflows/ci.yml)

Projeto 3 de 15 do Salto. API REST que impede, no banco de dados, que dois pacientes fiquem com o mesmo
horário do mesmo profissional, mesmo com muitos pedidos simultâneos.

## O problema

A Clínica Jacarandá (fictícia, em Lavras) tem três profissionais e agenda tudo por WhatsApp. Quando dois
pacientes pedem o mesmo horário quase ao mesmo tempo, os dois recebem "confirmado" e alguém fica sem atendimento.
O sistema precisa garantir que isso nunca aconteça.

## A solução

A garantia está no PostgreSQL, não no código da aplicação: uma constraint de exclusão rejeita qualquer
reserva ativa que sobreponha outra do mesmo profissional.

```sql
EXCLUDE USING gist (
    professional_id WITH =,
    tstzrange(starts_at, ends_at, '[)') WITH &&
) WHERE (status = 'ACTIVE')
```

A API valida as demais regras (expediente, almoço, feriados, antecedência) antes e traduz o erro do banco em
`409 CONFLITO`. Por que essa escolha e o que foi descartado: [DECISOES.md](DECISOES.md).

## Resultados

- **Teste de concorrência:** 50 threads pedem o mesmo horário ao mesmo tempo (Dra. Helena, consulta,
  05/11 às 15:00), repetido 20 vezes em cada execução. Em todas as repetições: exatamente 1 reserva criada
  (201) e 49 respostas `409 CONFLITO`. Em quatro execuções seguidas da suíte (80 rodadas de 50 pedidos) não
  houve nenhuma exceção.
- **Contraprova:** um teste reproduz o clássico "verifica se está livre e depois insere" sem constraint, com
  uma barreira que força o pior intercalamento: as 50 threads inserem, 50 reservas no mesmo horário. O mesmo
  intercalamento contra a tabela real resulta em 1 reserva. Ou seja, o teste detecta a corrida, e não passa
  por sorte.
- **Aceitação:** os 26 passos de `data/cenarios.csv` rodam em sequência pela API real, contra PostgreSQL
  de verdade (Testcontainers), e passam.
- **Total:** 100 testes (unitários, banco, API e concorrência), rodando no GitHub Actions.

## Regras de negócio

- Fuso `America/Sao_Paulo`; instantes guardados como `timestamptz`.
- Reserva ocupa `[início, início + duração)`: começar exatamente quando outra termina é permitido.
- O profissional precisa oferecer o serviço e trabalhar no dia (dias da semana, feriados, ausências).
- O atendimento inteiro cabe no expediente e não encosta no almoço.
- Início em múltiplos de 10 minutos; antecedência mínima de 1 hora e máxima de 30 dias.
- Cancelar libera o horário; cancelar duas vezes é erro.

Quando mais de uma regra falha, vale o primeiro erro desta ordem:

| código | quando | HTTP |
|---|---|---|
| `HORARIO_INVALIDO` | início fora dos múltiplos de 10 minutos | 422 |
| `SERVICO_NAO_OFERECIDO` | o profissional não faz o serviço | 422 |
| `ANTECEDENCIA_INVALIDA` | no passado, menos de 1 hora ou mais de 30 dias | 422 |
| `DIA_FECHADO` | feriado, folga semanal ou ausência | 422 |
| `FORA_DO_EXPEDIENTE` | começa antes, termina depois ou invade o almoço | 422 |
| `CONFLITO` | sobrepõe outra reserva ativa do mesmo profissional | 409 |
| `JA_CANCELADA` | cancelar uma reserva já cancelada | 409 |

Além desses: `REQUISICAO_INVALIDA` (400) e `RECURSO_NAO_ENCONTRADO` (404). Os erros saem em
Problem Details (RFC 9457) com o código no campo `codigo`.

## API

| método e caminho | o que faz |
|---|---|
| `GET /professionals` | profissionais, expediente, dias e serviços |
| `GET /services` | serviços, duração e preço |
| `GET /availability?serviceId=S1&date=2026-11-05[&professionalId=PRO2]` | horários livres (lista plana) |
| `POST /bookings` | cria reserva |
| `GET /bookings/{id}` | consulta reserva |
| `POST /bookings/{id}/cancel` | cancela reserva |

Com a API no ar, a documentação interativa (Swagger UI) fica em http://localhost:8080/swagger-ui/index.html e o
contrato OpenAPI em `/v3/api-docs`.

Exemplo (o horário de entrada é local, sem offset; as respostas trazem `-03:00`):

```bash
curl -X POST localhost:8080/bookings -H 'Content-Type: application/json' \
  -d '{"professionalId":"PRO1","serviceId":"S1","startsAt":"2026-11-03T09:00","patientName":"Maria"}'
# 201 Created

curl -X POST localhost:8080/bookings -H 'Content-Type: application/json' \
  -d '{"professionalId":"PRO1","serviceId":"S2","startsAt":"2026-11-03T09:20","patientName":"João"}'
# 409 {"codigo":"CONFLITO","title":"Horário indisponível", ...}
```

## Arquitetura

Pacotes por funcionalidade, com a regra de negócio isolada do banco.

```
src/main/java/br/com/jacaranda/agenda/
  config/        relógio (Clock) e carga do catálogo (seed/clinica.json)
  catalog/       profissionais e serviços
  booking/       BookingRules (regras puras), BookingService, BookingRepository (JDBC), controller
  availability/  cálculo de horários livres
  error/         códigos de erro e Problem Details
src/main/resources/db/migration/V1__schema.sql   schema e constraint de exclusão (Flyway)
```

Stack: Java 25, Spring Boot 4.1, Spring JDBC (`JdbcClient`), PostgreSQL 17, Flyway, springdoc-openapi, JUnit 5,
AssertJ e Testcontainers.

## Modelo de dados

- `professional`, `professional_weekday`, `service`, `professional_service`: catálogo e escala.
- `holiday`, `absence`: dias fechados.
- `booking`: `starts_at` e `ends_at` (`timestamptz`), `status` (`ACTIVE` ou `CANCELLED`) e a constraint
  `booking_no_overlap`.

## Como rodar

Requisitos: JDK 21 ou 25 e Docker em execução. O Maven vem junto no projeto (`mvnw`), não precisa instalar.
O `JAVA_HOME` precisa apontar para o JDK 21 ou 25, senão o build usa o Java que estiver no PATH.

Windows (PowerShell):

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot"   # ajuste para o seu JDK
$env:Path = "$env:JAVA_HOMEin;$env:Path"

.\mvnw.cmd verify                  # roda todos os testes (sobe o PostgreSQL sozinho via Testcontainers)

docker compose up -d --wait        # PostgreSQL local para rodar a API
.\mvnw.cmd spring-boot:run         # API em http://localhost:8080 (Swagger em /swagger-ui/index.html)
docker compose down -v             # ao terminar
```

Linux e macOS: `export JAVA_HOME=/caminho/do/jdk`, `./mvnw verify`, `make up` e `make run` (ou os mesmos comandos
`docker compose` e `./mvnw` acima).

A antecedência é calculada com o relógio real: use datas entre 1 hora e 30 dias à frente de hoje, em dias úteis
do profissional (os exemplos acima usam novembro de 2026).

## Próximos passos

Bloqueio temporário do horário enquanto o paciente confirma e lembrete por
e-mail.
