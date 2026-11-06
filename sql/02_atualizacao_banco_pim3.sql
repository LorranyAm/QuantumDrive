-- ============================================================================
-- 1. CABEÇALHO E IDENTIFICAÇÃO DO PROJETO
-- ============================================================================
-- Projeto ......: Quantum Drive — locação de veículos elétricos
-- Disciplina ...: PIM IV — evolução do banco de dados do PIM III
-- SGBD .........: MySQL 8.0 ou superior (compatível com Aiven for MySQL)
-- Arquivo ......: 02_atualizacao_banco_pim3.sql
-- Finalidade ...: atualizar um banco JÁ EXISTENTE do PIM III para a versão
--                 do PIM IV, sem apagar tabelas nem dados.
--                 Para um banco novo, use 01_criacao_banco_novo.sql.
--
-- O que este script NÃO faz: DROP DATABASE, DROP TABLE, DELETE ou TRUNCATE.
-- Ele só usa ALTER TABLE, UPDATE de padronização e CREATE de índices,
-- procedures e triggers (DROP PROCEDURE/TRIGGER IF EXISTS apenas para
-- permitir executar o script de novo sem erro).
--
-- ANTES DE EXECUTAR:
--   1. Faça um backup (na Aiven: Backups > o serviço já guarda cópias
--      automáticas; ou exporte pelo Workbench em Server > Data Export).
--   2. Execute a ETAPA 0 sozinha e confira o diagnóstico.
--   3. Depois execute o restante. Este script deve ser executado uma vez:
--      as ETAPAS 2, 4, 5 e 6 dão erro de "Duplicate" se já tiverem sido aplicadas.
--
-- Pré-requisito: as tabelas do PIM III existem com as chaves primárias
-- e estrangeiras do script original.
-- ============================================================================


-- ============================================================================
-- 2. CONFIGURAÇÃO DA SESSÃO
-- ============================================================================
USE locacao_veiculos_eletricos;
SET NAMES utf8mb4;
SET SQL_SAFE_UPDATES = 0;   -- o Workbench bloqueia UPDATE sem chave; vale só nesta sessão


-- ============================================================================
-- ETAPA 0 — DIAGNÓSTICO (somente leitura)
-- Mostra os valores de status que existem hoje. Valores que não forem
-- reconhecidos pela padronização da ETAPA 3 farão a ETAPA 5 falhar com o
-- erro 3819 ("Check constraint ... is violated"). Nesse caso, corrija o
-- valor com um UPDATE e execute a etapa de novo.
-- ============================================================================
SELECT 'Veiculo.status_veiculo' AS coluna, status_veiculo AS valor, COUNT(*) AS qtd FROM Veiculo GROUP BY status_veiculo
UNION ALL
SELECT 'Agendamento.status_agendamento', status_agendamento, COUNT(*) FROM Agendamento GROUP BY status_agendamento
UNION ALL
SELECT 'Locacao.status_locacao', status_locacao, COUNT(*) FROM Locacao GROUP BY status_locacao
UNION ALL
SELECT 'Pagamento.forma_pagamento', forma_pagamento, COUNT(*) FROM Pagamento GROUP BY forma_pagamento
UNION ALL
SELECT 'Pagamento.status_pagamento', status_pagamento, COUNT(*) FROM Pagamento GROUP BY status_pagamento
UNION ALL
SELECT 'Inspecao.status_inspecao', status_inspecao, COUNT(*) FROM Inspecao GROUP BY status_inspecao
UNION ALL
SELECT 'AtendimentoChat.status_chat', status_chat, COUNT(*) FROM AtendimentoChat GROUP BY status_chat
UNION ALL
SELECT 'MensagemChat.remetente', remetente, COUNT(*) FROM MensagemChat GROUP BY remetente;

-- Linhas que violariam as novas regras de datas e valores (esperado: 0 em cada)
SELECT 'Locacao com devolução antes da retirada' AS problema, COUNT(*) AS qtd
  FROM Locacao WHERE data_devolucao IS NOT NULL AND data_devolucao <= data_retirada
UNION ALL
SELECT 'Inspecao com fim antes do início', COUNT(*)
  FROM Inspecao WHERE data_fim IS NOT NULL AND data_fim < data_inicio
UNION ALL
SELECT 'Pagamento sem forma de pagamento', COUNT(*)
  FROM Pagamento WHERE forma_pagamento IS NULL OR TRIM(forma_pagamento) = ''
UNION ALL
SELECT 'Pagamento com valor <= 0', COUNT(*)
  FROM Pagamento WHERE valor_pago <= 0
UNION ALL
SELECT 'Categoria com nome repetido', COUNT(*)
  FROM (SELECT nome_categoria FROM CategoriaVeiculo GROUP BY nome_categoria HAVING COUNT(*) > 1) d
UNION ALL
SELECT 'Agendamentos ativos sobrepostos', COUNT(*)
  FROM Agendamento a1 JOIN Agendamento a2
    ON a2.id_veiculo = a1.id_veiculo AND a2.id_agendamento > a1.id_agendamento
   AND a1.data_inicio < a2.data_fim AND a1.data_fim > a2.data_inicio;


-- ============================================================================
-- ETAPA 1 — CONJUNTO DE CARACTERES
-- Garante utf8mb4 (acentos e emojis). Em MySQL 8 normalmente já é o padrão;
-- nesse caso os comandos apenas confirmam a configuração.
-- ============================================================================
ALTER DATABASE locacao_veiculos_eletricos CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

ALTER TABLE Cliente          CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE CategoriaVeiculo CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE Veiculo          CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE Agendamento      CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE Locacao          CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE Pagamento        CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE Inspecao         CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE AtendimentoChat  CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE MensagemChat     CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;


