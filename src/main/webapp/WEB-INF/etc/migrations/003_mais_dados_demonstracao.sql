-- =============================================================================
-- 003 - Mais dados FICTICIOS de demonstracao (nomes e CPFs gerados; nenhum dado real)
--
-- Gerado por script: 30 pacientes, 6 especialidades, 8 profissionais (4 com login),
-- disponibilidade semanal e cerca de tres semanas de consultas RELATIVAS AO DIA EM QUE O
-- BANCO E CRIADO: passadas (realizadas com triagem e prontuario finalizado, ausencias e
-- cancelamentos), as de hoje em cada etapa da fila e as futuras agendadas.
--
-- Logins novos (mesmas senhas de demonstracao dos perfis existentes):
--   carla.cardio@hospital.local / medico123      (Cardiologia)
--   rui.neuro@hospital.local    / medico123      (Neurologia, Clinica Geral)
--   sofia.enf@hospital.local    / enfermeiro123
--   tiago.recepcao@hospital.local / recepcao123
-- Para recomecar com datas atuais, recrie o banco (o script e as migracoes rodam de novo).
-- =============================================================================

INSERT IGNORE INTO `especialidades` (`id_especialidade`, `nome`, `descricao`) VALUES
(6, 'Neurologia', 'Sistema nervoso central e periferico'),
(7, 'Gastroenterologia', 'Aparelho digestivo'),
(8, 'Ginecologia', 'Saude da mulher'),
(9, 'Psiquiatria', 'Saude mental'),
(10, 'Oftalmologia', 'Olhos e visao'),
(11, 'Endocrinologia', 'Hormonios e metabolismo');

INSERT IGNORE INTO `usuarios` (`id_usuario`, `nome`, `email`, `senha_hash`, `status`, `primeiro_acesso`) VALUES
(50, 'Carla Coracao (ficticia)', 'carla.cardio@hospital.local', '$2y$12$ubXYBMqotNZbf9d040k0DeUnmg/rBunPSKKihau9SwIL.bpL.ETka', 1, 0),
(51, 'Rui Neves (ficticio)', 'rui.neuro@hospital.local', '$2y$12$ubXYBMqotNZbf9d040k0DeUnmg/rBunPSKKihau9SwIL.bpL.ETka', 1, 0),
(52, 'Sofia Ribeiro (ficticia)', 'sofia.enf@hospital.local', '$2y$12$s7uNetcaOe1oj0PYncJfyuX5CM/30DJ59Vyz/uqVjMGcaBX9bWyJu', 1, 0),
(53, 'Tiago Recepcao (ficticio)', 'tiago.recepcao@hospital.local', '$2y$12$dU6opFCSw5plfiM86XTfouWFmvHuI5aF/0rWLckEi5nH0NPkoQzpq', 1, 0);

INSERT IGNORE INTO `usuarios_grupos` (`id_usuario`, `id_grupo`) VALUES (50, 8), (51, 8), (52, 7), (53, 6);

INSERT IGNORE INTO `profissionais` (`id_profissional`, `id_usuario`, `tipo`, `nome`, `cargo`, `email`, `telefone`) VALUES
(101, '50', 'medico', 'Carla Coracao (ficticia)', NULL, 'carla.cardio@hospital.local', '(11) 3000-0101'),
(102, '51', 'medico', 'Rui Neves (ficticio)', NULL, 'rui.neuro@hospital.local', '(11) 3000-0102'),
(103, NULL, 'medico', 'Beatriz Gastao (ficticia)', NULL, NULL, '(11) 3000-0103'),
(104, NULL, 'medico', 'Helena Gineco (ficticia)', NULL, NULL, '(11) 3000-0104'),
(105, NULL, 'medico', 'Otavio Visao (ficticio)', NULL, NULL, '(11) 3000-0105'),
(106, NULL, 'medico', 'Paula Mente (ficticia)', NULL, NULL, '(11) 3000-0106'),
(107, '52', 'enfermeiro', 'Sofia Ribeiro (ficticia)', NULL, 'sofia.enf@hospital.local', '(11) 3000-0107'),
(108, '53', 'funcionario', 'Tiago Recepcao (ficticio)', 'Recepcionista', 'tiago.recepcao@hospital.local', '(11) 3000-0108');

