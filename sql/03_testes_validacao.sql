-- ============================================================================
-- QUANTUM DRIVE — PIM IV
-- Arquivo ......: 03_testes_validacao.sql
-- Finalidade ...: validar tabelas, restrições, procedures e triggers.
-- Pré-requisito : banco criado pelo 01 (ou atualizado pelo 02).
--
-- Como funciona:
--   * Todos os dados são fictícios (e-mails @teste.quantumdrive.local,
--     placas começando com TST). Nenhum dado real é usado.
--   * Cada teste grava PASSOU ou FALHOU na tabela temporária tmp_resultado.
--     Os testes que devem dar erro são executados pelo procedimento
--     qd_teste_espera_erro, que captura a mensagem e continua o script.
--     Por isso o arquivo roda inteiro mesmo com as tentativas inválidas.
--   * No fim aparecem o resumo e a lista de testes. Esperado: 0 FALHOU.
--   * Os dados de teste são apagados no começo e no fim do script.
--   * Alguns testes chamam procedures que devolvem tabelas (CALL); o
--     resultado esperado está escrito no comentário logo acima.
-- ============================================================================

USE locacao_veiculos_eletricos;
SET NAMES utf8mb4;
SET SQL_SAFE_UPDATES = 0;   -- só nesta sessão: permite apagar os dados de teste

-- ----------------------------------------------------------------------------
-- Preparação: tabela de resultados e procedimentos auxiliares de teste
-- ----------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_resultado;
CREATE TEMPORARY TABLE tmp_resultado (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    teste      VARCHAR(120) NOT NULL,
    resultado  VARCHAR(10)  NOT NULL,
    detalhe    VARCHAR(500) NULL
);

DROP PROCEDURE IF EXISTS qd_teste_ok;
DROP PROCEDURE IF EXISTS qd_teste_espera_erro;
DROP PROCEDURE IF EXISTS qd_limpar_dados_teste;

DELIMITER $$

-- Registra um teste cujo resultado é uma condição verdadeira/falsa
CREATE PROCEDURE qd_teste_ok (IN p_teste VARCHAR(120), IN p_ok BOOLEAN, IN p_detalhe VARCHAR(500))
BEGIN
    INSERT INTO tmp_resultado (teste, resultado, detalhe)
    VALUES (p_teste, IF(p_ok, 'PASSOU', 'FALHOU'), p_detalhe);
END$$

-- Executa um comando que DEVE falhar e confere a mensagem de erro
CREATE PROCEDURE qd_teste_espera_erro (IN p_teste VARCHAR(120), IN p_sql TEXT, IN p_msg_esperada VARCHAR(200))
BEGIN
    DECLARE v_msg TEXT DEFAULT NULL;
    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION
        GET DIAGNOSTICS CONDITION 1 v_msg = MESSAGE_TEXT;

    SET @qd_sql = p_sql;
    PREPARE qd_stmt FROM @qd_sql;
    EXECUTE qd_stmt;
    DEALLOCATE PREPARE qd_stmt;

    INSERT INTO tmp_resultado (teste, resultado, detalhe)
    VALUES (p_teste,
            IF(v_msg IS NOT NULL AND LOCATE(p_msg_esperada, v_msg) > 0, 'PASSOU', 'FALHOU'),
            COALESCE(CONCAT('Erro recebido: ', v_msg), 'O comando foi aceito, mas deveria ter sido recusado'));
END$$

