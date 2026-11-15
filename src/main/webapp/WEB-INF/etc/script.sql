-- =============================================================================
-- ZTF_J2EE - Script Oficial de Estrutura (DDL) e Carga Inicial (DML)
-- Charset: UTF-8 (utf8mb4) / Collation: utf8mb4_unicode_ci
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `hospital` 
DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `hospital`;

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- =============================================================================
-- 1. DDL (DATA DEFINITION LANGUAGE) - ESTRUTURA DAS TABELAS
-- =============================================================================

-- Tabela 1: Usuários
CREATE TABLE IF NOT EXISTS `usuarios` (
    `id_usuario` INT AUTO_INCREMENT PRIMARY KEY,
    `nome` VARCHAR(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `email` VARCHAR(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL UNIQUE,
    `senha_hash` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `foto` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Foto do perfil em Base64',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=Ativo, 0=Inativo',
    `primeiro_acesso` TINYINT NOT NULL DEFAULT 0 COMMENT '1=Troca obrigatória de senha',
    `criado_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `atualizado_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_usuario_email` (`email`),
    INDEX `idx_usuario_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabela 2: Grupos de Acesso (Níveis)
CREATE TABLE IF NOT EXISTS `grupos` (
    `id_grupo` INT AUTO_INCREMENT PRIMARY KEY,
    `nome` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL UNIQUE,
    `descricao` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `is_admin` TINYINT NOT NULL DEFAULT 0 COMMENT '1=SuperAdmin',
    INDEX `idx_grupo_nome` (`nome`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabela 3: Permissões Granulares
CREATE TABLE IF NOT EXISTS `permissoes` (
    `id_permissao` INT AUTO_INCREMENT PRIMARY KEY,
    `codigo` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL UNIQUE,
    `descricao` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    INDEX `idx_permissao_codigo` (`codigo`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabela 4: Associação Usuários <-> Grupos (M:N)
CREATE TABLE IF NOT EXISTS `usuarios_grupos` (
    `id_usuario` INT NOT NULL,
    `id_grupo` INT NOT NULL,
    PRIMARY KEY (`id_usuario`, `id_grupo`),
    CONSTRAINT `fk_ug_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE,
    CONSTRAINT `fk_ug_grupo` FOREIGN KEY (`id_grupo`) REFERENCES `grupos` (`id_grupo`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabela 5: Associação Grupos <-> Permissões (M:N)
CREATE TABLE IF NOT EXISTS `grupos_permissoes` (
    `id_grupo` INT NOT NULL,
    `id_permissao` INT NOT NULL,
    PRIMARY KEY (`id_grupo`, `id_permissao`),
    CONSTRAINT `fk_gp_grupo` FOREIGN KEY (`id_grupo`) REFERENCES `grupos` (`id_grupo`) ON DELETE CASCADE,
    CONSTRAINT `fk_gp_permissao` FOREIGN KEY (`id_permissao`) REFERENCES `permissoes` (`id_permissao`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabela 6: Uploads Privados em Base64 com Controle de Acesso (ACL)
CREATE TABLE IF NOT EXISTS `arquivos_upload` (
    `id_arquivo` INT AUTO_INCREMENT PRIMARY KEY,
    `hash` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL UNIQUE,
    `nome_original` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `mime_type` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `tamanho_bytes` INT NOT NULL,
    `id_dono` INT NOT NULL,
    `publico` TINYINT NOT NULL DEFAULT 0 COMMENT '1=Público, 0=Privado por ACL',
    `grupos_permitidos` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'JSON array de grupos autorizados',
    `usuarios_permitidos` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'JSON array de idUsuarios autorizados',
    `caminho_fisico` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Nome do arquivo base64 no storage',
    `enviado_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_arquivo_hash` (`hash`),
    INDEX `idx_arquivo_dono` (`id_dono`),
    CONSTRAINT `fk_arq_dono` FOREIGN KEY (`id_dono`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- 2. DML (DATA MANIPULATION LANGUAGE) - DADOS INICIAIS DE EXEMPLO
-- =============================================================================

-- 2.1 Carga de Grupos Padrão
INSERT IGNORE INTO `grupos` (`id_grupo`, `nome`, `descricao`, `is_admin`) VALUES
(1, 'admin', 'Administrador do Sistema com Acesso Total', 1),
(2, 'usuario', 'Usuário Padrão do Sistema (Nível Inicial de Registro)', 0),
(3, 'gerente', 'Gestor de Recursos, Usuários e Conteúdo', 0),
(4, 'financeiro', 'Acesso ao Departamento Financeiro e Relatórios', 0),
(5, 'suporte', 'Equipe de Atendimento e Suporte Técnico', 0);

-- 2.2 Carga de Permissões Granulares
INSERT IGNORE INTO `permissoes` (`id_permissao`, `codigo`, `descricao`) VALUES
(1, 'usuarios.view', 'Visualizar perfis e listagem de usuários'),
(2, 'usuarios.create', 'Cadastrar novos usuários no sistema'),
(3, 'usuarios.edit', 'Editar dados e status de usuários'),
(4, 'usuarios.delete', 'Excluir registros de usuários'),
(5, 'grupos.manage', 'Gerenciar matriz de grupos e permissões'),
(6, 'arquivos.upload', 'Efetuar uploads de arquivos em Base64'),
(7, 'arquivos.manage', 'Auditar e gerenciar arquivos do sistema'),
(8, 'financeiro.view', 'Visualizar lançamentos e relatórios financeiros'),
(9, 'suporte.tickets', 'Gerenciar e responder chamados de suporte');

-- 2.3 Matriz de Permissões por Grupo
-- Grupo 1 (Admin): Todas as permissões
INSERT IGNORE INTO `grupos_permissoes` (`id_grupo`, `id_permissao`) VALUES
(1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6), (1, 7), (1, 8), (1, 9);

-- Grupo 2 (Usuario): Apenas upload básico
INSERT IGNORE INTO `grupos_permissoes` (`id_grupo`, `id_permissao`) VALUES
(2, 6);

-- Grupo 3 (Gerente): Usuários, arquivos e uploads
INSERT IGNORE INTO `grupos_permissoes` (`id_grupo`, `id_permissao`) VALUES
(3, 1), (3, 2), (3, 3), (3, 6), (3, 7);

-- Grupo 4 (Financeiro): Financeiro e arquivos
INSERT IGNORE INTO `grupos_permissoes` (`id_grupo`, `id_permissao`) VALUES
(4, 6), (4, 8);

-- Grupo 5 (Suporte): Chamados e visualização de usuários
INSERT IGNORE INTO `grupos_permissoes` (`id_grupo`, `id_permissao`) VALUES
(5, 1), (5, 6), (5, 9);

-- 2.4 Usuários Iniciais de Exemplo com Hashes BCrypt + Salt + Pepper
-- Senha de todos configurada com Pepper oficial do config.json:
-- admin@hospital.local      -> admin123      (Nível: admin)
-- gerente@hospital.local    -> gerente123    (Nível: gerente)
-- usuario@hospital.local    -> usuario123    (Nível: usuario)
-- financeiro@hospital.local -> financeiro123 (Nível: financeiro)
-- suporte@hospital.local    -> suporte123    (Nível: suporte)
INSERT IGNORE INTO `usuarios` (`id_usuario`, `nome`, `email`, `senha_hash`, `status`, `primeiro_acesso`) VALUES
(1, 'Administrador Master', 'admin@hospital.local', '$2y$12$mHCg.KJrd72V1YTb2khXTODFjLX2utPXwemKozrnXDPN5fd6CecAO', 1, 0),
(20, 'Gerente de Operações', 'gerente@hospital.local', '$2y$12$fl3Onp5AwlrGIo/kqGFm6OXIyCB84NeQjPQ9cdvJ8cw2ybkRu.V12', 1, 0),
(21, 'Usuário Padrão', 'usuario@hospital.local', '$2y$12$.uDw0otVRL5/f20hEdBGIOg4QgIXDrIUDCdsP3gmCbDxOcDDU4mBK', 1, 0),
(22, 'Analista Financeiro', 'financeiro@hospital.local', '$2y$12$41K6E2NDJ3tcnqAiBR89SuNuTP/dhrS8.6h7PowWGVMMZWYUySvtm', 1, 0),
(23, 'Especialista de Suporte', 'suporte@hospital.local', '$2y$12$GAqfBYh1NWaQw8S13GaOzeXZaGvZjj7eLGhKV2m9EaedZDYiKukV6', 1, 0);

-- 2.5 Associação dos Usuários Iniciais aos seus Grupos
INSERT IGNORE INTO `usuarios_grupos` (`id_usuario`, `id_grupo`) VALUES
(1, 1),  -- Admin no grupo admin
(20, 3), -- Gerente no grupo gerente
(21, 2), -- Usuário no grupo usuario
(22, 4), -- Financeiro no grupo financeiro
(23, 5); -- Suporte no grupo suporte

-- =============================================================================
-- 3. MÓDULO SUPORTE / HELPDESK (CHAMADOS)
-- =============================================================================

CREATE TABLE IF NOT EXISTS `chamados` (
    `id_chamado` INT AUTO_INCREMENT PRIMARY KEY,
    `id_usuario` INT NOT NULL,
    `titulo` VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `descricao` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `status` ENUM('aberto', 'em_andamento', 'fechado') NOT NULL DEFAULT 'aberto',
    `aberto_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT `fk_chamados_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `chamados` (`id_chamado`, `id_usuario`, `titulo`, `descricao`, `status`) VALUES
(1, 21, 'Dúvida sobre upload de arquivo PDF', 'Gostaria de saber qual o limite de tamanho para upload de comprovantes.', 'aberto'),
(2, 22, 'Integração bancária com Gateway', 'Solicitação de liberação de porta para webhook financeiro.', 'em_andamento'),
(3, 20, 'Configuração de novo grupo de usuários', 'Necessário liberar permissão de acesso ao módulo de relatórios para novos coordenadores.', 'fechado');

-- =============================================================================
-- 4. RECUPERACAO DE SENHA (TOKEN DE USO UNICO)
-- Guarda apenas o SHA-256 do token enviado por e-mail; o token em claro nunca
-- e persistido. Expiracao e consumo sao controlados pelo relogio do banco (NOW()).
-- =============================================================================

CREATE TABLE IF NOT EXISTS `recuperacoes_senha` (
    `id_recuperacao` INT AUTO_INCREMENT PRIMARY KEY,
    `id_usuario` INT NOT NULL,
    `token_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE COMMENT 'SHA-256 hex do token',
    `expira_em` DATETIME NOT NULL,
    `usado_em` DATETIME DEFAULT NULL COMMENT 'Preenchido quando o token e consumido ou invalidado',
    `ip_solicitante` VARCHAR(45) CHARACTER SET ascii COLLATE ascii_general_ci DEFAULT NULL,
    `criado_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_recuperacao_usuario` (`id_usuario`, `usado_em`),
    INDEX `idx_recuperacao_expiracao` (`expira_em`),
    CONSTRAINT `fk_recuperacao_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Controle das migracoes aplicadas (WEB-INF/etc/migrations); ver database.Migracoes
CREATE TABLE IF NOT EXISTS `schema_migracoes` (
    `versao` VARCHAR(20) NOT NULL PRIMARY KEY,
    `arquivo` VARCHAR(200) NOT NULL,
    `checksum` CHAR(64) NOT NULL,
    `aplicada_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- 5. DOMINIO HOSPITALAR (FASE 3 - MODELAGEM)
-- Convencoes:
--  - snake_case; PK "id_<entidade>"; datas "<evento>_em"; autoria "<acao>_por".
--  - Dados clinicos nunca sao apagados: FKs clinicas usam ON DELETE RESTRICT e
--    triggers bloqueiam UPDATE/DELETE onde o registro e imutavel.
--  - Exclusao logica por "ativo" (cadastros) ou "status" (consultas, receitas).
-- =============================================================================

-- 5.1 Seguranca de login (tentativas e bloqueio temporario - RN-AUT-02)
-- Tabela separada (1:1) para nao exigir ALTER TABLE em "usuarios": o
-- DatabaseProvisioner so cria tabelas ausentes, nao migra colunas.
CREATE TABLE IF NOT EXISTS `usuarios_seguranca` (
    `id_usuario` INT NOT NULL PRIMARY KEY,
    `falhas_consecutivas` SMALLINT NOT NULL DEFAULT 0,
    `bloqueado_ate` DATETIME DEFAULT NULL,
    `ultima_falha_em` DATETIME DEFAULT NULL,
    `ultimo_login_em` DATETIME DEFAULT NULL,
    CONSTRAINT `fk_useg_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE,
    CONSTRAINT `chk_useg_falhas` CHECK (`falhas_consecutivas` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5.2 Pacientes (RN-PAC-01: CPF obrigatorio e unico; RN-PAC-05: exclusao logica)
CREATE TABLE IF NOT EXISTS `pacientes` (
    `id_paciente` INT AUTO_INCREMENT PRIMARY KEY,
    `nome` VARCHAR(150) NOT NULL,
    `cpf` CHAR(11) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Somente digitos',
    `data_nascimento` DATE NOT NULL,
    `sexo` ENUM('feminino', 'masculino', 'outro', 'nao_informado') DEFAULT NULL,
    `telefone` VARCHAR(20) DEFAULT NULL,
    `email` VARCHAR(150) DEFAULT NULL,
    `cep` CHAR(8) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    `logradouro` VARCHAR(150) DEFAULT NULL,
    `numero` VARCHAR(10) DEFAULT NULL,
    `complemento` VARCHAR(60) DEFAULT NULL,
    `bairro` VARCHAR(80) DEFAULT NULL,
    `cidade` VARCHAR(80) DEFAULT NULL,
    `uf` CHAR(2) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    `contato_emergencia_nome` VARCHAR(150) DEFAULT NULL,
    `contato_emergencia_telefone` VARCHAR(20) DEFAULT NULL,
    `contato_emergencia_parentesco` VARCHAR(40) DEFAULT NULL,
    `ativo` TINYINT NOT NULL DEFAULT 1,
    `criado_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `criado_por` INT DEFAULT NULL,
    `atualizado_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `atualizado_por` INT DEFAULT NULL,
    UNIQUE KEY `uk_paciente_cpf` (`cpf`),
    INDEX `idx_paciente_nome` (`nome`),
    INDEX `idx_paciente_ativo` (`ativo`),
    CONSTRAINT `fk_paciente_criado_por` FOREIGN KEY (`criado_por`) REFERENCES `usuarios` (`id_usuario`) ON DELETE SET NULL,
    CONSTRAINT `fk_paciente_atualizado_por` FOREIGN KEY (`atualizado_por`) REFERENCES `usuarios` (`id_usuario`) ON DELETE SET NULL,
    CONSTRAINT `chk_paciente_cpf` CHECK (`cpf` REGEXP '^[0-9]{11}$'),
    CONSTRAINT `chk_paciente_ativo` CHECK (`ativo` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5.3 Alergias no nivel do paciente (valem para todos os atendimentos; evitam repetir
-- a informacao em cada prontuario). Inativadas, nunca apagadas.
CREATE TABLE IF NOT EXISTS `paciente_alergias` (
    `id_alergia` INT AUTO_INCREMENT PRIMARY KEY,
    `id_paciente` INT NOT NULL,
    `substancia` VARCHAR(150) NOT NULL,
    `reacao` VARCHAR(255) DEFAULT NULL,
    `gravidade` ENUM('leve', 'moderada', 'grave') DEFAULT NULL,
    `ativo` TINYINT NOT NULL DEFAULT 1,
    `registrado_por` INT DEFAULT NULL,
    `registrado_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_alergia_paciente` (`id_paciente`, `ativo`),
    CONSTRAINT `fk_alergia_paciente` FOREIGN KEY (`id_paciente`) REFERENCES `pacientes` (`id_paciente`) ON DELETE RESTRICT,
    CONSTRAINT `fk_alergia_registrado_por` FOREIGN KEY (`registrado_por`) REFERENCES `usuarios` (`id_usuario`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5.4 Profissionais (supertipo) e especializacoes medico/enfermeiro
-- RN-PRO-03: um usuario se vincula a no maximo um profissional (UNIQUE id_usuario).
CREATE TABLE IF NOT EXISTS `profissionais` (
    `id_profissional` INT AUTO_INCREMENT PRIMARY KEY,
    `id_usuario` INT DEFAULT NULL,
    `tipo` ENUM('medico', 'enfermeiro', 'funcionario') NOT NULL,
    `nome` VARCHAR(150) NOT NULL,
    `cargo` VARCHAR(80) DEFAULT NULL COMMENT 'Para funcionarios (ex.: Recepcionista)',
    `email` VARCHAR(150) DEFAULT NULL,
    `telefone` VARCHAR(20) DEFAULT NULL,
    `ativo` TINYINT NOT NULL DEFAULT 1,
    `criado_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `criado_por` INT DEFAULT NULL,
    `atualizado_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `atualizado_por` INT DEFAULT NULL,
    UNIQUE KEY `uk_profissional_usuario` (`id_usuario`),
    INDEX `idx_profissional_tipo` (`tipo`, `ativo`),
    INDEX `idx_profissional_nome` (`nome`),
    CONSTRAINT `fk_profissional_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`) ON DELETE SET NULL,
    CONSTRAINT `fk_profissional_criado_por` FOREIGN KEY (`criado_por`) REFERENCES `usuarios` (`id_usuario`) ON DELETE SET NULL,
    CONSTRAINT `fk_profissional_atualizado_por` FOREIGN KEY (`atualizado_por`) REFERENCES `usuarios` (`id_usuario`) ON DELETE SET NULL,
    CONSTRAINT `chk_profissional_ativo` CHECK (`ativo` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- RN-PRO-01: CRM + UF unico
CREATE TABLE IF NOT EXISTS `medicos` (
    `id_profissional` INT NOT NULL PRIMARY KEY,
    `crm` VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `crm_uf` CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `duracao_consulta_min` SMALLINT NOT NULL DEFAULT 30,
    UNIQUE KEY `uk_medico_crm` (`crm`, `crm_uf`),
    CONSTRAINT `fk_medico_profissional` FOREIGN KEY (`id_profissional`) REFERENCES `profissionais` (`id_profissional`) ON DELETE RESTRICT,
    CONSTRAINT `chk_medico_duracao` CHECK (`duracao_consulta_min` BETWEEN 5 AND 240)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- RN-PRO-01: COREN + UF unico
CREATE TABLE IF NOT EXISTS `enfermeiros` (
    `id_profissional` INT NOT NULL PRIMARY KEY,
    `coren` VARCHAR(12) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `coren_uf` CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    UNIQUE KEY `uk_enfermeiro_coren` (`coren`, `coren_uf`),
    CONSTRAINT `fk_enfermeiro_profissional` FOREIGN KEY (`id_profissional`) REFERENCES `profissionais` (`id_profissional`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `especialidades` (
    `id_especialidade` INT AUTO_INCREMENT PRIMARY KEY,
    `nome` VARCHAR(100) NOT NULL,
    `descricao` VARCHAR(255) DEFAULT NULL,
    `ativo` TINYINT NOT NULL DEFAULT 1,
    UNIQUE KEY `uk_especialidade_nome` (`nome`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- N:M medico x especialidade (RN-PRO-02)
CREATE TABLE IF NOT EXISTS `medico_especialidades` (
    `id_profissional` INT NOT NULL,
    `id_especialidade` INT NOT NULL,
    PRIMARY KEY (`id_profissional`, `id_especialidade`),
    INDEX `idx_me_especialidade` (`id_especialidade`),
    CONSTRAINT `fk_me_medico` FOREIGN KEY (`id_profissional`) REFERENCES `medicos` (`id_profissional`) ON DELETE CASCADE,
    CONSTRAINT `fk_me_especialidade` FOREIGN KEY (`id_especialidade`) REFERENCES `especialidades` (`id_especialidade`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Disponibilidade semanal (RF-PRO-06 / RN-AGE-01). dia_semana: 1=segunda ... 7=domingo (ISO-8601).
CREATE TABLE IF NOT EXISTS `disponibilidades_medico` (
    `id_disponibilidade` INT AUTO_INCREMENT PRIMARY KEY,
    `id_profissional` INT NOT NULL,
    `dia_semana` TINYINT NOT NULL,
    `hora_inicio` TIME NOT NULL,
    `hora_fim` TIME NOT NULL,
    `ativo` TINYINT NOT NULL DEFAULT 1,
    INDEX `idx_disp_medico_dia` (`id_profissional`, `dia_semana`, `ativo`),
    CONSTRAINT `fk_disp_medico` FOREIGN KEY (`id_profissional`) REFERENCES `medicos` (`id_profissional`) ON DELETE CASCADE,
    CONSTRAINT `chk_disp_dia` CHECK (`dia_semana` BETWEEN 1 AND 7),
    CONSTRAINT `chk_disp_horario` CHECK (`hora_fim` > `hora_inicio`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5.5 Classificacao de prioridade configuravel (RF-TRI-05/08). Modelo didatico por cores;
-- a escolha e sempre do enfermeiro (RN-TRI-02).
CREATE TABLE IF NOT EXISTS `classificacoes_prioridade` (
    `id_classificacao` TINYINT NOT NULL PRIMARY KEY,
    `codigo` VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `nome` VARCHAR(40) NOT NULL,
    `cor_hex` CHAR(7) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `ordem` TINYINT NOT NULL COMMENT '1 = maior prioridade',
    `tempo_alvo_min` SMALLINT NOT NULL COMMENT 'Tempo-alvo de espera (referencia didatica)',
    `ativo` TINYINT NOT NULL DEFAULT 1,
    UNIQUE KEY `uk_classificacao_codigo` (`codigo`),
    UNIQUE KEY `uk_classificacao_ordem` (`ordem`),
    CONSTRAINT `chk_classificacao_cor` CHECK (`cor_hex` REGEXP '^#[0-9A-Fa-f]{6}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5.6 Consultas (agenda + ciclo do atendimento)
-- RN-AGE-02/03: "slot_ativo" vale 1 para consultas agendadas que ocupam horario e NULL para
-- canceladas e encaixes. Como o UNIQUE ignora NULL, o proprio banco impede duas consultas
-- ativas no mesmo horario do mesmo medico (ou do mesmo paciente), mesmo em requisicoes
-- concorrentes.
CREATE TABLE IF NOT EXISTS `consultas` (
    `id_consulta` INT AUTO_INCREMENT PRIMARY KEY,
    `id_paciente` INT NOT NULL,
    `id_medico` INT NOT NULL,
    `id_especialidade` INT NOT NULL,
    `data_hora` DATETIME NOT NULL,
    `duracao_min` SMALLINT NOT NULL DEFAULT 30,
    `tipo` ENUM('agendada', 'encaixe') NOT NULL DEFAULT 'agendada',
    `status` ENUM('agendada', 'aguardando_triagem', 'em_triagem', 'aguardando_atendimento',
                  'em_atendimento', 'realizada', 'cancelada', 'ausente') NOT NULL DEFAULT 'agendada',
    `slot_ativo` TINYINT AS (IF(`status` = 'cancelada' OR `tipo` = 'encaixe', NULL, 1)) STORED,
    `chegada_em` DATETIME DEFAULT NULL,
    `inicio_atendimento_em` DATETIME DEFAULT NULL,
    `fim_atendimento_em` DATETIME DEFAULT NULL,
    `cancelada_em` DATETIME DEFAULT NULL,
    `cancelada_por` INT DEFAULT NULL,
    `motivo_cancelamento` VARCHAR(255) DEFAULT NULL,
    `remarcada_de` INT DEFAULT NULL COMMENT 'Consulta original quando esta e uma remarcacao (RN-AGE-07)',
    `criado_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `criado_por` INT DEFAULT NULL,
    `atualizado_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `atualizado_por` INT DEFAULT NULL,
    UNIQUE KEY `uk_consulta_slot_medico` (`id_medico`, `data_hora`, `slot_ativo`),
    UNIQUE KEY `uk_consulta_slot_paciente` (`id_paciente`, `data_hora`, `slot_ativo`),
    UNIQUE KEY `uk_consulta_remarcada_de` (`remarcada_de`),
    INDEX `idx_consulta_data_status` (`data_hora`, `status`),
    INDEX `idx_consulta_status` (`status`),
    INDEX `idx_consulta_especialidade` (`id_especialidade`),
    CONSTRAINT `fk_consulta_paciente` FOREIGN KEY (`id_paciente`) REFERENCES `pacientes` (`id_paciente`) ON DELETE RESTRICT,
    CONSTRAINT `fk_consulta_medico` FOREIGN KEY (`id_medico`) REFERENCES `medicos` (`id_profissional`) ON DELETE RESTRICT,
    CONSTRAINT `fk_consulta_especialidade` FOREIGN KEY (`id_especialidade`) REFERENCES `especialidades` (`id_especialidade`) ON DELETE RESTRICT,
    CONSTRAINT `fk_consulta_remarcada_de` FOREIGN KEY (`remarcada_de`) REFERENCES `consultas` (`id_consulta`) ON DELETE RESTRICT,
    CONSTRAINT `fk_consulta_cancelada_por` FOREIGN KEY (`cancelada_por`) REFERENCES `usuarios` (`id_usuario`) ON DELETE SET NULL,
    CONSTRAINT `fk_consulta_criado_por` FOREIGN KEY (`criado_por`) REFERENCES `usuarios` (`id_usuario`) ON DELETE SET NULL,
    CONSTRAINT `fk_consulta_atualizado_por` FOREIGN KEY (`atualizado_por`) REFERENCES `usuarios` (`id_usuario`) ON DELETE SET NULL,
    CONSTRAINT `chk_consulta_duracao` CHECK (`duracao_min` BETWEEN 5 AND 240),
    CONSTRAINT `chk_consulta_cancelamento` CHECK (`status` <> 'cancelada' OR `motivo_cancelamento` IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5.7 Triagens (RN-TRI-03: nunca editadas; correcao = nova triagem com retifica_id)
CREATE TABLE IF NOT EXISTS `triagens` (
    `id_triagem` INT AUTO_INCREMENT PRIMARY KEY,
    `id_consulta` INT NOT NULL,
    `id_enfermeiro` INT NOT NULL,
    `pa_sistolica` SMALLINT DEFAULT NULL COMMENT 'mmHg',
    `pa_diastolica` SMALLINT DEFAULT NULL COMMENT 'mmHg',
    `frequencia_cardiaca` SMALLINT DEFAULT NULL COMMENT 'bpm',
    `temperatura` DECIMAL(4,1) DEFAULT NULL COMMENT 'graus Celsius',
    `saturacao_o2` TINYINT DEFAULT NULL COMMENT '%',
    `peso_kg` DECIMAL(5,2) DEFAULT NULL,
    `altura_cm` SMALLINT DEFAULT NULL,
    `queixa_principal` VARCHAR(500) NOT NULL,
    `sintomas` TEXT DEFAULT NULL,
    `observacoes` TEXT DEFAULT NULL,
    `id_classificacao` TINYINT NOT NULL,
    `retifica_id` INT DEFAULT NULL COMMENT 'Triagem anterior que esta corrige',
    `motivo_retificacao` VARCHAR(255) DEFAULT NULL,
    `registrado_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_triagem_retifica` (`retifica_id`),
    INDEX `idx_triagem_consulta` (`id_consulta`),
    INDEX `idx_triagem_enfermeiro` (`id_enfermeiro`, `registrado_em`),
    CONSTRAINT `fk_triagem_consulta` FOREIGN KEY (`id_consulta`) REFERENCES `consultas` (`id_consulta`) ON DELETE RESTRICT,
    CONSTRAINT `fk_triagem_enfermeiro` FOREIGN KEY (`id_enfermeiro`) REFERENCES `enfermeiros` (`id_profissional`) ON DELETE RESTRICT,
    CONSTRAINT `fk_triagem_classificacao` FOREIGN KEY (`id_classificacao`) REFERENCES `classificacoes_prioridade` (`id_classificacao`) ON DELETE RESTRICT,
    CONSTRAINT `fk_triagem_retifica` FOREIGN KEY (`retifica_id`) REFERENCES `triagens` (`id_triagem`) ON DELETE RESTRICT,
    -- RN-TRI-04: faixas de plausibilidade
    CONSTRAINT `chk_triagem_pa` CHECK ((`pa_sistolica` IS NULL OR `pa_sistolica` BETWEEN 50 AND 300)
                                   AND (`pa_diastolica` IS NULL OR `pa_diastolica` BETWEEN 20 AND 200)),
    CONSTRAINT `chk_triagem_fc` CHECK (`frequencia_cardiaca` IS NULL OR `frequencia_cardiaca` BETWEEN 20 AND 250),
    CONSTRAINT `chk_triagem_temp` CHECK (`temperatura` IS NULL OR `temperatura` BETWEEN 30.0 AND 45.0),
    CONSTRAINT `chk_triagem_spo2` CHECK (`saturacao_o2` IS NULL OR `saturacao_o2` BETWEEN 0 AND 100),
    CONSTRAINT `chk_triagem_peso` CHECK (`peso_kg` IS NULL OR `peso_kg` BETWEEN 0.3 AND 400),
    CONSTRAINT `chk_triagem_altura` CHECK (`altura_cm` IS NULL OR `altura_cm` BETWEEN 20 AND 260),
    CONSTRAINT `chk_triagem_retificacao` CHECK (`retifica_id` IS NULL OR `motivo_retificacao` IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5.8 Prontuario: registro do atendimento (1 por consulta). Rascunho editavel so pelo autor;
-- finalizado e imutavel (trigger). Cada alteracao do rascunho grava versao anterior.
CREATE TABLE IF NOT EXISTS `prontuarios` (
    `id_prontuario` INT AUTO_INCREMENT PRIMARY KEY,
    `id_consulta` INT NOT NULL,
    `id_medico` INT NOT NULL COMMENT 'Autor do registro',
    `queixa_principal` TEXT DEFAULT NULL,
    `historia_doenca_atual` TEXT DEFAULT NULL,
    `historico_relevante` TEXT DEFAULT NULL,
    `observacoes` TEXT DEFAULT NULL,
    `hipoteses_diagnosticas` TEXT DEFAULT NULL,
    `conduta` TEXT DEFAULT NULL,
    `status` ENUM('rascunho', 'finalizado') NOT NULL DEFAULT 'rascunho',
    `versao` INT NOT NULL DEFAULT 1,
    `criado_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `atualizado_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `finalizado_em` DATETIME DEFAULT NULL,
    UNIQUE KEY `uk_prontuario_consulta` (`id_consulta`),
    INDEX `idx_prontuario_medico` (`id_medico`, `criado_em`),
    CONSTRAINT `fk_prontuario_consulta` FOREIGN KEY (`id_consulta`) REFERENCES `consultas` (`id_consulta`) ON DELETE RESTRICT,
    CONSTRAINT `fk_prontuario_medico` FOREIGN KEY (`id_medico`) REFERENCES `medicos` (`id_profissional`) ON DELETE RESTRICT,
    CONSTRAINT `chk_prontuario_finalizado` CHECK (`status` = 'rascunho' OR `finalizado_em` IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Historico de versoes do rascunho (RF-PRT-06). Conteudo anterior em JSON.
CREATE TABLE IF NOT EXISTS `prontuario_versoes` (
    `id_versao` INT AUTO_INCREMENT PRIMARY KEY,
    `id_prontuario` INT NOT NULL,
    `versao` INT NOT NULL,
    `conteudo` LONGTEXT NOT NULL COMMENT 'JSON com os campos do prontuario nesta versao',
    `alterado_por` INT DEFAULT NULL,
    `alterado_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_prontuario_versao` (`id_prontuario`, `versao`),
    CONSTRAINT `fk_pv_prontuario` FOREIGN KEY (`id_prontuario`) REFERENCES `prontuarios` (`id_prontuario`) ON DELETE RESTRICT,
    CONSTRAINT `fk_pv_alterado_por` FOREIGN KEY (`alterado_por`) REFERENCES `usuarios` (`id_usuario`) ON DELETE SET NULL,
    CONSTRAINT `chk_pv_json` CHECK (JSON_VALID(`conteudo`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Evolucoes clinicas e adendos (RF-PRT-05): apenas inclusao.
CREATE TABLE IF NOT EXISTS `evolucoes_clinicas` (
    `id_evolucao` INT AUTO_INCREMENT PRIMARY KEY,
    `id_prontuario` INT NOT NULL,
    `id_autor` INT NOT NULL COMMENT 'Profissional que registrou',
    `tipo` ENUM('evolucao', 'adendo') NOT NULL DEFAULT 'evolucao',
    `texto` TEXT NOT NULL,
    `registrado_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_evolucao_prontuario` (`id_prontuario`, `registrado_em`),
    CONSTRAINT `fk_evolucao_prontuario` FOREIGN KEY (`id_prontuario`) REFERENCES `prontuarios` (`id_prontuario`) ON DELETE RESTRICT,
    CONSTRAINT `fk_evolucao_autor` FOREIGN KEY (`id_autor`) REFERENCES `profissionais` (`id_profissional`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5.9 Receitas e itens (RN-REC-01..03): emitida nao e editada; so pode ser cancelada.
CREATE TABLE IF NOT EXISTS `receitas` (
    `id_receita` INT AUTO_INCREMENT PRIMARY KEY,
    `id_consulta` INT NOT NULL,
    `id_medico` INT NOT NULL,
    `orientacoes_gerais` TEXT DEFAULT NULL,
    `status` ENUM('emitida', 'cancelada') NOT NULL DEFAULT 'emitida',
    `emitida_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `cancelada_em` DATETIME DEFAULT NULL,
    `motivo_cancelamento` VARCHAR(255) DEFAULT NULL,
    INDEX `idx_receita_consulta` (`id_consulta`),
    INDEX `idx_receita_medico` (`id_medico`, `emitida_em`),
    CONSTRAINT `fk_receita_consulta` FOREIGN KEY (`id_consulta`) REFERENCES `consultas` (`id_consulta`) ON DELETE RESTRICT,
    CONSTRAINT `fk_receita_medico` FOREIGN KEY (`id_medico`) REFERENCES `medicos` (`id_profissional`) ON DELETE RESTRICT,
    CONSTRAINT `chk_receita_cancelamento` CHECK (`status` = 'emitida' OR (`cancelada_em` IS NOT NULL AND `motivo_cancelamento` IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `receita_itens` (
    `id_item` INT AUTO_INCREMENT PRIMARY KEY,
    `id_receita` INT NOT NULL,
    `ordem` TINYINT NOT NULL,
    `medicamento` VARCHAR(200) NOT NULL,
    `dosagem` VARCHAR(100) NOT NULL,
    `via` VARCHAR(50) DEFAULT NULL,
    `frequencia` VARCHAR(100) NOT NULL,
    `duracao` VARCHAR(100) NOT NULL,
    `orientacoes` VARCHAR(500) DEFAULT NULL,
    UNIQUE KEY `uk_receita_item_ordem` (`id_receita`, `ordem`),
    CONSTRAINT `fk_item_receita` FOREIGN KEY (`id_receita`) REFERENCES `receitas` (`id_receita`) ON DELETE RESTRICT,
    CONSTRAINT `chk_item_ordem` CHECK (`ordem` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5.10 Solicitacoes de exames (RF-EXA-*). Resultados estao fora do escopo.
CREATE TABLE IF NOT EXISTS `exames` (
    `id_exame` INT AUTO_INCREMENT PRIMARY KEY,
    `id_consulta` INT NOT NULL,
    `id_medico` INT NOT NULL,
    `nome_exame` VARCHAR(200) NOT NULL,
    `codigo` VARCHAR(20) DEFAULT NULL COMMENT 'Codigo de referencia opcional (ex.: TUSS)',
    `justificativa` VARCHAR(500) DEFAULT NULL,
    `observacoes` VARCHAR(500) DEFAULT NULL,
    `status` ENUM('solicitado', 'cancelado') NOT NULL DEFAULT 'solicitado',
    `solicitado_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `cancelado_em` DATETIME DEFAULT NULL,
    `motivo_cancelamento` VARCHAR(255) DEFAULT NULL,
    INDEX `idx_exame_consulta` (`id_consulta`),
    CONSTRAINT `fk_exame_consulta` FOREIGN KEY (`id_consulta`) REFERENCES `consultas` (`id_consulta`) ON DELETE RESTRICT,
    CONSTRAINT `fk_exame_medico` FOREIGN KEY (`id_medico`) REFERENCES `medicos` (`id_profissional`) ON DELETE RESTRICT,
    CONSTRAINT `chk_exame_cancelamento` CHECK (`status` = 'solicitado' OR (`cancelado_em` IS NOT NULL AND `motivo_cancelamento` IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5.11 Auditoria (RF-AUD-01): somente inclusao. Sem FK para usuarios, para o registro
-- sobreviver mesmo que o usuario seja removido. "detalhes" nunca leva conteudo clinico.
CREATE TABLE IF NOT EXISTS `auditoria` (
    `id_auditoria` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `id_usuario` INT DEFAULT NULL,
    `acao` VARCHAR(60) NOT NULL COMMENT 'ex.: login.sucesso, prontuario.leitura, consulta.cancelar',
    `entidade` VARCHAR(60) DEFAULT NULL,
    `id_entidade` BIGINT DEFAULT NULL,
    `id_paciente` INT DEFAULT NULL COMMENT 'Paciente afetado, para rastrear acesso a dados de saude',
    `detalhes` VARCHAR(1000) DEFAULT NULL,
    `ip` VARCHAR(45) CHARACTER SET ascii COLLATE ascii_general_ci DEFAULT NULL,
    `registrado_em` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX `idx_auditoria_usuario` (`id_usuario`, `registrado_em`),
    INDEX `idx_auditoria_entidade` (`entidade`, `id_entidade`),
    INDEX `idx_auditoria_paciente` (`id_paciente`, `registrado_em`),
    INDEX `idx_auditoria_acao` (`acao`, `registrado_em`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5.12 Notificacoes (RF-NOT-02). O corpo da mensagem NAO e armazenado (pode conter
-- links de recuperacao ou senhas temporarias).
CREATE TABLE IF NOT EXISTS `notificacoes` (
    `id_notificacao` INT AUTO_INCREMENT PRIMARY KEY,
    `tipo` VARCHAR(40) NOT NULL COMMENT 'ex.: consulta.agendada, senha.recuperacao',
    `canal` ENUM('email', 'sms') NOT NULL DEFAULT 'email',
    `destinatario` VARCHAR(150) NOT NULL,
    `assunto` VARCHAR(200) DEFAULT NULL,
    `id_consulta` INT DEFAULT NULL,
    `id_usuario` INT DEFAULT NULL,
    `status` ENUM('pendente', 'enviada', 'simulada', 'falhou') NOT NULL DEFAULT 'pendente',
    `erro` VARCHAR(500) DEFAULT NULL,
    `criado_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `enviado_em` DATETIME DEFAULT NULL,
    INDEX `idx_notificacao_status` (`status`, `criado_em`),
    INDEX `idx_notificacao_consulta` (`id_consulta`),
    CONSTRAINT `fk_notificacao_consulta` FOREIGN KEY (`id_consulta`) REFERENCES `consultas` (`id_consulta`) ON DELETE SET NULL,
    CONSTRAINT `fk_notificacao_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- 6. TRIGGERS DE IMUTABILIDADE (garantia no banco, alem da regra na aplicacao)
-- Recriados a cada execucao do script (DROP + CREATE) para manter a definicao atual.
-- =============================================================================
DROP TRIGGER IF EXISTS `trg_triagens_sem_update`;
CREATE TRIGGER `trg_triagens_sem_update` BEFORE UPDATE ON `triagens` FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Triagem registrada nao pode ser alterada; registre uma retificacao.';

DROP TRIGGER IF EXISTS `trg_triagens_sem_delete`;
CREATE TRIGGER `trg_triagens_sem_delete` BEFORE DELETE ON `triagens` FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Triagem nao pode ser excluida.';

DROP TRIGGER IF EXISTS `trg_prontuarios_finalizado`;
CREATE TRIGGER `trg_prontuarios_finalizado` BEFORE UPDATE ON `prontuarios` FOR EACH ROW
BEGIN
    IF OLD.`status` = 'finalizado' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Prontuario finalizado nao pode ser alterado; registre um adendo.';
    END IF;
END;

DROP TRIGGER IF EXISTS `trg_prontuarios_sem_delete`;
CREATE TRIGGER `trg_prontuarios_sem_delete` BEFORE DELETE ON `prontuarios` FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Prontuario nao pode ser excluido.';

DROP TRIGGER IF EXISTS `trg_evolucoes_sem_update`;
CREATE TRIGGER `trg_evolucoes_sem_update` BEFORE UPDATE ON `evolucoes_clinicas` FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Evolucao clinica nao pode ser alterada.';

DROP TRIGGER IF EXISTS `trg_evolucoes_sem_delete`;
CREATE TRIGGER `trg_evolucoes_sem_delete` BEFORE DELETE ON `evolucoes_clinicas` FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Evolucao clinica nao pode ser excluida.';

DROP TRIGGER IF EXISTS `trg_receitas_somente_cancelar`;
CREATE TRIGGER `trg_receitas_somente_cancelar` BEFORE UPDATE ON `receitas` FOR EACH ROW
BEGIN
    IF OLD.`status` = 'cancelada'
       OR NOT (NEW.`status` = 'cancelada'
               AND NEW.`id_consulta` = OLD.`id_consulta`
               AND NEW.`id_medico` = OLD.`id_medico`
               AND NEW.`emitida_em` = OLD.`emitida_em`
               AND (NEW.`orientacoes_gerais` <=> OLD.`orientacoes_gerais`)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Receita emitida so pode ser cancelada.';
    END IF;
END;

DROP TRIGGER IF EXISTS `trg_receita_itens_sem_update`;
CREATE TRIGGER `trg_receita_itens_sem_update` BEFORE UPDATE ON `receita_itens` FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Itens de receita nao podem ser alterados.';

DROP TRIGGER IF EXISTS `trg_auditoria_sem_update`;
CREATE TRIGGER `trg_auditoria_sem_update` BEFORE UPDATE ON `auditoria` FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Registro de auditoria nao pode ser alterado.';

DROP TRIGGER IF EXISTS `trg_auditoria_sem_delete`;
CREATE TRIGGER `trg_auditoria_sem_delete` BEFORE DELETE ON `auditoria` FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Registro de auditoria nao pode ser excluido.';

-- =============================================================================
-- 7. CARGA INICIAL DO DOMINIO HOSPITALAR (DADOS FICTICIOS)
-- =============================================================================

-- 7.1 Grupos dos perfis hospitalares
INSERT IGNORE INTO `grupos` (`id_grupo`, `nome`, `descricao`, `is_admin`) VALUES
(6, 'recepcionista', 'Recepcao: pacientes, agenda e chegada (sem dados clinicos)', 0),
(7, 'enfermeiro', 'Enfermagem: fila de triagem, sinais vitais e classificacao de prioridade', 0),
(8, 'medico', 'Medico: atendimento, prontuario, receitas e exames', 0);

-- 7.2 Permissoes do dominio (padrao modulo.acao) - matriz da Fase 2, secao 7
INSERT IGNORE INTO `permissoes` (`id_permissao`, `codigo`, `descricao`) VALUES
(10, 'usuarios.unlock', 'Desbloquear usuarios bloqueados por tentativas de login'),
(11, 'profissionais.manage', 'Cadastrar e manter profissionais'),
(12, 'especialidades.manage', 'Cadastrar e manter especialidades'),
(13, 'agenda.disponibilidade.manage', 'Definir disponibilidade semanal dos medicos'),
(14, 'config.manage', 'Configuracoes gerais (prioridades, duracao de consulta)'),
(15, 'auditoria.view', 'Consultar registros de auditoria'),
(16, 'pacientes.view', 'Consultar cadastro de pacientes'),
(17, 'pacientes.create', 'Cadastrar pacientes'),
(18, 'pacientes.edit', 'Editar cadastro de pacientes'),
(19, 'pacientes.inativar', 'Inativar e reativar pacientes'),
(20, 'agenda.view', 'Ver a agenda de todos os medicos'),
(21, 'agenda.view.propria', 'Ver a propria agenda'),
(22, 'consultas.agendar', 'Agendar consultas'),
(23, 'consultas.cancelar', 'Cancelar consultas'),
(24, 'consultas.remarcar', 'Remarcar consultas'),
(25, 'consultas.chegada', 'Registrar chegada do paciente'),
(26, 'consultas.ausencia', 'Registrar ausencia do paciente'),
(27, 'triagem.fila', 'Ver a fila de triagem'),
(28, 'triagem.registrar', 'Registrar e retificar triagens'),
(29, 'triagem.view', 'Ver triagens (com vinculo)'),
(30, 'atendimento.fila', 'Ver a fila de atendimento medico'),
(31, 'atendimento.iniciar', 'Iniciar atendimento'),
(32, 'atendimento.finalizar', 'Finalizar atendimento'),
(33, 'prontuario.view', 'Ler prontuario (com vinculo)'),
(34, 'prontuario.registrar', 'Registrar prontuario e evolucoes'),
(35, 'alergias.view', 'Ver alergias do paciente (com vinculo)'),
(36, 'receitas.emitir', 'Emitir e cancelar receitas'),
(37, 'receitas.view', 'Ver receitas (com vinculo)'),
(38, 'exames.solicitar', 'Solicitar e cancelar exames'),
(39, 'exames.view', 'Ver solicitacoes de exames (com vinculo)'),
(40, 'historico.view', 'Ver historico do paciente (filtrado por perfil)'),
(41, 'dashboard.admin', 'Painel do administrador'),
(42, 'dashboard.recepcao', 'Painel da recepcao'),
(43, 'dashboard.enfermagem', 'Painel da enfermagem'),
(44, 'dashboard.medico', 'Painel do medico');

-- Administrador: governanca (sem permissoes clinicas - decisao P1)
INSERT IGNORE INTO `grupos_permissoes` (`id_grupo`, `id_permissao`) VALUES
(1, 10), (1, 11), (1, 12), (1, 13), (1, 14), (1, 15), (1, 16), (1, 19), (1, 20), (1, 41);

-- Recepcionista
INSERT IGNORE INTO `grupos_permissoes` (`id_grupo`, `id_permissao`) VALUES
(6, 16), (6, 17), (6, 18), (6, 19), (6, 20), (6, 22), (6, 23), (6, 24), (6, 25), (6, 26), (6, 40), (6, 42);

-- Enfermeiro
INSERT IGNORE INTO `grupos_permissoes` (`id_grupo`, `id_permissao`) VALUES
(7, 16), (7, 20), (7, 27), (7, 28), (7, 29), (7, 35), (7, 40), (7, 43);

-- Medico
INSERT IGNORE INTO `grupos_permissoes` (`id_grupo`, `id_permissao`) VALUES
(8, 16), (8, 21), (8, 29), (8, 30), (8, 31), (8, 32), (8, 33), (8, 34), (8, 35), (8, 36), (8, 37), (8, 38), (8, 39), (8, 40), (8, 44);

-- 7.3 Classificacao de prioridade (modelo didatico de cores)
INSERT IGNORE INTO `classificacoes_prioridade` (`id_classificacao`, `codigo`, `nome`, `cor_hex`, `ordem`, `tempo_alvo_min`) VALUES
(1, 'vermelho', 'Emergencia', '#DC3545', 1, 0),
(2, 'laranja', 'Muito urgente', '#FD7E14', 2, 10),
(3, 'amarelo', 'Urgente', '#FFC107', 3, 60),
(4, 'verde', 'Pouco urgente', '#198754', 4, 120),
(5, 'azul', 'Nao urgente', '#0D6EFD', 5, 240);

-- 7.4 Especialidades
INSERT IGNORE INTO `especialidades` (`id_especialidade`, `nome`, `descricao`) VALUES
(1, 'Clinica Geral', 'Atendimento clinico geral'),
(2, 'Cardiologia', 'Doencas do coracao e do sistema circulatorio'),
(3, 'Pediatria', 'Atendimento a criancas e adolescentes'),
(4, 'Ortopedia', 'Sistema musculoesqueletico'),
(5, 'Dermatologia', 'Doencas da pele');

-- 7.5 Usuarios ficticios dos perfis hospitalares (senhas de demonstracao):
-- recepcao@hospital.local   -> recepcao123
-- enfermeiro@hospital.local -> enfermeiro123
-- medico@hospital.local     -> medico123
INSERT IGNORE INTO `usuarios` (`id_usuario`, `nome`, `email`, `senha_hash`, `status`, `primeiro_acesso`) VALUES
(30, 'Renata Recepcao (ficticia)', 'recepcao@hospital.local', '$2y$12$dU6opFCSw5plfiM86XTfouWFmvHuI5aF/0rWLckEi5nH0NPkoQzpq', 1, 0),
(31, 'Eduardo Enfermeiro (ficticio)', 'enfermeiro@hospital.local', '$2y$12$s7uNetcaOe1oj0PYncJfyuX5CM/30DJ59Vyz/uqVjMGcaBX9bWyJu', 1, 0),
(32, 'Marina Medica (ficticia)', 'medico@hospital.local', '$2y$12$ubXYBMqotNZbf9d040k0DeUnmg/rBunPSKKihau9SwIL.bpL.ETka', 1, 0);

INSERT IGNORE INTO `usuarios_grupos` (`id_usuario`, `id_grupo`) VALUES
(30, 6), (31, 7), (32, 8);

INSERT IGNORE INTO `profissionais` (`id_profissional`, `id_usuario`, `tipo`, `nome`, `cargo`, `email`) VALUES
(1, 30, 'funcionario', 'Renata Recepcao (ficticia)', 'Recepcionista', 'recepcao@hospital.local'),
(2, 31, 'enfermeiro', 'Eduardo Enfermeiro (ficticio)', NULL, 'enfermeiro@hospital.local'),
(3, 32, 'medico', 'Marina Medica (ficticia)', NULL, 'medico@hospital.local');

INSERT IGNORE INTO `enfermeiros` (`id_profissional`, `coren`, `coren_uf`) VALUES (2, '000001', 'SP');
INSERT IGNORE INTO `medicos` (`id_profissional`, `crm`, `crm_uf`, `duracao_consulta_min`) VALUES (3, '000001', 'SP', 30);
INSERT IGNORE INTO `medico_especialidades` (`id_profissional`, `id_especialidade`) VALUES (3, 1), (3, 2);
INSERT IGNORE INTO `disponibilidades_medico` (`id_disponibilidade`, `id_profissional`, `dia_semana`, `hora_inicio`, `hora_fim`) VALUES
(1, 3, 1, '08:00:00', '12:00:00'),
(2, 3, 3, '08:00:00', '12:00:00'),
(3, 3, 5, '13:00:00', '17:00:00');

-- 7.6 Pacientes ficticios (CPFs validos gerados, sem correspondencia com pessoas reais)
INSERT IGNORE INTO `pacientes` (`id_paciente`, `nome`, `cpf`, `data_nascimento`, `sexo`, `telefone`, `email`, `cidade`, `uf`) VALUES
(1, 'Ana Paciente Ficticia', '12345678909', '1985-03-14', 'feminino', '(11) 90000-0001', 'ana.ficticia@exemplo.test', 'Sao Paulo', 'SP'),
(2, 'Bruno Paciente Ficticio', '98765432100', '1972-11-02', 'masculino', '(11) 90000-0002', NULL, 'Sao Paulo', 'SP'),
(3, 'Carla Paciente Ficticia', '11144477735', '2015-07-21', 'feminino', NULL, 'responsavel.carla@exemplo.test', 'Campinas', 'SP');