INSERT IGNORE INTO `medicos` (`id_profissional`, `crm`, `crm_uf`, `duracao_consulta_min`) VALUES
(101, '100101', 'SP', 30), (102, '100102', 'SP', 30), (103, '100103', 'SP', 30),
(104, '100104', 'SP', 30), (105, '100105', 'SP', 30), (106, '100106', 'SP', 30);
INSERT IGNORE INTO `enfermeiros` (`id_profissional`, `coren`, `coren_uf`) VALUES (107, '100107', 'SP');
INSERT IGNORE INTO `medico_especialidades` (`id_profissional`, `id_especialidade`) VALUES
(101, 2), (102, 6), (102, 1), (103, 7), (104, 8), (105, 10), (106, 9);

INSERT IGNORE INTO `disponibilidades_medico` (`id_disponibilidade`, `id_profissional`, `dia_semana`, `hora_inicio`, `hora_fim`) VALUES
(101, 101, 1, '08:00:00', '12:00:00'),
(102, 101, 1, '14:00:00', '18:00:00'),
(103, 101, 2, '08:00:00', '12:00:00'),
(104, 101, 2, '14:00:00', '18:00:00'),
(105, 101, 3, '08:00:00', '12:00:00'),
(106, 101, 3, '14:00:00', '18:00:00'),
(107, 101, 4, '08:00:00', '12:00:00'),
(108, 101, 4, '14:00:00', '18:00:00'),
(109, 101, 5, '08:00:00', '12:00:00'),
(110, 101, 5, '14:00:00', '18:00:00'),
(111, 102, 1, '08:00:00', '12:00:00'),
(112, 102, 1, '14:00:00', '18:00:00'),
(113, 102, 2, '08:00:00', '12:00:00'),
(114, 102, 2, '14:00:00', '18:00:00'),
(115, 102, 3, '08:00:00', '12:00:00'),
(116, 102, 3, '14:00:00', '18:00:00'),
(117, 102, 4, '08:00:00', '12:00:00'),
(118, 102, 4, '14:00:00', '18:00:00'),
(119, 102, 5, '08:00:00', '12:00:00'),
(120, 102, 5, '14:00:00', '18:00:00'),
(121, 103, 1, '08:00:00', '12:00:00'),
(122, 103, 1, '14:00:00', '18:00:00'),
(123, 103, 2, '08:00:00', '12:00:00'),
(124, 103, 2, '14:00:00', '18:00:00'),
(125, 103, 3, '08:00:00', '12:00:00'),
(126, 103, 3, '14:00:00', '18:00:00'),
(127, 103, 4, '08:00:00', '12:00:00'),
(128, 103, 4, '14:00:00', '18:00:00'),
(129, 103, 5, '08:00:00', '12:00:00'),
(130, 103, 5, '14:00:00', '18:00:00'),
(131, 104, 1, '08:00:00', '12:00:00'),
(132, 104, 1, '14:00:00', '18:00:00'),
(133, 104, 2, '08:00:00', '12:00:00'),
(134, 104, 2, '14:00:00', '18:00:00'),
(135, 104, 3, '08:00:00', '12:00:00'),
(136, 104, 3, '14:00:00', '18:00:00'),
(137, 104, 4, '08:00:00', '12:00:00'),
(138, 104, 4, '14:00:00', '18:00:00'),
(139, 104, 5, '08:00:00', '12:00:00'),
(140, 104, 5, '14:00:00', '18:00:00'),
(141, 105, 1, '08:00:00', '12:00:00'),
(142, 105, 1, '14:00:00', '18:00:00'),
(143, 105, 2, '08:00:00', '12:00:00'),
(144, 105, 2, '14:00:00', '18:00:00'),
(145, 105, 3, '08:00:00', '12:00:00'),
(146, 105, 3, '14:00:00', '18:00:00'),
(147, 105, 4, '08:00:00', '12:00:00'),
(148, 105, 4, '14:00:00', '18:00:00'),
(149, 105, 5, '08:00:00', '12:00:00'),
(150, 105, 5, '14:00:00', '18:00:00'),
(151, 106, 1, '08:00:00', '12:00:00'),
(152, 106, 1, '14:00:00', '18:00:00'),
(153, 106, 2, '08:00:00', '12:00:00'),
(154, 106, 2, '14:00:00', '18:00:00'),
(155, 106, 3, '08:00:00', '12:00:00'),
(156, 106, 3, '14:00:00', '18:00:00'),
(157, 106, 4, '08:00:00', '12:00:00'),
(158, 106, 4, '14:00:00', '18:00:00'),
(159, 106, 5, '08:00:00', '12:00:00'),
(160, 106, 5, '14:00:00', '18:00:00');