-- Apaga apenas os dados fictícios criados por este arquivo
CREATE PROCEDURE qd_limpar_dados_teste ()
BEGIN
    DELETE p FROM Pagamento p
      JOIN Locacao l     ON l.id_locacao = p.id_locacao
      JOIN Agendamento a ON a.id_agendamento = l.id_agendamento
      JOIN Cliente c     ON c.id_cliente = a.id_cliente
     WHERE c.email LIKE '%@teste.quantumdrive.local';

    DELETE l FROM Locacao l
      JOIN Agendamento a ON a.id_agendamento = l.id_agendamento
      JOIN Cliente c     ON c.id_cliente = a.id_cliente
     WHERE c.email LIKE '%@teste.quantumdrive.local';

    DELETE a FROM Agendamento a
      JOIN Cliente c ON c.id_cliente = a.id_cliente
     WHERE c.email LIKE '%@teste.quantumdrive.local';

    DELETE m FROM MensagemChat m
      JOIN AtendimentoChat ch ON ch.id_chat = m.id_chat
      JOIN Cliente c          ON c.id_cliente = ch.id_cliente
     WHERE c.email LIKE '%@teste.quantumdrive.local';

    DELETE ch FROM AtendimentoChat ch
      JOIN Cliente c ON c.id_cliente = ch.id_cliente
     WHERE c.email LIKE '%@teste.quantumdrive.local';

    DELETE i FROM Inspecao i
      JOIN Veiculo v ON v.id_veiculo = i.id_veiculo
     WHERE v.placa LIKE 'TST%';

    DELETE FROM Veiculo          WHERE placa LIKE 'TST%';
    DELETE FROM CategoriaVeiculo WHERE nome_categoria LIKE 'TESTE %';
    DELETE FROM Cliente          WHERE email LIKE '%@teste.quantumdrive.local';
END$$

DELIMITER ;

CALL qd_limpar_dados_teste();

-- ----------------------------------------------------------------------------
-- Dados fictícios de base
-- ----------------------------------------------------------------------------
INSERT INTO CategoriaVeiculo (nome_categoria, descricao)
VALUES ('TESTE Hatch', 'Categoria fictícia para testes');
SET @cat = LAST_INSERT_ID();

INSERT INTO Veiculo (id_categoria, marca, modelo, placa, ano, autonomia_km, tipo_cambio,
                     possui_som, nivel_conforto, adequado_viagem, status_veiculo, valor_diaria)
VALUES (@cat, 'BYD', 'Dolphin Mini', 'TST1A01', 2025, 280, 'Automático', TRUE, 'Básico', FALSE, 'disponivel', 159.00);
SET @v1 = LAST_INSERT_ID();

INSERT INTO Veiculo (id_categoria, marca, modelo, placa, ano, autonomia_km, tipo_cambio,
                     possui_som, nivel_conforto, adequado_viagem, status_veiculo, valor_diaria)
VALUES (@cat, 'BYD', 'Dolphin', 'TST1A02', 2025, 291, 'Automático', TRUE, 'Médio', TRUE, 'disponivel', 189.00);
SET @v2 = LAST_INSERT_ID();

INSERT INTO Veiculo (id_categoria, marca, modelo, placa, ano, autonomia_km, tipo_cambio,
                     possui_som, nivel_conforto, adequado_viagem, status_veiculo, valor_diaria)
VALUES (@cat, 'BYD', 'Seal', 'TST1A03', 2025, 372, 'Automático', TRUE, 'Alto', TRUE, 'manutencao', 349.00);
SET @v3 = LAST_INSERT_ID();

-- Período-base: daqui a 10 dias, às 10h, por 3 dias
SET @ini = TIMESTAMP(CURDATE() + INTERVAL 10 DAY, '10:00:00');
SET @fim = @ini + INTERVAL 3 DAY;


-- ============================================================================
-- BLOCO A — CLIENTES
-- ============================================================================

-- T01 Inserção de cliente válido
-- Esperado: 1 cliente inserido. Falha se: qualquer erro.
INSERT INTO Cliente (nome, email, telefone, cpf, data_nascimento, senha_hash)
VALUES ('Ana Teste Silva', 'ana@teste.quantumdrive.local', '(11) 90000-0001', '000.000.001-01',
        '1995-04-10', '$2b$12$hashficticiogeradopelaapi.................................');
SET @c1 = LAST_INSERT_ID();
CALL qd_teste_ok('T01 Inserir cliente válido', @c1 > 0, CONCAT('id_cliente = ', @c1));

INSERT INTO Cliente (nome, email, telefone, cpf)
VALUES ('Bruno Teste Souza', 'bruno@teste.quantumdrive.local', '(21) 90000-0002', '000.000.002-02');
SET @c2 = LAST_INSERT_ID();

-- T02 E-mail repetido
-- Esperado: erro "Duplicate entry". Falha se: o cliente for aceito.
CALL qd_teste_espera_erro('T02 Recusar e-mail repetido',
    'INSERT INTO Cliente (nome, email, cpf) VALUES (''Outra Pessoa'', ''ana@teste.quantumdrive.local'', ''000.000.003-03'')',
    'Duplicate entry');

