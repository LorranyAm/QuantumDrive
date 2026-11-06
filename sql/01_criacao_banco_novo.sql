-- ============================================================================
-- 1. CABEÇALHO E IDENTIFICAÇÃO DO PROJETO
-- ============================================================================
-- Projeto ......: Quantum Drive — locação de veículos elétricos
-- Disciplina ...: PIM IV — evolução do banco de dados do PIM III
-- SGBD .........: MySQL 8.0 ou superior (compatível com Aiven for MySQL)
-- Arquivo ......: 01_criacao_banco_novo.sql
-- Finalidade ...: criar o banco do zero, já na versão do PIM IV.
--                 Para atualizar um banco do PIM III que já tem dados,
--                 use o arquivo 02_atualizacao_banco_pim3.sql.
--
-- Como executar:
--   MySQL Workbench: abra o arquivo e execute tudo (raio sem cursor, Ctrl+Shift+Enter).
--   Linha de comando: mysql -h <host> -P <porta> -u <usuario> -p < 01_criacao_banco_novo.sql
--
-- Observações para a Aiven:
--   * A Aiven exige chave primária em toda tabela (sql_require_primary_key = ON).
--     Por isso as PKs são declaradas dentro do CREATE TABLE.
--   * CREATE DATABASE funciona com o usuário administrador do serviço (avnadmin).
--     Se o seu usuário não puder criar bancos, crie o banco pelo console da Aiven
--     e execute o script a partir da seção 3.
--   * CREATE TRIGGER, com o log binário ligado (padrão na Aiven), exige um usuário
--     com privilégio para isso (SUPER ou SET_USER_ID). Se aparecer o erro 1419,
--     execute as triggers com o usuário avnadmin.
--   * O script não usa DEFINER, SUPER, SET GLOBAL nem funções armazenadas.
-- ============================================================================


-- ============================================================================
-- 2. CONFIGURAÇÃO DO BANCO DE DADOS
-- ============================================================================
CREATE DATABASE IF NOT EXISTS locacao_veiculos_eletricos
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE locacao_veiculos_eletricos;

-- Configurações só desta sessão (não exigem privilégio de administrador)
SET NAMES utf8mb4;


-- ============================================================================
-- 3. CRIAÇÃO DAS TABELAS
-- 4. DEFINIÇÃO DOS CAMPOS E TIPOS DE DADOS
-- 5. CHAVES PRIMÁRIAS (declaradas aqui: AUTO_INCREMENT exige que a coluna
--    seja chave, e a Aiven não aceita tabela sem PK)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Cliente: pessoa que se cadastra e faz reservas (Web e Mobile).
-- Novos no PIM IV: data_nascimento, senha_hash, data_cadastro.
-- ----------------------------------------------------------------------------
CREATE TABLE Cliente (
    id_cliente                  INT            NOT NULL AUTO_INCREMENT,
    nome                        VARCHAR(100)   NOT NULL,
    email                       VARCHAR(150)   NOT NULL,
    telefone                    VARCHAR(20)    NULL,
    cpf                         VARCHAR(14)    NOT NULL,        -- 000.000.000-00
    necessidades_acessibilidade TEXT           NULL,
    data_nascimento             DATE           NULL,            -- regra de 18 anos da aplicação
    senha_hash                  VARCHAR(255)   NULL,            -- só o hash gerado pela API (ex.: bcrypt)
    data_cadastro               DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_cliente PRIMARY KEY (id_cliente)
) ENGINE = InnoDB;

-- ----------------------------------------------------------------------------
-- CategoriaVeiculo: Hatch, Sedan, SUV, Picape...
-- ----------------------------------------------------------------------------
CREATE TABLE CategoriaVeiculo (
    id_categoria    INT           NOT NULL AUTO_INCREMENT,
    nome_categoria  VARCHAR(100)  NOT NULL,
    descricao       TEXT          NULL,
    CONSTRAINT pk_categoria_veiculo PRIMARY KEY (id_categoria)
) ENGINE = InnoDB;

-- ----------------------------------------------------------------------------
-- Veiculo: cada carro físico da frota (uma linha por placa).
-- Novo no PIM IV: valor_diaria. status_veiculo passa a ter valores fixos.
-- ----------------------------------------------------------------------------
CREATE TABLE Veiculo (
    id_veiculo       INT            NOT NULL AUTO_INCREMENT,
    id_categoria     INT            NOT NULL,
    marca            VARCHAR(100)   NOT NULL,
    modelo           VARCHAR(100)   NOT NULL,
    placa            VARCHAR(10)    NOT NULL,
    ano              INT            NULL,
    autonomia_km     INT            NULL,
    tipo_cambio      VARCHAR(50)    NULL,
    possui_som       BOOLEAN        NULL,
    nivel_conforto   VARCHAR(50)    NULL,
    adequado_viagem  BOOLEAN        NULL,
    status_veiculo   VARCHAR(50)    NOT NULL DEFAULT 'disponivel',
    valor_diaria     DECIMAL(10,2)  NOT NULL DEFAULT 0.00,
    CONSTRAINT pk_veiculo PRIMARY KEY (id_veiculo)
) ENGINE = InnoDB;