INSERT IGNORE INTO `pacientes` (`id_paciente`, `nome`, `cpf`, `data_nascimento`, `sexo`, `telefone`, `email`, `cidade`, `uf`) VALUES
(11, 'Caio Lima Nogueira Ficticio', '86987973008', '1943-04-20', 'masculino', '(11) 91011-0407', 'caio.11@exemplo.test', 'Sao Bernardo do Campo', 'SP'),
(12, 'Juliana Machado Queiroz Ficticia', '53645568166', '1959-08-01', 'feminino', '(11) 91012-0444', NULL, 'Osasco', 'SP'),
(13, 'Bruno Nogueira Toledo Ficticio', '94055761600', '1997-05-15', 'masculino', '(11) 91013-0481', 'bruno.13@exemplo.test', 'Sao Paulo', 'SP'),
(14, 'Ingrid Oliveira Cardoso Ficticia', '76846978895', '2013-09-26', 'feminino', '(11) 91014-0518', 'ingrid.14@exemplo.test', 'Sao Bernardo do Campo', 'SP'),
(15, 'Andre Pereira Farias Ficticio', '19672731701', '1966-01-23', 'masculino', '(11) 91015-0555', NULL, 'Guarulhos', 'SP'),
(16, 'Helena Queiroz Iglesias Ficticia', '88766344018', '2007-09-13', 'feminino', '(11) 91016-0592', 'helena.16@exemplo.test', 'Sao Paulo', 'SP'),
(17, 'Thiago Rocha Lima Ficticio', '34778768060', '2021-09-16', 'masculino', '(11) 91017-0629', 'thiago.17@exemplo.test', 'Campinas', 'SP'),
(18, 'Giovana Siqueira Oliveira Ficticia', '47267147593', '2013-12-23', 'feminino', '(11) 91018-0666', NULL, 'Guarulhos', 'SP'),
(19, 'Rafael Toledo Rocha Ficticio', '07981933102', '1972-08-01', 'masculino', '(11) 91019-0703', 'rafael.19@exemplo.test', 'Sao Paulo', 'SP'),
(20, 'Fernanda Almeida Almeida Ficticia', '20599728337', '2008-11-25', 'feminino', '(11) 91020-0740', 'fernanda.20@exemplo.test', 'Campinas', 'SP'),
(21, 'Otavio Barbosa Dias Ficticio', '29486628343', '1998-02-14', 'masculino', '(11) 91021-0777', NULL, 'Sao Paulo', 'SP'),
(22, 'Eduarda Cardoso Gomes Ficticia', '50087547503', '1952-01-27', 'feminino', '(11) 91022-0814', 'eduarda.22@exemplo.test', 'Campinas', 'SP'),
(23, 'Nicolas Dias Junqueira Ficticio', '36957931803', '1981-03-19', 'masculino', '(11) 91023-0851', 'nicolas.23@exemplo.test', 'Guarulhos', 'SP'),
(24, 'Daniela Esteves Machado Ficticia', '07370283860', '1967-01-21', 'feminino', '(11) 91024-0888', NULL, 'Guarulhos', 'SP'),
(25, 'Mateus Farias Pereira Ficticio', '61642524743', '2014-05-14', 'masculino', '(11) 91025-0925', 'mateus.25@exemplo.test', 'Guarulhos', 'SP'),
(26, 'Camila Gomes Siqueira Ficticia', '87718841308', '2009-08-06', 'feminino', '(11) 91026-0962', 'camila.26@exemplo.test', 'Campinas', 'SP'),
(27, 'Lucas Honorato Barbosa Ficticio', '05258983102', '1997-07-18', 'masculino', '(11) 91027-0999', NULL, 'Sao Paulo', 'SP'),
(28, 'Beatriz Iglesias Esteves Ficticia', '48214162122', '1988-10-10', 'feminino', '(11) 91028-1036', 'beatriz.28@exemplo.test', 'Sao Bernardo do Campo', 'SP'),
(29, 'Igor Junqueira Honorato Ficticio', '85491452467', '1967-11-21', 'masculino', '(11) 91029-1073', 'igor.29@exemplo.test', 'Sao Paulo', 'SP'),
(30, 'Amanda Kato Kato Ficticia', '13041189593', '1996-07-01', 'feminino', '(11) 91030-1110', NULL, 'Sao Paulo', 'SP'),
(31, 'Heitor Lima Nogueira Ficticio', '39761584615', '1950-07-11', 'masculino', '(11) 91031-1147', 'heitor.31@exemplo.test', 'Sao Bernardo do Campo', 'SP'),
(32, 'Patricia Machado Queiroz Ficticia', '41465908501', '2012-01-10', 'feminino', '(11) 91032-1184', 'patricia.32@exemplo.test', 'Campinas', 'SP'),
(33, 'Gustavo Nogueira Toledo Ficticio', '02859406948', '1983-10-04', 'masculino', '(11) 91033-1221', NULL, 'Sao Paulo', 'SP'),
(34, 'Olivia Oliveira Cardoso Ficticia', '32280886421', '2018-04-25', 'feminino', '(11) 91034-1258', 'olivia.34@exemplo.test', 'Santos', 'SP'),
(35, 'Felipe Pereira Farias Ficticio', '64529128016', '1965-05-18', 'masculino', '(11) 91035-1295', 'felipe.35@exemplo.test', 'Osasco', 'SP'),
(36, 'Natalia Queiroz Iglesias Ficticia', '50037038214', '2009-06-21', 'feminino', '(11) 91036-1332', NULL, 'Guarulhos', 'SP'),
(37, 'Enzo Rocha Lima Ficticio', '53557787982', '2009-04-18', 'masculino', '(11) 91037-1369', 'enzo.37@exemplo.test', 'Sao Paulo', 'SP'),
(38, 'Mariana Siqueira Oliveira Ficticia', '58602145888', '2022-03-16', 'feminino', '(11) 91038-1406', 'mariana.38@exemplo.test', 'Sao Paulo', 'SP'),
(39, 'Davi Toledo Rocha Ficticio', '49066882840', '1963-09-07', 'masculino', '(11) 91039-1443', NULL, 'Sao Bernardo do Campo', 'SP'),
(40, 'Larissa Almeida Almeida Ficticia', '09324484532', '1998-09-09', 'feminino', '(11) 91040-1480', 'larissa.40@exemplo.test', 'Sao Paulo', 'SP');

