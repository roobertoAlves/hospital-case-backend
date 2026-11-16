-- =============================================================================
-- 006 - Catalogo ampliado (exames por categoria, check-ups, vacinas e especialidades)
--
-- Estrutura de categorias inspirada nos catalogos de grandes laboratorios (analises
-- clinicas, imagem, cardiologia, genetica, check-up, vacinas). Nomes de exames e vacinas
-- sao de uso corrente; descricoes e preparos foram escritos para este projeto e sao
-- DIDATICOS: nao substituem a orientacao do laboratorio ou do medico.
-- =============================================================================

ALTER TABLE `tipos_exame`
    ADD COLUMN `categoria` VARCHAR(40) NOT NULL DEFAULT 'Analises clinicas',
    ADD COLUMN `descricao` VARCHAR(300) DEFAULT NULL;
ALTER TABLE `tipos_exame` ADD INDEX `idx_tipo_exame_categoria` (`categoria`, `nome`);
ALTER TABLE `vacinas` ADD COLUMN `faixa` VARCHAR(40) DEFAULT NULL COMMENT 'Infantil, Adolescente, Adulto, Idoso, Gestante, Viajante';

INSERT IGNORE INTO `locais_exame` (`id_local`, `nome`, `hora_inicio`, `hora_fim`, `atende_sabado`) VALUES
(4, 'Ressonancia e tomografia', '07:00:00', '19:00:00', 1),
(5, 'Mama e densitometria', '08:00:00', '17:00:00', 1),
(6, 'Oftalmologia - sala de exames', '08:00:00', '17:00:00', 0),
(7, 'Audiologia', '08:00:00', '16:00:00', 0),
(8, 'Funcao pulmonar', '08:00:00', '12:00:00', 0),
(9, 'Ultrassonografia - sala 2', '07:00:00', '19:00:00', 1);

UPDATE `tipos_exame` SET `categoria` = 'Analises clinicas' WHERE `id_tipo_exame` IN (1, 2, 3);
UPDATE `tipos_exame` SET `categoria` = 'Imagem' WHERE `id_tipo_exame` IN (4, 5);
UPDATE `tipos_exame` SET `categoria` = 'Cardiologia' WHERE `id_tipo_exame` IN (6, 7);
UPDATE `tipos_exame` SET `descricao` = 'Contagem de celulas do sangue: globulos vermelhos, brancos e plaquetas.' WHERE `id_tipo_exame` = 1;
UPDATE `tipos_exame` SET `descricao` = 'Nivel de acucar no sangue em jejum.' WHERE `id_tipo_exame` = 2;
UPDATE `tipos_exame` SET `descricao` = 'Colesterol total e fracoes (HDL, LDL) e triglicerides.' WHERE `id_tipo_exame` = 3;
UPDATE `tipos_exame` SET `descricao` = 'Imagem dos pulmoes, coracao e ossos do torax.' WHERE `id_tipo_exame` = 4;
UPDATE `tipos_exame` SET `descricao` = 'Avalia figado, vesicula, pancreas, rins e baco.' WHERE `id_tipo_exame` = 5;
UPDATE `tipos_exame` SET `descricao` = 'Registro da atividade eletrica do coracao em repouso.' WHERE `id_tipo_exame` = 6;
UPDATE `tipos_exame` SET `descricao` = 'Eletrocardiograma durante esforco na esteira.' WHERE `id_tipo_exame` = 7;