-- T03 CPF com quantidade errada de dígitos
-- Esperado: erro da restrição chk_cliente_cpf. Falha se: o cliente for aceito.
CALL qd_teste_espera_erro('T03 Recusar CPF inválido',
    'INSERT INTO Cliente (nome, email, cpf) VALUES (''Carla Teste'', ''carla@teste.quantumdrive.local'', ''123.456'')',
    'chk_cliente_cpf');


-- ============================================================================
-- BLOCO B — VEÍCULOS E DISPONIBILIDADE
-- ============================================================================

-- T04 Consulta de veículos
-- Esperado: 3 veículos de teste cadastrados. Falha se: número diferente de 3.
SELECT COUNT(*) INTO @qtd FROM Veiculo WHERE placa LIKE 'TST%';
CALL qd_teste_ok('T04 Consultar veículos cadastrados', @qtd = 3, CONCAT(@qtd, ' veículo(s)'));

-- T05 Disponibilidade sem nenhuma reserva (visual)
-- Esperado: entre os veículos de teste aparecem TST1A01 e TST1A02, com
-- quantidade_diarias = 3. TST1A03 NÃO aparece (está em manutenção).
-- Falha se: TST1A03 aparecer ou faltar TST1A01/TST1A02.
CALL sp_consultar_veiculos_disponiveis(@ini, @fim, @cat);

-- T06 Disponibilidade com período invertido
-- Esperado: erro "A data de fim precisa ser depois...". Falha se: devolver lista.
CALL qd_teste_espera_erro('T06 Recusar consulta com período invertido',
    'CALL sp_consultar_veiculos_disponiveis(@fim, @ini, NULL)',
    'precisa ser depois');


-- ============================================================================
-- BLOCO C — AGENDAMENTOS (procedure + triggers)
-- ============================================================================

-- T07 Criar agendamento pela procedure
-- Esperado: @ag1 preenchido e status em_analise. Falha se: @ag1 NULL.
CALL sp_registrar_agendamento(@c1, @v1, @ini, @fim, NULL, @ag1);
SELECT status_agendamento INTO @st FROM Agendamento WHERE id_agendamento = @ag1;
CALL qd_teste_ok('T07 Registrar agendamento válido', @ag1 IS NOT NULL AND @st = 'em_analise',
                 CONCAT('id = ', COALESCE(@ag1, 'NULL'), ', status = ', COALESCE(@st, 'NULL')));

-- T08 Devolução antes da retirada
-- Esperado: erro da trigger "A devolução precisa ser depois da retirada."
CALL qd_teste_espera_erro('T08 Recusar devolução antes da retirada',
    'CALL sp_registrar_agendamento(@c2, @v2, @fim, @ini, NULL, @x)',
    'A devolução precisa ser depois da retirada');

-- T09 Retirada no passado
-- Esperado: erro "...data que já passou."
CALL qd_teste_espera_erro('T09 Recusar retirada no passado',
    'CALL sp_registrar_agendamento(@c2, @v2, NOW() - INTERVAL 2 DAY, NOW() + INTERVAL 1 DAY, NULL, @x)',
    'já passou');

-- T10 Período maior que 30 dias
-- Esperado: erro "O período máximo de uma reserva é de 30 dias."
CALL qd_teste_espera_erro('T10 Recusar reserva com mais de 30 dias',
    'CALL sp_registrar_agendamento(@c2, @v2, @ini, @ini + INTERVAL 31 DAY, NULL, @x)',
    '30 dias');

-- T11 Conflito parcial: começa no meio de uma reserva existente
-- Esperado: erro "O veículo já está reservado neste período."
CALL qd_teste_espera_erro('T11 Recusar sobreposição parcial',
    'CALL sp_registrar_agendamento(@c2, @v1, @ini + INTERVAL 1 DAY, @fim + INTERVAL 1 DAY, NULL, @x)',
    'já está reservado');

-- T12 Conflito total: a nova reserva engloba a existente
-- Esperado: o mesmo erro de sobreposição (não basta comparar datas iguais).
CALL qd_teste_espera_erro('T12 Recusar reserva que engloba outra',
    'CALL sp_registrar_agendamento(@c2, @v1, @ini - INTERVAL 1 DAY, @fim + INTERVAL 1 DAY, NULL, @x)',
    'já está reservado');

