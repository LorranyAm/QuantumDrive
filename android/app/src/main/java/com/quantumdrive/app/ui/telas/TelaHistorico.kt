package com.quantumdrive.app.ui.telas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumdrive.app.data.EmailEnviado
import com.quantumdrive.app.data.Reserva
import com.quantumdrive.app.data.StatusReserva
import com.quantumdrive.app.ui.QuantumViewModel
import com.quantumdrive.app.ui.componentes.*
import com.quantumdrive.app.ui.theme.LocalPaleta
import java.time.Duration
import java.time.LocalDateTime

/**
 * Histórico com atualização em tempo real: a lista observa as reservas, então qualquer mudança
 * (dono no painel, aprovação automática, relógio) aparece na hora.
 */
@Composable
fun TelaHistorico(vm: QuantumViewModel, novaReserva: () -> Unit, alugarDeNovo: (String) -> Unit) {
    val p = LocalPaleta.current
    val todas by vm.reservas.collectAsStateWithLifecycle()
    val usuario by vm.sessao.collectAsStateWithLifecycle()
    val emails by vm.emails.collectAsStateWithLifecycle()
    val frota by vm.frota.collectAsStateWithLifecycle()
    val u = usuario ?: return
    val minhas = todas.filter { it.usuarioId == u.id }
    var filtro by remember { mutableStateOf("Todas") }
    var cancelar by remember { mutableStateOf<Reserva?>(null) }

    val visiveis = minhas.filter {
        when (filtro) {
            "Ativas" -> it.status.ativa
            "Concluídas" -> it.status == StatusReserva.CONCLUIDA
            "Canceladas" -> it.status == StatusReserva.CANCELADA
            else -> true
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Stepper(5) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Etiqueta("Minha conta"); Titulo("Histórico de reservas", 24) }
                Text("● AO VIVO", color = p.verde, fontWeight = FontWeight.Black, fontSize = 11.sp)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Kpi("Total de reservas", "${minhas.size}", Modifier.weight(1f))
                    Kpi("Ativas", "${minhas.count { it.status.ativa }}", Modifier.weight(1f), p.verde)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Kpi("Concluídas", "${minhas.count { it.status == StatusReserva.CONCLUIDA }}", Modifier.weight(1f))
                    Kpi("Km elétricos possíveis",
                        "%,d".format(minhas.filter { it.status != StatusReserva.CANCELADA }.sumOf { r -> frota.find { it.id == r.carroId }?.autonomiaKm ?: 0 }).replace(',', '.'),
                        Modifier.weight(1f), p.azul)
                }
            }
        }
        item { Abas(listOf("Todas", "Ativas", "Concluídas", "Canceladas"), filtro) { filtro = it } }
        if (visiveis.isEmpty()) item {
            CaixaQuantum {
                Text("Nenhuma reserva aqui ainda.", color = p.cinza)
                BotaoQuantum("Escolher um carro", Modifier.fillMaxWidth(), aoClicar = novaReserva)
            }
        }
        items(visiveis, key = { it.id }) { r ->
            val c = frota.find { it.id == r.carroId }
            CaixaQuantum {
                if (c != null) FotoCarro(c.foto, c.nome, Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(14.dp)))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text((c?.nome ?: r.carroId).uppercase(), fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    SeloStatus(r.status)
                }
                Text("Reserva ${r.id} · ${r.inicio.format(fmtDataHora)} → ${r.fim.format(fmtDataHora)} · ${r.dias} diária(s)\n${r.loja} · ${r.pagamento}",
                    color = p.cinza, fontSize = 13.sp)
                Text(brl(r.total), color = p.lima, fontWeight = FontWeight.Black)
                if (r.status != StatusReserva.CANCELADA) LinhaDoTempo(r.status)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (r.status == StatusReserva.ANALISE || r.status == StatusReserva.CONFIRMADA) BotaoContorno("Cancelar") { cancelar = r }
                    if (r.status == StatusReserva.CONCLUIDA) BotaoQuantum("Alugar de novo") { alugarDeNovo(r.carroId) }
                }
            }
        }
        item { Text("E-MAILS RECEBIDOS", fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 16.dp)) }
        val meus = emails.filter { it.para == u.email }.take(6)
        if (meus.isEmpty()) item { CaixaQuantum { Text("Os e-mails sobre suas reservas aparecem aqui.", color = p.cinza) } }
        items(meus) { CartaoEmail(it) }
    }

    cancelar?.let { r ->
        val horas = Duration.between(LocalDateTime.now(), r.inicio).toHours()
        AlertDialog(
            onDismissRequest = { cancelar = null },
            title = { Text("Cancelar a reserva ${r.id}?") },
            text = { Text(if (horas >= 24) "O cancelamento é gratuito porque faltam mais de 24 h para a retirada."
                          else "Faltam menos de 24 h para a retirada: pode haver cobrança de 1 diária.") },
            confirmButton = {
                TextButton({ vm.mudarStatus(r.id, StatusReserva.CANCELADA, "Cancelada pelo cliente"); cancelar = null }) {
                    Text("Cancelar reserva", color = p.erro, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = { TextButton({ cancelar = null }) { Text("Manter") } }
        )
    }
}

@Composable
fun CartaoEmail(m: EmailEnviado) {
    val p = LocalPaleta.current
    var aberto by remember { mutableStateOf(false) }
    CaixaQuantum {
        Text("Para: ${m.nome} <${m.para}>\n${m.em.format(fmtDataHora)} · via ${if (m.via == "EmailJS") "e-mail" else m.via}",
            color = p.cinza2, fontSize = 11.sp)
        Text(m.assunto, fontWeight = FontWeight.Bold)
        TextButton({ aberto = !aberto }) { Text(if (aberto) "Ocultar mensagem" else "Ver mensagem", color = p.verde) }
        if (aberto) Text(m.mensagem, color = p.cinza, fontSize = 13.sp)
    }
}

