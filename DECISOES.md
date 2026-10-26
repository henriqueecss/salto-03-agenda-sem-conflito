# Decisões de projeto

Cada decisão traz o que foi escolhido, as alternativas descartadas e o motivo. A decisão 5 não estava
no plano: apareceu quando o teste de 50 threads falhou.

## 1. Como impedir a sobreposição de reservas

**Escolha: constraint de exclusão do PostgreSQL.**

```sql
CONSTRAINT booking_no_overlap EXCLUDE USING gist (
    professional_id WITH =,
    tstzrange(starts_at, ends_at, '[)') WITH &&
) WHERE (status = 'ACTIVE')
```

- `'[)'` (início fechado, fim aberto) faz uma reserva que começa quando outra termina ser permitida.
- O `WHERE (status = 'ACTIVE')` faz a reserva cancelada liberar o horário.
- `btree_gist` é necessário para usar `=` sobre `text` dentro de um índice GiST.

O que acontece quando duas transações T1 e T2 pedem o mesmo horário (nível READ COMMITTED, o padrão):

| alternativa | passo a passo | problema |
|---|---|---|
| **Constraint de exclusão** (escolhida) | T1 e T2 inserem; cada INSERT consulta o índice. T2 enxerga a linha ainda não confirmada de T1 e espera. T1 confirma, T2 recebe erro `23P01` e vira `CONFLITO`. Se T1 desse rollback, T2 seguiria. | Específica do PostgreSQL. Tem uma armadilha de deadlock (decisão 5). |
| **`SELECT ... FOR UPDATE` no profissional** | T1 trava a linha do profissional; T2 bloqueia nesse SELECT. T1 verifica sobreposição, insere e confirma. T2 acorda, a verificação dela usa um snapshot novo, vê a reserva de T1 e vira `CONFLITO`. | Só protege quem lembrar de pegar o lock: um script de manutenção ou outro serviço escrevendo na tabela burla a regra. Serializa todas as reservas do profissional, até em dias diferentes. |
| **Lock otimista (versão)** | `@Version` na reserva não ajuda, porque é um INSERT de linha nova. Precisa de uma coluna de versão no profissional: T1 e T2 leem v1, verificam e inserem; ambos fazem `UPDATE ... SET version = v+1 WHERE version = v`. Um afeta 1 linha, o outro 0, e esse recebe exceção e tenta de novo. | Exige retry, e sob disputa alta (50 pedidos no mesmo horário) quase todo mundo repete. Também não protege escrita fora da aplicação. |
| **`SERIALIZABLE`** | Ambos leem o intervalo (lock de predicado) e inserem. T1 confirma; T2 falha no commit com `40001` (ciclo de dependência) e precisa repetir. | Pode abortar reservas sem relação com a disputa (falsos positivos) e exige retry em todo caminho de código que usa o nível. |

Por que a constraint: a regra "nunca dois atendimentos sobrepostos" é uma propriedade dos dados, não de um
caminho de código. Colocada no banco, vale para qualquer instância da API, para scripts e para SQL manual.
Nas outras três a garantia depende de a aplicação lembrar de fazer a coisa certa.

## 2. Como modelar a disponibilidade

**Escolha: calcular os horários livres na hora.** Para cada início candidato (de 10 em 10 minutos dentro do
expediente), o `AvailabilityService` aplica as mesmas regras da reserva (`BookingRules`) e descarta quem
sobrepõe uma reserva ativa.

- **Tabela de slots pré-gerados (descartada):** a leitura é simples, mas serviços de duração variável (10 a 90
  minutos) ocupam vários slots, o que obriga a travar vários registros em ordem fixa para não dar deadlock.
  Também exige regenerar os slots quando muda expediente, feriado ou ausência: duas fontes de verdade que
  podem divergir.
- Reaproveitar `BookingRules` garante que todo horário listado é aceito pela reserva (há um teste para isso).
- A lista é informativa: entre listar e reservar outra pessoa pode pegar o horário, e quem decide é o banco.
  O custo do cálculo (poucas dezenas de candidatos por profissional) é irrelevante nesta escala.

## 3. Como representar o tempo

