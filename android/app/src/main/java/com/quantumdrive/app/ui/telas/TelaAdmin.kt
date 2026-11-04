package com.quantumdrive.app.ui.telas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumdrive.app.data.Carro
import com.quantumdrive.app.data.Financeiro
import com.quantumdrive.app.data.Papel
import com.quantumdrive.app.data.Reserva
import com.quantumdrive.app.data.StatusReserva
import com.quantumdrive.app.data.Usuario
import com.quantumdrive.app.ui.Aviso
import com.quantumdrive.app.ui.QuantumViewModel
import com.quantumdrive.app.ui.componentes.*
import com.quantumdrive.app.ui.theme.LocalPaleta
import com.quantumdrive.app.util.Mascaras
import com.quantumdrive.app.util.Validadores
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val ABAS = listOf("📊 Visão geral", "💰 Financeiro", "📅 Reservas", "🚗 Frota", "👥 Clientes", "✉️ E-mails", "🔐 Administradores")

/** Painel exclusivo do dono — mesmas abas e números do site. */
@Composable
fun TelaAdmin(vm: QuantumViewModel) {
    val p = LocalPaleta.current
    val reservas by vm.reservas.collectAsStateWithLifecycle()
    val frota by vm.frota.collectAsStateWithLifecycle()
    val usuarios by vm.usuarios.collectAsStateWithLifecycle()
    val emails by vm.emails.collectAsStateWithLifecycle()
    val sessao by vm.sessao.collectAsStateWithLifecycle()
    var aba by remember { mutableStateOf(ABAS[vm.abaInicialAdmin.coerceIn(0, ABAS.lastIndex)]) }
    var finAno by remember { mutableIntStateOf(LocalDate.now().year) }
    var finMes by remember { mutableIntStateOf(LocalDate.now().monthValue) }
    var busca by remember { mutableStateOf("") }
    val clientes = usuarios.filter { it.papel == Papel.CLIENTE }
    val hoje = LocalDate.now()

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) { Etiqueta("Área restrita"); Titulo("Painel do dono", 26) }
                Text("● SINCRONIZADO", color = p.verde, fontWeight = FontWeight.Black, fontSize = 10.sp)
            }
        }
        item { Abas(ABAS, aba) { aba = it } }
        when (aba) {
            ABAS[0] -> visaoGeral(vm, reservas, frota, clientes, hoje)
            ABAS[1] -> financeiro(vm, reservas, finAno, finMes, { finAno = it }, { finMes = it })
            ABAS[2] -> {
                item { Campo("Buscar por código, cliente ou e-mail…", busca, { busca = it }) }
                val lista = reservas.filter { r -> busca.isBlank() || listOf(r.id, r.nome, r.email).any { it.contains(busca.trim(), true) } }
                if (lista.isEmpty()) item { CaixaQuantum { Text("Nenhuma reserva ainda.", color = p.cinza) } }
                items(lista.take(80), key = { it.id }) { r -> CartaoReservaAdmin(vm, r, frota) }
            }
            ABAS[3] -> {
                item { CaixaQuantum { Text("Altere preço, unidades e carros em manutenção. O catálogo dos clientes atualiza na hora.", color = p.cinza) } }
                items(frota, key = { it.id }) { c -> LinhaFrota(vm, c) }
            }
            ABAS[4] -> {
                if (clientes.isEmpty()) item { CaixaQuantum { Text("Nenhum cliente ainda.", color = p.cinza) } }
                items(clientes, key = { it.id }) { u ->
                    CaixaQuantum {
                        Text(u.nome, fontWeight = FontWeight.Bold)
                        Text("${u.email} · ${u.telefone}\nCPF ${Mascaras.cpfMascarado(u.cpf)} · cliente desde ${u.criadoEm.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}" +
                            " · ${reservas.count { it.usuarioId == u.id }} reserva(s)", color = p.cinza, fontSize = 13.sp)
                    }
                }
            }
            ABAS[5] -> {
                item {
                    CaixaQuantum(borda = p.verde) {
                        Text("${if (p.comIcones) "✓ " else ""}Envio real ativado (EmailJS · adminquantumdrive.com@gmail.com).", fontWeight = FontWeight.Bold)
                        Text("Cadastros, reservas e mudanças de status geram e-mail automático para o cliente.", color = p.cinza, fontSize = 13.sp)
                    }
                }
                if (emails.isEmpty()) item { CaixaQuantum { Text("Nenhum e-mail enviado ainda.", color = p.cinza) } }
                items(emails.take(60)) { CartaoEmail(it) }
            }
            ABAS[6] -> administradores(vm, usuarios.filter { it.papel == Papel.DONO }, sessao)
        }
    }
}

