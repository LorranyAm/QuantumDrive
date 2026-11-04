package com.quantumdrive.app.ui.telas

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumdrive.app.data.Frota
import com.quantumdrive.app.data.Orcamento
import com.quantumdrive.app.data.Reserva
import com.quantumdrive.app.ui.Aviso
import com.quantumdrive.app.ui.QuantumViewModel
import com.quantumdrive.app.ui.componentes.*
import com.quantumdrive.app.ui.theme.LocalPaleta
import com.quantumdrive.app.util.Mascaras
import com.quantumdrive.app.util.Validadores
import java.time.LocalDateTime

private data class Documento(val nome: String, val tamanho: Long, val tipo: String?)

@Composable
fun TelaReserva(vm: QuantumViewModel, id: String, aoConcluir: () -> Unit, voltar: () -> Unit) {
    val p = LocalPaleta.current
    val ctx = LocalContext.current
    val frota by vm.frota.collectAsStateWithLifecycle()
    val usuario by vm.sessao.collectAsStateWithLifecycle()
    val c = frota.find { it.id == id } ?: return
    val u = usuario ?: return

    val (ini0, fim0) = remember(id) { datasIniciais(vm) }
    var dataIni by remember { mutableStateOf(ini0.toLocalDate()) }
    var horaIni by remember { mutableStateOf(ini0.toLocalTime()) }
    var dataFim by remember { mutableStateOf(fim0.toLocalDate()) }
    var horaFim by remember { mutableStateOf(fim0.toLocalTime()) }
    var loja by remember { mutableStateOf("") }
    var pagamento by remember { mutableStateOf("") }
    // dados do cadastro já preenchidos
    var nome by remember { mutableStateOf(u.nome) }
    var email by remember { mutableStateOf(u.email) }
    var tel by remember { mutableStateOf(u.telefone) }
    var cpf by remember { mutableStateOf(Mascaras.cpf(u.cpf)) }
    var cnh by remember { mutableStateOf("") }
    var cnhValidade by remember { mutableStateOf("") }
    var protecao by remember { mutableStateOf(true) }
    val docs = remember { mutableStateMapOf<String, Documento>() }
    var erros by remember { mutableStateOf(mapOf<String, String>()) }
    var confirmar by remember { mutableStateOf(false) }
    var enviando by remember { mutableStateOf(false) }

    val ini = LocalDateTime.of(dataIni, horaIni)
    val fim = LocalDateTime.of(dataFim, horaFim)
    val periodoOk = fim.isAfter(ini)
    val orc = Orcamento.calcular(c.precoDia, ini, fim, protecao)

    fun lerDoc(uri: Uri?): Documento? {
        uri ?: return null
        var n = "arquivo"; var t = 0L
        ctx.contentResolver.query(uri, null, null, null, null)?.use { cur ->
            if (cur.moveToFirst()) {
                cur.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { n = cur.getString(it) }
                cur.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { t = cur.getLong(it) }
            }
        }
        return Documento(n, t, ctx.contentResolver.getType(uri))
    }
    var docAtual by remember { mutableStateOf("") }
    val seletor = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        lerDoc(uri)?.let { docs[docAtual] = it; erros = erros - ("doc$docAtual") }
    }

    fun validar(): Map<String, String> = buildMap {
        (Validadores.periodo(ini, fim) ?: if (vm.repo.temConflito(c.id, ini, fim)) "Sem unidades livres neste período. Tente outras datas ou outro modelo." else null)
            ?.let { put("periodo", it) }
        if (loja.isEmpty()) put("loja", "Escolha a loja de retirada.")
        if (pagamento.isEmpty()) put("pagamento", "Escolha a forma de pagamento.")
        Validadores.nome(nome)?.let { put("nome", it) }
        Validadores.email(email)?.let { put("email", it) }
        Validadores.telefone(tel)?.let { put("tel", it) }
        Validadores.cpf(cpf)?.let { put("cpf", it) }
        Validadores.cnh(cnh)?.let { put("cnh", it) }
        val validade = Validadores.lerData(cnhValidade)
        when {
            validade == null -> put("cnhValidade", "Informe a validade (dd/mm/aaaa).")
            validade.isBefore(fim.toLocalDate()) -> put("cnhValidade", "A CNH vence antes da devolução.")
        }
        listOf("CNH", "RG", "Comprovante").forEach { k ->
            val d = docs[k]
            Validadores.documento(d?.nome, d?.tipo, d?.tamanho ?: 0)?.let { put("doc$k", it) }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Stepper(4)
        TextButton(onClick = voltar) { Text("← Trocar veículo ou datas", color = p.verde, fontWeight = FontWeight.Bold) }

        // Resumo (no site fica na lateral; no celular fica em cima)
        CaixaQuantum {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FotoCarro(c.foto, c.nome, Modifier.size(110.dp, 70.dp).clip(RoundedCornerShape(12.dp)), 4.dp)
                Column { Etiqueta(c.categoria.name); Text(c.nome.uppercase(), fontWeight = FontWeight.Black) }
            }
            LinhaResumo("Retirada", if (periodoOk) ini.format(fmtDataHora) else "—")
            LinhaResumo("Devolução", if (periodoOk) fim.format(fmtDataHora) else "—")
            LinhaResumo("${orc.dias} diária(s) × ${brl(c.precoDia)}", brl(orc.subtotal))
            LinhaResumo("Proteção completa", brl(orc.protecao))
            if (orc.desconto > 0) LinhaResumo("Desconto 7+ dias", "− " + brl(orc.desconto), p.verde)
            LinhaResumo("Total", brl(orc.total), p.lima)
            Text("Cancelamento grátis até 24 h antes da retirada. Você recebe a confirmação por e-mail.", color = p.cinza2, fontSize = 12.sp)
        }

        Etiqueta("Finalizar reserva")
        Titulo("Seus dados e documentos", 24)
        Text("Seus dados de cadastro já vieram preenchidos. Tudo é conferido antes do envio.", color = p.cinza2, fontSize = 13.sp)
        CaixaQuantum {
            SeletorDataHora("Retirada", dataIni, horaIni, { dataIni = it }, { horaIni = it })
            SeletorDataHora("Devolução", dataFim, horaFim, { dataFim = it }, { horaFim = it })
            erros["periodo"]?.let { Text((if (p.comIcones) "⚠ " else "") + it, color = p.erro, fontSize = 12.sp) }
            Lista("Loja de retirada", Frota.lojas, loja, erros["loja"]) { loja = it }
            Lista("Forma de pagamento", Frota.pagamentos, pagamento, erros["pagamento"]) { pagamento = it }
            Campo("Nome completo", nome, { nome = it }, erros["nome"])
            Campo("E-mail", email, { email = it }, erros["email"], teclado = KeyboardType.Email)
            Campo("Telefone", tel, { tel = Mascaras.telefone(it) }, erros["tel"], teclado = KeyboardType.Phone)
            Campo("CPF", cpf, { cpf = Mascaras.cpf(it) }, erros["cpf"], teclado = KeyboardType.Number)
            Campo("Número da CNH", cnh, { cnh = it.filter(Char::isDigit).take(11) }, erros["cnh"], teclado = KeyboardType.Number, dica = "11 dígitos")
            Campo("Validade da CNH (dd/mm/aaaa)", cnhValidade, { cnhValidade = Mascaras.data(it) }, erros["cnhValidade"], teclado = KeyboardType.Number)
            listOf("CNH", "RG", "Comprovante").forEach { k ->
                Column {
                    OutlinedButton({ docAtual = k; seletor.launch(arrayOf("application/pdf", "image/jpeg", "image/png")) },
                        Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Text(docs[k]?.let { "📎 ${if (k == "Comprovante") "Comprovante de residência" else k}: ${it.nome}" }
                            ?: "📎 Enviar ${if (k == "Comprovante") "comprovante de residência" else k} (PDF/JPG/PNG)",
                            color = if (docs[k] != null) p.verde else Color.White)
                    }
                    erros["doc$k"]?.let { Text((if (p.comIcones) "⚠ " else "") + it, color = p.erro, fontSize = 12.sp) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(protecao, { protecao = it }, colors = CheckboxDefaults.colors(checkedColor = p.verde))
                Text("Incluir proteção completa (+ R$ 39/dia)", color = p.cinza)
            }
            BotaoQuantum("Confirmar reserva", Modifier.fillMaxWidth(), enviando) {
                erros = validar()
                if (erros.isEmpty()) confirmar = true
                else vm.avisar(Aviso(Aviso.Tipo.ERRO, "Não foi possível enviar", "Corrija ${erros.size} campo(s) destacado(s)."))
            }
        }
    }

    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("Confirmar reserva?") },
            text = {
                Column {
                    Text("Confira o resumo antes de enviar:", color = p.cinza)
                    LinhaResumo("Veículo", c.nome)
                    LinhaResumo("Período", "${ini.format(fmtCurto)} → ${fim.format(fmtCurto)}")
                    LinhaResumo("Loja", loja.substringAfter("— "))
                    LinhaResumo("Total", brl(orc.total), p.lima)
                }
            },
            confirmButton = {
                TextButton({
                    confirmar = false; enviando = true
                    vm.reservar(
                        Reserva(id = "", usuarioId = u.id, carroId = c.id, nome = nome.trim(), email = email.trim().lowercase(),
                            telefone = tel, cpf = cpf.filter(Char::isDigit), cnh = cnh, loja = loja, pagamento = pagamento,
                            inicio = ini, fim = fim, dias = orc.dias, protecao = protecao, subtotal = orc.subtotal,
                            valorProtecao = orc.protecao, desconto = orc.desconto, total = orc.total,
                            documentos = listOf("CNH", "RG", "Comprovante").mapNotNull { docs[it]?.nome }),
                        aoConcluir = { enviando = false; aoConcluir() },
                        aoErrar = { enviando = false; erros = mapOf("periodo" to it) })
                }) { Text("Sim, reservar", color = p.verde, fontWeight = FontWeight.Black) }
            },
            dismissButton = { TextButton({ confirmar = false }) { Text("Voltar") } }
        )
    }
}