INSERT IGNORE INTO `paciente_alergias` (`id_alergia`, `id_paciente`, `substancia`, `reacao`, `gravidade`) VALUES
(101, 12, 'Dipirona', 'Urticaria', 'moderada'),
(102, 15, 'Penicilina', 'Edema de glote', 'grave'),
(103, 18, 'Frutos do mar', 'Coceira e vermelhidao', 'leve'),
(104, 21, 'Ibuprofeno', 'Broncoespasmo', 'grave'),
(105, 27, 'Latex', 'Dermatite de contato', 'leve'),
(106, 33, 'Sulfa', 'Erupcao cutanea', 'moderada');

-- Consultas: id = 10000 + (dia + 20) * 100 + slot (deterministico, para ligar triagens e prontuarios)
INSERT IGNORE INTO `consultas` (`id_consulta`, `id_paciente`, `id_medico`, `id_especialidade`, `data_hora`, `duracao_min`,
    `status`, `chegada_em`, `inicio_atendimento_em`, `fim_atendimento_em`, `cancelada_em`, `motivo_cancelamento`, `criado_por`)
SELECT x.id, x.pac, x.med, x.esp, x.dh, x.dur, x.st,
       IF(x.st IN ('realizada', 'aguardando_triagem', 'aguardando_atendimento'), DATE_SUB(x.dh, INTERVAL 15 MINUTE), NULL),
       IF(x.st = 'realizada', x.dh, NULL),
       IF(x.st = 'realizada', DATE_ADD(x.dh, INTERVAL 25 MINUTE), NULL),
       IF(x.st = 'cancelada', DATE_SUB(x.dh, INTERVAL 2 DAY), NULL),
       IF(x.st = 'cancelada', ELT(1 + MOD(x.id, 3), 'Pedido do paciente', 'Imprevisto de trabalho', 'Paciente com viagem marcada'), NULL),
       30
