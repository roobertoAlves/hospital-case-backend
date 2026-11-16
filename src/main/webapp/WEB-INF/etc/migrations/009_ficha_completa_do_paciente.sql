-- 009: complete patient intake record (what reception asks at registration): social name, marital
-- status, occupation, blood type, health plan and SUS card. Demo patients get plausible values.

ALTER TABLE `pacientes`
    ADD COLUMN IF NOT EXISTS `nome_social` VARCHAR(150) NULL AFTER `nome`,
    ADD COLUMN IF NOT EXISTS `estado_civil` ENUM('solteiro','casado','uniao_estavel','divorciado','viuvo','nao_informado') NULL AFTER `sexo`,
    ADD COLUMN IF NOT EXISTS `profissao` VARCHAR(80) NULL AFTER `estado_civil`,
    ADD COLUMN IF NOT EXISTS `tipo_sanguineo` ENUM('A+','A-','B+','B-','AB+','AB-','O+','O-') NULL AFTER `profissao`,
    ADD COLUMN IF NOT EXISTS `convenio` VARCHAR(80) NULL AFTER `contato_emergencia_parentesco`,
    ADD COLUMN IF NOT EXISTS `numero_carteirinha` VARCHAR(30) NULL AFTER `convenio`,
    ADD COLUMN IF NOT EXISTS `cartao_sus` CHAR(15) NULL AFTER `numero_carteirinha`;

UPDATE `pacientes` SET
    `estado_civil` = ELT(1 + (`id_paciente` % 5), 'solteiro', 'casado', 'uniao_estavel', 'divorciado', 'viuvo'),
    `profissao` = ELT(1 + (`id_paciente` % 12), 'Professora', 'Engenheiro civil', 'Estudante', 'Comerciante', 'Enfermeira',
        'Motorista', 'Analista de sistemas', 'Aposentado', 'Advogada', 'Cozinheiro', 'Vendedora', 'Eletricista'),
    `tipo_sanguineo` = ELT(1 + (`id_paciente` % 8), 'O+', 'A+', 'B+', 'O-', 'A-', 'AB+', 'B-', 'AB-'),
    `convenio` = ELT(1 + (`id_paciente` % 5), NULL, 'Unimed', 'Bradesco Saúde', 'SulAmérica', 'Amil'),
    `cartao_sus` = LPAD(CONCAT('70', LPAD(`id_paciente`, 4, '0'), '000', LPAD(`id_paciente` * 37 % 1000000, 6, '0')), 15, '0')
 WHERE `estado_civil` IS NULL AND `id_paciente` <= 1000;

UPDATE `pacientes` SET `numero_carteirinha` = CONCAT(UPPER(LEFT(REPLACE(`convenio`, ' ', ''), 3)), LPAD(`id_paciente` * 7919 % 100000000, 8, '0'))
 WHERE `convenio` IS NOT NULL AND `numero_carteirinha` IS NULL AND `id_paciente` <= 1000;

-- Minors: single, listed as students
UPDATE `pacientes` SET `estado_civil` = 'solteiro', `profissao` = 'Estudante'
 WHERE `data_nascimento` > DATE_SUB(CURDATE(), INTERVAL 18 YEAR) AND `id_paciente` <= 1000;

-- More demo allergies, so the record shows the safety alert on several patients
INSERT IGNORE INTO `paciente_alergias` (`id_alergia`, `id_paciente`, `substancia`, `reacao`, `gravidade`, `registrado_por`) VALUES
(201, 1, 'Látex', 'Vermelhidão e coceira no local', 'leve', 32),
(202, 1, 'Frutos do mar', 'Inchaço nos lábios', 'moderada', 32),
(203, 3, 'Ácido acetilsalicílico (AAS)', 'Falta de ar', 'grave', 32),
(204, 5, 'Sulfa', 'Manchas na pele', 'moderada', 32),
(205, 9, 'Iodo (contraste)', 'Náusea e urticária', 'moderada', 32),
(206, 18, 'Amendoim', 'Anafilaxia', 'grave', 32),
(207, 22, 'Ibuprofeno', 'Dor de estômago intensa', 'leve', 32),
(208, 27, 'Poeira e ácaros', 'Rinite e espirros', 'leve', 32);
