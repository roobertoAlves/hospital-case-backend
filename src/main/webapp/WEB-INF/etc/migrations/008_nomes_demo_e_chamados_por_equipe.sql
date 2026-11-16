-- 008: support tickets get a target team and a subject; demo people get realistic names
-- (no "(fictícia)" markers: everyone knows the data is a demo).

ALTER TABLE `chamados`
    ADD COLUMN IF NOT EXISTS `equipe` VARCHAR(30) NOT NULL DEFAULT 'ti' AFTER `descricao`,
    ADD COLUMN IF NOT EXISTS `assunto` VARCHAR(40) NOT NULL DEFAULT 'outro' AFTER `equipe`;

UPDATE `chamados` SET `equipe` = 'ti', `assunto` = 'acesso' WHERE `id_chamado` = 1;
UPDATE `chamados` SET `equipe` = 'produtos', `assunto` = 'erro' WHERE `id_chamado` = 2;
UPDATE `chamados` SET `equipe` = 'infraestrutura', `assunto` = 'manutencao' WHERE `id_chamado` = 3;

-- Staff and example users: person names instead of role names
UPDATE `usuarios` SET `nome` = 'Roberta Lima' WHERE `id_usuario` = 1 AND `nome` = 'Administrador Master';
UPDATE `usuarios` SET `nome` = 'Cláudia Ferreira' WHERE `id_usuario` = 20 AND `nome` = 'Gerente de Operações';
UPDATE `usuarios` SET `nome` = 'Lucas Almeida' WHERE `id_usuario` = 21 AND `nome` = 'Usuário Padrão';
UPDATE `usuarios` SET `nome` = 'Fernanda Rocha' WHERE `id_usuario` = 22 AND `nome` = 'Analista Financeiro';
UPDATE `usuarios` SET `nome` = 'Diego Martins' WHERE `id_usuario` = 23 AND `nome` = 'Especialista de Suporte';
UPDATE `usuarios` SET `nome` = 'Renata Campos' WHERE `id_usuario` = 30;
UPDATE `usuarios` SET `nome` = 'Eduardo Lima' WHERE `id_usuario` = 31;
UPDATE `usuarios` SET `nome` = 'Marina Duarte' WHERE `id_usuario` = 32;
UPDATE `usuarios` SET `nome` = 'Carla Menezes' WHERE `id_usuario` = 50;
UPDATE `usuarios` SET `nome` = 'Rui Neves' WHERE `id_usuario` = 51;
UPDATE `usuarios` SET `nome` = 'Sofia Ribeiro' WHERE `id_usuario` = 52;
UPDATE `usuarios` SET `nome` = 'Tiago Barros' WHERE `id_usuario` = 53;
UPDATE `usuarios` SET `nome` = 'Ana Beatriz Souza' WHERE `id_usuario` = 60;