FROM (
    SELECT 10000 + (d.n + 20) * 100 + s.k AS id,
           11 + MOD((d.n + 20) * 7 + s.k * 11, 30) AS pac,
           s.med, s.esp, s.dur,
           TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL d.n DAY), s.hora) AS dh,
           CASE
               WHEN d.n < 0 THEN ELT(1 + MOD(s.k + d.n * 3 + 100, 10), 'ausente', 'cancelada', 'realizada', 'realizada', 'realizada',
                                     'realizada', 'realizada', 'realizada', 'realizada', 'realizada')
               WHEN d.n = 0 AND s.hora < '09:00:00' THEN 'realizada'
               WHEN d.n = 0 AND s.hora < '10:00:00' THEN 'aguardando_atendimento'
               WHEN d.n = 0 AND s.hora < '11:00:00' THEN 'aguardando_triagem'
               WHEN d.n > 0 AND MOD(s.k + d.n, 9) = 0 THEN 'cancelada'
               ELSE 'agendada'
           END AS st
    FROM (SELECT -12 n UNION ALL SELECT -11 n UNION ALL SELECT -10 n UNION ALL SELECT -9 n UNION ALL SELECT -8 n UNION ALL SELECT -7 n UNION ALL SELECT -6 n UNION ALL SELECT -5 n UNION ALL SELECT -4 n UNION ALL SELECT -3 n UNION ALL SELECT -2 n UNION ALL SELECT -1 n UNION ALL SELECT 0 n UNION ALL SELECT 1 n UNION ALL SELECT 2 n UNION ALL SELECT 3 n UNION ALL SELECT 4 n UNION ALL SELECT 5 n UNION ALL SELECT 6 n UNION ALL SELECT 7 n UNION ALL SELECT 8 n UNION ALL SELECT 9 n UNION ALL SELECT 10 n UNION ALL SELECT 11 n UNION ALL SELECT 12 n UNION ALL SELECT 13 n UNION ALL SELECT 14 n) d
    CROSS JOIN (SELECT 0 k, 3 med, 1 esp, '08:00:00' hora, 30 dur, 3 maxwd UNION ALL SELECT 1 k, 3 med, 1 esp, '08:30:00' hora, 30 dur, 3 maxwd UNION ALL SELECT 2 k, 3 med, 1 esp, '09:00:00' hora, 30 dur, 3 maxwd UNION ALL SELECT 3 k, 3 med, 1 esp, '09:30:00' hora, 30 dur, 3 maxwd UNION ALL SELECT 4 k, 3 med, 1 esp, '10:00:00' hora, 30 dur, 3 maxwd UNION ALL SELECT 5 k, 3 med, 1 esp, '10:30:00' hora, 30 dur, 3 maxwd UNION ALL SELECT 6 k, 4 med, 2 esp, '13:00:00' hora, 30 dur, 3 maxwd UNION ALL SELECT 7 k, 4 med, 2 esp, '14:00:00' hora, 30 dur, 3 maxwd UNION ALL SELECT 8 k, 5 med, 3 esp, '08:00:00' hora, 20 dur, 4 maxwd UNION ALL SELECT 9 k, 5 med, 3 esp, '08:40:00' hora, 20 dur, 4 maxwd UNION ALL SELECT 10 k, 101 med, 2 esp, '08:00:00' hora, 30 dur, 4 maxwd UNION ALL SELECT 11 k, 101 med, 2 esp, '09:00:00' hora, 30 dur, 4 maxwd UNION ALL SELECT 12 k, 101 med, 2 esp, '10:30:00' hora, 30 dur, 4 maxwd UNION ALL SELECT 13 k, 101 med, 2 esp, '14:00:00' hora, 30 dur, 4 maxwd UNION ALL SELECT 14 k, 101 med, 2 esp, '15:30:00' hora, 30 dur, 4 maxwd UNION ALL SELECT 15 k, 102 med, 6 esp, '08:00:00' hora, 30 dur, 4 maxwd UNION ALL SELECT 16 k, 102 med, 6 esp, '09:00:00' hora, 30 dur, 4 maxwd UNION ALL SELECT 17 k, 102 med, 6 esp, '10:30:00' hora, 30 dur, 4 maxwd UNION ALL SELECT 18 k, 102 med, 6 esp, '14:00:00' hora, 30 dur, 4 maxwd UNION ALL SELECT 19 k, 102 med, 6 esp, '15:30:00' hora, 30 dur, 4 maxwd UNION ALL SELECT 20 k, 103 med, 7 esp, '08:00:00' hora, 30 dur, 4 maxwd UNION ALL SELECT 21 k, 103 med, 7 esp, '09:00:00' hora, 30 dur, 4 maxwd UNION ALL SELECT 22 k, 103 med, 7 esp, '10:30:00' hora, 30 dur, 4 maxwd UNION ALL SELECT 23 k, 103 med, 7 esp, '14:00:00' hora, 30 dur, 4 maxwd UNION ALL SELECT 24 k, 103 med, 7 esp, '15:30:00' hora, 30 dur, 4 maxwd UNION ALL SELECT 25 k, 104 med, 8 esp, '08:00:00' hora, 30 dur, 4 maxwd UNION ALL SELECT 26 k, 104 med, 8 esp, '09:00:00' hora, 30 dur, 4 maxwd UNION ALL SELECT 27 k, 104 med, 8 esp, '10:30:00' hora, 30 dur, 4 maxwd UNION ALL SELECT 28 k, 104 med, 8 esp, '14:00:00' hora, 30 dur, 4 maxwd UNION ALL SELECT 29 k, 104 med, 8 esp, '15:30:00' hora, 30 dur, 4 maxwd UNION ALL SELECT 30 k, 105 med, 10 esp, '08:00:00' hora, 30 dur, 4 maxwd UNION ALL SELECT 31 k, 105 med, 10 esp, '09:00:00' hora, 30 dur, 4 maxwd UNION ALL SELECT 32 k, 105 med, 10 esp, '10:30:00' hora, 30 dur, 4 maxwd UNION ALL SELECT 33 k, 105 med, 10 esp, '14:00:00' hora, 30 dur, 4 maxwd UNION ALL SELECT 34 k, 105 med, 10 esp, '15:30:00' hora, 30 dur, 4 maxwd UNION ALL SELECT 35 k, 106 med, 9 esp, '08:00:00' hora, 30 dur, 4 maxwd UNION ALL SELECT 36 k, 106 med, 9 esp, '09:00:00' hora, 30 dur, 4 maxwd UNION ALL SELECT 37 k, 106 med, 9 esp, '10:30:00' hora, 30 dur, 4 maxwd UNION ALL SELECT 38 k, 106 med, 9 esp, '14:00:00' hora, 30 dur, 4 maxwd UNION ALL SELECT 39 k, 106 med, 9 esp, '15:30:00' hora, 30 dur, 4 maxwd) s
    WHERE WEEKDAY(DATE_ADD(CURDATE(), INTERVAL d.n DAY)) <= s.maxwd
      AND (d.n <> 0 OR s.hora < '17:00:00')
      AND MOD(s.k * 5 + d.n + 40, 7) <> 3
) x;

