-- =============================================================================
-- 005 - Demonstracao do portal: um atendimento passado da Ana (paciente 1, login
-- paciente@hospital.local) com prontuario, receita e um pedido de exame do medico,
-- para as telas "Receitas" e "Pedidos do seu medico" terem o que mostrar. Dados ficticios.
-- =============================================================================

INSERT IGNORE INTO `consultas` (`id_consulta`, `id_paciente`, `id_medico`, `id_especialidade`, `data_hora`, `duracao_min`, `status`,
    `chegada_em`, `inicio_atendimento_em`, `fim_atendimento_em`, `criado_por`)
VALUES (9001, 1, 3, 1, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 20 DAY), '09:00:00'), 30, 'realizada',
    TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 20 DAY), '08:45:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 20 DAY), '09:00:00'),
    TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 20 DAY), '09:25:00'), 30);

INSERT INTO `triagens` (`id_consulta`, `id_enfermeiro`, `pa_sistolica`, `pa_diastolica`, `frequencia_cardiaca`, `temperatura`, `saturacao_o2`,
    `queixa_principal`, `id_classificacao`, `registrado_em`)
SELECT 9001, 2, 124, 80, 88, 37.4, 97, 'Tosse ha uma semana e cansaco', 4, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 20 DAY), '08:52:00')
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `triagens` WHERE `id_consulta` = 9001);

INSERT INTO `prontuarios` (`id_consulta`, `id_medico`, `queixa_principal`, `historia_doenca_atual`, `hipoteses_diagnosticas`, `conduta`,
    `status`, `versao`, `criado_em`, `finalizado_em`)
SELECT 9001, 3, 'Tosse ha uma semana e cansaco', 'Tosse seca, sem febre alta (texto ficticio de demonstracao).',
    'Bronquite aguda a confirmar', 'Sintomaticos, hidratacao e raio-X de torax. Retorno com o exame.', 'finalizado', 1,
    TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 20 DAY), '09:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 20 DAY), '09:25:00')
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `prontuarios` WHERE `id_consulta` = 9001);

INSERT INTO `receitas` (`id_consulta`, `id_medico`, `orientacoes_gerais`, `emitida_em`)
SELECT 9001, 3, 'Beber bastante agua. Se tiver falta de ar ou febre acima de 38,5, procure atendimento.',
    TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 20 DAY), '09:20:00')
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `receitas` WHERE `id_consulta` = 9001);

INSERT INTO `receita_itens` (`id_receita`, `ordem`, `medicamento`, `dosagem`, `via`, `frequencia`, `duracao`, `orientacoes`)
SELECT r.`id_receita`, 1, 'Dipirona (ficticio)', '500 mg', 'oral', 'A cada 6 horas se dor ou febre', '5 dias', NULL
FROM `receitas` r WHERE r.`id_consulta` = 9001 AND NOT EXISTS (SELECT 1 FROM `receita_itens` i WHERE i.`id_receita` = r.`id_receita`);
INSERT INTO `receita_itens` (`id_receita`, `ordem`, `medicamento`, `dosagem`, `via`, `frequencia`, `duracao`, `orientacoes`)
SELECT r.`id_receita`, 2, 'Xarope expectorante (ficticio)', '10 ml', 'oral', '3 vezes ao dia', '7 dias', 'Apos as refeicoes'
FROM `receitas` r WHERE r.`id_consulta` = 9001 AND (SELECT COUNT(*) FROM `receita_itens` i WHERE i.`id_receita` = r.`id_receita`) = 1;

INSERT INTO `exames` (`id_consulta`, `id_medico`, `nome_exame`, `justificativa`, `solicitado_em`)
SELECT 9001, 3, 'Raio-X de torax', 'Tosse persistente', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 20 DAY), '09:22:00')
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `exames` WHERE `id_consulta` = 9001);