INSERT IGNORE INTO `tipos_exame` (`id_tipo_exame`, `nome`, `duracao_min`, `preparo`, `id_local`, `exige_pedido`, `categoria`, `descricao`) VALUES
(101, 'Hemoglobina glicada (HbA1c)', 10, 'Jejum nao obrigatorio.', 1, 0, 'Analises clinicas', 'Media do acucar no sangue dos ultimos 3 meses.'),
(102, 'Glicose pos-prandial', 10, 'Coleta 2 horas apos a refeicao habitual.', 1, 0, 'Analises clinicas', 'Acucar no sangue depois de comer.'),
(103, 'Curva glicemica (TOTG)', 120, 'Jejum de 8 horas. Agua liberada. Permanecer na unidade durante o exame (cerca de 2 horas).', 1, 1, 'Analises clinicas', 'Teste de tolerancia a glicose com coletas seriadas.'),
(104, 'Insulina', 10, 'Jejum de 8 horas. Agua liberada.', 1, 1, 'Analises clinicas', 'Dosagem do hormonio insulina.'),
(105, 'TSH', 10, 'Jejum nao obrigatorio.', 1, 0, 'Analises clinicas', 'Hormonio que regula a tireoide.'),
(106, 'T4 livre', 10, 'Jejum nao obrigatorio.', 1, 0, 'Analises clinicas', 'Hormonio da tireoide na forma livre.'),
(107, 'T3 total', 10, 'Jejum nao obrigatorio.', 1, 1, 'Analises clinicas', 'Hormonio da tireoide.'),
(108, 'Anti-TPO', 10, 'Jejum nao obrigatorio.', 1, 1, 'Analises clinicas', 'Anticorpos contra a tireoide.'),
(109, 'Creatinina', 10, 'Jejum nao obrigatorio.', 1, 0, 'Analises clinicas', 'Avalia a funcao dos rins.'),
(110, 'Ureia', 10, 'Jejum nao obrigatorio.', 1, 0, 'Analises clinicas', 'Avalia a funcao dos rins.'),
(111, 'TGO (AST)', 10, 'Jejum nao obrigatorio.', 1, 0, 'Analises clinicas', 'Enzima do figado e dos musculos.'),
(112, 'TGP (ALT)', 10, 'Jejum nao obrigatorio.', 1, 0, 'Analises clinicas', 'Enzima do figado.'),
(113, 'Gama GT', 10, 'Jejum de 4 horas.', 1, 0, 'Analises clinicas', 'Enzima do figado e das vias biliares.'),
(114, 'Bilirrubinas', 10, 'Jejum de 4 horas.', 1, 1, 'Analises clinicas', 'Avalia figado e vias biliares.'),
(115, 'Acido urico', 10, 'Jejum de 4 horas.', 1, 0, 'Analises clinicas', 'Relacionado a gota e a funcao renal.'),
(116, 'Sodio e potassio', 10, 'Jejum nao obrigatorio.', 1, 1, 'Analises clinicas', 'Eletrolitos do sangue.'),
(117, 'Calcio', 10, 'Jejum nao obrigatorio.', 1, 1, 'Analises clinicas', 'Calcio no sangue.'),
(118, 'Magnesio', 10, 'Jejum nao obrigatorio.', 1, 1, 'Analises clinicas', 'Magnesio no sangue.'),
(119, 'Vitamina D (25-OH)', 10, 'Jejum nao obrigatorio.', 1, 0, 'Analises clinicas', 'Reserva de vitamina D do organismo.'),
(120, 'Vitamina B12', 10, 'Jejum de 4 horas.', 1, 0, 'Analises clinicas', 'Importante para nervos e formacao do sangue.'),
(121, 'Vitamina B2', 10, 'Jejum de 4 horas.', 1, 1, 'Analises clinicas', 'Riboflavina.'),
(122, 'Vitamina C', 10, 'Jejum de 4 horas.', 1, 1, 'Analises clinicas', 'Acido ascorbico no sangue.'),
(123, 'Acido folico', 10, 'Jejum de 4 horas.', 1, 0, 'Analises clinicas', 'Folato no sangue.'),
(124, 'Ferritina', 10, 'Jejum nao obrigatorio.', 1, 0, 'Analises clinicas', 'Estoque de ferro do organismo.'),
(125, 'Ferro serico', 10, 'Jejum de 4 horas.', 1, 0, 'Analises clinicas', 'Ferro circulante no sangue.'),
(126, 'PCR (proteina C reativa)', 10, 'Jejum nao obrigatorio.', 1, 0, 'Analises clinicas', 'Marcador de inflamacao.'),
(127, 'VHS', 10, 'Jejum nao obrigatorio.', 1, 0, 'Analises clinicas', 'Velocidade de hemossedimentacao, marcador de inflamacao.'),
(128, 'Coagulograma', 10, 'Jejum nao obrigatorio.', 1, 1, 'Analises clinicas', 'Avalia a coagulacao do sangue.'),
(129, 'Tempo de protrombina (TP/INR)', 10, 'Jejum nao obrigatorio.', 1, 1, 'Analises clinicas', 'Controle de anticoagulantes e da coagulacao.'),
(130, 'PSA total', 10, 'Evitar relacao sexual e exercicio de bicicleta nas 48 horas anteriores.', 1, 1, 'Analises clinicas', 'Marcador da prostata.'),
(131, 'Beta-hCG quantitativo', 10, 'Jejum nao obrigatorio.', 1, 0, 'Analises clinicas', 'Exame de sangue para gravidez.'),
(132, 'Testosterona total', 10, 'Coleta pela manha, ate as 10h.', 1, 1, 'Analises clinicas', 'Hormonio masculino.'),
(133, 'Estradiol', 10, 'Jejum nao obrigatorio.', 1, 1, 'Analises clinicas', 'Hormonio feminino.'),
(134, 'Prolactina', 10, 'Repouso de 30 minutos na unidade antes da coleta.', 1, 1, 'Analises clinicas', 'Hormonio da hipofise.'),
(135, 'Cortisol', 10, 'Coleta pela manha, ate as 9h.', 1, 1, 'Analises clinicas', 'Hormonio do estresse.'),
(136, 'Urina tipo 1 (EAS)', 10, 'Primeira urina da manha, jato medio, em frasco proprio.', 1, 0, 'Analises clinicas', 'Exame de rotina da urina.'),
(137, 'Urocultura', 10, 'Higiene intima antes; jato medio em frasco esteril.', 1, 1, 'Analises clinicas', 'Pesquisa de bacterias na urina.'),
(138, 'Parasitologico de fezes', 10, 'Amostra colhida em casa, no frasco entregue pela recepcao.', 1, 0, 'Analises clinicas', 'Pesquisa de parasitas.'),
(139, 'Sangue oculto nas fezes', 10, 'Amostra colhida em casa, no frasco entregue pela recepcao.', 1, 0, 'Analises clinicas', 'Rastreamento de sangramento intestinal.'),
(140, 'Sorologia HIV 1 e 2', 10, 'Jejum nao obrigatorio.', 1, 0, 'Analises clinicas', 'Pesquisa de anticorpos e antigeno do HIV. Resultado sigiloso.'),
(141, 'Hepatite B (HBsAg)', 10, 'Jejum nao obrigatorio.', 1, 0, 'Analises clinicas', 'Pesquisa do virus da hepatite B.'),
(142, 'Hepatite C (anti-HCV)', 10, 'Jejum nao obrigatorio.', 1, 0, 'Analises clinicas', 'Pesquisa de anticorpos da hepatite C.'),
(143, 'Sifilis (VDRL)', 10, 'Jejum nao obrigatorio.', 1, 0, 'Analises clinicas', 'Triagem para sifilis.'),
(144, 'Dengue NS1', 10, 'Jejum nao obrigatorio.', 1, 1, 'Analises clinicas', 'Deteccao precoce da dengue (primeiros dias de sintomas).'),
(145, 'Covid-19 RT-PCR', 10, 'Nao assoar o nariz 30 minutos antes.', 1, 0, 'Analises clinicas', 'Pesquisa do virus por swab nasal.'),
(146, 'NIPT ampliado', 10, 'Jejum nao obrigatorio.', 1, 1, 'Genetica', 'Teste pre-natal nao invasivo a partir de 10 semanas de gestacao.'),
(147, 'Exoma completo', 15, 'Jejum nao obrigatorio.', 1, 1, 'Genetica', 'Sequenciamento das regioes codificantes do DNA. Requer aconselhamento genetico.'),
(148, 'Painel genetico de cancer hereditario', 15, 'Jejum nao obrigatorio.', 1, 1, 'Genetica', 'Genes associados a cancer de mama, ovario, intestino e outros.'),
(149, 'Painel cardiogenetico', 15, 'Jejum nao obrigatorio.', 1, 1, 'Genetica', 'Genes ligados a cardiomiopatias e arritmias hereditarias.'),
(150, 'Farmacogenetica', 15, 'Jejum nao obrigatorio.', 1, 1, 'Genetica', 'Como seus genes influenciam a resposta a medicamentos.'),
(151, 'Raio-X de coluna lombar', 15, 'Retirar objetos metalicos.', 2, 1, 'Imagem', 'Imagem dos ossos da coluna lombar.'),
(152, 'Raio-X de joelho', 15, 'Retirar objetos metalicos.', 2, 1, 'Imagem', 'Imagem dos ossos do joelho.'),
(153, 'Raio-X dos seios da face', 15, 'Retirar objetos metalicos.', 2, 1, 'Imagem', 'Imagem dos seios paranasais.'),
(154, 'Ultrassonografia de tireoide', 20, 'Jejum nao obrigatorio.', 9, 1, 'Imagem', 'Imagem da tireoide e do pescoco.'),
(155, 'Ultrassonografia pelvica transvaginal', 30, 'Bexiga vazia.', 9, 1, 'Imagem', 'Avalia utero e ovarios.'),
(156, 'Ultrassonografia de mamas', 20, 'Nao usar desodorante ou creme nas mamas.', 9, 1, 'Imagem', 'Complementa a mamografia.'),
(157, 'Ultrassonografia obstetrica morfologica', 45, 'Jejum nao obrigatorio.', 9, 1, 'Imagem', 'Avalia a anatomia do bebe (geralmente entre 20 e 24 semanas).'),
(158, 'Ultrassonografia de prostata (via abdominal)', 20, 'Bexiga cheia: beber 1 litro de agua 1 hora antes e nao urinar.', 9, 1, 'Imagem', 'Avalia o volume da prostata.'),
(159, 'Mamografia digital', 20, 'Nao usar desodorante, talco ou creme. Levar exames anteriores.', 5, 1, 'Imagem', 'Rastreamento do cancer de mama.'),
(160, 'Densitometria ossea', 20, 'Nao tomar calcio nas 24 horas anteriores.', 5, 1, 'Imagem', 'Mede a densidade dos ossos (osteoporose).'),
(161, 'Tomografia de cranio', 20, 'Jejum de 4 horas se houver contraste.', 4, 1, 'Imagem', 'Imagem em cortes do cranio e do encefalo.'),
(162, 'Tomografia de torax', 20, 'Jejum de 4 horas se houver contraste.', 4, 1, 'Imagem', 'Imagem detalhada dos pulmoes.'),
(163, 'Ressonancia magnetica de cranio', 40, 'Retirar todos os metais. Informar marca-passo, implantes ou claustrofobia.', 4, 1, 'Imagem', 'Imagem detalhada do encefalo.'),
(164, 'Ressonancia magnetica de joelho', 40, 'Retirar todos os metais. Informar marca-passo ou implantes.', 4, 1, 'Imagem', 'Avalia ligamentos, meniscos e cartilagem.'),
(165, 'Ressonancia magnetica de coluna lombar', 40, 'Retirar todos os metais. Informar marca-passo ou implantes.', 4, 1, 'Imagem', 'Avalia discos, nervos e vertebras.'),
(166, 'Ecocardiograma transtoracico', 30, 'Jejum nao obrigatorio.', 3, 1, 'Cardiologia', 'Ultrassom do coracao: estrutura e funcionamento.'),
(167, 'Holter 24 horas', 20, 'Tomar banho antes: o aparelho fica 24 horas e nao pode molhar.', 3, 1, 'Cardiologia', 'Registro continuo do ritmo cardiaco por 24 horas.'),
(168, 'MAPA 24 horas', 20, 'Usar roupa de manga larga; o aparelho fica 24 horas.', 3, 1, 'Cardiologia', 'Monitorizacao da pressao arterial por 24 horas.'),
(169, 'Espirometria', 30, 'Nao fumar nas 4 horas anteriores. Informar uso de bombinhas.', 8, 1, 'Respiratorio', 'Avalia a capacidade e o fluxo dos pulmoes.'),
(170, 'Exame de refracao', 20, 'Levar oculos ou lentes em uso.', 6, 0, 'Oftalmologia', 'Mede o grau para oculos.'),
(171, 'Tonometria', 10, 'Jejum nao obrigatorio.', 6, 0, 'Oftalmologia', 'Mede a pressao do olho (glaucoma).'),
(172, 'Mapeamento de retina', 30, 'A pupila e dilatada: vir acompanhado e nao dirigir depois.', 6, 1, 'Oftalmologia', 'Exame detalhado do fundo do olho.'),
(173, 'Audiometria tonal', 30, 'Evitar ambientes barulhentos nas 14 horas anteriores.', 7, 1, 'Audiologia', 'Avalia a audicao em diferentes frequencias.'),
(174, 'Imitanciometria', 15, 'Jejum nao obrigatorio.', 7, 1, 'Audiologia', 'Avalia o ouvido medio e os reflexos auditivos.'),
(175, 'Check-up essencial', 60, 'Jejum de 12 horas. Agua liberada.', 1, 0, 'Check-up', 'Hemograma, glicose, perfil lipidico, creatinina, TSH, urina e consulta de clinica geral para entregar resultados.'),
(176, 'Check-up padrao', 120, 'Jejum de 12 horas. Agua liberada.', 1, 0, 'Check-up', 'Essencial + vitamina D, ferritina, PCR, eletrocardiograma, avaliacao oftalmologica e de vacinas.'),
(177, 'Check-up plus', 180, 'Jejum de 12 horas. Agua liberada.', 1, 0, 'Check-up', 'Padrao + ecocardiograma, teste ergometrico, ultrassonografia de abdome, espirometria e audiometria.'),
(178, 'Check-up da mulher', 120, 'Jejum de 12 horas. Agua liberada.', 1, 0, 'Check-up', 'Laboratorio basico, ultrassonografia pelvica e de mamas e mamografia conforme a idade.'),
(179, 'Check-up do homem', 120, 'Jejum de 12 horas. Agua liberada.', 1, 0, 'Check-up', 'Laboratorio basico, PSA, testosterona e ultrassonografia de prostata conforme a idade.');