private fun LazyListScope.visaoGeral(vm: QuantumViewModel, reservas: List<Reserva>, frota: List<Carro>, clientes: List<Usuario>, hoje: LocalDate) {
    val validas = reservas.filter { it.status != StatusReserva.CANCELADA }
    item {
        val p = LocalPaleta.current
        val totalUn = frota.sumOf { it.unidades }.coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Kpi("Faturamento do mês", brl(Financeiro.doMes(validas, hoje.year, hoje.monthValue).sumOf { it.total }), Modifier.weight(1f), p.lima)
                Kpi("Faturamento do ano", brl(Financeiro.doAno(validas, hoje.year).sumOf { it.total }), Modifier.weight(1f), p.lima)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Kpi("Reservas ativas", "${reservas.count { it.status.ativa }}", Modifier.weight(1f))
                Kpi("Aguardando análise", "${reservas.count { it.status == StatusReserva.ANALISE }}", Modifier.weight(1f), p.azul)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Kpi("Ocupação agora", "${reservas.count { it.status == StatusReserva.ANDAMENTO } * 100 / totalUn}%", Modifier.weight(1f))
                Kpi("Clientes", "${clientes.size}", Modifier.weight(1f))
            }
            CaixaQuantum {
                Text("RESERVAS POR MODELO", fontWeight = FontWeight.Black)
                GraficoBarras(frota.map { c -> c.nome.removePrefix("BYD ").take(9) to validas.count { it.carroId == c.id } }, altura = 120.dp)
            }
            Text("ÚLTIMAS RESERVAS", fontWeight = FontWeight.Black)
        }
    }
    items(reservas.take(5), key = { "u" + it.id }) { r -> CartaoReservaAdmin(vm, r, frota) }
}

private fun LazyListScope.financeiro(vm: QuantumViewModel, reservas: List<Reserva>, ano: Int, mes: Int,
                                     mudarAno: (Int) -> Unit, mudarMes: (Int) -> Unit) {
    val anos = (reservas.map { it.inicio.year } + LocalDate.now().year).distinct().sortedDescending()
    val doAno = Financeiro.doAno(reservas, ano)
    val r = Financeiro.resumir(doAno)
    val anterior = Financeiro.resumir(Financeiro.doAno(reservas, ano - 1))
    val meses = (1..12).map { m -> Financeiro.resumir(Financeiro.doMes(reservas, ano, m)) }
    val doMes = Financeiro.doMes(reservas, ano, mes)
    item {
        val p = LocalPaleta.current
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Lista("Ano", anos.map { "$it" }, "$ano", null) { mudarAno(it.toInt()) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotaoContorno("⬇ Exportar CSV", Modifier.weight(1f)) { vm.exportarCsv(ano) }
                if (reservas.any { it.demo }) BotaoContorno("Remover exemplos", Modifier.weight(1f)) { vm.removerExemplos() }
                else BotaoQuantum("Gerar dados de exemplo", Modifier.weight(1f)) { vm.gerarExemplos() }
            }
            val variacao = if (anterior.total > 0) Math.round((r.total.toDouble() / anterior.total - 1) * 100).toInt() else null
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Kpi("Total de $ano", brl(r.total), Modifier.weight(1f), p.lima,
                    variacao?.let { "${if (it >= 0) "▲" else "▼"} ${kotlin.math.abs(it)}% vs ${ano - 1}" })
                Kpi("Recebido", brl(r.recebido), Modifier.weight(1f), p.verde)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Kpi("A receber", brl(r.aReceber), Modifier.weight(1f), p.azul)
                Kpi("Reservas · diárias", "${r.reservas} · ${r.diarias}", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Kpi("Ticket médio", brl(r.ticketMedio), Modifier.weight(1f))
                Kpi("Cancelado", brl(r.cancelado), Modifier.weight(1f), p.erro, "${r.canceladas} reserva(s)")
            }
            CaixaQuantum {
                Text("FATURAMENTO POR MÊS — $ano", fontWeight = FontWeight.Black)
                Text("Toque num mês para ver o detalhe.", color = p.cinza2, fontSize = 12.sp)
                GraficoBarras(meses.mapIndexed { i, x -> Financeiro.MESES[i].take(3) to x.total }, selecionado = mes - 1,
                    rotuloValor = { if (it >= 1000) "${it / 1000}k" else "$it" }) { mudarMes(it + 1) }
            }
            CaixaQuantum {
                Text("MÊS A MÊS", fontWeight = FontWeight.Black)
                meses.forEachIndexed { i, x ->
                    val sel = i == mes - 1
                    Column(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton({ mudarMes(i + 1) }, Modifier.weight(1f)) {
                                Text(Financeiro.MESES[i] + if (sel) "  ◀" else "", color = if (sel) p.verde else androidx.compose.ui.graphics.Color.White,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                            }
                            Text(brl(x.total), color = p.lima, fontWeight = FontWeight.Black)
                        }
                        Text("${x.reservas} reservas · ${x.diarias} diárias · recebido ${brl(x.recebido)} · a receber ${brl(x.aReceber)}" +
                            if (x.cancelado > 0) " · cancelado ${brl(x.cancelado)}" else "", color = p.cinza2, fontSize = 11.sp)
                    }
                }
                HorizontalDivider()
                LinhaResumo("Aluguel", brl(r.aluguel)); LinhaResumo("Proteção", brl(r.protecao))
                LinhaResumo("Descontos", "− " + brl(r.descontos)); LinhaResumo("TOTAL $ano", brl(r.total), p.lima)
            }
            Text("${Financeiro.MESES[mes - 1].uppercase()} DE $ano — ${brl(meses[mes - 1].total)}", fontWeight = FontWeight.Black)
            listOf<Pair<String, (Reserva) -> String>>(
                "Por veículo" to { x -> vm.repo.carro(x.carroId)?.nome ?: x.carroId },
                "Por forma de pagamento" to { x -> x.pagamento },
                "Por loja" to { x -> x.loja }
            ).forEach { (titulo, chave) ->
                CaixaQuantum {
                    Text(titulo, fontWeight = FontWeight.Bold)
                    val grupos = Financeiro.agrupar(doMes, chave)
                    if (grupos.isEmpty()) Text("Sem reservas neste mês.", color = p.cinza2, fontSize = 12.sp)
                    grupos.forEach { (k, n, t) -> LinhaResumo("$k ($n)", brl(t)) }
                }
            }
            Text("Cada reserva entra no mês da retirada. Recebido = concluídas e em andamento · A receber = em análise e confirmadas · canceladas ficam fora do total.",
                color = p.cinza2, fontSize = 12.sp)
            Text("RESERVAS DO MÊS", fontWeight = FontWeight.Black)
        }
    }
    items(doMes.take(40), key = { "f" + it.id }) { x -> CartaoReservaAdmin(vm, x, vm.frota.value) }
}

