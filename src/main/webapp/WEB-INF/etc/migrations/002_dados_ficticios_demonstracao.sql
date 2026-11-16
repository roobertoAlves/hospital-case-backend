-- =============================================================================
-- 002 - Dados FICTICIOS de demonstracao (nomes e CPFs gerados; nenhum dado real)
-- Mais dois medicos (sem login) e pacientes para as telas de agenda e busca.
-- =============================================================================

INSERT IGNORE INTO `profissionais` (`id_profissional`, `id_usuario`, `tipo`, `nome`) VALUES
(4, NULL, 'medico', 'Paulo Cardoso (ficticio)'),
(5, NULL, 'medico', 'Lucia Andrade (ficticia)');

INSERT IGNORE INTO `medicos` (`id_profissional`, `crm`, `crm_uf`, `duracao_consulta_min`) VALUES
(4, '000002', 'SP', 30),
(5, '000003', 'SP', 20);

INSERT IGNORE INTO `medico_especialidades` (`id_profissional`, `id_especialidade`) VALUES
(4, 2), (4, 1), (5, 3);

-- Disponibilidade: Marina (3) passa a atender todos os dias uteis; Paulo e Lucia tambem.
INSERT IGNORE INTO `disponibilidades_medico` (`id_disponibilidade`, `id_profissional`, `dia_semana`, `hora_inicio`, `hora_fim`) VALUES
(4, 3, 2, '08:00:00', '12:00:00'),
(5, 3, 4, '08:00:00', '12:00:00'),
(6, 4, 1, '13:00:00', '18:00:00'),
(7, 4, 2, '13:00:00', '18:00:00'),
(8, 4, 3, '13:00:00', '18:00:00'),
(9, 4, 4, '13:00:00', '18:00:00'),
(10, 4, 5, '08:00:00', '12:00:00'),
(11, 5, 1, '08:00:00', '12:00:00'),
(12, 5, 2, '08:00:00', '12:00:00'),
(13, 5, 3, '08:00:00', '12:00:00'),
(14, 5, 4, '08:00:00', '12:00:00'),
(15, 5, 5, '08:00:00', '12:00:00');

INSERT IGNORE INTO `pacientes` (`id_paciente`, `nome`, `cpf`, `data_nascimento`, `sexo`, `telefone`, `email`, `cidade`, `uf`) VALUES
(4, 'Diego Ramos Ficticio', '24681357928', '1990-05-09', 'masculino', '(11) 90000-0004', NULL, 'Sao Paulo', 'SP'),
(5, 'Elaine Moura Ficticia', '52998224725', '1968-01-30', 'feminino', '(11) 90000-0005', 'elaine.ficticia@exemplo.test', 'Osasco', 'SP'),
(6, 'Fabio Teixeira Ficticio', '15350946056', '1979-09-17', 'masculino', '(11) 90000-0006', NULL, 'Sao Paulo', 'SP'),
(7, 'Gabriela Nunes Ficticia', '86288366757', '2001-12-03', 'feminino', '(11) 90000-0007', 'gabi.ficticia@exemplo.test', 'Guarulhos', 'SP'),
(8, 'Henrique Lopes Ficticio', '71428793860', '1955-04-22', 'masculino', '(11) 90000-0008', NULL, 'Sao Paulo', 'SP'),
(9, 'Isabela Prado Ficticia', '39053344705', '2018-08-14', 'feminino', '(11) 90000-0009', 'responsavel.isabela@exemplo.test', 'Santo Andre', 'SP'),
(10, 'Joao Vitor Melo Ficticio', '11122233396', '1994-02-27', 'masculino', '(11) 90000-0010', NULL, 'Sao Paulo', 'SP');
