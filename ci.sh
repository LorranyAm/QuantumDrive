#!/usr/bin/env bash
set -u
M="docker exec -i db mysql --default-character-set=utf8mb4 -uroot -proot"
sec(){ echo; echo "################ $* ################"; }
falhas=0
reset(){ $M -e "DROP DATABASE IF EXISTS locacao_veiculos_eletricos;" 2>&1 | grep -v Warning; }
docker exec db mysql -uroot -proot -e "SELECT VERSION() versao, @@sql_require_primary_key, @@log_bin, @@log_bin_trust_function_creators" 2>&1 | grep -v Warning

sec "A. SCRIPT ORIGINAL DO PIM III (sem alterações)"
reset
$M -t < sql/00_original_pim3.sql 2>&1 | grep -v Warning | head -5
echo "tabelas criadas pelo original:"; $M -N -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='locacao_veiculos_eletricos'" 2>&1 | grep -v Warning

sec "A2. ORIGINAL com sql_require_primary_key=OFF (para isolar o erro 1075)"
reset
( echo "SET SESSION sql_require_primary_key=0;"; cat sql/00_original_pim3.sql ) | $M -t 2>&1 | grep -v Warning | head -5

sec "B. 01 COMO USUÁRIO SEM SUPER (simula restrição de hospedagem)"
reset
$M -e "CREATE DATABASE locacao_veiculos_eletricos CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER IF NOT EXISTS 'aluno'@'%' IDENTIFIED BY 'aluno'; GRANT ALL PRIVILEGES ON locacao_veiculos_eletricos.* TO 'aluno'@'%';" 2>&1 | grep -v Warning
docker exec -i db mysql --default-character-set=utf8mb4 -ualuno -paluno -h127.0.0.1 -t < sql/01_criacao_banco_novo.sql 2>&1 | grep -v Warning | grep -i "error" | head -5
echo "(acima: erros encontrados com usuário sem SUPER; vazio = nenhum)"

sec "C. 01 CRIAÇÃO DO BANCO NOVO (root)"
reset
$M -t < sql/01_criacao_banco_novo.sql > c.log 2>&1; rc=$?
grep -v Warning c.log
echo "codigo de saida: $rc"; [ $rc -ne 0 ] && falhas=$((falhas+1))

sec "D. 03 TESTES (sessão com SQL_SAFE_UPDATES=1, como no Workbench)"
( echo "SET SQL_SAFE_UPDATES=1;"; cat sql/03_testes_validacao.sql ) | $M -t > d.log 2>&1; rc=$?
grep -v Warning d.log
echo "codigo de saida: $rc"; [ $rc -ne 0 ] && falhas=$((falhas+1))
grep -q "FALHOU" d.log && falhas=$((falhas+1))

sec "D2. 03 TESTES DE NOVO (o arquivo pode ser repetido?)"
$M -t < sql/03_testes_validacao.sql > d2.log 2>&1; rc=$?
grep -A4 "total_testes" d2.log; echo "codigo de saida: $rc"; [ $rc -ne 0 ] && falhas=$((falhas+1))
echo "dados de teste restantes:"; $M -N -e "SELECT COUNT(*) FROM locacao_veiculos_eletricos.Cliente WHERE email LIKE '%@teste.quantumdrive.local'" 2>&1 | grep -v Warning

sec "E. CONCORRÊNCIA: 8 sessões reservam o mesmo carro no mesmo período ao mesmo tempo"
$M locacao_veiculos_eletricos -e "
INSERT INTO CategoriaVeiculo (nome_categoria) VALUES ('CONC');
INSERT INTO Veiculo (id_categoria, marca, modelo, placa, valor_diaria) VALUES (LAST_INSERT_ID(),'BYD','Dolphin','CONC001',189);
INSERT INTO Cliente (nome,email,cpf) VALUES ('Conc Teste','conc@x.local','000.000.009-09');" 2>&1 | grep -v Warning
for i in $(seq 1 8); do
  $M locacao_veiculos_eletricos -N -e "CALL sp_registrar_agendamento((SELECT id_cliente FROM Cliente WHERE email='conc@x.local'),(SELECT id_veiculo FROM Veiculo WHERE placa='CONC001'), TIMESTAMP(CURDATE()+INTERVAL 5 DAY,'10:00'), TIMESTAMP(CURDATE()+INTERVAL 7 DAY,'10:00'), NULL, @id); SELECT CONCAT('sessao $i: criou ', @id);" 2>&1 | grep -v Warning &
