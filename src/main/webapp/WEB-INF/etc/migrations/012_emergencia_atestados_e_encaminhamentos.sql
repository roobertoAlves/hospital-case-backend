ALTER TABLE `tipos_exame`
    ADD COLUMN IF NOT EXISTS `codigo` VARCHAR(20) NULL AFTER `nome`;

UPDATE `tipos_exame` t
JOIN (
    SELECT `id_tipo_exame`,
           CONCAT(CASE `categoria`
                      WHEN 'Analises clinicas' THEN 'LAB'
                      WHEN 'Audiologia' THEN 'AUD'
                      WHEN 'Cardiologia' THEN 'CAR'
                      WHEN 'Check-up' THEN 'CHK'
                      WHEN 'Genetica' THEN 'GEN'
                      WHEN 'Imagem' THEN 'IMG'
                      WHEN 'Oftalmologia' THEN 'OFT'
                      WHEN 'Respiratorio' THEN 'RES'
                      ELSE 'EXA' END,
                  '-', LPAD(ROW_NUMBER() OVER (PARTITION BY `categoria` ORDER BY `id_tipo_exame`), 3, '0')) AS `novo`
    FROM `tipos_exame`
) n ON n.`id_tipo_exame` = t.`id_tipo_exame`
SET t.`codigo` = n.`novo`
WHERE t.`codigo` IS NULL;

ALTER TABLE `tipos_exame` ADD UNIQUE KEY `uk_tipo_exame_codigo` (`codigo`);

ALTER TABLE `exames`
    ADD COLUMN IF NOT EXISTS `id_tipo_exame` INT NULL AFTER `id_medico`;

UPDATE `exames` x JOIN `tipos_exame` t ON t.`nome` = x.`nome_exame`
SET x.`id_tipo_exame` = t.`id_tipo_exame`, x.`codigo` = COALESCE(x.`codigo`, t.`codigo`)
WHERE x.`id_tipo_exame` IS NULL;

ALTER TABLE `exames` ADD CONSTRAINT `fk_exame_tipo` FOREIGN KEY (`id_tipo_exame`) REFERENCES `tipos_exame` (`id_tipo_exame`);

ALTER TABLE `especialidades`
    ADD COLUMN IF NOT EXISTS `emergencia` TINYINT NOT NULL DEFAULT 0 AFTER `descricao`;

INSERT INTO `especialidades` (`nome`, `descricao`, `emergencia`)
SELECT 'Emergência', 'Pronto atendimento: casos graves, mal súbito e prioridades da triagem', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `especialidades` WHERE `nome` = 'Emergência');

ALTER TABLE `consultas`
    MODIFY COLUMN `tipo` ENUM('agendada','encaixe','emergencia') NOT NULL DEFAULT 'agendada';

ALTER TABLE `consultas`
    MODIFY COLUMN `slot_ativo` TINYINT GENERATED ALWAYS AS (IF(`status` = 'cancelada' OR `tipo` IN ('encaixe', 'emergencia'), NULL, 1)) STORED;

ALTER TABLE `consultas`
    ADD COLUMN IF NOT EXISTS `medico_original` INT NULL AFTER `id_medico`,
    ADD COLUMN IF NOT EXISTS `motivo_emergencia` VARCHAR(255) NULL AFTER `tipo`;

ALTER TABLE `consultas` ADD CONSTRAINT `fk_consulta_medico_original` FOREIGN KEY (`medico_original`) REFERENCES `medicos` (`id_profissional`);