**Escolha: `timestamptz` no banco, `Instant` no domínio e um `Clock` injetável.**

- Um `Instant` identifica um momento sem ambiguidade; é o que a constraint compara.
- As regras de relógio de parede (expediente 09:00, almoço, dia da semana, feriado) são convertidas com
  `ZoneId America/Sao_Paulo`. O fuso fica na configuração (`agenda.zone`), não espalhado pelo código.
- Na API, a entrada é horário local sem offset (`2026-11-03T09:00`, o mesmo formato do CSV, mais difícil de
  errar para quem chama, como o bot do Case 11) e a saída traz o offset (`-03:00`).
- `OffsetDateTime` (descartado): guarda o offset, mas não a regra do fuso; é ruído sem ganho aqui.
- `LocalDateTime` (descartado para guardar reservas): sem fuso, fica ambíguo em mudança de horário de verão
  e perde informação.
- O `Clock` é um bean; nenhum código chama `Instant.now()`. Nos testes, um `Clock` fixo em
  `2026-10-31T08:30-03:00` (lido de `data/barbearia.json`) torna os testes de antecedência independentes do dia.

## 4. Desenho da API

- Recursos: `/professionals`, `/services`, `/availability`, `/bookings`.
- `GET /availability?serviceId=S1&date=2026-11-05[&professionalId=PRO2]` devolve uma lista plana de
  `{professionalId, startsAt, endsAt}`. Sem `professionalId`, traz todos que fazem o serviço. É a chamada que
  um bot faz em um passo, sem juntar respostas de vários endpoints.
- `POST /bookings` cria (201 + `Location`). **Cancelar é `POST /bookings/{id}/cancel`, não `DELETE`**: a
  reserva continua existindo com status `CANCELLED`, e cancelar duas vezes é erro `409 JA_CANCELADA`, o que
  contraria a idempotência esperada de um `DELETE`.
- Onde cada validação mora:
  - **Controller:** formato (campo faltando, data ilegível) -> 400.
  - **`BookingRules`** (domínio puro, sem banco): as cinco primeiras regras, na ordem de prioridade dos erros.
  - **Banco:** `CONFLITO`. Não existe "SELECT antes de INSERT" como garantia; o INSERT é tentado e o erro
    `23P01` é traduzido.
- O cancelamento é um `UPDATE ... WHERE status = 'ACTIVE'` único: dois cancelamentos simultâneos nunca passam
  os dois, sem precisar de lock.
- Erros seguem Problem Details (RFC 9457), `application/problem+json`, com o campo `codigo`.

## 5. Lock consultivo antes do INSERT (descoberto pelo teste de concorrência)

Na primeira versão, só com a constraint, o teste de 50 threads falhou: nenhuma reserva duplicada, mas
requisições terminando em erro 500 e um teste que levava mais de 80 segundos.

Causa: a constraint é verificada **depois** que a linha já entrou no índice. Com dois INSERTs
sobrepostos ao mesmo tempo, cada um enxerga a linha ainda não confirmada do outro e espera por ele. Isso é um
ciclo: o PostgreSQL detecta o deadlock depois de `deadlock_timeout` (1 s) e aborta uma das transações com
`40P01`, que a aplicação devolvia como erro 500. Com 50 disputando, isso se repete em série.

Correção: dentro da mesma transação do INSERT, `pg_advisory_xact_lock(hashtext(professional_id))`.
Os pedidos do mesmo profissional entram em fila por alguns milissegundos; o segundo da fila já encontra a
reserva confirmada e recebe `23P01` na hora. A suíte de concorrência caiu de 308 s para ~17 s, sem erros.

- A **constraint continua sendo a garantia de integridade**: se alguém inserir sem o lock, no pior caso toma
  um deadlock, nunca grava uma sobreposição. O lock só dá previsibilidade (sem 500, sem espera de 1 s).
- O lock é por profissional (hash do id): reservas de profissionais diferentes não esperam umas pelas outras.
- Alternativa descartada: tratar `40P01` como conflito ou repetir o INSERT. Funciona, mas cada colisão custa um
  `deadlock_timeout` inteiro de latência.
