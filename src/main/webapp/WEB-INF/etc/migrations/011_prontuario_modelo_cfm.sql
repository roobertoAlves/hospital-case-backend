-- 011: medical record structured after the CFM model.
-- * identification: mother's name, place of birth and legal guardian;
-- * patient background (one row per patient): pre-existing conditions, surgeries, hospitalizations,
--   continuous medication, lifestyle habits and family history;
-- * medical record: review of systems, physical exam, diagnosis (with ICD-10) and referrals.

ALTER TABLE `pacientes`
    ADD COLUMN IF NOT EXISTS `nome_mae` VARCHAR(150) NULL AFTER `nome_social`,
    ADD COLUMN IF NOT EXISTS `naturalidade` VARCHAR(80) NULL AFTER `profissao`,
    ADD COLUMN IF NOT EXISTS `responsavel_nome` VARCHAR(150) NULL AFTER `contato_emergencia_parentesco`,
    ADD COLUMN IF NOT EXISTS `responsavel_parentesco` VARCHAR(40) NULL AFTER `responsavel_nome`,
    ADD COLUMN IF NOT EXISTS `responsavel_telefone` VARCHAR(20) NULL AFTER `responsavel_parentesco`;

CREATE TABLE IF NOT EXISTS `paciente_antecedentes` (
    `id_paciente` INT NOT NULL,
    `doencas_preexistentes` TEXT NULL,
    `cirurgias` TEXT NULL,
    `internacoes` TEXT NULL,
    `medicamentos_uso_continuo` TEXT NULL,
    `tabagismo` ENUM('nunca','ex_fumante','fumante') NULL,
    `etilismo` ENUM('nao','social','frequente') NULL,
    `atividade_fisica` ENUM('sedentario','ate_2x_semana','3x_ou_mais') NULL,
    `sono` VARCHAR(255) NULL,
    `alimentacao` VARCHAR(255) NULL,
    `historia_familiar` TEXT NULL,
    `atualizado_por` INT NULL,
    `atualizado_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id_paciente`),
    CONSTRAINT `fk_antecedentes_paciente` FOREIGN KEY (`id_paciente`) REFERENCES `pacientes` (`id_paciente`),
    CONSTRAINT `fk_antecedentes_usuario` FOREIGN KEY (`atualizado_por`) REFERENCES `usuarios` (`id_usuario`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `prontuarios`
    ADD COLUMN IF NOT EXISTS `revisao_sistemas` TEXT NULL AFTER `historico_relevante`,
    ADD COLUMN IF NOT EXISTS `exame_fisico` TEXT NULL AFTER `revisao_sistemas`,
    ADD COLUMN IF NOT EXISTS `diagnostico` TEXT NULL AFTER `hipoteses_diagnosticas`,
    ADD COLUMN IF NOT EXISTS `cid10` VARCHAR(10) NULL AFTER `diagnostico`,
    ADD COLUMN IF NOT EXISTS `encaminhamentos` TEXT NULL AFTER `conduta`;

-- ---------------------------------------------------------------- demo data

UPDATE `pacientes` SET
    `nome_mae` = CONCAT(ELT(1 + (`id_paciente` % 10), 'Maria', 'Ana', 'Francisca', 'Antônia', 'Adriana', 'Juliana', 'Márcia', 'Fernanda', 'Patrícia', 'Aline'),
        ' ', ELT(1 + (`id_paciente` % 7), 'da Silva', 'dos Santos', 'Oliveira', 'Souza', 'Rodrigues', 'Ferreira', 'Alves')),
    `naturalidade` = ELT(1 + (`id_paciente` % 8), 'São Paulo/SP', 'Campinas/SP', 'Santos/SP', 'Belo Horizonte/MG', 'Rio de Janeiro/RJ',
        'Salvador/BA', 'Curitiba/PR', 'Recife/PE')
 WHERE `nome_mae` IS NULL AND `id_paciente` <= 1000;

-- Minors have a legal guardian
UPDATE `pacientes` SET `responsavel_nome` = `contato_emergencia_nome`, `responsavel_parentesco` = 'Mãe',
    `responsavel_telefone` = `contato_emergencia_telefone`
 WHERE `data_nascimento` > DATE_SUB(CURDATE(), INTERVAL 18 YEAR) AND `responsavel_nome` IS NULL AND `id_paciente` <= 1000;

INSERT IGNORE INTO `paciente_antecedentes` (`id_paciente`, `doencas_preexistentes`, `cirurgias`, `internacoes`, `medicamentos_uso_continuo`,
    `tabagismo`, `etilismo`, `atividade_fisica`, `sono`, `alimentacao`, `historia_familiar`, `atualizado_por`)
SELECT p.`id_paciente`,
    ELT(1 + (p.`id_paciente` % 6), 'Nenhuma conhecida', 'Hipertensão arterial', 'Diabetes tipo 2', 'Asma na infância',
        'Hipotireoidismo', 'Hipertensão arterial e dislipidemia'),
    ELT(1 + (p.`id_paciente` % 5), 'Nenhuma', 'Apendicectomia (2012)', 'Cesárea (2018)', 'Nenhuma', 'Colecistectomia (2020)'),
    ELT(1 + (p.`id_paciente` % 4), 'Nenhuma', 'Pneumonia, 3 dias (2019)', 'Nenhuma', 'Dengue, 2 dias (2022)'),
    ELT(1 + (p.`id_paciente` % 6), 'Nenhum', 'Losartana 50 mg 1x ao dia', 'Metformina 850 mg 2x ao dia', 'Nenhum',
        'Levotiroxina 50 mcg em jejum', 'Losartana 50 mg e sinvastatina 20 mg'),
    ELT(1 + (p.`id_paciente` % 3), 'nunca', 'ex_fumante', 'fumante'),
    ELT(1 + (p.`id_paciente` % 3), 'nao', 'social', 'nao'),
    ELT(1 + (p.`id_paciente` % 3), 'sedentario', 'ate_2x_semana', '3x_ou_mais'),
    ELT(1 + (p.`id_paciente` % 3), 'Dorme cerca de 7 horas, sono reparador', 'Dorme 5 a 6 horas, acorda à noite', 'Dorme bem, 8 horas'),
    ELT(1 + (p.`id_paciente` % 3), 'Variada, com frutas e verduras', 'Rica em ultraprocessados', 'Equilibrada, evita sal'),
    ELT(1 + (p.`id_paciente` % 5), 'Pai com hipertensão', 'Mãe com diabetes tipo 2', 'Avô com infarto aos 60 anos',
        'Nada relevante', 'Mãe com câncer de mama'),
    32
FROM `pacientes` p WHERE p.`id_paciente` <= 1000;

-- Finished demo records are immutable: the trigger is dropped only for this data fix and recreated.
DROP TRIGGER IF EXISTS `trg_prontuarios_finalizado`;

UPDATE `prontuarios` SET
    `revisao_sistemas` = 'Nega febre persistente, perda de peso, dor torácica, dispneia, alterações urinárias ou intestinais.',
    `exame_fisico` = 'Bom estado geral, corado, hidratado, eupneico. Ausculta cardíaca rítmica, sem sopros. Murmúrio vesicular presente, sem ruídos adventícios. Abdome flácido, indolor.',
    `diagnostico` = `hipoteses_diagnosticas`,
    `cid10` = CASE `hipoteses_diagnosticas`
        WHEN 'Tosse pos-viral' THEN 'R05'
        WHEN 'Miopia a confirmar' THEN 'H52.1'
        WHEN 'Dispepsia funcional' THEN 'K30'
        WHEN 'Sindrome gripal' THEN 'J11.1'
        WHEN 'Hipertensao controlada' THEN 'I10'
        WHEN 'Transtorno de ansiedade' THEN 'F41.1'
        WHEN 'Cefaleia tensional' THEN 'G44.2'
        WHEN 'Hipotensao ortostatica' THEN 'I95.1'
        WHEN 'Angina estavel a investigar' THEN 'I20.9'
        WHEN 'Exames dentro da normalidade' THEN 'Z00.0'
        WHEN 'IVAS' THEN 'J06.9'
        WHEN 'Bronquite aguda a confirmar' THEN 'J20.9'
        ELSE NULL END,
    `encaminhamentos` = CASE `hipoteses_diagnosticas`
        WHEN 'Angina estavel a investigar' THEN 'Cardiologia: teste ergométrico e ecocardiograma.'
        WHEN 'Miopia a confirmar' THEN 'Oftalmologia: refração.'
        ELSE NULL END
 WHERE `exame_fisico` IS NULL;

CREATE TRIGGER `trg_prontuarios_finalizado` BEFORE UPDATE ON `prontuarios` FOR EACH ROW
BEGIN
    IF OLD.`status` = 'finalizado' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Prontuario finalizado nao pode ser alterado; registre um adendo.';
    END IF;
END;