-- Triagens das consultas realizadas e das que aguardam o medico (enfermeiros 2 e 107)
INSERT INTO `triagens` (`id_consulta`, `id_enfermeiro`, `pa_sistolica`, `pa_diastolica`, `frequencia_cardiaca`, `temperatura`,
    `saturacao_o2`, `peso_kg`, `altura_cm`, `queixa_principal`, `id_classificacao`, `registrado_em`)
SELECT c.id_consulta, IF(MOD(c.id_consulta, 2) = 0, 2, 107),
       105 + MOD(c.id_consulta, 40), 65 + MOD(c.id_consulta, 25), 62 + MOD(c.id_consulta, 38),
       36.0 + MOD(c.id_consulta, 25) / 10, 93 + MOD(c.id_consulta, 7), 50 + MOD(c.id_consulta, 45), 150 + MOD(c.id_consulta, 40),
       ELT(1 + MOD(c.id_consulta, 10), 'Dor de cabeca ha tres dias', 'Febre e dor no corpo', 'Dor toracica ao esforco', 'Tontura ao levantar', 'Dor abdominal apos refeicoes', 'Tosse seca persistente', 'Retorno para avaliar exames', 'Ansiedade e insonia', 'Visao embacada para longe', 'Controle de pressao arterial'),
       2 + MOD(c.id_consulta, 4), DATE_SUB(c.data_hora, INTERVAL 8 MINUTE)
FROM `consultas` c
WHERE c.id_consulta >= 10000 AND c.status IN ('realizada', 'aguardando_atendimento')
  AND NOT EXISTS (SELECT 1 FROM `triagens` t WHERE t.id_consulta = c.id_consulta);

-- Prontuarios finalizados das consultas realizadas
INSERT INTO `prontuarios` (`id_consulta`, `id_medico`, `queixa_principal`, `historia_doenca_atual`, `hipoteses_diagnosticas`,
    `conduta`, `status`, `versao`, `criado_em`, `finalizado_em`)