-- Vacinas: faixa etaria e mais opcoes (texto informativo; o calendario oficial prevalece)
UPDATE `vacinas` SET `faixa` = 'Adulto' WHERE `id_vacina` IN (1, 2, 3, 4, 7);
UPDATE `vacinas` SET `faixa` = 'Infantil' WHERE `id_vacina` = 5;
UPDATE `vacinas` SET `faixa` = 'Adolescente', `nome` = 'HPV nonavalente', `descricao` = 'Protege contra 9 tipos de HPV ligados a canceres e verrugas genitais.' WHERE `id_vacina` = 6;
UPDATE `vacinas` SET `nome` = 'Gripe quadrivalente', `descricao` = 'Dose anual contra os 4 tipos de influenza da temporada.' WHERE `id_vacina` = 1;
INSERT IGNORE INTO `vacinas` (`id_vacina`, `nome`, `descricao`, `doses`, `intervalo_dias`, `publico`, `faixa`) VALUES
(8, 'Gripe alta dose', 'Dose anual com mais antigeno, para melhor resposta em idosos.', 1, NULL, '60 anos ou mais', 'Idoso'),
(9, 'Herpes-zoster recombinante', 'Previne o herpes-zoster (cobreiro) e a dor que pode ficar depois.', 2, 60, '50 anos ou mais', 'Idoso'),
(10, 'Pneumococica 13-valente', 'Protege contra 13 tipos de pneumococo (pneumonia, meningite, otite).', 1, NULL, 'Criancas, idosos e grupos de risco', 'Idoso'),
(11, 'Pneumococica 15-valente', 'Protege contra 15 tipos de pneumococo.', 1, NULL, 'Criancas, idosos e grupos de risco', 'Idoso'),
(12, 'Pneumococica 23-valente', 'Amplia a protecao contra pneumococo em adultos de risco e idosos.', 1, NULL, '60 anos ou mais e grupos de risco', 'Idoso'),
(13, 'Meningococica ACWY', 'Protege contra 4 tipos de meningococo.', 1, NULL, 'Criancas, adolescentes e adultos', 'Adolescente'),
(14, 'Meningococica B', 'Protege contra o meningococo B.', 2, 30, 'Criancas, adolescentes e adultos', 'Adolescente'),
(15, 'Rotavirus pentavalente', 'Previne a diarreia grave por rotavirus em bebes.', 3, 30, 'Bebes de 6 semanas a 8 meses', 'Infantil'),
(16, 'Hepatite A', 'Previne a hepatite A.', 2, 180, 'A partir de 12 meses', 'Infantil'),
(17, 'Varicela (catapora)', 'Previne a catapora.', 2, 90, 'A partir de 12 meses', 'Infantil'),
(18, 'dTpa (triplice bacteriana acelular)', 'Difteria, tetano e coqueluche; protege o bebe quando aplicada na gestacao.', 1, NULL, 'Gestantes e adultos', 'Gestante'),
(19, 'Dengue', 'Reduz casos graves de dengue.', 2, 90, '4 a 60 anos', 'Adulto'),
(20, 'Febre tifoide', 'Para quem vai viajar a regioes de risco.', 1, NULL, 'Viajantes', 'Viajante');