-- ============================================================================
-- ETAPA 2 — NOVAS COLUNAS (justificativa na documentação, item "Evolução")
-- ============================================================================
-- Login da aplicação: guarda apenas o hash gerado pela API (nunca a senha).
-- Fica NULL para os clientes antigos, que vão definir a senha no primeiro acesso.
ALTER TABLE Cliente
    ADD COLUMN data_nascimento DATE         NULL AFTER necessidades_acessibilidade,
    ADD COLUMN senha_hash      VARCHAR(255) NULL AFTER data_nascimento,
    ADD COLUMN data_cadastro   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER senha_hash;

-- Valor da diária: necessário para calcular Locacao.valor_total.
-- Os veículos antigos ficam com 0,00 e precisam ser atualizados (ETAPA 3);
-- enquanto estiverem com 0,00, a procedure não aceita reserva para eles.
ALTER TABLE Veiculo
    ADD COLUMN valor_diaria DECIMAL(10,2) NOT NULL DEFAULT 0.00 AFTER status_veiculo;


-- ============================================================================
-- ETAPA 3 — PADRONIZAÇÃO DOS DADOS EXISTENTES
-- a) Status vazios recebem o valor padrão.
-- b) Texto em minúsculas e espaços trocados por "_".
-- c) A collation utf8mb4_0900_ai_ci ignora acentos e maiúsculas na
--    comparação; por isso "WHERE col = 'em_analise'" também encontra
--    "em_análise" e o UPDATE grava a forma padrão, sem acento.
-- ============================================================================

-- Veiculo
UPDATE Veiculo SET status_veiculo = 'disponivel' WHERE status_veiculo IS NULL OR TRIM(status_veiculo) = '';
UPDATE Veiculo SET status_veiculo = REPLACE(LOWER(TRIM(status_veiculo)), ' ', '_');
UPDATE Veiculo SET status_veiculo = 'disponivel' WHERE status_veiculo = 'disponivel';
UPDATE Veiculo SET status_veiculo = 'manutencao' WHERE status_veiculo IN ('manutencao', 'em_manutencao');
UPDATE Veiculo SET status_veiculo = 'locado'     WHERE status_veiculo IN ('locado', 'alugado');
UPDATE Veiculo SET status_veiculo = 'inativo'    WHERE status_veiculo = 'inativo';

-- Defina aqui o valor da diária de cada veículo antigo. Exemplo:
-- UPDATE Veiculo SET valor_diaria = 189.00 WHERE placa = 'ABC1D23';

-- Agendamento
UPDATE Agendamento SET status_agendamento = 'em_analise' WHERE status_agendamento IS NULL OR TRIM(status_agendamento) = '';
UPDATE Agendamento SET status_agendamento = REPLACE(LOWER(TRIM(status_agendamento)), ' ', '_');
UPDATE Agendamento SET status_agendamento = 'em_analise'   WHERE status_agendamento = 'em_analise';
UPDATE Agendamento SET status_agendamento = 'confirmado'   WHERE status_agendamento IN ('confirmado', 'confirmada');
UPDATE Agendamento SET status_agendamento = 'em_andamento' WHERE status_agendamento = 'em_andamento';
UPDATE Agendamento SET status_agendamento = 'concluido'    WHERE status_agendamento IN ('concluido', 'concluida');
UPDATE Agendamento SET status_agendamento = 'cancelado'    WHERE status_agendamento IN ('cancelado', 'cancelada');

-- Locacao
UPDATE Locacao SET status_locacao = 'em_andamento' WHERE status_locacao IS NULL OR TRIM(status_locacao) = '';
UPDATE Locacao SET status_locacao = REPLACE(LOWER(TRIM(status_locacao)), ' ', '_');
UPDATE Locacao SET status_locacao = 'em_andamento' WHERE status_locacao IN ('em_andamento', 'ativa');
UPDATE Locacao SET status_locacao = 'finalizada'   WHERE status_locacao IN ('finalizada', 'finalizado', 'concluida', 'concluido');

-- Pagamento (formas oferecidas pela aplicação: Pix, cartão de crédito,
-- cartão de débito e pagamento na loja)
UPDATE Pagamento SET forma_pagamento = REPLACE(LOWER(TRIM(forma_pagamento)), ' ', '_');
UPDATE Pagamento SET forma_pagamento = 'pix'            WHERE forma_pagamento = 'pix';
UPDATE Pagamento SET forma_pagamento = 'cartao_credito' WHERE forma_pagamento IN ('cartao_credito', 'cartao_de_credito', 'credito');
UPDATE Pagamento SET forma_pagamento = 'cartao_debito'  WHERE forma_pagamento IN ('cartao_debito', 'cartao_de_debito', 'debito');
UPDATE Pagamento SET forma_pagamento = 'pagamento_loja' WHERE forma_pagamento IN ('pagamento_loja', 'pagamento_na_loja', 'loja');
UPDATE Pagamento SET status_pagamento = 'pendente' WHERE status_pagamento IS NULL OR TRIM(status_pagamento) = '';
UPDATE Pagamento SET status_pagamento = REPLACE(LOWER(TRIM(status_pagamento)), ' ', '_');
UPDATE Pagamento SET status_pagamento = 'pendente'  WHERE status_pagamento = 'pendente';
UPDATE Pagamento SET status_pagamento = 'pago'      WHERE status_pagamento IN ('pago', 'aprovado', 'confirmado');
UPDATE Pagamento SET status_pagamento = 'estornado' WHERE status_pagamento = 'estornado';

