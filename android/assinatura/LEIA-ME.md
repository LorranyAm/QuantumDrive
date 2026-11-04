# Chave de assinatura do app Android

`quantumdrive.jks` é a chave que assina o APK do Quantum Drive.
O arquivo é criptografado: sem a senha ele não serve para nada.

A senha fica **só** no segredo `QD_KEYSTORE_PASSWORD` do repositório
(Settings › Secrets and variables › Actions). Nunca coloque a senha em arquivos do repositório.

Use sempre esta mesma chave: é ela que faz o celular aceitar as atualizações por cima do app instalado.