CREATE TABLE IF NOT EXISTS `atestados` (
    `id_atestado` INT NOT NULL AUTO_INCREMENT,
    `id_consulta` INT NOT NULL,
    `id_medico` INT NOT NULL,
    `tipo` ENUM('afastamento','comparecimento','acompanhante') NOT NULL,
    `data_inicio` DATE NOT NULL,
    `dias` SMALLINT NULL,
    `hora_inicio` TIME NULL,
    `hora_fim` TIME NULL,
    `acompanhante_nome` VARCHAR(150) NULL,
    `cid10` VARCHAR(10) NULL,
    `observacoes` VARCHAR(500) NULL,
    `status` ENUM('emitido','cancelado') NOT NULL DEFAULT 'emitido',
    `emitido_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `cancelado_em` DATETIME NULL,
    `motivo_cancelamento` VARCHAR(255) NULL,
    PRIMARY KEY (`id_atestado`),
    KEY `idx_atestado_consulta` (`id_consulta`),
    CONSTRAINT `fk_atestado_consulta` FOREIGN KEY (`id_consulta`) REFERENCES `consultas` (`id_consulta`),
    CONSTRAINT `fk_atestado_medico` FOREIGN KEY (`id_medico`) REFERENCES `medicos` (`id_profissional`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `encaminhamentos` (
    `id_encaminhamento` INT NOT NULL AUTO_INCREMENT,
    `id_consulta` INT NOT NULL,
    `id_medico` INT NOT NULL,
    `id_especialidade` INT NOT NULL,
    `prioridade` ENUM('eletivo','prioritario','urgente') NOT NULL DEFAULT 'eletivo',
    `motivo` VARCHAR(500) NOT NULL,
    `status` ENUM('emitido','agendado','cancelado') NOT NULL DEFAULT 'emitido',
    `id_consulta_agendada` INT NULL,
    `emitido_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `cancelado_em` DATETIME NULL,
    `motivo_cancelamento` VARCHAR(255) NULL,
    PRIMARY KEY (`id_encaminhamento`),
    KEY `idx_encaminhamento_consulta` (`id_consulta`),
    CONSTRAINT `fk_encaminhamento_consulta` FOREIGN KEY (`id_consulta`) REFERENCES `consultas` (`id_consulta`),
    CONSTRAINT `fk_encaminhamento_medico` FOREIGN KEY (`id_medico`) REFERENCES `medicos` (`id_profissional`),
    CONSTRAINT `fk_encaminhamento_especialidade` FOREIGN KEY (`id_especialidade`) REFERENCES `especialidades` (`id_especialidade`),
    CONSTRAINT `fk_encaminhamento_agendada` FOREIGN KEY (`id_consulta_agendada`) REFERENCES `consultas` (`id_consulta`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `permissoes` (`codigo`, `descricao`)
SELECT v.`codigo`, v.`descricao` FROM (
    SELECT 'atestados.emitir' AS `codigo`, 'Emitir e cancelar atestados médicos' AS `descricao`
    UNION ALL SELECT 'encaminhamentos.emitir', 'Encaminhar o paciente para outra especialidade'
    UNION ALL SELECT 'emergencia.registrar', 'Abrir atendimento de emergência (paciente sem agendamento)'
    UNION ALL SELECT 'exames.catalogo.manage', 'Cadastrar e editar o catálogo de exames'
) v WHERE NOT EXISTS (SELECT 1 FROM `permissoes` p WHERE p.`codigo` = v.`codigo`);

INSERT IGNORE INTO `grupos_permissoes` (`id_grupo`, `id_permissao`)
SELECT g.`id_grupo`, p.`id_permissao` FROM `grupos` g JOIN `permissoes` p
WHERE (g.`nome` = 'medico' AND p.`codigo` IN ('atestados.emitir', 'encaminhamentos.emitir'))
   OR (g.`nome` = 'recepcionista' AND p.`codigo` IN ('emergencia.registrar'))
   OR (g.`nome` = 'admin' AND p.`codigo` IN ('exames.catalogo.manage'));

DELETE gp FROM `grupos_permissoes` gp
JOIN `grupos` g ON g.`id_grupo` = gp.`id_grupo`
JOIN `permissoes` p ON p.`id_permissao` = gp.`id_permissao`
WHERE g.`nome` = 'admin'
  AND p.`codigo` IN ('agenda.view', 'exames.agenda.view', 'vacinas.agenda.view', 'pacientes.view', 'pacientes.inativar');

INSERT INTO `usuarios` (`nome`, `email`, `senha_hash`, `status`, `primeiro_acesso`)
SELECT v.`nome`, v.`email`, '$2y$12$ubXYBMqotNZbf9d040k0DeUnmg/rBunPSKKihau9SwIL.bpL.ETka', 1, 0 FROM (
    SELECT 'Otávio Prates' AS `nome`, 'plantao@hospital.local' AS `email`
    UNION ALL SELECT 'Lívia Assunção', 'livia.plantao@hospital.local'
) v WHERE NOT EXISTS (SELECT 1 FROM `usuarios` u WHERE u.`email` = v.`email`);

INSERT IGNORE INTO `usuarios_grupos` (`id_usuario`, `id_grupo`)
SELECT u.`id_usuario`, g.`id_grupo` FROM `usuarios` u JOIN `grupos` g ON g.`nome` = 'medico'
WHERE u.`email` IN ('plantao@hospital.local', 'livia.plantao@hospital.local');

INSERT INTO `profissionais` (`id_usuario`, `tipo`, `nome`, `cargo`, `email`, `telefone`)
SELECT u.`id_usuario`, 'medico', u.`nome`, 'Médico plantonista', u.`email`, v.`telefone` FROM `usuarios` u JOIN (
    SELECT 'plantao@hospital.local' AS `email`, '(11) 98877-4101' AS `telefone`
    UNION ALL SELECT 'livia.plantao@hospital.local', '(11) 98877-4102'
) v ON v.`email` = u.`email`
WHERE NOT EXISTS (SELECT 1 FROM `profissionais` p WHERE p.`id_usuario` = u.`id_usuario`);

INSERT INTO `medicos` (`id_profissional`, `crm`, `crm_uf`, `duracao_consulta_min`)
SELECT p.`id_profissional`, v.`crm`, 'SP', 20 FROM `profissionais` p JOIN (
    SELECT 'plantao@hospital.local' AS `email`, '184520' AS `crm`
    UNION ALL SELECT 'livia.plantao@hospital.local', '191377'
) v ON v.`email` = p.`email`
WHERE NOT EXISTS (SELECT 1 FROM `medicos` m WHERE m.`id_profissional` = p.`id_profissional`);

INSERT IGNORE INTO `medico_especialidades` (`id_profissional`, `id_especialidade`)
SELECT p.`id_profissional`, e.`id_especialidade` FROM `profissionais` p JOIN `especialidades` e ON e.`nome` = 'Emergência'
WHERE p.`email` IN ('plantao@hospital.local', 'livia.plantao@hospital.local');

INSERT INTO `consultas` (`id_paciente`, `id_medico`, `id_especialidade`, `data_hora`, `duracao_min`, `tipo`, `motivo_emergencia`, `status`, `chegada_em`)
SELECT pa.`id_paciente`, p.`id_profissional`, e.`id_especialidade`, NOW() - INTERVAL 5 MINUTE, 20, 'emergencia',
       'Dor no peito e falta de ar ao chegar na recepção', 'aguardando_triagem', NOW() - INTERVAL 5 MINUTE
FROM `pacientes` pa
JOIN `profissionais` p ON p.`email` = 'plantao@hospital.local'
JOIN `especialidades` e ON e.`nome` = 'Emergência'
WHERE pa.`id_paciente` = (SELECT MIN(`id_paciente`) FROM `pacientes` WHERE `ativo` = 1 AND `id_paciente` > 1)
  AND NOT EXISTS (SELECT 1 FROM `consultas` c WHERE c.`tipo` = 'emergencia');
