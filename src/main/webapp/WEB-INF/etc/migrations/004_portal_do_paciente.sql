-- =============================================================================
-- 004 - Portal do paciente
--
-- - pacientes.id_usuario: a conta do portal ligada a ficha (no maximo uma; RN-POR-01).
-- - vinculos_portal: codigo de uso unico (hash) enviado ao e-mail da FICHA quando o CPF ja
--   estava cadastrado pela recepcao (RN-POR-02: ninguem toma a ficha so sabendo o CPF).
-- - tipos_exame.exige_pedido + pedidos_medicos: exame que exige pedido so e agendado pelo
--   portal com pedido anexado ou solicitacao do sistema; a recepcao confere (RN-POR-04).
-- - vacinas, vacinas_agendadas e vacinas_aplicadas: catalogo, agenda da sala de vacinacao e
--   cartao de vacinacao (aplicacao registrada pela enfermagem, somente inclusao).
-- - consultas.confirmada_em: o paciente confirma presenca pelo portal.
-- =============================================================================

ALTER TABLE `pacientes`
    ADD COLUMN `id_usuario` INT DEFAULT NULL COMMENT 'Conta do portal do paciente',
    ADD UNIQUE KEY `uk_paciente_usuario` (`id_usuario`);
ALTER TABLE `pacientes`
    ADD CONSTRAINT `fk_paciente_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`) ON DELETE SET NULL;

ALTER TABLE `consultas` ADD COLUMN `confirmada_em` DATETIME DEFAULT NULL COMMENT 'Confirmacao do paciente pelo portal';
ALTER TABLE `tipos_exame` ADD COLUMN `exige_pedido` TINYINT NOT NULL DEFAULT 1 COMMENT 'Agendamento pelo portal exige pedido medico';