INSERT IGNORE INTO `especialidades` (`id_especialidade`, `nome`, `descricao`) VALUES
(12, 'Urologia', 'Rins, vias urinarias e saude do homem'),
(13, 'Otorrinolaringologia', 'Ouvido, nariz e garganta'),
(14, 'Pneumologia', 'Pulmoes e vias respiratorias'),
(15, 'Reumatologia', 'Articulacoes e doencas autoimunes'),
(16, 'Nefrologia', 'Doencas dos rins'),
(17, 'Geriatria', 'Saude da pessoa idosa'),
(18, 'Mastologia', 'Saude das mamas'),
(19, 'Infectologia', 'Doencas infecciosas e vacinacao'),
(20, 'Alergia e Imunologia', 'Alergias e sistema imune'),
(21, 'Nutrologia', 'Nutricao e metabolismo');

INSERT IGNORE INTO `profissionais` (`id_profissional`, `tipo`, `nome`, `telefone`) VALUES
(201, 'medico', 'Andre Urologo (ficticio)', '(11) 3000-0201'),
(202, 'medico', 'Bianca Otorrino (ficticia)', '(11) 3000-0202'),
(203, 'medico', 'Caio Pneumo (ficticio)', '(11) 3000-0203'),
(204, 'medico', 'Diana Reumato (ficticia)', '(11) 3000-0204'),
(205, 'medico', 'Elias Nefro (ficticio)', '(11) 3000-0205'),
(206, 'medico', 'Fatima Geriatra (ficticia)', '(11) 3000-0206'),
(207, 'medico', 'Gisele Mastologista (ficticia)', '(11) 3000-0207'),
(208, 'medico', 'Hugo Infecto (ficticio)', '(11) 3000-0208'),
(209, 'medico', 'Iris Alergista (ficticia)', '(11) 3000-0209'),
(210, 'medico', 'Jorge Nutrologo (ficticio)', '(11) 3000-0210'),
(211, 'medico', 'Karen Endocrino (ficticia)', '(11) 3000-0211'),
(212, 'medico', 'Leo Dermato (ficticio)', '(11) 3000-0212'),
(213, 'medico', 'Mara Pediatra (ficticia)', '(11) 3000-0213'),
(214, 'medico', 'Nilo Ortopedista (ficticio)', '(11) 3000-0214');