done; wait
$M locacao_veiculos_eletricos -N -e "SELECT CONCAT('reservas criadas: ', COUNT(*)) FROM Agendamento a JOIN Veiculo v USING(id_veiculo) WHERE v.placa='CONC001'" 2>&1 | grep -v Warning

sec "F. 02 ATUALIZAÇÃO DE UM BANCO DO PIM III COM DADOS"
reset
python3 - <<'PY'
import re
s=open('sql/00_original_pim3.sql',encoding='utf-8').read()
s=re.sub(r'(\bid_\w+) INT AUTO_INCREMENT,', r'\1 INT AUTO_INCREMENT PRIMARY KEY,', s)
i=s.index('-- Criação das chaves primárias'); j=s.index('-- Criação das constraints UNIQUE')
s=s[:i]+s[j:]
s+="""
-- dados antigos, com status escritos de formas diferentes
INSERT INTO Cliente (nome,email,telefone,cpf) VALUES ('Cliente Antigo','antigo@pim3.local','11999990000','111.222.333-44');
INSERT INTO CategoriaVeiculo (nome_categoria) VALUES ('SUV'),('Hatch');
INSERT INTO Veiculo (id_categoria,marca,modelo,placa,ano,autonomia_km,status_veiculo) VALUES (1,'BYD','Song Plus','OLD1A11',2024,505,'Disponível'),(2,'BYD','Dolphin','OLD1A12',2024,291,'Em manutenção'),(2,'BYD','Dolphin Mini','OLD1A13',2024,280,NULL);
INSERT INTO Agendamento (id_cliente,id_veiculo,data_inicio,data_fim,periodo,status_agendamento) VALUES (1,1,'2025-05-01 10:00','2025-05-03 10:00','manhã','Concluído'),(1,3,'2025-06-01 10:00','2025-06-02 10:00',NULL,'Confirmada');
INSERT INTO Locacao (id_agendamento,data_retirada,data_devolucao,tempo_locacao_horas,valor_total,status_locacao) VALUES (1,'2025-05-01 10:10','2025-05-03 09:50',47.67,378.00,'Finalizada');
INSERT INTO Pagamento (id_locacao,valor_pago,forma_pagamento,data_pagamento,status_pagamento) VALUES (1,378.00,'Cartão de crédito','2025-05-03 10:00','Pago');
INSERT INTO Inspecao (id_veiculo,data_inicio,status_inspecao) VALUES (2,'2025-07-01 08:00','Em andamento');
INSERT INTO AtendimentoChat (id_cliente,data_abertura,status_chat,tipo_triagem) VALUES (1,'2025-05-02 12:00','Encerrado','duvida');
INSERT INTO MensagemChat (id_chat,remetente,conteudo,data_envio) VALUES (1,'Cliente','Olá','2025-05-02 12:00');
"""
open('base_pim3.sql','w',encoding='utf-8').write(s)
PY
$M -t < base_pim3.sql 2>&1 | grep -v Warning; echo "base PIM III criada"
$M -t < sql/02_atualizacao_banco_pim3.sql > f.log 2>&1; rc=$?
grep -v Warning f.log
echo "codigo de saida: $rc"; [ $rc -ne 0 ] && falhas=$((falhas+1))
echo "dados antigos depois da atualização:"
$M -t locacao_veiculos_eletricos -e "SELECT placa,status_veiculo,valor_diaria FROM Veiculo; SELECT id_agendamento,periodo,status_agendamento FROM Agendamento; SELECT status_locacao,valor_total FROM Locacao; SELECT forma_pagamento,status_pagamento FROM Pagamento; SELECT status_inspecao FROM Inspecao; SELECT status_chat FROM AtendimentoChat; SELECT remetente FROM MensagemChat; SELECT nome,data_cadastro IS NOT NULL tem_data FROM Cliente;" 2>&1 | grep -v Warning
$M locacao_veiculos_eletricos -e "UPDATE Veiculo SET valor_diaria=189 WHERE id_veiculo>0;" 2>&1 | grep -v Warning

sec "G. 03 TESTES NO BANCO ATUALIZADO"
$M -t < sql/03_testes_validacao.sql > g.log 2>&1; rc=$?
grep -B2 -A40 "total_testes" g.log | grep -v Warning
echo "codigo de saida: $rc"; [ $rc -ne 0 ] && falhas=$((falhas+1)); grep -q "FALHOU" g.log && falhas=$((falhas+1))
echo "dados antigos preservados:"; $M -N locacao_veiculos_eletricos -e "SELECT COUNT(*) FROM Cliente WHERE email='antigo@pim3.local'" 2>&1 | grep -v Warning

sec "RESULTADO: falhas=$falhas"
exit $falhas