-- T13 Conflito por INSERT direto (sem a procedure): a trigger também protege
-- Esperado: erro de sobreposição.
CALL qd_teste_espera_erro('T13 Trigger bloqueia conflito em INSERT direto',
    'INSERT INTO Agendamento (id_cliente, id_veiculo, data_inicio, data_fim) VALUES (@c2, @v1, @ini + INTERVAL 2 HOUR, @ini + INTERVAL 5 HOUR)',
    'já está reservado');

-- T14 Reserva encostada: começa exatamente quando a outra termina
-- Esperado: aceita (os períodos não se sobrepõem). Falha se: der erro.
CALL sp_registrar_agendamento(@c2, @v1, @fim, @fim + INTERVAL 2 DAY, NULL, @ag2);
CALL qd_teste_ok('T14 Aceitar reserva que começa no fim da outra', @ag2 IS NOT NULL,
                 CONCAT('id = ', COALESCE(@ag2, 'NULL')));

-- T15 Veículo em manutenção
-- Esperado: erro "Este veículo não está disponível para reserva."
CALL qd_teste_espera_erro('T15 Recusar veículo em manutenção',
    'CALL sp_registrar_agendamento(@c2, @v3, @ini, @fim, NULL, @x)',
    'não está disponível');

-- T16 Cliente inexistente
-- Esperado: erro "Cliente não encontrado."
CALL qd_teste_espera_erro('T16 Recusar cliente inexistente',
    'CALL sp_registrar_agendamento(-1, @v2, @ini, @fim, NULL, @x)',
    'Cliente não encontrado');

-- T17 Inspeção aberta bloqueia a reserva
-- Esperado: erro "O veículo estará em inspeção neste período."
INSERT INTO Inspecao (id_veiculo, data_inicio, data_fim, status_inspecao, observacoes)
VALUES (@v2, @ini + INTERVAL 20 DAY, NULL, 'agendada', 'Revisão fictícia de teste');
SET @insp = LAST_INSERT_ID();
CALL qd_teste_espera_erro('T17 Recusar reserva durante inspeção',
    'CALL sp_registrar_agendamento(@c2, @v2, @ini + INTERVAL 21 DAY, @ini + INTERVAL 23 DAY, NULL, @x)',
    'inspeção');

-- T18 Mudança de status inválida (em_analise direto para concluido)
-- Esperado: erro "Mudança de status da reserva não permitida."
CALL qd_teste_espera_erro('T18 Recusar mudança de status inválida',
    'UPDATE Agendamento SET status_agendamento = ''concluido'' WHERE id_agendamento = @ag1',
    'não permitida');

-- T19 Consulta de agendamentos do cliente (visual)
-- Esperado: 1 linha para Ana (TST1A01, em_analise), sem locação nem pagamento.
CALL sp_consultar_agendamentos_cliente(@c1, NULL);

-- T20 Cancelar libera o período
-- Esperado: depois de cancelar a reserva de Bruno (@ag2), outra reserva no
-- mesmo período é aceita.
UPDATE Agendamento SET status_agendamento = 'cancelado' WHERE id_agendamento = @ag2;
CALL sp_registrar_agendamento(@c1, @v1, @fim, @fim + INTERVAL 2 DAY, 'teste', @ag3);
CALL qd_teste_ok('T20 Período liberado após cancelamento', @ag3 IS NOT NULL,
                 CONCAT('id = ', COALESCE(@ag3, 'NULL')));


-- ============================================================================
-- BLOCO D — LOCAÇÕES (triggers de status e cálculo)
-- ============================================================================

-- Reserva de Bruno no veículo 2, começando daqui a 1 minuto, por 3 dias
CALL sp_registrar_agendamento(@c2, @v2, NOW() + INTERVAL 1 MINUTE, NOW() + INTERVAL 3 DAY, NULL, @ag_loc);
SET @retirada = NOW();

-- T21 Locação a partir de reserva ainda em análise
-- Esperado: erro "A locação só pode começar a partir de uma reserva confirmada."
CALL qd_teste_espera_erro('T21 Recusar locação de reserva não confirmada',
    'INSERT INTO Locacao (id_agendamento, data_retirada) VALUES (@ag_loc, @retirada)',
    'reserva confirmada');