INSERT IGNORE INTO `medicos` (`id_profissional`, `crm`, `crm_uf`, `duracao_consulta_min`) VALUES
(201, '100201', 'SP', 30),
(202, '100202', 'SP', 30),
(203, '100203', 'SP', 30),
(204, '100204', 'SP', 30),
(205, '100205', 'SP', 30),
(206, '100206', 'SP', 30),
(207, '100207', 'SP', 30),
(208, '100208', 'SP', 30),
(209, '100209', 'SP', 30),
(210, '100210', 'SP', 30),
(211, '100211', 'SP', 30),
(212, '100212', 'SP', 30),
(213, '100213', 'SP', 30),
(214, '100214', 'SP', 30);

INSERT IGNORE INTO `medico_especialidades` (`id_profissional`, `id_especialidade`) VALUES
(201, 12),
(202, 13),
(203, 14),
(204, 15),
(205, 16),
(206, 17),
(206, 1),
(207, 18),
(208, 19),
(209, 20),
(210, 21),
(211, 11),
(212, 5),
(213, 3),
(214, 4);

INSERT IGNORE INTO `disponibilidades_medico` (`id_disponibilidade`, `id_profissional`, `dia_semana`, `hora_inicio`, `hora_fim`) VALUES
(301, 201, 1, '08:00:00', '12:00:00'),
(302, 201, 2, '13:00:00', '18:00:00'),
(303, 201, 3, '08:00:00', '12:00:00'),
(304, 201, 4, '13:00:00', '18:00:00'),
(305, 202, 1, '13:00:00', '18:00:00'),
(306, 202, 2, '08:00:00', '12:00:00'),
(307, 202, 3, '13:00:00', '18:00:00'),
(308, 202, 5, '13:00:00', '18:00:00'),
(309, 203, 1, '08:00:00', '12:00:00'),
(310, 203, 2, '13:00:00', '18:00:00'),
(311, 203, 4, '13:00:00', '18:00:00'),
(312, 203, 5, '08:00:00', '12:00:00'),
(313, 204, 1, '13:00:00', '18:00:00'),
(314, 204, 3, '13:00:00', '18:00:00'),
(315, 204, 4, '08:00:00', '12:00:00'),
(316, 204, 5, '13:00:00', '18:00:00'),
(317, 205, 2, '13:00:00', '18:00:00'),
(318, 205, 3, '08:00:00', '12:00:00'),
(319, 205, 4, '13:00:00', '18:00:00'),
(320, 205, 5, '08:00:00', '12:00:00'),
(321, 206, 1, '13:00:00', '18:00:00'),
(322, 206, 2, '08:00:00', '12:00:00'),
(323, 206, 3, '13:00:00', '18:00:00'),
(324, 206, 4, '08:00:00', '12:00:00'),
(325, 207, 1, '08:00:00', '12:00:00'),
(326, 207, 2, '13:00:00', '18:00:00'),
(327, 207, 3, '08:00:00', '12:00:00'),
(328, 207, 5, '08:00:00', '12:00:00'),
(329, 208, 1, '13:00:00', '18:00:00'),
(330, 208, 2, '08:00:00', '12:00:00'),
(331, 208, 4, '08:00:00', '12:00:00'),
(332, 208, 5, '13:00:00', '18:00:00'),
(333, 209, 1, '08:00:00', '12:00:00'),
(334, 209, 3, '08:00:00', '12:00:00'),
(335, 209, 4, '13:00:00', '18:00:00'),
(336, 209, 5, '08:00:00', '12:00:00'),
(337, 210, 2, '08:00:00', '12:00:00'),
(338, 210, 3, '13:00:00', '18:00:00'),
(339, 210, 4, '08:00:00', '12:00:00'),
(340, 210, 5, '13:00:00', '18:00:00'),
(341, 211, 1, '08:00:00', '12:00:00'),
(342, 211, 2, '13:00:00', '18:00:00'),
(343, 211, 3, '08:00:00', '12:00:00'),
(344, 211, 4, '13:00:00', '18:00:00'),
(345, 212, 1, '13:00:00', '18:00:00'),
(346, 212, 2, '08:00:00', '12:00:00'),
(347, 212, 3, '13:00:00', '18:00:00'),
(348, 212, 5, '13:00:00', '18:00:00'),
(349, 213, 1, '08:00:00', '12:00:00'),
(350, 213, 2, '13:00:00', '18:00:00'),
(351, 213, 4, '13:00:00', '18:00:00'),
(352, 213, 5, '08:00:00', '12:00:00'),
(353, 214, 1, '13:00:00', '18:00:00'),
(354, 214, 3, '13:00:00', '18:00:00'),
(355, 214, 4, '08:00:00', '12:00:00'),
(356, 214, 5, '13:00:00', '18:00:00');
