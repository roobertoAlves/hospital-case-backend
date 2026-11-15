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
