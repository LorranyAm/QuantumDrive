CREATE DATABASE IF NOT EXISTS locacao_veiculos_eletricos;

USE locacao_veiculos_eletricos;

-- ============================================================
-- Criação do banco de dados
-- ============================================================
 
CREATE DATABASE IF NOT EXISTS locacao_veiculos_eletricos;
 
USE locacao_veiculos_eletricos;
 
 
-- ============================================================
-- Criação das tabelas SEM constraints
-- ============================================================
 
CREATE TABLE Cliente (
    id_cliente INT AUTO_INCREMENT,
    nome VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL,
    telefone VARCHAR(20),
    cpf VARCHAR(14) NOT NULL,
    necessidades_acessibilidade TEXT
);
 
 
CREATE TABLE CategoriaVeiculo (
    id_categoria INT AUTO_INCREMENT,
    nome_categoria VARCHAR(100) NOT NULL,
    descricao TEXT
);
 
 
CREATE TABLE Veiculo (
    id_veiculo INT AUTO_INCREMENT,
    id_categoria INT NOT NULL,
    marca VARCHAR(100) NOT NULL,
    modelo VARCHAR(100) NOT NULL,
    placa VARCHAR(10) NOT NULL,
    ano INT,
    autonomia_km INT,
    tipo_cambio VARCHAR(50),
    possui_som BOOLEAN,
    nivel_conforto VARCHAR(50),
    adequado_viagem BOOLEAN,
    status_veiculo VARCHAR(50)
);
 
 
CREATE TABLE Agendamento (
    id_agendamento INT AUTO_INCREMENT,
    id_cliente INT NOT NULL,
    id_veiculo INT NOT NULL,
    data_inicio DATETIME NOT NULL,
    data_fim DATETIME NOT NULL,
    periodo VARCHAR(50),
    status_agendamento VARCHAR(50)
);
 
 
CREATE TABLE Locacao (
    id_locacao INT AUTO_INCREMENT,
    id_agendamento INT NOT NULL,
    data_retirada DATETIME NOT NULL,
    data_devolucao DATETIME,
    tempo_locacao_horas DECIMAL(10,2),
    valor_total DECIMAL(10,2),
    status_locacao VARCHAR(50)
);
 
 
CREATE TABLE Pagamento (
    id_pagamento INT AUTO_INCREMENT,
    id_locacao INT NOT NULL,
    valor_pago DECIMAL(10,2) NOT NULL,
    forma_pagamento VARCHAR(50),
    data_pagamento DATETIME,
    status_pagamento VARCHAR(50)
);
 
 
CREATE TABLE Inspecao (
    id_inspecao INT AUTO_INCREMENT,
    id_veiculo INT NOT NULL,
    data_inicio DATETIME NOT NULL,
    data_fim DATETIME,
    status_inspecao VARCHAR(50),
    observacoes TEXT
);
 
 
CREATE TABLE AtendimentoChat (
    id_chat INT AUTO_INCREMENT,
    id_cliente INT NOT NULL,
    data_abertura DATETIME NOT NULL,
    status_chat VARCHAR(50),
    tipo_triagem VARCHAR(100)
);
 
 
CREATE TABLE MensagemChat (
    id_mensagem INT AUTO_INCREMENT,
    id_chat INT NOT NULL,
    remetente VARCHAR(50) NOT NULL,
    conteudo TEXT NOT NULL,
    data_envio DATETIME NOT NULL
);
 
 
-- ============================================================
-- Criação das chaves primárias
-- ============================================================
 
ALTER TABLE Cliente
ADD CONSTRAINT pk_cliente
PRIMARY KEY (id_cliente);
 
 
ALTER TABLE CategoriaVeiculo
ADD CONSTRAINT pk_categoria_veiculo
PRIMARY KEY (id_categoria);
 
 
ALTER TABLE Veiculo
ADD CONSTRAINT pk_veiculo
PRIMARY KEY (id_veiculo);
 
 
ALTER TABLE Agendamento
ADD CONSTRAINT pk_agendamento
PRIMARY KEY (id_agendamento);
 
 
ALTER TABLE Locacao
ADD CONSTRAINT pk_locacao
PRIMARY KEY (id_locacao);
 
 
ALTER TABLE Pagamento
ADD CONSTRAINT pk_pagamento
PRIMARY KEY (id_pagamento);
 
 
ALTER TABLE Inspecao
ADD CONSTRAINT pk_inspecao
PRIMARY KEY (id_inspecao);
 
 
ALTER TABLE AtendimentoChat
ADD CONSTRAINT pk_atendimento_chat
PRIMARY KEY (id_chat);
 
 
ALTER TABLE MensagemChat
ADD CONSTRAINT pk_mensagem_chat
PRIMARY KEY (id_mensagem);
 
 
-- ============================================================
-- Criação das constraints UNIQUE
-- ============================================================
 
ALTER TABLE Cliente
ADD CONSTRAINT uq_cliente_email
UNIQUE (email);
 
 
ALTER TABLE Cliente
ADD CONSTRAINT uq_cliente_cpf
UNIQUE (cpf);
 
 
ALTER TABLE Veiculo
ADD CONSTRAINT uq_veiculo_placa
UNIQUE (placa);
 
 
ALTER TABLE Locacao
ADD CONSTRAINT uq_locacao_agendamento
UNIQUE (id_agendamento);
 
 
ALTER TABLE Pagamento
ADD CONSTRAINT uq_pagamento_locacao
UNIQUE (id_locacao);
 
 
-- ============================================================
-- Criação das chaves estrangeiras
-- ============================================================
 
ALTER TABLE Veiculo
ADD CONSTRAINT fk_veiculo_categoria
FOREIGN KEY (id_categoria)
REFERENCES CategoriaVeiculo (id_categoria);
 
 
ALTER TABLE Agendamento
ADD CONSTRAINT fk_agendamento_cliente
FOREIGN KEY (id_cliente)
REFERENCES Cliente (id_cliente);
 
 
ALTER TABLE Agendamento
ADD CONSTRAINT fk_agendamento_veiculo
FOREIGN KEY (id_veiculo)
REFERENCES Veiculo (id_veiculo);
 
 
ALTER TABLE Locacao
ADD CONSTRAINT fk_locacao_agendamento
FOREIGN KEY (id_agendamento)
REFERENCES Agendamento (id_agendamento);
 
 
ALTER TABLE Pagamento
ADD CONSTRAINT fk_pagamento_locacao
FOREIGN KEY (id_locacao)
REFERENCES Locacao (id_locacao);
 
 
ALTER TABLE Inspecao
ADD CONSTRAINT fk_inspecao_veiculo
FOREIGN KEY (id_veiculo)
REFERENCES Veiculo (id_veiculo);
 
 
ALTER TABLE AtendimentoChat
ADD CONSTRAINT fk_chat_cliente
FOREIGN KEY (id_cliente)
REFERENCES Cliente (id_cliente);
 
 
ALTER TABLE MensagemChat
ADD CONSTRAINT fk_mensagem_chat
FOREIGN KEY (id_chat)
REFERENCES AtendimentoChat (id_chat);