-- Inspecao
UPDATE Inspecao SET status_inspecao = 'agendada' WHERE status_inspecao IS NULL OR TRIM(status_inspecao) = '';
UPDATE Inspecao SET status_inspecao = REPLACE(LOWER(TRIM(status_inspecao)), ' ', '_');
UPDATE Inspecao SET status_inspecao = 'agendada'     WHERE status_inspecao = 'agendada';
UPDATE Inspecao SET status_inspecao = 'em_andamento' WHERE status_inspecao = 'em_andamento';
UPDATE Inspecao SET status_inspecao = 'concluida'    WHERE status_inspecao IN ('concluida', 'concluido', 'finalizada');

-- Chat
UPDATE AtendimentoChat SET status_chat = 'aberto' WHERE status_chat IS NULL OR TRIM(status_chat) = '';
UPDATE AtendimentoChat SET status_chat = REPLACE(LOWER(TRIM(status_chat)), ' ', '_');
UPDATE AtendimentoChat SET status_chat = 'aberto'         WHERE status_chat = 'aberto';
UPDATE AtendimentoChat SET status_chat = 'em_atendimento' WHERE status_chat = 'em_atendimento';
UPDATE AtendimentoChat SET status_chat = 'encerrado'      WHERE status_chat IN ('encerrado', 'fechado', 'finalizado');
UPDATE MensagemChat SET remetente = LOWER(TRIM(remetente));
UPDATE MensagemChat SET remetente = 'cliente'   WHERE remetente = 'cliente';
UPDATE MensagemChat SET remetente = 'atendente' WHERE remetente = 'atendente';
UPDATE MensagemChat SET remetente = 'sistema'   WHERE remetente IN ('sistema', 'bot');


-- ============================================================================
-- ETAPA 4 — NOT NULL E DEFAULT NOS CAMPOS DE STATUS E DATAS
-- (os tipos continuam os mesmos do PIM III)
-- ============================================================================
ALTER TABLE Veiculo         MODIFY status_veiculo     VARCHAR(50) NOT NULL DEFAULT 'disponivel';
ALTER TABLE Agendamento     MODIFY status_agendamento VARCHAR(50) NOT NULL DEFAULT 'em_analise';
ALTER TABLE Locacao         MODIFY status_locacao     VARCHAR(50) NOT NULL DEFAULT 'em_andamento';
ALTER TABLE Pagamento       MODIFY forma_pagamento    VARCHAR(50) NOT NULL;
ALTER TABLE Pagamento       MODIFY status_pagamento   VARCHAR(50) NOT NULL DEFAULT 'pendente';
ALTER TABLE Inspecao        MODIFY status_inspecao    VARCHAR(50) NOT NULL DEFAULT 'agendada';
ALTER TABLE AtendimentoChat MODIFY status_chat        VARCHAR(50) NOT NULL DEFAULT 'aberto';
ALTER TABLE AtendimentoChat MODIFY data_abertura      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE MensagemChat    MODIFY data_envio         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP;


-- ============================================================================
-- ETAPA 5 — RESTRIÇÕES DE INTEGRIDADE NOVAS (UNIQUE e CHECK)
-- As PKs, FKs e UNIQUE do PIM III já existem e não são alteradas.
-- ============================================================================
-- UNIQUE novo: evita duas categorias com o mesmo nome
ALTER TABLE CategoriaVeiculo ADD CONSTRAINT uq_categoria_nome UNIQUE (nome_categoria);

-- Cliente
ALTER TABLE Cliente
    ADD CONSTRAINT chk_cliente_nome  CHECK (CHAR_LENGTH(TRIM(nome)) >= 3),
    ADD CONSTRAINT chk_cliente_email CHECK (email LIKE '_%@_%'),
    ADD CONSTRAINT chk_cliente_cpf   CHECK (CHAR_LENGTH(REGEXP_REPLACE(cpf, '[^0-9]', '')) = 11);

-- Veiculo
ALTER TABLE Veiculo
    ADD CONSTRAINT chk_veiculo_status
        CHECK (status_veiculo IN ('disponivel', 'locado', 'manutencao', 'inativo')),
    ADD CONSTRAINT chk_veiculo_ano
        CHECK (ano IS NULL OR ano BETWEEN 2000 AND 2100),
    ADD CONSTRAINT chk_veiculo_autonomia
        CHECK (autonomia_km IS NULL OR autonomia_km > 0),
    ADD CONSTRAINT chk_veiculo_valor_diaria
        CHECK (valor_diaria >= 0);

-- Agendamento (a ordem das datas é conferida pela trigger, com mensagem clara)
ALTER TABLE Agendamento
    ADD CONSTRAINT chk_agendamento_status
        CHECK (status_agendamento IN ('em_analise', 'confirmado', 'em_andamento', 'concluido', 'cancelado'));

-- Locacao
ALTER TABLE Locacao
    ADD CONSTRAINT chk_locacao_status
        CHECK (status_locacao IN ('em_andamento', 'finalizada')),
    ADD CONSTRAINT chk_locacao_datas
        CHECK (data_devolucao IS NULL OR data_devolucao > data_retirada),
    ADD CONSTRAINT chk_locacao_tempo
        CHECK (tempo_locacao_horas IS NULL OR tempo_locacao_horas >= 0),
    ADD CONSTRAINT chk_locacao_valor
        CHECK (valor_total IS NULL OR valor_total >= 0);

-- Pagamento (formas de pagamento oferecidas pela aplicação)
ALTER TABLE Pagamento
    ADD CONSTRAINT chk_pagamento_valor
        CHECK (valor_pago > 0),
    ADD CONSTRAINT chk_pagamento_forma
        CHECK (forma_pagamento IN ('pix', 'cartao_credito', 'cartao_debito', 'pagamento_loja')),
    ADD CONSTRAINT chk_pagamento_status
        CHECK (status_pagamento IN ('pendente', 'pago', 'estornado'));