@Composable
private fun CartaoReservaAdmin(vm: QuantumViewModel, r: Reserva, frota: List<Carro>) {
    val p = LocalPaleta.current
    var menu by remember { mutableStateOf(false) }
    var confirmarCancelamento by remember { mutableStateOf(false) }
    CaixaQuantum {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.id, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f)); SeloStatus(r.status)
        }
        Text("${r.nome} · ${r.email}\n${frota.find { it.id == r.carroId }?.nome ?: r.carroId} · ${r.inicio.format(fmtCurto)} → ${r.fim.format(fmtCurto)}" +
            "\nDocumentos: ${r.documentos.size}/3 ✓", color = p.cinza, fontSize = 13.sp)
        Text(brl(r.total), color = p.lima, fontWeight = FontWeight.Black)
        Box {
            BotaoContorno("Mudar status (avisa por e-mail)") { menu = true }
            DropdownMenu(menu, { menu = false }) {
                StatusReserva.entries.filter { it != r.status }.forEach { s ->
                    DropdownMenuItem({ Text(s.rotulo) }, {
                        menu = false
                        if (s == StatusReserva.CANCELADA) confirmarCancelamento = true else vm.mudarStatus(r.id, s)
                    })
                }
            }
        }
    }
    if (confirmarCancelamento) AlertDialog(
        onDismissRequest = { confirmarCancelamento = false },
        title = { Text("Cancelar ${r.id}?") },
        text = { Text("O cliente será avisado por e-mail.") },
        confirmButton = { TextButton({ confirmarCancelamento = false; vm.mudarStatus(r.id, StatusReserva.CANCELADA, "Cancelada pela locadora") }) {
            Text("Cancelar reserva", color = p.erro, fontWeight = FontWeight.Black) } },
        dismissButton = { TextButton({ confirmarCancelamento = false }) { Text("Voltar") } }
    )
}

