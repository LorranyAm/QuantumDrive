package com.quantumdrive.app.ui.telas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumdrive.app.data.Carro
import com.quantumdrive.app.data.Categoria
import com.quantumdrive.app.data.Orcamento
import com.quantumdrive.app.ui.Aviso
import com.quantumdrive.app.ui.QuantumViewModel
import com.quantumdrive.app.ui.componentes.*
import com.quantumdrive.app.ui.theme.LocalPaleta
import com.quantumdrive.app.util.Validadores
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Composable
fun TelaVeiculos(vm: QuantumViewModel, abrirDetalhes: (String) -> Unit) {
    val p = LocalPaleta.current
    val frota by vm.frota.collectAsStateWithLifecycle()
    vm.reservas.collectAsStateWithLifecycle() // recompõe quando a disponibilidade muda
    var categoria by remember { mutableStateOf("Todos") }
    var disponibilidade by remember { mutableStateOf("Qualquer disponibilidade") }
    var ordem by remember { mutableStateOf("Menor preço") }
    var busca by remember { mutableStateOf("") }

    val lista = frota
        .filter { categoria == "Todos" || it.categoria.name == categoria }
        .filter { disponibilidade != "Somente disponíveis" || (it.unidadesOperando > 0 && vm.repo.livres(it) > 0) }
        .filter { busca.isBlank() || it.nome.contains(busca.trim(), ignoreCase = true) }
        .let { l ->
            when (ordem) {
                "Maior preço" -> l.sortedByDescending { it.precoDia }
                "Maior autonomia" -> l.sortedByDescending { it.autonomiaKm }
                "Mais potência" -> l.sortedByDescending { it.potenciaCv }
                else -> l.sortedBy { it.precoDia }
            }
        }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Stepper(2) }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Etiqueta("Catálogo"); Spacer(Modifier.height(6.dp))
                Titulo("Escolha seu elétrico")
                Text("Status de disponibilidade atualizado em tempo real.", color = p.cinza, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item { Abas(listOf("Todos") + Categoria.entries.map { it.name }, categoria) { categoria = it } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Lista("Disponibilidade", listOf("Qualquer disponibilidade", "Somente disponíveis"), disponibilidade, null) { disponibilidade = it }
                Lista("Ordenar", listOf("Menor preço", "Maior preço", "Maior autonomia", "Mais potência"), ordem, null) { ordem = it }
                Campo("Buscar modelo…", busca, { busca = it })
            }
        }
        if (lista.isEmpty()) item {
            CaixaQuantum {
                Text("Nenhum veículo encontrado com esses filtros.", color = p.cinza)
                TextButton({ categoria = "Todos"; disponibilidade = "Qualquer disponibilidade"; busca = "" }) {
                    Text("Limpar filtros", color = p.verde, fontWeight = FontWeight.Bold)
                }
            }
        }
        items(lista, key = { it.id }) { c -> CardCarro(c, vm.repo.livres(c)) { abrirDetalhes(c.id) } }
    }
}

@Composable
fun CardCarro(c: Carro, livres: Int, prefixo: String = "", aoClicar: () -> Unit) {
    val p = LocalPaleta.current
    CaixaQuantum(Modifier.clickable(onClick = aoClicar)) {
        Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(16.dp))) {
            FotoCarro(c.foto, c.nome, Modifier.fillMaxSize(), 14.dp)
            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                SeloDisponibilidade(c, livres)
                Selo(c.categoria.name, androidx.compose.ui.graphics.Color(0xB3000000), androidx.compose.ui.graphics.Color.White)
            }
        }
        Text((prefixo + c.nome).uppercase(), fontWeight = FontWeight.Black, fontSize = 20.sp)
        Text(c.descricao, color = p.cinza, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spec("${c.autonomiaKm} km", "Autonomia", Modifier.weight(1f))
            Spec("${c.potenciaCv} cv", "Potência", Modifier.weight(1f))
            Spec("${c.lugares}", "Lugares", Modifier.weight(1f))
        }
        BarraAutonomia(c.autonomiaKm)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(brl(c.precoDia), color = p.lima, fontWeight = FontWeight.Black, fontSize = 22.sp)
                Text("/dia", color = p.cinza2, fontSize = 12.sp)
            }
            BotaoQuantum(if (livres > 0 && c.unidadesOperando > 0) "Ver e reservar" else "Ver detalhes", aoClicar = aoClicar)
        }
    }
}

/** Datas iniciais: rascunho salvo (se ainda for futuro) ou amanhã → +3 dias, às 10h. */
fun datasIniciais(vm: QuantumViewModel): Pair<LocalDateTime, LocalDateTime> {
    val r = vm.rascunhoInicio; val f = vm.rascunhoFim
    if (r != null && f != null && r.isAfter(LocalDateTime.now())) return r to f
    val amanha = LocalDate.now().plusDays(1)
    return LocalDateTime.of(amanha, LocalTime.of(10, 0)) to LocalDateTime.of(amanha.plusDays(3), LocalTime.of(10, 0))
}