CREATE TABLE IF NOT EXISTS `vinculos_portal` (
    `id_vinculo` INT AUTO_INCREMENT PRIMARY KEY,
    `id_usuario` INT NOT NULL,
    `id_paciente` INT NOT NULL,
    `codigo_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `expira_em` DATETIME NOT NULL,
    `tentativas` TINYINT NOT NULL DEFAULT 0,
    `usado_em` DATETIME DEFAULT NULL,
    `criado_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_vinculo_usuario` (`id_usuario`, `usado_em`),
    CONSTRAINT `fk_vinculo_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE,
    CONSTRAINT `fk_vinculo_paciente` FOREIGN KEY (`id_paciente`) REFERENCES `pacientes` (`id_paciente`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- O arquivo fica fora da pasta publica (paths.storage/pedidos); aqui so os metadados.
CREATE TABLE IF NOT EXISTS `pedidos_medicos` (
    `id_pedido` INT AUTO_INCREMENT PRIMARY KEY,
    `id_paciente` INT NOT NULL,
    `id_tipo_exame` INT DEFAULT NULL COMMENT 'Exame a que o pedido se refere, quando informado',
    `arquivo` VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Nome interno aleatorio',
    `nome_original` VARCHAR(150) NOT NULL,
    `mime` VARCHAR(50) NOT NULL,
    `tamanho_bytes` INT NOT NULL,
    `observacao` VARCHAR(255) DEFAULT NULL,
    `status` ENUM('pendente', 'aprovado', 'recusado') NOT NULL DEFAULT 'pendente',
    `conferido_por` INT DEFAULT NULL,
    `conferido_em` DATETIME DEFAULT NULL,
    `motivo_recusa` VARCHAR(255) DEFAULT NULL,
    `enviado_por` INT DEFAULT NULL,
    `enviado_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_pedido_arquivo` (`arquivo`),
    INDEX `idx_pedido_status` (`status`, `enviado_em`),
    INDEX `idx_pedido_paciente` (`id_paciente`),
    CONSTRAINT `fk_pedido_paciente` FOREIGN KEY (`id_paciente`) REFERENCES `pacientes` (`id_paciente`) ON DELETE RESTRICT,
    CONSTRAINT `fk_pedido_tipo` FOREIGN KEY (`id_tipo_exame`) REFERENCES `tipos_exame` (`id_tipo_exame`) ON DELETE SET NULL,
    CONSTRAINT `fk_pedido_conferido_por` FOREIGN KEY (`conferido_por`) REFERENCES `usuarios` (`id_usuario`) ON DELETE SET NULL,
    CONSTRAINT `fk_pedido_enviado_por` FOREIGN KEY (`enviado_por`) REFERENCES `usuarios` (`id_usuario`) ON DELETE SET NULL,
    CONSTRAINT `chk_pedido_tamanho` CHECK (`tamanho_bytes` BETWEEN 1 AND 5242880),
    CONSTRAINT `chk_pedido_recusa` CHECK (`status` <> 'recusado' OR `motivo_recusa` IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `exames_agendados`
    ADD COLUMN `id_pedido` INT DEFAULT NULL COMMENT 'Pedido medico anexado pelo paciente',
    ADD COLUMN `origem` ENUM('recepcao', 'portal') NOT NULL DEFAULT 'recepcao';
ALTER TABLE `exames_agendados`
    ADD CONSTRAINT `fk_ea_pedido` FOREIGN KEY (`id_pedido`) REFERENCES `pedidos_medicos` (`id_pedido`) ON DELETE RESTRICT;
ALTER TABLE `consultas` ADD COLUMN `origem` ENUM('recepcao', 'portal') NOT NULL DEFAULT 'recepcao';

CREATE TABLE IF NOT EXISTS `vacinas` (
    `id_vacina` INT AUTO_INCREMENT PRIMARY KEY,
    `nome` VARCHAR(120) NOT NULL,
    `descricao` VARCHAR(300) DEFAULT NULL,
    `doses` TINYINT NOT NULL DEFAULT 1,
    `intervalo_dias` SMALLINT DEFAULT NULL COMMENT 'Intervalo minimo entre doses',
    `publico` VARCHAR(150) DEFAULT NULL,
    `ativo` TINYINT NOT NULL DEFAULT 1,
    UNIQUE KEY `uk_vacina_nome` (`nome`),
    CONSTRAINT `chk_vacina_doses` CHECK (`doses` BETWEEN 1 AND 10)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Sala de vacinacao: um paciente por horario (UNIQUE em data_hora + slot_ativo).
CREATE TABLE IF NOT EXISTS `vacinas_agendadas` (
    `id_agendamento` INT AUTO_INCREMENT PRIMARY KEY,
    `id_paciente` INT NOT NULL,
    `id_vacina` INT NOT NULL,
    `dose` TINYINT NOT NULL DEFAULT 1,
    `data_hora` DATETIME NOT NULL,
    `status` ENUM('agendada', 'aplicada', 'cancelada', 'ausente') NOT NULL DEFAULT 'agendada',
    `slot_ativo` TINYINT AS (IF(`status` = 'cancelada', NULL, 1)) STORED,
    `origem` ENUM('recepcao', 'portal') NOT NULL DEFAULT 'portal',
    `motivo_cancelamento` VARCHAR(255) DEFAULT NULL,
    `criado_por` INT DEFAULT NULL,
    `criado_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_vacina_slot` (`data_hora`, `slot_ativo`),
    INDEX `idx_vacina_ag_paciente` (`id_paciente`, `status`),
    CONSTRAINT `fk_vag_paciente` FOREIGN KEY (`id_paciente`) REFERENCES `pacientes` (`id_paciente`) ON DELETE RESTRICT,
    CONSTRAINT `fk_vag_vacina` FOREIGN KEY (`id_vacina`) REFERENCES `vacinas` (`id_vacina`) ON DELETE RESTRICT,
    CONSTRAINT `fk_vag_criado_por` FOREIGN KEY (`criado_por`) REFERENCES `usuarios` (`id_usuario`) ON DELETE SET NULL,
    CONSTRAINT `chk_vag_cancelamento` CHECK (`status` <> 'cancelada' OR `motivo_cancelamento` IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `vacinas_aplicadas` (
    `id_aplicacao` INT AUTO_INCREMENT PRIMARY KEY,
    `id_paciente` INT NOT NULL,
    `id_vacina` INT NOT NULL,
    `dose` TINYINT NOT NULL,
    `lote` VARCHAR(40) NOT NULL,
    `fabricante` VARCHAR(80) DEFAULT NULL,
    `aplicada_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `id_enfermeiro` INT DEFAULT NULL,
    `id_agendamento` INT DEFAULT NULL,
    `observacoes` VARCHAR(255) DEFAULT NULL,
    UNIQUE KEY `uk_aplicacao_agendamento` (`id_agendamento`),
    INDEX `idx_aplicacao_paciente` (`id_paciente`, `aplicada_em`),
    CONSTRAINT `fk_vap_paciente` FOREIGN KEY (`id_paciente`) REFERENCES `pacientes` (`id_paciente`) ON DELETE RESTRICT,
    CONSTRAINT `fk_vap_vacina` FOREIGN KEY (`id_vacina`) REFERENCES `vacinas` (`id_vacina`) ON DELETE RESTRICT,
    CONSTRAINT `fk_vap_enfermeiro` FOREIGN KEY (`id_enfermeiro`) REFERENCES `enfermeiros` (`id_profissional`) ON DELETE RESTRICT,
    CONSTRAINT `fk_vap_agendamento` FOREIGN KEY (`id_agendamento`) REFERENCES `vacinas_agendadas` (`id_agendamento`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TRIGGER IF EXISTS `trg_vacinas_aplicadas_sem_update`;
CREATE TRIGGER `trg_vacinas_aplicadas_sem_update` BEFORE UPDATE ON `vacinas_aplicadas` FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Registro de vacina aplicada nao pode ser alterado.';
DROP TRIGGER IF EXISTS `trg_vacinas_aplicadas_sem_delete`;
CREATE TRIGGER `trg_vacinas_aplicadas_sem_delete` BEFORE DELETE ON `vacinas_aplicadas` FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Registro de vacina aplicada nao pode ser excluido.';

-- Perfil e permissoes do portal
INSERT IGNORE INTO `grupos` (`id_grupo`, `nome`, `descricao`, `is_admin`) VALUES
(9, 'paciente', 'Paciente: portal com os proprios agendamentos, exames, vacinas e receitas', 0);

INSERT IGNORE INTO `permissoes` (`id_permissao`, `codigo`, `descricao`) VALUES
(47, 'portal.paciente', 'Usar o portal do paciente (so os proprios dados)'),
(48, 'pedidos.conferir', 'Conferir pedidos medicos enviados pelo portal'),
(49, 'vacinas.agenda.view', 'Ver a agenda da sala de vacinacao'),
(50, 'vacinas.aplicar', 'Registrar aplicacao de vacina'),
(51, 'vacinas.agendar', 'Agendar e cancelar vacinas na recepcao'),
(52, 'pacientes.vincular_conta', 'Ligar a conta do portal a ficha do paciente');

INSERT IGNORE INTO `grupos_permissoes` (`id_grupo`, `id_permissao`) VALUES
(9, 47),
(6, 48), (6, 49), (6, 51), (6, 52),
(7, 49), (7, 50),
(1, 49);

-- Catalogo de vacinas (texto informativo de demonstracao; nao e orientacao medica)
INSERT IGNORE INTO `vacinas` (`id_vacina`, `nome`, `descricao`, `doses`, `intervalo_dias`, `publico`) VALUES
(1, 'Influenza (gripe)', 'Dose anual, atualizada a cada campanha.', 1, NULL, 'A partir de 6 meses'),
(2, 'Hepatite B', 'Esquema de tres doses.', 3, 30, 'Todas as idades'),
(3, 'Tetano e difteria (dT)', 'Reforco a cada 10 anos.', 1, NULL, 'Adultos'),
(4, 'Febre amarela', 'Dose unica para a maioria dos adultos.', 1, NULL, 'A partir de 9 meses'),
(5, 'Triplice viral (sarampo, caxumba, rubeola)', 'Duas doses ao longo da vida.', 2, 30, 'A partir de 12 meses'),
(6, 'HPV', 'Prevencao de canceres associados ao HPV.', 2, 180, '9 a 14 anos'),
(7, 'Covid-19', 'Conforme o calendario vigente.', 1, NULL, 'Conforme o calendario vigente');

-- Exames de rotina que o paciente agenda sem pedido
UPDATE `tipos_exame` SET `exige_pedido` = 0 WHERE `id_tipo_exame` IN (1, 2, 3);

-- Conta de demonstracao do portal: Ana Paciente Ficticia (paciente 1), senha paciente123
INSERT IGNORE INTO `usuarios` (`id_usuario`, `nome`, `email`, `senha_hash`, `status`, `primeiro_acesso`) VALUES
(60, 'Ana Paciente Ficticia', 'paciente@hospital.local', '$2y$12$sUh5Q.39dMxOSAoWTA53AOM6eCC1q6lJjPPIVEmfIBZumNSvHsZqq', 1, 0);
INSERT IGNORE INTO `usuarios_grupos` (`id_usuario`, `id_grupo`) VALUES (60, 9);
UPDATE `pacientes` SET `id_usuario` = 60 WHERE `id_paciente` = 1 AND `id_usuario` IS NULL;

-- Cartao de vacinacao de demonstracao da Ana
INSERT IGNORE INTO `vacinas_aplicadas` (`id_aplicacao`, `id_paciente`, `id_vacina`, `dose`, `lote`, `fabricante`, `aplicada_em`, `id_enfermeiro`) VALUES
(1, 1, 2, 1, 'HB-2025-001', 'Fabricante ficticio', DATE_SUB(NOW(), INTERVAL 200 DAY), 2),
(2, 1, 2, 2, 'HB-2025-014', 'Fabricante ficticio', DATE_SUB(NOW(), INTERVAL 160 DAY), 2),
(3, 1, 1, 1, 'FLU-2026-07', 'Fabricante ficticio', DATE_SUB(NOW(), INTERVAL 40 DAY), 107);