-- ----------------------------------------------------------------------------
-- Agendamento: a reserva feita pelo cliente (período de retirada e devolução).
-- ----------------------------------------------------------------------------
CREATE TABLE Agendamento (
    id_agendamento      INT          NOT NULL AUTO_INCREMENT,
    id_cliente          INT          NOT NULL,
    id_veiculo          INT          NOT NULL,
    data_inicio         DATETIME     NOT NULL,   -- retirada prevista
    data_fim            DATETIME     NOT NULL,   -- devolução prevista
    periodo             VARCHAR(50)  NULL,       -- mantido do PIM III
    status_agendamento  VARCHAR(50)  NOT NULL DEFAULT 'em_analise',
    CONSTRAINT pk_agendamento PRIMARY KEY (id_agendamento)
) ENGINE = InnoDB;

-- ----------------------------------------------------------------------------
-- Locacao: o aluguel efetivo, aberto na retirada do carro.
-- ----------------------------------------------------------------------------
CREATE TABLE Locacao (
    id_locacao           INT            NOT NULL AUTO_INCREMENT,
    id_agendamento       INT            NOT NULL,
    data_retirada        DATETIME       NOT NULL,
    data_devolucao       DATETIME       NULL,          -- preenchida na devolução
    tempo_locacao_horas  DECIMAL(10,2)  NULL,          -- calculado pela trigger
    valor_total          DECIMAL(10,2)  NULL,          -- calculado pela trigger
    status_locacao       VARCHAR(50)    NOT NULL DEFAULT 'em_andamento',
    CONSTRAINT pk_locacao PRIMARY KEY (id_locacao)
) ENGINE = InnoDB;

-- ----------------------------------------------------------------------------
-- Pagamento: um pagamento por locação. Nunca guarda número de cartão nem CVV.
-- ----------------------------------------------------------------------------
CREATE TABLE Pagamento (
    id_pagamento      INT            NOT NULL AUTO_INCREMENT,
    id_locacao        INT            NOT NULL,
    valor_pago        DECIMAL(10,2)  NOT NULL,
    forma_pagamento   VARCHAR(50)    NOT NULL,
    data_pagamento    DATETIME       NULL,
    status_pagamento  VARCHAR(50)    NOT NULL DEFAULT 'pendente',
    CONSTRAINT pk_pagamento PRIMARY KEY (id_pagamento)
) ENGINE = InnoDB;

-- ----------------------------------------------------------------------------
-- Inspecao: vistoria ou manutenção do veículo; enquanto aberta, bloqueia reservas.
-- ----------------------------------------------------------------------------
CREATE TABLE Inspecao (
    id_inspecao      INT          NOT NULL AUTO_INCREMENT,
    id_veiculo       INT          NOT NULL,
    data_inicio      DATETIME     NOT NULL,
    data_fim         DATETIME     NULL,      -- NULL = ainda sem previsão de término
    status_inspecao  VARCHAR(50)  NOT NULL DEFAULT 'agendada',
    observacoes      TEXT         NULL,
    CONSTRAINT pk_inspecao PRIMARY KEY (id_inspecao)
) ENGINE = InnoDB;

-- ----------------------------------------------------------------------------
-- AtendimentoChat: conversa de atendimento aberta por um cliente.
-- ----------------------------------------------------------------------------
CREATE TABLE AtendimentoChat (
    id_chat        INT           NOT NULL AUTO_INCREMENT,
    id_cliente     INT           NOT NULL,
    data_abertura  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status_chat    VARCHAR(50)   NOT NULL DEFAULT 'aberto',
    tipo_triagem   VARCHAR(100)  NULL,
    CONSTRAINT pk_atendimento_chat PRIMARY KEY (id_chat)
) ENGINE = InnoDB;

-- ----------------------------------------------------------------------------
-- MensagemChat: histórico de mensagens de cada atendimento.
-- ----------------------------------------------------------------------------
CREATE TABLE MensagemChat (
    id_mensagem  INT          NOT NULL AUTO_INCREMENT,
    id_chat      INT          NOT NULL,
    remetente    VARCHAR(50)  NOT NULL,
    conteudo     TEXT         NOT NULL,
    data_envio   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_mensagem_chat PRIMARY KEY (id_mensagem)
) ENGINE = InnoDB;