-- Inspecao
ALTER TABLE Inspecao
    ADD CONSTRAINT chk_inspecao_status
        CHECK (status_inspecao IN ('agendada', 'em_andamento', 'concluida')),
    ADD CONSTRAINT chk_inspecao_datas
        CHECK (data_fim IS NULL OR data_fim >= data_inicio);

-- Atendimento por chat
ALTER TABLE AtendimentoChat
    ADD CONSTRAINT chk_chat_status
        CHECK (status_chat IN ('aberto', 'em_atendimento', 'encerrado'));

ALTER TABLE MensagemChat
    ADD CONSTRAINT chk_mensagem_remetente
        CHECK (remetente IN ('cliente', 'atendente', 'sistema')),
    ADD CONSTRAINT chk_mensagem_conteudo
        CHECK (CHAR_LENGTH(TRIM(conteudo)) > 0);


-- ============================================================================
-- ETAPA 6 — ÍNDICES (8. ÍNDICES)
-- O InnoDB já cria índice para cada FK. Estes índices compostos atendem às
-- consultas mais usadas: disponibilidade por período, reservas do cliente,
-- relatórios por data e histórico do chat. Quando um índice composto começa
-- pela coluna da FK, o MySQL passa a usá-lo no lugar do índice automático.
-- ============================================================================
CREATE INDEX idx_veiculo_status            ON Veiculo (status_veiculo);
CREATE INDEX idx_agendamento_veiculo_periodo ON Agendamento (id_veiculo, data_inicio, data_fim);
CREATE INDEX idx_agendamento_cliente_inicio  ON Agendamento (id_cliente, data_inicio);
CREATE INDEX idx_agendamento_status          ON Agendamento (status_agendamento);
CREATE INDEX idx_locacao_retirada            ON Locacao (data_retirada);
CREATE INDEX idx_pagamento_data              ON Pagamento (data_pagamento);
CREATE INDEX idx_inspecao_veiculo_periodo    ON Inspecao (id_veiculo, data_inicio);
CREATE INDEX idx_chat_cliente_abertura       ON AtendimentoChat (id_cliente, data_abertura);
CREATE INDEX idx_mensagem_chat_envio         ON MensagemChat (id_chat, data_envio);


-- ============================================================================
-- ETAPA 7 — STORED PROCEDURES (iguais às do 01_criacao_banco_novo.sql)
-- Erros de regra de negócio usam SIGNAL SQLSTATE '45000' com mensagem em
-- português, que a API pode repassar ao usuário.
-- ============================================================================
DROP PROCEDURE IF EXISTS sp_consultar_veiculos_disponiveis;
DROP PROCEDURE IF EXISTS sp_registrar_agendamento;
DROP PROCEDURE IF EXISTS sp_consultar_agendamentos_cliente;
DROP PROCEDURE IF EXISTS sp_relatorio_locacoes_pagamentos;

DELIMITER $$

-- ==========================================
-- PROCEDURE: CONSULTAR VEÍCULOS DISPONÍVEIS
-- Finalidade: listar os veículos livres em determinado período, com o valor
-- estimado do aluguel. Opcionalmente filtra por categoria.
-- Um veículo está livre quando:
--   * não está em manutenção nem inativo;
--   * não tem reserva ativa que se sobreponha ao período;
--   * não tem inspeção aberta que se sobreponha ao período.
-- Sobreposição: [ini, fim) cruza [A, B) quando ini < B e fim > A.
-- ==========================================
CREATE PROCEDURE sp_consultar_veiculos_disponiveis (
    IN p_data_inicio   DATETIME,
    IN p_data_fim      DATETIME,
    IN p_id_categoria  INT          -- NULL = todas as categorias
)
BEGIN
    DECLARE v_diarias INT;

    IF p_data_inicio IS NULL OR p_data_fim IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Informe a data de início e a data de fim.';
    END IF;
    IF p_data_fim <= p_data_inicio THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A data de fim precisa ser depois da data de início.';
    END IF;
    IF p_id_categoria IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM CategoriaVeiculo WHERE id_categoria = p_id_categoria) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Categoria não encontrada.';
    END IF;

    -- Diárias cobradas: arredonda para cima, mínimo de 1 (mesma regra da aplicação)
    SET v_diarias = GREATEST(1, CEIL(TIMESTAMPDIFF(MINUTE, p_data_inicio, p_data_fim) / 1440));

    SELECT v.id_veiculo,
           v.marca,
           v.modelo,
           v.placa,
           v.ano,
           c.nome_categoria,
           v.autonomia_km,
           v.tipo_cambio,
           v.possui_som,
           v.nivel_conforto,
           v.adequado_viagem,
           v.valor_diaria,
           v_diarias                  AS quantidade_diarias,
           v_diarias * v.valor_diaria AS valor_estimado
      FROM Veiculo v
      JOIN CategoriaVeiculo c ON c.id_categoria = v.id_categoria
     WHERE v.status_veiculo NOT IN ('manutencao', 'inativo')
       AND (p_id_categoria IS NULL OR v.id_categoria = p_id_categoria)
       AND NOT EXISTS (
             SELECT 1
               FROM Agendamento a
              WHERE a.id_veiculo = v.id_veiculo
                AND a.status_agendamento IN ('em_analise', 'confirmado', 'em_andamento')
                AND a.data_inicio < p_data_fim
                AND a.data_fim    > p_data_inicio)
       AND NOT EXISTS (
             SELECT 1
               FROM Inspecao i
              WHERE i.id_veiculo = v.id_veiculo
                AND i.status_inspecao <> 'concluida'
                AND i.data_inicio < p_data_fim
                AND (i.data_fim IS NULL OR i.data_fim > p_data_inicio))
     ORDER BY v.valor_diaria, v.modelo;
END$$