-- T22 Registrar locação de reserva confirmada
-- Esperado: locação em_andamento, reserva em_andamento, veículo locado.
UPDATE Agendamento SET status_agendamento = 'confirmado' WHERE id_agendamento = @ag_loc;
INSERT INTO Locacao (id_agendamento, data_retirada) VALUES (@ag_loc, @retirada);
SET @loc = LAST_INSERT_ID();
SELECT l.status_locacao, a.status_agendamento, v.status_veiculo
  INTO @st_loc, @st_ag, @st_v
  FROM Locacao l
  JOIN Agendamento a ON a.id_agendamento = l.id_agendamento
  JOIN Veiculo v     ON v.id_veiculo = a.id_veiculo
 WHERE l.id_locacao = @loc;
CALL qd_teste_ok('T22 Iniciar locação atualiza reserva e veículo',
                 @st_loc = 'em_andamento' AND @st_ag = 'em_andamento' AND @st_v = 'locado',
                 CONCAT_WS(' / ', @st_loc, @st_ag, @st_v));

-- T23 Segunda locação para a mesma reserva
-- Esperado: recusada (a reserva já está em andamento).
CALL qd_teste_espera_erro('T23 Recusar segunda locação da mesma reserva',
    'INSERT INTO Locacao (id_agendamento, data_retirada) VALUES (@ag_loc, NOW())',
    'reserva confirmada');

-- T24 Finalizar locação: cálculo automático
-- 51 horas -> 3 diárias (arredonda para cima) x R$ 189,00 = R$ 567,00
-- Esperado: 51.00 h, R$ 567.00, reserva concluido, veículo disponivel.
UPDATE Locacao
   SET status_locacao = 'finalizada', data_devolucao = @retirada + INTERVAL 51 HOUR
 WHERE id_locacao = @loc;
SELECT l.tempo_locacao_horas, l.valor_total, a.status_agendamento, v.status_veiculo
  INTO @horas, @valor, @st_ag, @st_v
  FROM Locacao l
  JOIN Agendamento a ON a.id_agendamento = l.id_agendamento
  JOIN Veiculo v     ON v.id_veiculo = a.id_veiculo
 WHERE l.id_locacao = @loc;
CALL qd_teste_ok('T24 Finalizar locação calcula valor e libera veículo',
                 @horas = 51.00 AND @valor = 567.00 AND @st_ag = 'concluido' AND @st_v = 'disponivel',
                 CONCAT_WS(' / ', @horas, @valor, @st_ag, @st_v));

-- T25 Reabrir locação finalizada
-- Esperado: erro "Uma locação finalizada não pode ser reaberta."
CALL qd_teste_espera_erro('T25 Recusar reabrir locação finalizada',
    'UPDATE Locacao SET status_locacao = ''em_andamento'' WHERE id_locacao = @loc',
    'não pode ser reaberta');


-- ============================================================================
-- BLOCO E — PAGAMENTOS
-- ============================================================================

-- T26 Pagamento maior que o total
-- Esperado: erro "O valor pago é maior que o valor total da locação."
CALL qd_teste_espera_erro('T26 Recusar pagamento acima do total',
    'INSERT INTO Pagamento (id_locacao, valor_pago, forma_pagamento, status_pagamento) VALUES (@loc, 999.00, ''pix'', ''pago'')',
    'maior que o valor total');

-- T27 Forma de pagamento não aceita
-- Esperado: erro da restrição chk_pagamento_forma.
CALL qd_teste_espera_erro('T27 Recusar forma de pagamento inválida',
    'INSERT INTO Pagamento (id_locacao, valor_pago, forma_pagamento) VALUES (@loc, 100.00, ''cheque'')',
    'chk_pagamento_forma');

-- T28 Registrar pagamento válido
-- Esperado: pagamento pago, com data preenchida pela trigger.
INSERT INTO Pagamento (id_locacao, valor_pago, forma_pagamento, status_pagamento)
VALUES (@loc, 567.00, 'pix', 'pago');
SELECT data_pagamento INTO @dt_pag FROM Pagamento WHERE id_locacao = @loc;
CALL qd_teste_ok('T28 Registrar pagamento válido', @dt_pag IS NOT NULL,
                 CONCAT('data_pagamento = ', COALESCE(@dt_pag, 'NULL')));

