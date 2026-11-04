package com.quantumdrive.app.ui.telas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quantumdrive.app.ui.Aviso
import com.quantumdrive.app.ui.QuantumViewModel
import com.quantumdrive.app.ui.componentes.*
import com.quantumdrive.app.ui.theme.LocalPaleta
import com.quantumdrive.app.util.Mascaras
import com.quantumdrive.app.util.Validadores

@Composable
private fun Logo() = Row {
    Text("Quantum", color = LocalPaleta.current.verde, fontWeight = FontWeight.Black, fontSize = 30.sp)
    Text("Drive", color = Color.White, fontSize = 30.sp)
}

@Composable
fun TelaLogin(vm: QuantumViewModel, aoEntrar: () -> Unit, irCadastro: () -> Unit, irAdmin: () -> Unit) {
    val p = LocalPaleta.current
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var erroEmail by remember { mutableStateOf<String?>(null) }
    var erroSenha by remember { mutableStateOf<String?>(null) }
    var erroGeral by remember { mutableStateOf<String?>(null) }
    var carregando by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Stepper(1)
        Spacer(Modifier.height(8.dp))
        Logo()
        Etiqueta("Bem-vindo de volta")
        Titulo("Entrar")
        CaixaQuantum {
            erroGeral?.let { Text((if (p.comIcones) "⚠ " else "") + it, color = p.erro, fontWeight = FontWeight.Bold) }
            Campo("E-mail", email, { email = it; erroEmail = null }, erroEmail, teclado = KeyboardType.Email)
            Campo("Senha", senha, { senha = it; erroSenha = null }, erroSenha, senha = true, teclado = KeyboardType.Password)
            BotaoQuantum("Entrar", Modifier.fillMaxWidth(), carregando) {
                erroEmail = Validadores.email(email)
                erroSenha = if (senha.isBlank()) "Digite sua senha." else null
                if (erroEmail != null || erroSenha != null) {
                    vm.avisar(Aviso(Aviso.Tipo.ERRO, "Confira os campos", "Há informações faltando ou inválidas.")); return@BotaoQuantum
                }
                carregando = true; erroGeral = null
                vm.login(email, senha, aoEntrar = { carregando = false; aoEntrar() }, aoErrar = { carregando = false; erroGeral = it })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("Ainda não tem conta?", color = p.cinza)
                TextButton(onClick = irCadastro) { Text("Cadastre-se", color = p.verde, fontWeight = FontWeight.Black) }
            }
            TextButton(onClick = irAdmin, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("🔐 Acesso do administrador", color = p.azul, fontWeight = FontWeight.Bold)
            }
        }
        CaixaQuantum {
            Text("Conta de demonstração", fontWeight = FontWeight.Bold)
            Text("Cliente: cliente@quantumdrive.com / Cliente@123\nOu crie sua conta com qualquer e-mail.", color = p.cinza, fontSize = 13.sp)
        }
    }
}

@Composable
fun TelaCadastro(vm: QuantumViewModel, aoCriar: () -> Unit, irLogin: () -> Unit) {
    val p = LocalPaleta.current
    var nome by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var tel by remember { mutableStateOf("") }
    var cpf by remember { mutableStateOf("") }
    var nasc by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var senha2 by remember { mutableStateOf("") }
    var termos by remember { mutableStateOf(false) }
    var erros by remember { mutableStateOf(mapOf<String, String>()) }
    var carregando by remember { mutableStateOf(false) }

    fun validar(): Map<String, String> = buildMap {
        val dataNasc = Validadores.lerData(nasc)
        Validadores.nome(nome)?.let { put("nome", it) }
        Validadores.email(email)?.let { put("email", it) }
        Validadores.telefone(tel)?.let { put("tel", it) }
        Validadores.cpf(cpf)?.let { put("cpf", it) }
        (if (nasc.isNotBlank() && dataNasc == null) "Use o formato dd/mm/aaaa." else Validadores.idadeMinima(dataNasc))?.let { put("nasc", it) }
        Validadores.senha(senha)?.let { put("senha", it) }
        if (senha2.isEmpty() || senha2 != senha) put("senha2", "As senhas não conferem.")
        if (!termos) put("termos", "Você precisa aceitar os termos.")
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Stepper(1)
        Etiqueta("Crie sua conta")
        Titulo("Cadastro")
        CaixaQuantum {
            Campo("Nome completo", nome, { nome = it }, erros["nome"])
            Campo("E-mail", email, { email = it }, erros["email"], teclado = KeyboardType.Email, dica = "Qualquer e-mail: Gmail, Hotmail, Outlook…")
            Campo("Telefone", tel, { tel = Mascaras.telefone(it) }, erros["tel"], teclado = KeyboardType.Phone)
            Campo("CPF", cpf, { cpf = Mascaras.cpf(it) }, erros["cpf"], teclado = KeyboardType.Number)
            Campo("Nascimento (dd/mm/aaaa)", nasc, { nasc = Mascaras.data(it) }, erros["nasc"], teclado = KeyboardType.Number)
            Campo("Senha", senha, { senha = it }, erros["senha"], senha = true, dica = "Mínimo de 6 caracteres.")
            Campo("Confirmar senha", senha2, { senha2 = it }, erros["senha2"], senha = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(termos, { termos = it }, colors = CheckboxDefaults.colors(checkedColor = p.verde))
                Text("Li e aceito os termos de uso e a política de privacidade (LGPD).", color = p.cinza, fontSize = 13.sp)
            }
            erros["termos"]?.let { Text((if (p.comIcones) "⚠ " else "") + it, color = p.erro, fontSize = 12.sp) }
            BotaoQuantum("Criar conta", Modifier.fillMaxWidth(), carregando) {
                erros = validar()
                if (erros.isNotEmpty()) {
                    vm.avisar(Aviso(Aviso.Tipo.ERRO, "Cadastro incompleto", "Corrija ${erros.size} campo(s) destacado(s).")); return@BotaoQuantum
                }
                carregando = true
                val iso = Validadores.lerData(nasc)!!.toString()
                vm.cadastrar(nome, email, tel, cpf, iso, senha,
                    aoCriar = { carregando = false; aoCriar() },
                    aoErrar = { campo, msg -> carregando = false; if (campo != null) erros = mapOf(campo to msg) })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("Já tem conta?", color = p.cinza)
                TextButton(onClick = irLogin) { Text("Entrar", color = p.verde, fontWeight = FontWeight.Black) }
            }
        }
    }
}