SELECT c.id_consulta, c.id_medico,
       ELT(1 + MOD(c.id_consulta, 10), 'Dor de cabeca ha tres dias', 'Febre e dor no corpo', 'Dor toracica ao esforco', 'Tontura ao levantar', 'Dor abdominal apos refeicoes', 'Tosse seca persistente', 'Retorno para avaliar exames', 'Ansiedade e insonia', 'Visao embacada para longe', 'Controle de pressao arterial'),
       'Relato do paciente registrado na consulta (texto ficticio de demonstracao).',
       ELT(1 + MOD(c.id_consulta, 10), 'Cefaleia tensional', 'Sindrome gripal', 'Angina estavel a investigar', 'Hipotensao ortostatica', 'Dispepsia funcional', 'Tosse pos-viral', 'Exames dentro da normalidade', 'Transtorno de ansiedade', 'Miopia a confirmar', 'Hipertensao controlada'),
       ELT(1 + MOD(c.id_consulta, 10), 'Analgesico se dor, hidratacao e retorno se piorar.', 'Repouso, antitermico e hidratacao. Retorno em 7 dias.', 'Solicitado teste ergometrico e ECG. Retorno com resultados.', 'Orientado levantar devagar e aumentar ingestao de agua.', 'Dieta fracionada e inibidor de bomba por 30 dias.', 'Xarope sintomatico e retorno se febre.', 'Manter acompanhamento anual.', 'Encaminhado para psicoterapia; retorno em 30 dias.', 'Encaminhado para exame de refracao.', 'Manter medicacao e controle domiciliar da pressao.'),
       'finalizado', 1, c.data_hora, DATE_ADD(c.data_hora, INTERVAL 25 MINUTE)
FROM `consultas` c
WHERE c.id_consulta >= 10000 AND c.status = 'realizada'
  AND NOT EXISTS (SELECT 1 FROM `prontuarios` p WHERE p.id_consulta = c.id_consulta);

-- Receitas de parte das consultas realizadas (um item cada) e pedidos de exame
INSERT INTO `receitas` (`id_consulta`, `id_medico`, `orientacoes_gerais`, `emitida_em`)
SELECT c.id_consulta, c.id_medico, 'Tomar conforme prescrito. Em caso de reacao, suspender e procurar atendimento.',
       DATE_ADD(c.data_hora, INTERVAL 20 MINUTE)
FROM `consultas` c
WHERE c.id_consulta >= 10000 AND c.status = 'realizada' AND MOD(c.id_consulta, 3) = 0
  AND NOT EXISTS (SELECT 1 FROM `receitas` r WHERE r.id_consulta = c.id_consulta);

INSERT INTO `receita_itens` (`id_receita`, `ordem`, `medicamento`, `dosagem`, `via`, `frequencia`, `duracao`)
SELECT r.id_receita, 1,
       ELT(1 + MOD(r.id_consulta, 5), 'Paracetamol', 'Omeprazol', 'Losartana', 'Loratadina', 'Amoxicilina'),
       ELT(1 + MOD(r.id_consulta, 5), '750 mg', '20 mg', '50 mg', '10 mg', '500 mg'),
       'oral',
       ELT(1 + MOD(r.id_consulta, 5), 'A cada 6 horas se dor', '1 vez ao dia em jejum', '1 vez ao dia', '1 vez ao dia', 'A cada 8 horas'),
       ELT(1 + MOD(r.id_consulta, 5), '5 dias', '30 dias', 'Uso continuo', '7 dias', '7 dias')
FROM `receitas` r
WHERE r.id_consulta >= 10000 AND NOT EXISTS (SELECT 1 FROM `receita_itens` i WHERE i.id_receita = r.id_receita);

INSERT INTO `exames` (`id_consulta`, `id_medico`, `nome_exame`, `justificativa`, `solicitado_em`)
SELECT c.id_consulta, c.id_medico,
       ELT(1 + MOD(c.id_consulta, 4), 'Hemograma completo', 'Eletrocardiograma', 'Glicemia de jejum', 'Raio-X de torax'),
       'Investigacao da queixa principal', DATE_ADD(c.data_hora, INTERVAL 22 MINUTE)
FROM `consultas` c
WHERE c.id_consulta >= 10000 AND c.status = 'realizada' AND MOD(c.id_consulta, 4) = 1
  AND NOT EXISTS (SELECT 1 FROM `exames` e WHERE e.id_consulta = c.id_consulta);