-- ============================================================================
-- 6. CHAVES ESTRANGEIRAS (as mesmas nove do PIM III)
-- ON DELETE RESTRICT: não deixa apagar um registro que tem histórico ligado
-- (ex.: um cliente com reservas). ON UPDATE CASCADE não é usado porque as PKs
-- são AUTO_INCREMENT e não mudam.
-- ============================================================================
ALTER TABLE Veiculo
    ADD CONSTRAINT fk_veiculo_categoria
    FOREIGN KEY (id_categoria) REFERENCES CategoriaVeiculo (id_categoria)
    ON DELETE RESTRICT;

ALTER TABLE Agendamento
    ADD CONSTRAINT fk_agendamento_cliente
    FOREIGN KEY (id_cliente) REFERENCES Cliente (id_cliente)
    ON DELETE RESTRICT;

ALTER TABLE Agendamento
    ADD CONSTRAINT fk_agendamento_veiculo
    FOREIGN KEY (id_veiculo) REFERENCES Veiculo (id_veiculo)
    ON DELETE RESTRICT;

ALTER TABLE Locacao
    ADD CONSTRAINT fk_locacao_agendamento
    FOREIGN KEY (id_agendamento) REFERENCES Agendamento (id_agendamento)
    ON DELETE RESTRICT;

ALTER TABLE Pagamento
    ADD CONSTRAINT fk_pagamento_locacao
    FOREIGN KEY (id_locacao) REFERENCES Locacao (id_locacao)
    ON DELETE RESTRICT;

ALTER TABLE Inspecao
    ADD CONSTRAINT fk_inspecao_veiculo
    FOREIGN KEY (id_veiculo) REFERENCES Veiculo (id_veiculo)
    ON DELETE RESTRICT;

ALTER TABLE AtendimentoChat
    ADD CONSTRAINT fk_chat_cliente
    FOREIGN KEY (id_cliente) REFERENCES Cliente (id_cliente)
    ON DELETE RESTRICT;

ALTER TABLE MensagemChat
    ADD CONSTRAINT fk_mensagem_chat
    FOREIGN KEY (id_chat) REFERENCES AtendimentoChat (id_chat)
    ON DELETE RESTRICT;


-- ============================================================================
-- 7. RESTRIÇÕES DE INTEGRIDADE (UNIQUE e CHECK)
-- CHECK é usado para regras que dependem só da própria linha.
-- Regras que dependem de outras linhas ou da data atual ficam nas triggers
-- (o MySQL não aceita subconsulta nem NOW() dentro de CHECK).
-- ============================================================================

-- UNIQUE do PIM III (mantidos)
ALTER TABLE Cliente   ADD CONSTRAINT uq_cliente_email        UNIQUE (email);
ALTER TABLE Cliente   ADD CONSTRAINT uq_cliente_cpf          UNIQUE (cpf);
ALTER TABLE Veiculo   ADD CONSTRAINT uq_veiculo_placa        UNIQUE (placa);
ALTER TABLE Locacao   ADD CONSTRAINT uq_locacao_agendamento  UNIQUE (id_agendamento);
ALTER TABLE Pagamento ADD CONSTRAINT uq_pagamento_locacao    UNIQUE (id_locacao);

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
-- 8. ÍNDICES
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
-- 9. STORED PROCEDURES
-- Erros de regra de negócio usam SIGNAL SQLSTATE '45000' com mensagem em
-- português, que a API pode repassar ao usuário.
-- ============================================================================
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
-- 10. TRIGGERS
-- Ordem de dependência (sem recursão):
--   Locacao -> atualiza Agendamento e Veiculo
--   Agendamento -> só lê outras tabelas
--   Veiculo, Pagamento -> não alteram outras tabelas
-- Um trigger nunca altera a própria tabela (o MySQL não permite: erro 1442).
-- Tudo roda dentro da mesma transação do comando que disparou o trigger:
-- se o trigger falhar, o comando inteiro é desfeito.
-- ============================================================================
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
-- 11. CONSULTAS DE VERIFICAÇÃO
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
-- 12. EXEMPLOS DE EXECUÇÃO E TESTES
-- Os testes completos, com dados fictícios, resultado esperado e resultado
-- de falha, estão no arquivo 03_testes_validacao.sql. Exemplos rápidos:
-- ============================================================================
-- CALL sp_consultar_veiculos_disponiveis('2026-12-10 10:00:00', '2026-12-13 10:00:00', NULL);
--
-- CALL sp_registrar_agendamento(1, 1, '2026-12-10 10:00:00', '2026-12-13 10:00:00', NULL, @novo_id);
-- SELECT @novo_id;
--
-- CALL sp_consultar_agendamentos_cliente(1, NULL);
--
-- CALL sp_relatorio_locacoes_pagamentos('2026-01-01', '2026-12-31');
-- ============================================================================
-- Fim do script de criação
-- ============================================================================