@Composable
private fun LinhaFrota(vm: QuantumViewModel, c: Carro) {
    var preco by remember(c.precoDia) { mutableStateOf(c.precoDia.toString()) }
    var unidades by remember(c.unidades) { mutableStateOf(c.unidades.toString()) }
    var manut by remember(c.manutencao) { mutableStateOf(c.manutencao.toString()) }
    CaixaQuantum {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.nome, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            SeloDisponibilidade(c, vm.repo.livres(c))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoNumero("Diária R$", preco, Modifier.weight(1f)) { preco = it }
            CampoNumero("Unidades", unidades, Modifier.weight(1f)) { unidades = it }
            CampoNumero("Manutenção", manut, Modifier.weight(1f)) { manut = it }
        }
        BotaoQuantum("Salvar", Modifier.fillMaxWidth()) {
            val pr = preco.toIntOrNull(); val un = unidades.toIntOrNull(); val m = manut.toIntOrNull()
            when {
                pr == null || pr !in 50..5000 -> vm.avisar(Aviso(Aviso.Tipo.ERRO, "Valor inválido", "A diária deve ficar entre R$ 50 e R$ 5.000."))
                un == null || un !in 0..50 -> vm.avisar(Aviso(Aviso.Tipo.ERRO, "Valor inválido", "Unidades entre 0 e 50."))
                m == null || m !in 0..un -> vm.avisar(Aviso(Aviso.Tipo.ERRO, "Valor inválido", "Manutenção não pode passar das unidades."))
                else -> vm.atualizarCarro(c.id, pr, un, m)
            }
        }
    }
}

@Composable
private fun CampoNumero(rotulo: String, valor: String, modifier: Modifier, aoMudar: (String) -> Unit) {
    OutlinedTextField(valor, { aoMudar(it.filter(Char::isDigit).take(4)) }, modifier, label = { Text(rotulo, fontSize = 11.sp) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}

private fun LazyListScope.administradores(vm: QuantumViewModel, admins: List<Usuario>, sessao: Usuario?) {
    item {
        val p = LocalPaleta.current
        CaixaQuantum {
            Text("ACESSO DIRETO", fontWeight = FontWeight.Black)
            Text("Os donos entram pela Área do administrador: na tela de login do app, toque em 🔐 Acesso do administrador " +
                "(no site: rodapé › Área do administrador).", color = p.cinza, fontSize = 13.sp)
        }
    }
    items(admins, key = { "a" + it.id }) { u ->
        val p = LocalPaleta.current
        CaixaQuantum {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(u.nome + if (u.id == sessao?.id) " (você)" else "", fontWeight = FontWeight.Bold)
                    Text(u.email, color = p.cinza, fontSize = 13.sp)
                }
                if (u.id != sessao?.id) BotaoContorno("Remover") { vm.removerAdmin(u.id) }
            }
        }
    }
    item { NovoAdmin(vm) }
    item { TrocarSenha(vm) }
}

@Composable
private fun NovoAdmin(vm: QuantumViewModel) {
    var nome by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var erros by remember { mutableStateOf(mapOf<String, String>()) }
    CaixaQuantum {
        Text("ADICIONAR ADMINISTRADOR", fontWeight = FontWeight.Black)
        Campo("Nome", nome, { nome = it }, erros["nome"])
        Campo("E-mail", email, { email = it }, erros["email"], teclado = KeyboardType.Email)
        Campo("Senha inicial", senha, { senha = it }, erros["senha"], senha = true, dica = "Mínimo de 6 caracteres.")
        BotaoQuantum("Adicionar", Modifier.fillMaxWidth()) {
            erros = buildMap {
                if (nome.trim().length < 2) put("nome", "Informe o nome.")
                Validadores.email(email)?.let { put("email", it) }
                Validadores.senha(senha)?.let { put("senha", it) }
            }
            if (erros.isEmpty()) vm.criarAdmin(nome, email, senha) { nome = ""; email = ""; senha = "" }
            else vm.avisar(Aviso(Aviso.Tipo.ERRO, "Confira os campos"))
        }
    }
}

@Composable
private fun TrocarSenha(vm: QuantumViewModel) {
    var atual by remember { mutableStateOf("") }
    var nova by remember { mutableStateOf("") }
    var nova2 by remember { mutableStateOf("") }
    var erros by remember { mutableStateOf(mapOf<String, String>()) }
    CaixaQuantum {
        Text("TROCAR MINHA SENHA", fontWeight = FontWeight.Black)
        Campo("Senha atual", atual, { atual = it }, erros["atual"], senha = true)
        Campo("Nova senha", nova, { nova = it }, erros["nova"], senha = true)
        Campo("Repita a nova senha", nova2, { nova2 = it }, erros["nova2"], senha = true)
        BotaoQuantum("Salvar nova senha", Modifier.fillMaxWidth()) {
            erros = buildMap {
                if (atual.isEmpty()) put("atual", "Digite a senha atual.")
                Validadores.senha(nova)?.let { put("nova", it) }
                if (nova != nova2) put("nova2", "As senhas não conferem.")
            }
            if (erros.isEmpty()) vm.trocarSenha(atual, nova) { atual = ""; nova = ""; nova2 = "" }
            else vm.avisar(Aviso(Aviso.Tipo.ERRO, "Confira os campos"))
        }
    }
}