@Composable
fun TelaDetalhes(vm: QuantumViewModel, id: String, continuar: () -> Unit, voltar: () -> Unit, abrirOutro: (String) -> Unit) {
    val p = LocalPaleta.current
    val frota by vm.frota.collectAsStateWithLifecycle()
    vm.reservas.collectAsStateWithLifecycle()
    val c = frota.find { it.id == id } ?: run {
        CaixaQuantum(Modifier.padding(16.dp)) { Text("Veículo não encontrado."); TextButton(voltar) { Text("Voltar ao catálogo") } }; return
    }
    val livres = vm.repo.livres(c)
    val (ini0, fim0) = remember(id) { datasIniciais(vm) }
    var dataIni by remember(id) { mutableStateOf(ini0.toLocalDate()) }
    var horaIni by remember(id) { mutableStateOf(ini0.toLocalTime()) }
    var dataFim by remember(id) { mutableStateOf(fim0.toLocalDate()) }
    var horaFim by remember(id) { mutableStateOf(fim0.toLocalTime()) }
    val ini = LocalDateTime.of(dataIni, horaIni)
    val fim = LocalDateTime.of(dataFim, horaFim)
    val erro = Validadores.periodo(ini, fim) ?: if (vm.repo.temConflito(c.id, ini, fim)) "Sem unidades livres neste período. Tente outras datas ou outro modelo." else null
    val orc = Orcamento.calcular(c.precoDia, ini, fim, true)
    val semelhantes = frota.filter { it.categoria == c.categoria && it.id != c.id }.take(3)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Stepper(3)
        TextButton(onClick = voltar) { Text("← Voltar ao catálogo", color = p.verde, fontWeight = FontWeight.Bold) }
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(22.dp))) {
            FotoCarro(c.foto, c.nome, Modifier.fillMaxSize(), 20.dp)
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                SeloDisponibilidade(c, livres, mostrarLivres = true)
                Selo(c.categoria.name, androidx.compose.ui.graphics.Color(0xB3000000), androidx.compose.ui.graphics.Color.White)
            }
        }
        Etiqueta("${c.categoria.name} 100% elétrico")
        Titulo(c.nome, 32)
        Text(c.descricao, color = p.cinza, fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spec("${c.autonomiaKm} km", "Autonomia", Modifier.weight(1f))
            Spec("${c.potenciaCv} cv", "Potência", Modifier.weight(1f))
            Spec("${c.bateriaKwh} kWh", "Bateria", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spec(c.recarga.substringBefore(" em"), "Recarga rápida", Modifier.weight(1f))
            Spec("${"%.2f".format(c.kmPorKwh)} km/kWh", "Consumo (BYD)", Modifier.weight(1f))
            Spec("${c.lugares} · ${c.portaMalasL} L", "Lugares · malas", Modifier.weight(1f))
        }
        BarraAutonomia(c.autonomiaKm)
        Text("DESTAQUES", fontWeight = FontWeight.Black)
        (c.itens + "Seguro, assistência 24 h e cabo de recarga inclusos").forEach { Text("✓  $it", color = p.cinza) }

        CaixaQuantum {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(brl(c.precoDia), color = p.lima, fontWeight = FontWeight.Black, fontSize = 28.sp)
                Text("/dia", color = p.cinza2)
            }
            Text("10% de desconto a partir de 7 diárias.", color = p.cinza2, fontSize = 12.sp)
            SeletorDataHora("Retirada", dataIni, horaIni, { dataIni = it }, { horaIni = it })
            SeletorDataHora("Devolução", dataFim, horaFim, { dataFim = it }, { horaFim = it })
            if (erro != null) Text((if (p.comIcones) "⚠ " else "") + erro, color = p.erro, fontSize = 13.sp)
            else {
                LinhaResumo("${orc.dias} diária(s) × ${brl(c.precoDia)}", brl(orc.subtotal))
                LinhaResumo("Proteção completa", brl(orc.protecao))
                if (orc.desconto > 0) LinhaResumo("Desconto 7+ dias", "− " + brl(orc.desconto), p.verde)
                LinhaResumo("Total estimado", brl(orc.total), p.lima)
            }
            BotaoQuantum(if (c.unidadesOperando == 0) "Indisponível no momento" else "Continuar reserva", Modifier.fillMaxWidth(),
                habilitado = c.unidadesOperando > 0) {
                if (erro != null) vm.avisar(Aviso(Aviso.Tipo.ERRO, "Confira as datas", erro))
                else { vm.rascunhoInicio = ini; vm.rascunhoFim = fim; continuar() }
            }
            if (vm.sessao.value == null) Text("Você fará login no próximo passo — suas datas ficam salvas.", color = p.cinza2, fontSize = 12.sp)
        }
        if (semelhantes.isNotEmpty()) {
            Text("COMPARE COM OUTROS ${c.categoria.name.uppercase()}S", fontWeight = FontWeight.Black)
            semelhantes.forEach { s -> CardCarro(s, vm.repo.livres(s)) { abrirOutro(s.id) } }
        }
    }
}