-- ==========================================
-- PROCEDURE: REGISTRAR NOVO AGENDAMENTO
-- Finalidade: criar a reserva de um cliente com segurança, devolvendo o id.
-- Confere cliente e veículo e trava a linha do veículo (SELECT ... FOR UPDATE)
-- durante a transação. Assim, duas reservas simultâneas do mesmo carro
-- são processadas uma depois da outra, e a segunda encontra o conflito.
-- A validação das datas e da sobreposição fica na trigger
-- trg_agendamento_antes_inserir, que vale também para INSERT direto.
-- ==========================================
CREATE PROCEDURE sp_registrar_agendamento (
    IN  p_id_cliente      INT,
    IN  p_id_veiculo      INT,
    IN  p_data_inicio     DATETIME,
    IN  p_data_fim        DATETIME,
    IN  p_periodo         VARCHAR(50),
    OUT p_id_agendamento  INT
)
BEGIN
    DECLARE v_status_veiculo VARCHAR(50);
    DECLARE v_valor_diaria   DECIMAL(10,2);

    -- Qualquer erro desfaz a transação e é repassado para quem chamou
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    SET p_id_agendamento = NULL;

    IF p_id_cliente IS NULL OR p_id_veiculo IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Informe o cliente e o veículo.';
    END IF;

    START TRANSACTION;

    IF NOT EXISTS (SELECT 1 FROM Cliente WHERE id_cliente = p_id_cliente) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cliente não encontrado.';
    END IF;

    SELECT status_veiculo, valor_diaria
      INTO v_status_veiculo, v_valor_diaria
      FROM Veiculo
     WHERE id_veiculo = p_id_veiculo
       FOR UPDATE;

    IF v_status_veiculo IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Veículo não encontrado.';
    END IF;
    IF v_status_veiculo IN ('manutencao', 'inativo') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Este veículo não está disponível para reserva.';
    END IF;
    IF v_valor_diaria <= 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'O veículo está sem valor de diária cadastrado.';
    END IF;

    INSERT INTO Agendamento (id_cliente, id_veiculo, data_inicio, data_fim, periodo, status_agendamento)
    VALUES (p_id_cliente, p_id_veiculo, p_data_inicio, p_data_fim, p_periodo, 'em_analise');

    SET p_id_agendamento = LAST_INSERT_ID();

    COMMIT;
END$$

-- ==========================================
-- PROCEDURE: CONSULTAR AGENDAMENTOS DE UM CLIENTE
-- Finalidade: tela "Minhas reservas". Traz cada reserva com o veículo,
-- a locação e o pagamento (quando já existirem). Filtro de status opcional.
-- ==========================================
CREATE PROCEDURE sp_consultar_agendamentos_cliente (
    IN p_id_cliente  INT,
    IN p_status      VARCHAR(50)     -- NULL = todos os status
)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM Cliente WHERE id_cliente = p_id_cliente) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cliente não encontrado.';
    END IF;

    SELECT a.id_agendamento,
           a.status_agendamento,
           a.data_inicio,
           a.data_fim,
           a.periodo,
           v.id_veiculo,
           CONCAT(v.marca, ' ', v.modelo) AS veiculo,
           v.placa,
           c.nome_categoria,
           l.id_locacao,
           l.status_locacao,
           l.data_retirada,
           l.data_devolucao,
           l.valor_total,
           p.status_pagamento,
           p.forma_pagamento,
           p.valor_pago
      FROM Agendamento a
      JOIN Veiculo v            ON v.id_veiculo   = a.id_veiculo
      JOIN CategoriaVeiculo c   ON c.id_categoria = v.id_categoria
      LEFT JOIN Locacao l       ON l.id_agendamento = a.id_agendamento
      LEFT JOIN Pagamento p     ON p.id_locacao     = l.id_locacao
     WHERE a.id_cliente = p_id_cliente
       AND (p_status IS NULL OR a.status_agendamento = p_status)
     ORDER BY a.data_inicio DESC;
END$$

-- ==========================================
-- PROCEDURE: RELATÓRIO DE LOCAÇÕES E PAGAMENTOS
-- Finalidade: painel do dono da loja. Considera as locações com retirada
-- entre as duas datas (inclusive) e devolve três resultados:
--   1) resumo por mês;  2) resumo por veículo;  3) pagamentos por forma.
-- ==========================================
CREATE PROCEDURE sp_relatorio_locacoes_pagamentos (
    IN p_data_inicial DATE,
    IN p_data_final   DATE
)
BEGIN
    DECLARE v_ini DATETIME;
    DECLARE v_fim DATETIME;

    IF p_data_inicial IS NULL OR p_data_final IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Informe a data inicial e a data final.';
    END IF;
    IF p_data_final < p_data_inicial THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A data final precisa ser igual ou depois da data inicial.';
    END IF;

    SET v_ini = p_data_inicial;
    SET v_fim = p_data_final + INTERVAL 1 DAY;   -- inclui o último dia inteiro

    -- 1) Resumo por mês
    SELECT DATE_FORMAT(l.data_retirada, '%Y-%m')                              AS mes,
           COUNT(*)                                                          AS locacoes,
           SUM(l.status_locacao = 'em_andamento')                            AS em_andamento,
           SUM(l.status_locacao = 'finalizada')                              AS finalizadas,
           COALESCE(SUM(l.tempo_locacao_horas), 0)                           AS horas_locadas,
           COALESCE(SUM(l.valor_total), 0)                                   AS valor_faturado,
           COALESCE(SUM(CASE WHEN p.status_pagamento = 'pago' THEN p.valor_pago END), 0) AS valor_recebido,
           COALESCE(SUM(l.valor_total), 0)
             - COALESCE(SUM(CASE WHEN p.status_pagamento = 'pago' THEN p.valor_pago END), 0) AS valor_a_receber
      FROM Locacao l
      LEFT JOIN Pagamento p ON p.id_locacao = l.id_locacao
     WHERE l.data_retirada >= v_ini AND l.data_retirada < v_fim
     GROUP BY DATE_FORMAT(l.data_retirada, '%Y-%m')
     ORDER BY mes;

    -- 2) Utilização por veículo
    SELECT v.id_veiculo,
           CONCAT(v.marca, ' ', v.modelo)              AS veiculo,
           v.placa,
           c.nome_categoria,
           COUNT(l.id_locacao)                          AS locacoes,
           COALESCE(SUM(l.tempo_locacao_horas), 0)      AS horas_locadas,
           COALESCE(SUM(l.valor_total), 0)              AS valor_faturado
      FROM Veiculo v
      JOIN CategoriaVeiculo c ON c.id_categoria = v.id_categoria
      LEFT JOIN Agendamento a ON a.id_veiculo = v.id_veiculo
      LEFT JOIN Locacao l     ON l.id_agendamento = a.id_agendamento
                             AND l.data_retirada >= v_ini AND l.data_retirada < v_fim
     GROUP BY v.id_veiculo, v.marca, v.modelo, v.placa, c.nome_categoria
     ORDER BY valor_faturado DESC, veiculo;

    -- 3) Pagamentos por forma e status
    SELECT p.forma_pagamento,
           p.status_pagamento,
           COUNT(*)          AS quantidade,
           SUM(p.valor_pago) AS valor
      FROM Pagamento p
      JOIN Locacao l ON l.id_locacao = p.id_locacao
     WHERE l.data_retirada >= v_ini AND l.data_retirada < v_fim
     GROUP BY p.forma_pagamento, p.status_pagamento
     ORDER BY p.forma_pagamento, p.status_pagamento;