-- T29 Segundo pagamento para a mesma locação
-- Esperado: erro "Duplicate entry" (um pagamento por locação).
CALL qd_teste_espera_erro('T29 Recusar segundo pagamento da locação',
    'INSERT INTO Pagamento (id_locacao, valor_pago, forma_pagamento) VALUES (@loc, 10.00, ''pix'')',
    'Duplicate entry');

-- T30 Relatório (visual)
-- Esperado no 1º resultado: uma linha do mês atual com pelo menos 1 locação
-- finalizada, valor_faturado >= 567.00 e valor_recebido >= 567.00.
-- No 2º, TST1A02 com 1 locação e 51.00 horas. No 3º, pix / pago.
CALL sp_relatorio_locacoes_pagamentos(CURDATE(), CURDATE());

-- T31 Relatório com datas invertidas
-- Esperado: erro "A data final precisa ser igual ou depois..."
CALL qd_teste_espera_erro('T31 Recusar relatório com datas invertidas',
    'CALL sp_relatorio_locacoes_pagamentos(CURDATE(), CURDATE() - INTERVAL 1 DAY)',
    'data final');


-- ============================================================================
-- BLOCO F — ATENDIMENTO POR CHAT
-- ============================================================================

-- T32 Abrir atendimento e registrar mensagem
-- Esperado: 1 atendimento aberto com 1 mensagem.
INSERT INTO AtendimentoChat (id_cliente, tipo_triagem) VALUES (@c1, 'duvida_reserva');
SET @chat = LAST_INSERT_ID();
INSERT INTO MensagemChat (id_chat, remetente, conteudo) VALUES (@chat, 'cliente', 'Olá, posso retirar o carro mais cedo?');
SELECT COUNT(*) INTO @qtd FROM MensagemChat WHERE id_chat = @chat;
CALL qd_teste_ok('T32 Registrar atendimento e mensagem', @qtd = 1, CONCAT(@qtd, ' mensagem(ns)'));

-- T33 Remetente inválido
-- Esperado: erro da restrição chk_mensagem_remetente.
CALL qd_teste_espera_erro('T33 Recusar remetente inválido',
    'INSERT INTO MensagemChat (id_chat, remetente, conteudo) VALUES (@chat, ''robo'', ''teste'')',
    'chk_mensagem_remetente');


-- ============================================================================
-- BLOCO G — INTEGRIDADE REFERENCIAL
-- ============================================================================

-- T34 Apagar cliente que tem reservas
-- Esperado: erro de chave estrangeira ("foreign key constraint fails").
CALL qd_teste_espera_erro('T34 Impedir apagar cliente com histórico',
    'DELETE FROM Cliente WHERE id_cliente = @c1',
    'foreign key constraint fails');

-- T35 Reserva para veículo inexistente (INSERT direto)
-- Esperado: erro de chave estrangeira.
CALL qd_teste_espera_erro('T35 Recusar reserva de veículo inexistente',
    'INSERT INTO Agendamento (id_cliente, id_veiculo, data_inicio, data_fim) VALUES (@c1, -1, @ini + INTERVAL 40 DAY, @ini + INTERVAL 41 DAY)',
    'foreign key constraint fails');


-- ============================================================================
-- RESULTADO FINAL
-- Esperado: total_falhou = 0.
-- ============================================================================
SELECT COUNT(*)                       AS total_testes,
       SUM(resultado = 'PASSOU')      AS total_passou,
       SUM(resultado = 'FALHOU')      AS total_falhou
  FROM tmp_resultado;

SELECT id, teste, resultado, detalhe FROM tmp_resultado ORDER BY id;

-- Limpeza: apaga os dados fictícios e os procedimentos auxiliares.
-- (Para inspecionar os dados de teste antes de apagar, comente as linhas abaixo.)
CALL qd_limpar_dados_teste();
DROP PROCEDURE IF EXISTS qd_teste_ok;
DROP PROCEDURE IF EXISTS qd_teste_espera_erro;
DROP PROCEDURE IF EXISTS qd_limpar_dados_teste;
