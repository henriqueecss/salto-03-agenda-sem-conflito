# Sobre os dados

Tudo aqui é fictício. Os dias da semana seguem a ISO 8601: 1 é segunda e 7 é domingo.

## `barbearia.json`

| profissional | dias | expediente | almoço | observação |
|---|---|---|---|---|
| PRO1 Diego | seg a sáb | 09:00 a 19:00 | 12:00 a 13:00 | não faz pigmentação |
| PRO2 Renata | ter a sáb | 10:00 a 20:00 | 14:00 a 15:00 | única que faz pigmentação |
| PRO3 Tiago | seg a sex | 09:00 a 18:00 | 12:00 a 13:00 | ausente em 06/11 |

| serviço | duração | preço |
|---|---|---|
| S1 Corte | 30 min | R$ 45 |
| S2 Barba | 20 min | R$ 30 |
| S3 Corte e barba | 50 min | R$ 70 |
| S4 Pigmentação | 90 min | R$ 120 |
| S5 Sobrancelha | 10 min | R$ 15 |

Feriados: 02/11 (Finados), 15/11 (Proclamação da República), 20/11 (Consciência Negra) e 25/12 (Natal).

## `cenarios.csv`

26 passos em sequência sobre um banco limpo. A coluna `ref` dá um nome à reserva criada (A1, A2...) para os passos de cancelamento apontarem para ela. O "agora" dos testes é 31/10/2026 às 08:30.

O que os cenários cobrem:

- Intervalos que se tocam sem se sobrepor (um termina 09:30, outro começa 09:30)
- O mesmo horário para profissionais diferentes
- Atendimento que termina exatamente no almoço ou no fechamento, e um que passa 10 minutos
- Feriado, folga semanal e ausência pontual
- Serviço que o profissional não faz
- Antecedência no limite exato de 1 hora, abaixo dele, acima de 30 dias e no passado
- Cancelamento que libera o horário, cancelamento repetido e um encaixe exato de 10 minutos entre duas reservas

## O teste de concorrência

Não está no CSV porque depende de threads: 50 pedidos simultâneos para PRO2, S1, 05/11/2026 às 15:00. O resultado tem que ser exatamente 1 reserva criada e 49 `CONFLITO`, toda vez.