END$$

DELIMITER ;


-- ============================================================================
-- ETAPA 8 — TRIGGERS (iguais às do 01_criacao_banco_novo.sql)
-- Ordem de dependência (sem recursão):
--   Locacao -> atualiza Agendamento e Veiculo
--   Agendamento -> só lê outras tabelas
--   Veiculo, Pagamento -> não alteram outras tabelas
-- Um trigger nunca altera a própria tabela (o MySQL não permite: erro 1442).
-- Tudo roda dentro da mesma transação do comando que disparou o trigger:
-- se o trigger falhar, o comando inteiro é desfeito.
-- ============================================================================
DROP TRIGGER IF EXISTS trg_agendamento_antes_inserir;
DROP TRIGGER IF EXISTS trg_agendamento_antes_atualizar;
DROP TRIGGER IF EXISTS trg_locacao_antes_inserir;
DROP TRIGGER IF EXISTS trg_locacao_depois_inserir;
DROP TRIGGER IF EXISTS trg_locacao_antes_atualizar;
DROP TRIGGER IF EXISTS trg_locacao_depois_atualizar;
DROP TRIGGER IF EXISTS trg_pagamento_antes_inserir;

DELIMITER $$

-- ==========================================
-- TRIGGER: VALIDAR NOVO AGENDAMENTO
-- Tabela/evento: Agendamento, BEFORE INSERT
-- Regras: devolução depois da retirada; no máximo 30 dias; retirada não pode
-- estar no passado; o veículo não pode ter outra reserva ativa nem inspeção
-- aberta no mesmo período (sobreposição completa, não só datas iguais).
-- ==========================================
CREATE TRIGGER trg_agendamento_antes_inserir
BEFORE INSERT ON Agendamento
FOR EACH ROW
BEGIN
    IF NEW.data_fim <= NEW.data_inicio THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A devolução precisa ser depois da retirada.';
    END IF;

    IF NEW.data_fim > NEW.data_inicio + INTERVAL 30 DAY THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'O período máximo de uma reserva é de 30 dias.';
    END IF;

    IF NEW.data_inicio < NOW() - INTERVAL 5 MINUTE THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A retirada não pode ser em uma data que já passou.';
    END IF;

    IF NEW.status_agendamento NOT IN ('em_analise', 'confirmado') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Uma reserva nova começa como em_analise ou confirmado.';
    END IF;

    IF EXISTS (SELECT 1
                 FROM Agendamento a
                WHERE a.id_veiculo = NEW.id_veiculo
                  AND a.status_agendamento IN ('em_analise', 'confirmado', 'em_andamento')
                  AND a.data_inicio < NEW.data_fim
                  AND a.data_fim    > NEW.data_inicio) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'O veículo já está reservado neste período.';
    END IF;

    IF EXISTS (SELECT 1
                 FROM Inspecao i
                WHERE i.id_veiculo = NEW.id_veiculo
                  AND i.status_inspecao <> 'concluida'
                  AND i.data_inicio < NEW.data_fim
                  AND (i.data_fim IS NULL OR i.data_fim > NEW.data_inicio)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'O veículo estará em inspeção neste período.';
    END IF;
END$$

-- ==========================================
-- TRIGGER: VALIDAR ALTERAÇÃO DE AGENDAMENTO
-- Tabela/evento: Agendamento, BEFORE UPDATE
-- 1) Só permite mudanças de status que fazem sentido:
--      em_analise   -> confirmado | cancelado
--      confirmado   -> em_andamento | cancelado
--      em_andamento -> concluido
--      concluido e cancelado são finais.
-- 2) Se o período ou o veículo mudar, confere de novo as datas e a
--    sobreposição, ignorando a própria reserva (OLD.id_agendamento).
-- ==========================================
CREATE TRIGGER trg_agendamento_antes_atualizar
BEFORE UPDATE ON Agendamento
FOR EACH ROW
BEGIN
    IF NEW.status_agendamento <> OLD.status_agendamento AND NOT (
           (OLD.status_agendamento = 'em_analise'   AND NEW.status_agendamento IN ('confirmado', 'cancelado'))
        OR (OLD.status_agendamento = 'confirmado'   AND NEW.status_agendamento IN ('em_andamento', 'cancelado'))
        OR (OLD.status_agendamento = 'em_andamento' AND NEW.status_agendamento = 'concluido')
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Mudança de status da reserva não permitida.';
    END IF;

    IF NEW.id_cliente <> OLD.id_cliente THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Não é permitido trocar o cliente de uma reserva.';
    END IF;

    IF NEW.data_inicio <> OLD.data_inicio
       OR NEW.data_fim <> OLD.data_fim
       OR NEW.id_veiculo <> OLD.id_veiculo THEN

        IF OLD.status_agendamento NOT IN ('em_analise', 'confirmado') THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Só é possível alterar período ou veículo antes da retirada.';
        END IF;

        IF NEW.data_fim <= NEW.data_inicio THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A devolução precisa ser depois da retirada.';
        END IF;

        IF NEW.data_fim > NEW.data_inicio + INTERVAL 30 DAY THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'O período máximo de uma reserva é de 30 dias.';
        END IF;

        IF EXISTS (SELECT 1
                     FROM Agendamento a
                    WHERE a.id_veiculo = NEW.id_veiculo
                      AND a.id_agendamento <> OLD.id_agendamento
                      AND a.status_agendamento IN ('em_analise', 'confirmado', 'em_andamento')
                      AND a.data_inicio < NEW.data_fim
                      AND a.data_fim    > NEW.data_inicio) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'O veículo já está reservado neste período.';
        END IF;

        IF EXISTS (SELECT 1
                     FROM Inspecao i
                    WHERE i.id_veiculo = NEW.id_veiculo
                      AND i.status_inspecao <> 'concluida'
                      AND i.data_inicio < NEW.data_fim
                      AND (i.data_fim IS NULL OR i.data_fim > NEW.data_inicio)) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'O veículo estará em inspeção neste período.';
        END IF;
    END IF;
END$$

-- ==========================================
-- TRIGGER: VALIDAR INÍCIO DA LOCAÇÃO
-- Tabela/evento: Locacao, BEFORE INSERT
-- Consistência agendamento x locação: só abre locação para reserva
-- confirmada, dentro do período reservado, e sempre como em_andamento.
-- ==========================================
CREATE TRIGGER trg_locacao_antes_inserir
BEFORE INSERT ON Locacao
FOR EACH ROW
BEGIN
    DECLARE v_status   VARCHAR(50);
    DECLARE v_data_fim DATETIME;

    SELECT status_agendamento, data_fim
      INTO v_status, v_data_fim
      FROM Agendamento
     WHERE id_agendamento = NEW.id_agendamento;

    IF v_status IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Reserva não encontrada.';
    END IF;
    IF v_status <> 'confirmado' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A locação só pode começar a partir de uma reserva confirmada.';
    END IF;
    IF NEW.data_retirada >= v_data_fim THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A retirada precisa acontecer antes do fim do período reservado.';
    END IF;

    SET NEW.status_locacao      = 'em_andamento';
    SET NEW.data_devolucao      = NULL;
    SET NEW.tempo_locacao_horas = NULL;
    SET NEW.valor_total         = NULL;
END$$

-- ==========================================
-- TRIGGER: AUTOMATIZAR INÍCIO DA LOCAÇÃO
-- Tabela/evento: Locacao, AFTER INSERT
-- A reserva passa para em_andamento e o veículo para locado.
-- ==========================================
CREATE TRIGGER trg_locacao_depois_inserir
AFTER INSERT ON Locacao
FOR EACH ROW
BEGIN
    UPDATE Agendamento
       SET status_agendamento = 'em_andamento'
     WHERE id_agendamento = NEW.id_agendamento;

    UPDATE Veiculo v
      JOIN Agendamento a ON a.id_veiculo = v.id_veiculo
       SET v.status_veiculo = 'locado'
     WHERE a.id_agendamento = NEW.id_agendamento;
END$$

-- ==========================================
-- TRIGGER: CALCULAR O FECHAMENTO DA LOCAÇÃO
-- Tabela/evento: Locacao, BEFORE UPDATE
-- Ao mudar para finalizada: exige a data de devolução (usa NOW() se vier
-- vazia), calcula as horas e o valor (diárias arredondadas para cima,
-- mínimo de 1, vezes o valor da diária). Locação finalizada não reabre.
-- ==========================================
CREATE TRIGGER trg_locacao_antes_atualizar
BEFORE UPDATE ON Locacao
FOR EACH ROW
BEGIN
    DECLARE v_valor_diaria DECIMAL(10,2);
    DECLARE v_minutos      INT;

    IF NEW.id_agendamento <> OLD.id_agendamento THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Não é permitido trocar a reserva de uma locação.';
    END IF;

    IF OLD.status_locacao = 'finalizada' AND NEW.status_locacao <> 'finalizada' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Uma locação finalizada não pode ser reaberta.';
    END IF;

    IF NEW.status_locacao = 'finalizada' AND OLD.status_locacao = 'em_andamento' THEN
        IF NEW.data_devolucao IS NULL THEN
            SET NEW.data_devolucao = NOW();
        END IF;
        IF NEW.data_devolucao <= NEW.data_retirada THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A devolução precisa ser depois da retirada.';
        END IF;

        SELECT v.valor_diaria
          INTO v_valor_diaria
          FROM Agendamento a
          JOIN Veiculo v ON v.id_veiculo = a.id_veiculo
         WHERE a.id_agendamento = NEW.id_agendamento;

        SET v_minutos = TIMESTAMPDIFF(MINUTE, NEW.data_retirada, NEW.data_devolucao);
        SET NEW.tempo_locacao_horas = ROUND(v_minutos / 60, 2);
        SET NEW.valor_total = GREATEST(1, CEIL(v_minutos / 1440)) * v_valor_diaria;
    END IF;
END$$

-- ==========================================
-- TRIGGER: AUTOMATIZAR FIM DA LOCAÇÃO
-- Tabela/evento: Locacao, AFTER UPDATE
-- Ao finalizar: a reserva vai para concluido e o veículo volta para
-- disponivel (só se ainda estiver como locado, para não apagar uma
-- marcação de manutenção feita pela equipe).
-- ==========================================
CREATE TRIGGER trg_locacao_depois_atualizar
AFTER UPDATE ON Locacao
FOR EACH ROW
BEGIN
    IF NEW.status_locacao = 'finalizada' AND OLD.status_locacao = 'em_andamento' THEN
        UPDATE Agendamento
           SET status_agendamento = 'concluido'
         WHERE id_agendamento = NEW.id_agendamento;

        UPDATE Veiculo v
          JOIN Agendamento a ON a.id_veiculo = v.id_veiculo
           SET v.status_veiculo = 'disponivel'
         WHERE a.id_agendamento = NEW.id_agendamento
           AND v.status_veiculo = 'locado';
    END IF;
END$$

-- ==========================================
-- TRIGGER: VALIDAR PAGAMENTO
-- Tabela/evento: Pagamento, BEFORE INSERT
-- Consistência locação x pagamento: o valor pago não pode passar do
-- valor total da locação (quando já calculado) e um pagamento "pago"
-- recebe a data atual se vier sem data.
-- ==========================================
CREATE TRIGGER trg_pagamento_antes_inserir
BEFORE INSERT ON Pagamento
FOR EACH ROW
BEGIN
    DECLARE v_valor_total DECIMAL(10,2);

    SELECT valor_total INTO v_valor_total
      FROM Locacao
     WHERE id_locacao = NEW.id_locacao;

    IF v_valor_total IS NOT NULL AND NEW.valor_pago > v_valor_total THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'O valor pago é maior que o valor total da locação.';
    END IF;

    IF NEW.status_pagamento = 'pago' AND NEW.data_pagamento IS NULL THEN
        SET NEW.data_pagamento = NOW();
    END IF;
END$$

DELIMITER ;


-- ============================================================================
-- ETAPA 9 — CONSULTAS DE VERIFICAÇÃO (11)
-- Rode depois do script para conferir se tudo foi criado.
-- ============================================================================

-- 11.1 Tabelas criadas (esperado: 9 linhas)
SELECT TABLE_NAME AS tabela, TABLE_ROWS AS linhas_aprox
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
 ORDER BY TABLE_NAME;

-- 11.2 Chaves estrangeiras (esperado: 8 linhas)
SELECT TABLE_NAME AS tabela, CONSTRAINT_NAME AS fk, REFERENCED_TABLE_NAME AS referencia
  FROM information_schema.REFERENTIAL_CONSTRAINTS
 WHERE CONSTRAINT_SCHEMA = DATABASE()
 ORDER BY TABLE_NAME;

-- 11.3 Restrições CHECK
SELECT CONSTRAINT_NAME AS restricao, CHECK_CLAUSE AS regra
  FROM information_schema.CHECK_CONSTRAINTS
 WHERE CONSTRAINT_SCHEMA = DATABASE()
 ORDER BY CONSTRAINT_NAME;

-- 11.4 Procedures (esperado: 4) e triggers (esperado: 7)
SELECT ROUTINE_NAME AS procedure_criada
  FROM information_schema.ROUTINES
 WHERE ROUTINE_SCHEMA = DATABASE() AND ROUTINE_TYPE = 'PROCEDURE';

SELECT TRIGGER_NAME AS trigger_criada, EVENT_OBJECT_TABLE AS tabela,
       ACTION_TIMING AS momento, EVENT_MANIPULATION AS evento
  FROM information_schema.TRIGGERS
 WHERE TRIGGER_SCHEMA = DATABASE()
 ORDER BY EVENT_OBJECT_TABLE, ACTION_TIMING, EVENT_MANIPULATION;

-- 11.5 Consistência dos dados (esperado: nenhuma linha em cada consulta)
-- Reservas ativas sobrepostas para o mesmo veículo
SELECT a1.id_agendamento, a2.id_agendamento AS conflita_com, a1.id_veiculo
  FROM Agendamento a1
  JOIN Agendamento a2
    ON a2.id_veiculo = a1.id_veiculo
   AND a2.id_agendamento > a1.id_agendamento
   AND a1.status_agendamento IN ('em_analise', 'confirmado', 'em_andamento')
   AND a2.status_agendamento IN ('em_analise', 'confirmado', 'em_andamento')
   AND a1.data_inicio < a2.data_fim
   AND a1.data_fim    > a2.data_inicio;

-- Locação em andamento cuja reserva não está em andamento
SELECT l.id_locacao, a.status_agendamento
  FROM Locacao l JOIN Agendamento a ON a.id_agendamento = l.id_agendamento
 WHERE l.status_locacao = 'em_andamento' AND a.status_agendamento <> 'em_andamento';

-- Pagamento maior que o valor da locação
SELECT p.id_pagamento, p.valor_pago, l.valor_total
  FROM Pagamento p JOIN Locacao l ON l.id_locacao = p.id_locacao
 WHERE l.valor_total IS NOT NULL AND p.valor_pago > l.valor_total;


-- ============================================================================
-- ETAPA 10 — PRÓXIMOS PASSOS
--   * Atualize valor_diaria dos veículos antigos (ETAPA 3).
--   * Execute 03_testes_validacao.sql para validar a atualização.
-- ============================================================================
-- Fim do script de atualização
-- ============================================================================
