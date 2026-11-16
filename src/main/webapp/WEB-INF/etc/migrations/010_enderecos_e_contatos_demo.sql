-- 010: demo patients get full addresses, emergency contacts and e-mails without the "ficticia" marker.

-- Demo e-mails without the "ficticia" marker
UPDATE `pacientes` SET `email` = REPLACE(REPLACE(`email`, '.ficticia@', '.souza@'), '.ficticio@', '.souza@') WHERE `id_paciente` = 1;
UPDATE `pacientes` SET `email` = REPLACE(`email`, '.ficticia@', '.moura@') WHERE `id_paciente` = 5;
UPDATE `pacientes` SET `email` = REPLACE(`email`, '.ficticia@', '.nunes@') WHERE `id_paciente` = 7;

-- Full address and emergency contact for the demo patients
UPDATE `pacientes` SET `cidade` = 'São Paulo' WHERE `cidade` = 'Sao Paulo';
UPDATE `pacientes` SET
    `cep` = LPAD(1000000 + (`id_paciente` * 7331 % 8999999), 8, '0'),
    `logradouro` = ELT(1 + (`id_paciente` % 10), 'Rua Vergueiro', 'Avenida Paulista', 'Rua Augusta', 'Rua Domingos de Morais',
        'Avenida Brigadeiro Luís Antônio', 'Rua Teodoro Sampaio', 'Rua dos Pinheiros', 'Avenida Rebouças', 'Rua Haddock Lobo', 'Rua Oscar Freire'),
    `numero` = CAST(10 + (`id_paciente` * 113 % 1900) AS CHAR),
    `complemento` = IF(`id_paciente` % 3 = 0, CONCAT('Apto ', 10 + (`id_paciente` % 9) * 10 + 1), NULL),
    `bairro` = ELT(1 + (`id_paciente` % 8), 'Vila Mariana', 'Bela Vista', 'Consolação', 'Pinheiros', 'Jardim Paulista', 'Moema', 'Perdizes', 'Centro')
 WHERE `logradouro` IS NULL AND `id_paciente` <= 1000;
UPDATE `pacientes` SET
    `contato_emergencia_nome` = ELT(1 + (`id_paciente` % 8), 'Maria Aparecida Silva', 'José Carlos Oliveira', 'Fernanda Lima', 'Ricardo Souza',
        'Juliana Costa', 'Antônio Pereira', 'Patrícia Alves', 'Marcos Ribeiro'),
    `contato_emergencia_parentesco` = ELT(1 + (`id_paciente` % 6), 'Mãe', 'Pai', 'Cônjuge', 'Filho(a)', 'Irmão(ã)', 'Amigo(a)'),
    `contato_emergencia_telefone` = CONCAT('(11) 9', LPAD(1000 + (`id_paciente` * 37 % 8999), 4, '0'), '-', LPAD(`id_paciente` * 53 % 10000, 4, '0'))
 WHERE `contato_emergencia_nome` IS NULL AND `id_paciente` <= 1000;
