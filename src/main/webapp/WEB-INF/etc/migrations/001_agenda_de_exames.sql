-- =============================================================================
-- 001 - Agenda de exames (novo requisito: a recepcao agenda exames)
--
-- locais_exame:      onde o exame e feito e em que horario o local atende
-- tipos_exame:       catalogo com duracao, preparo e local padrao
-- exames_agendados:  o agendamento (pode vir de uma solicitacao medica ou ser avulso)
--
-- Conflito de horario garantido no banco, como em consultas: slot_ativo vale 1 para o
-- agendamento que ocupa o horario e NULL para cancelado; UNIQUE ignora NULL.
-- =============================================================================

CREATE TABLE IF NOT EXISTS `locais_exame` (
    `id_local` INT AUTO_INCREMENT PRIMARY KEY,
    `nome` VARCHAR(100) NOT NULL,
    `hora_inicio` TIME NOT NULL,
    `hora_fim` TIME NOT NULL,
    `atende_sabado` TINYINT NOT NULL DEFAULT 0,
    `ativo` TINYINT NOT NULL DEFAULT 1,
    UNIQUE KEY `uk_local_nome` (`nome`),
    CONSTRAINT `chk_local_horario` CHECK (`hora_fim` > `hora_inicio`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `tipos_exame` (
    `id_tipo_exame` INT AUTO_INCREMENT PRIMARY KEY,
    `nome` VARCHAR(150) NOT NULL,
    `duracao_min` SMALLINT NOT NULL DEFAULT 15,
    `preparo` VARCHAR(500) DEFAULT NULL COMMENT 'Orientacao entregue ao paciente na recepcao',
    `id_local` INT NOT NULL,
    `ativo` TINYINT NOT NULL DEFAULT 1,
    UNIQUE KEY `uk_tipo_exame_nome` (`nome`),
    CONSTRAINT `fk_tipo_exame_local` FOREIGN KEY (`id_local`) REFERENCES `locais_exame` (`id_local`) ON DELETE RESTRICT,
    CONSTRAINT `chk_tipo_exame_duracao` CHECK (`duracao_min` BETWEEN 5 AND 240)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `exames_agendados` (
    `id_exame_agendado` INT AUTO_INCREMENT PRIMARY KEY,
    `id_paciente` INT NOT NULL,
    `id_tipo_exame` INT NOT NULL,
    `id_local` INT NOT NULL,
    `id_solicitacao` INT DEFAULT NULL COMMENT 'Solicitacao medica (tabela exames), quando houver',
    `data_hora` DATETIME NOT NULL,
    `duracao_min` SMALLINT NOT NULL,
    `status` ENUM('agendado', 'realizado', 'cancelado', 'ausente') NOT NULL DEFAULT 'agendado',
    `slot_ativo` TINYINT AS (IF(`status` = 'cancelado', NULL, 1)) STORED,
    `cancelado_em` DATETIME DEFAULT NULL,
    `motivo_cancelamento` VARCHAR(255) DEFAULT NULL,
    `criado_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `criado_por` INT DEFAULT NULL,
    UNIQUE KEY `uk_exame_slot_local` (`id_local`, `data_hora`, `slot_ativo`),
    UNIQUE KEY `uk_exame_slot_paciente` (`id_paciente`, `data_hora`, `slot_ativo`),
    INDEX `idx_exame_agendado_data` (`data_hora`, `status`),
    CONSTRAINT `fk_ea_paciente` FOREIGN KEY (`id_paciente`) REFERENCES `pacientes` (`id_paciente`) ON DELETE RESTRICT,
    CONSTRAINT `fk_ea_tipo` FOREIGN KEY (`id_tipo_exame`) REFERENCES `tipos_exame` (`id_tipo_exame`) ON DELETE RESTRICT,
    CONSTRAINT `fk_ea_local` FOREIGN KEY (`id_local`) REFERENCES `locais_exame` (`id_local`) ON DELETE RESTRICT,
    CONSTRAINT `fk_ea_solicitacao` FOREIGN KEY (`id_solicitacao`) REFERENCES `exames` (`id_exame`) ON DELETE RESTRICT,
    CONSTRAINT `fk_ea_criado_por` FOREIGN KEY (`criado_por`) REFERENCES `usuarios` (`id_usuario`) ON DELETE SET NULL,
    CONSTRAINT `chk_ea_cancelamento` CHECK (`status` <> 'cancelado' OR `motivo_cancelamento` IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `locais_exame` (`id_local`, `nome`, `hora_inicio`, `hora_fim`, `atende_sabado`) VALUES
(1, 'Laboratorio - sala de coleta', '07:00:00', '12:00:00', 1),
(2, 'Diagnostico por imagem - sala 1', '08:00:00', '17:00:00', 0),
(3, 'Cardiologia - sala de exames', '08:00:00', '16:00:00', 0);

INSERT IGNORE INTO `tipos_exame` (`id_tipo_exame`, `nome`, `duracao_min`, `preparo`, `id_local`) VALUES
(1, 'Hemograma completo', 10, 'Jejum nao obrigatorio.', 1),
(2, 'Glicemia de jejum', 10, 'Jejum de 8 horas. Agua liberada.', 1),
(3, 'Perfil lipidico', 10, 'Jejum de 12 horas.', 1),
(4, 'Raio-X de torax', 15, 'Retirar objetos metalicos.', 2),
(5, 'Ultrassonografia de abdome total', 30, 'Jejum de 6 horas.', 2),
(6, 'Eletrocardiograma', 20, 'Evitar cremes no torax no dia do exame.', 3),
(7, 'Teste ergometrico', 40, 'Roupa e tenis confortaveis. Refeicao leve 2 horas antes.', 3);

-- Permissoes da agenda de exames (padrao modulo.acao)
INSERT IGNORE INTO `permissoes` (`id_permissao`, `codigo`, `descricao`) VALUES
(45, 'exames.agendar', 'Agendar, cancelar e registrar exames da agenda'),
(46, 'exames.agenda.view', 'Ver a agenda de exames');

-- Recepcao agenda; administrador e enfermagem consultam a agenda
INSERT IGNORE INTO `grupos_permissoes` (`id_grupo`, `id_permissao`) VALUES
(6, 45), (6, 46), (1, 46), (7, 46);