/** Entrada exclusiva dos donos. Não mostra nenhum dado de acesso. */
@Composable
fun TelaAdminLogin(vm: QuantumViewModel, aoEntrar: () -> Unit, souCliente: () -> Unit) {
    val p = LocalPaleta.current
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var erros by remember { mutableStateOf(mapOf<String, String>()) }
    var erroGeral by remember { mutableStateOf<String?>(null) }
    var carregando by remember { mutableStateOf(false) }
    var primeiroAcesso by remember { mutableStateOf<String?>(null) }   // id do dono

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(12.dp))
        CaixaQuantum(borda = p.azul) {
            Text("🔐", fontSize = 36.sp)
            Etiqueta("Acesso exclusivo", p.azul)
            Text("Área do administrador", fontWeight = FontWeight.Black, fontSize = 26.sp)
            Text("Entrada direta para os donos da loja: reservas, frota, clientes e financeiro.", color = p.cinza2, fontSize = 13.sp)
            erroGeral?.let { Text((if (p.comIcones) "⚠ " else "") + it, color = p.erro, fontWeight = FontWeight.Bold) }
            Campo("E-mail do administrador", email, { email = it }, erros["email"], teclado = KeyboardType.Email)
            Campo("Senha", senha, { senha = it }, erros["senha"], senha = true, teclado = KeyboardType.Password)
            BotaoQuantum("Entrar no painel", Modifier.fillMaxWidth(), carregando) {
                erros = buildMap {
                    Validadores.email(email)?.let { put("email", "Digite o e-mail do administrador.") }
                    if (senha.isEmpty()) put("senha", "Digite a senha.")
                }
                if (erros.isNotEmpty()) { vm.avisar(Aviso(Aviso.Tipo.ERRO, "Confira os campos", "Preencha e-mail e senha.")); return@BotaoQuantum }
                carregando = true; erroGeral = null
                vm.loginAdmin(email, senha,
                    aoEntrar = { carregando = false; aoEntrar() },
                    aoPrimeiroAcesso = { id -> carregando = false; primeiroAcesso = id },
                    aoErrar = { carregando = false; erroGeral = it })
            }
            Text("Esqueceu a senha? Peça a outro administrador para criar um novo acesso em Painel › Administradores.",
                color = p.cinza2, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = souCliente, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Sou cliente", color = p.verde, fontWeight = FontWeight.Black)
            }
        }
    }

    primeiroAcesso?.let { id ->
        DialogoNovaSenha(
            aoCancelar = {
                primeiroAcesso = null
                vm.avisar(Aviso(Aviso.Tipo.INFO, "Troca de senha obrigatória", "Por segurança, crie uma senha nova para entrar no painel."))
            },
            aoSalvar = { nova, aoErro -> vm.concluirPrimeiroAcesso(id, senha, nova, aoEntrar = { primeiroAcesso = null; aoEntrar() }, aoErrar = aoErro) }
        )
    }
}

@Composable
fun DialogoNovaSenha(aoCancelar: () -> Unit, aoSalvar: (String, (String) -> Unit) -> Unit) {
    var nova by remember { mutableStateOf("") }
    var nova2 by remember { mutableStateOf("") }
    var erros by remember { mutableStateOf(mapOf<String, String>()) }
    AlertDialog(
        onDismissRequest = aoCancelar,
        title = { Text("Crie sua senha de administrador") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Este é o primeiro acesso com a senha inicial. Crie uma senha só sua — ela não aparece em nenhum lugar do app.")
                Campo("Nova senha", nova, { nova = it }, erros["nova"], senha = true, dica = "Mínimo de 8 caracteres.")
                Campo("Repita a nova senha", nova2, { nova2 = it }, erros["nova2"], senha = true)
            }
        },
        confirmButton = {
            TextButton({
                erros = buildMap {
                    if (nova.length < 8) put("nova", "Use pelo menos 8 caracteres.")
                    if (nova != nova2) put("nova2", "As senhas não conferem.")
                }
                if (erros.isEmpty()) aoSalvar(nova) { msg -> erros = mapOf("nova" to msg) }
            }) { Text("Salvar e entrar", fontWeight = FontWeight.Black) }
        },
        dismissButton = { TextButton(aoCancelar) { Text("Cancelar") } }
    )
}