UPDATE `profissionais` SET `nome` = 'Renata Campos' WHERE `id_profissional` = 1;
UPDATE `profissionais` SET `nome` = 'Eduardo Lima' WHERE `id_profissional` = 2;
UPDATE `profissionais` SET `nome` = 'Marina Duarte' WHERE `id_profissional` = 3;
UPDATE `profissionais` SET `nome` = 'Paulo Cardoso' WHERE `id_profissional` = 4;
UPDATE `profissionais` SET `nome` = 'Lúcia Andrade' WHERE `id_profissional` = 5;
UPDATE `profissionais` SET `nome` = 'Beatriz Moura' WHERE `id_profissional` = 22;
UPDATE `profissionais` SET `nome` = 'Carla Menezes' WHERE `id_profissional` = 101;
UPDATE `profissionais` SET `nome` = 'Rui Neves' WHERE `id_profissional` = 102;
UPDATE `profissionais` SET `nome` = 'Beatriz Gaspar' WHERE `id_profissional` = 103;
UPDATE `profissionais` SET `nome` = 'Helena Prado' WHERE `id_profissional` = 104;
UPDATE `profissionais` SET `nome` = 'Otávio Vasconcelos' WHERE `id_profissional` = 105;
UPDATE `profissionais` SET `nome` = 'Paula Mendonça' WHERE `id_profissional` = 106;
UPDATE `profissionais` SET `nome` = 'Sofia Ribeiro' WHERE `id_profissional` = 107;
UPDATE `profissionais` SET `nome` = 'Tiago Barros' WHERE `id_profissional` = 108;
UPDATE `profissionais` SET `nome` = 'André Teles' WHERE `id_profissional` = 201;
UPDATE `profissionais` SET `nome` = 'Bianca Rocha' WHERE `id_profissional` = 202;
UPDATE `profissionais` SET `nome` = 'Caio Pimentel' WHERE `id_profissional` = 203;
UPDATE `profissionais` SET `nome` = 'Diana Reis' WHERE `id_profissional` = 204;
UPDATE `profissionais` SET `nome` = 'Elias Neto' WHERE `id_profissional` = 205;
UPDATE `profissionais` SET `nome` = 'Fátima Gomes' WHERE `id_profissional` = 206;
UPDATE `profissionais` SET `nome` = 'Gisele Martins' WHERE `id_profissional` = 207;
UPDATE `profissionais` SET `nome` = 'Hugo Ferraz' WHERE `id_profissional` = 208;
UPDATE `profissionais` SET `nome` = 'Íris Albuquerque' WHERE `id_profissional` = 209;
UPDATE `profissionais` SET `nome` = 'Jorge Nunes' WHERE `id_profissional` = 210;
UPDATE `profissionais` SET `nome` = 'Karen Esteves' WHERE `id_profissional` = 211;
UPDATE `profissionais` SET `nome` = 'Léo Dantas' WHERE `id_profissional` = 212;
UPDATE `profissionais` SET `nome` = 'Mara Peixoto' WHERE `id_profissional` = 213;
UPDATE `profissionais` SET `nome` = 'Nilo Ortiz' WHERE `id_profissional` = 214;

-- Demo patients: drop the " Fictícia" / " Fictício" suffix
UPDATE `pacientes` SET `nome` = 'Ana Beatriz Souza' WHERE `id_paciente` = 1 AND `nome` LIKE 'Ana Paciente%';
UPDATE `pacientes` SET `nome` = TRIM(REPLACE(REPLACE(`nome`, ' Fictícia', ''), ' Fictício', ''))
 WHERE `nome` LIKE '% Fictícia' OR `nome` LIKE '% Fictício';

-- Clinical demo texts live in immutable tables: the triggers are dropped only for this data fix
-- and recreated right after, exactly as before.
DROP TRIGGER IF EXISTS `trg_prontuarios_finalizado`;
DROP TRIGGER IF EXISTS `trg_receita_itens_sem_update`;
DROP TRIGGER IF EXISTS `trg_vacinas_aplicadas_sem_update`;

UPDATE `prontuarios` SET `historia_doenca_atual` = REPLACE(`historia_doenca_atual`, ' (texto ficticio de demonstracao)', '')
 WHERE `historia_doenca_atual` LIKE '%(texto ficticio de demonstracao)%';
UPDATE `receita_itens` SET `medicamento` = REPLACE(`medicamento`, ' (ficticio)', '') WHERE `medicamento` LIKE '% (ficticio)';
UPDATE `vacinas_aplicadas` SET `fabricante` = 'Instituto Butantan' WHERE `fabricante` = 'Fabricante ficticio';

CREATE TRIGGER `trg_prontuarios_finalizado` BEFORE UPDATE ON `prontuarios` FOR EACH ROW
BEGIN
    IF OLD.`status` = 'finalizado' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Prontuario finalizado nao pode ser alterado; registre um adendo.';
    END IF;
END;

CREATE TRIGGER `trg_receita_itens_sem_update` BEFORE UPDATE ON `receita_itens` FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Itens de receita nao podem ser alterados.';

CREATE TRIGGER `trg_vacinas_aplicadas_sem_update` BEFORE UPDATE ON `vacinas_aplicadas` FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Registro de vacina aplicada nao pode ser alterado.';
