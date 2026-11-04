package com.quantumdrive.app.ui.telas

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumdrive.app.data.Categoria
import com.quantumdrive.app.data.Frota
import com.quantumdrive.app.ui.QuantumViewModel
import com.quantumdrive.app.ui.componentes.*
import com.quantumdrive.app.ui.theme.LocalPaleta
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TelaInicio(vm: QuantumViewModel, abrirCarro: (String) -> Unit, verTodos: () -> Unit, abrirEconomia: () -> Unit) {
    val p = LocalPaleta.current
    val frota by vm.frota.collectAsStateWithLifecycle()
    vm.reservas.collectAsStateWithLifecycle()
    val destaques = Frota.destaques.mapNotNull { id -> frota.find { it.id == id } }
    val pager = rememberPagerState(pageCount = { destaques.size })
    var categoria by remember { mutableStateOf("Todos") }

    // Destaques trocam sozinhos, como no site
    LaunchedEffect(pager) {
        while (true) { delay(6500); pager.animateScrollToPage((pager.currentPage + 1) % destaques.size) }
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(520.dp)) {
                HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
                    val c = destaques[i]
                    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(p.verde.copy(alpha = .22f), Color.Transparent)))) {
                        FotoCarro(c.foto, c.nome, Modifier.fillMaxWidth().height(250.dp).padding(top = 8.dp), 0.dp)
                        Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Etiqueta("${c.categoria.name} 100% elétrico")
                            Titulo(c.nome, 34)
                            Text(c.descricao, color = p.cinza, maxLines = 2)
                            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                                DadoHero("${c.autonomiaKm} km", "Autonomia"); DadoHero("${c.potenciaCv} cv", "Potência"); DadoHero(brl(c.precoDia), "por dia")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                BotaoQuantum("Reservar agora") { abrirCarro(c.id) }
                                BotaoContorno("Saiba mais") { abrirCarro(c.id) }
                            }
                        }
                    }
                }
                Row(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(destaques.size) { i ->
                        Box(Modifier.width(34.dp).height(4.dp).clip(RoundedCornerShape(4.dp))
                            .background(if (i == pager.currentPage) p.verde else Color(0x4DFFFFFF)))
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Etiqueta("Frota Quantum Drive"); Spacer(Modifier.height(6.dp))
                Titulo("Modelos elétricos BYD", 26)
                Text("Escolha a categoria e veja quem está disponível agora.", color = p.cinza, modifier = Modifier.padding(vertical = 10.dp))
                Abas(listOf("Todos") + Categoria.entries.map { it.name }, categoria) { categoria = it }
            }
        }
        val lista = frota.filter { categoria == "Todos" || it.categoria.name == categoria }.take(6)
        items(lista, key = { it.id }) { c ->
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { CardCarro(c, vm.repo.livres(c)) { abrirCarro(c.id) } }
        }
        item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { BotaoContorno("Ver todos com filtros", aoClicar = verTodos) } }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Etiqueta("Simples assim"); Titulo("Como funciona", 26) } }
                listOf(
                    "Entre ou cadastre-se" to "Uma conta só para reservar, acompanhar e receber tudo por e-mail.",
                    "Escolha o veículo" to "Filtre por categoria, autonomia e disponibilidade.",
                    "Veja os detalhes" to "Ficha técnica completa e o valor total calculado na hora.",
                    "Reserve" to "Datas, pagamento e documentos validados antes do envio.",
                    "Acompanhe" to "Status ao vivo no histórico e avisos no seu e-mail."
                ).forEachIndexed { i, (t, d) ->
                    CaixaQuantum {
                        Text("%02d".format(i + 1), color = p.verde, fontSize = 30.sp, fontWeight = FontWeight.Black)
                        Text(t.uppercase(), fontWeight = FontWeight.Black)
                        Text(d, color = p.cinza)
                    }
                }
                CaixaQuantum(Modifier.clickable(onClick = abrirEconomia), borda = p.verde) {
                    Etiqueta("Calculadora de economia 2026")
                    Text("Quanto você economiza escolhendo um BYD?", fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text("Compare com um carro a combustão da mesma categoria, com os dados da BYD.", color = p.cinza)
                    BotaoQuantum("Calcular economia", Modifier.fillMaxWidth(), aoClicar = abrirEconomia)
                }
            }
        }
    }
}

@Composable
private fun DadoHero(valor: String, rotulo: String) = Column {
    Text(valor, fontWeight = FontWeight.Black, fontSize = 18.sp)
    Text(rotulo.uppercase(), color = LocalPaleta.current.cinza2, fontSize = 10.sp)
}

@Composable
fun TelaEconomia(vm: QuantumViewModel, abrirCarro: (String) -> Unit) {
    val p = LocalPaleta.current
    var filtro by remember { mutableStateOf("Todos") }
    var sel by remember { mutableIntStateOf(com.quantumdrive.app.data.Calculadora.modelos.indexOfFirst { it.nome == "BYD SEAL" }) }
    var km by remember { mutableStateOf("") }
    var gasolina by remember { mutableStateOf("") }
    var fracao by remember { mutableStateOf(0.5) }
    var erros by remember { mutableStateOf(mapOf<String, String>()) }
    var calculado by remember { mutableStateOf(false) }
    val modelos = com.quantumdrive.app.data.Calculadora.modelos
    val m = modelos[sel]

    // Só lê os valores (sem mexer no estado): seguro para usar enquanto a tela é desenhada
    fun lerKm() = km.filter(Char::isDigit).toIntOrNull() ?: 0
    fun lerGasolina() = gasolina.replace(".", "").filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0
    fun errosDe(k: Int, g: Double) = buildMap {
        if (k !in 1..200000) put("km", "Informe quantos km você roda por ano (ex.: 20000).")
        if (g <= 0 || g > 20) put("gas", "Informe o preço da gasolina (ex.: 6,29).")
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Etiqueta("Calculadora de economia 2026"); Spacer(Modifier.height(6.dp))
                Titulo("Calcule o quanto você pode economizar escolhendo um BYD", 22)
                Text("Selecione o modelo, informe quanto você roda por ano e veja o custo comparado a um carro a combustão.",
                    color = p.cinza, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item { Abas(listOf("Todos", "100% elétricos", "Super híbridos"), filtro) { f ->
            filtro = f
            val ok = { x: com.quantumdrive.app.data.Calculadora.Modelo -> f == "Todos" || (f == "100% elétricos") == x.eletrico }
            if (!ok(modelos[sel])) sel = modelos.indexOfFirst(ok)
        } }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(modelos.withIndex().filter { (_, x) -> filtro == "Todos" || (filtro == "100% elétricos") == x.eletrico }.toList()) { (i, x) ->
                    CaixaQuantum(Modifier.width(170.dp).clickable { sel = i }, borda = if (i == sel) p.verde else Color(0x14FFFFFF)) {
                        FotoCarro(x.foto, x.nome, Modifier.fillMaxWidth().height(80.dp), 2.dp)
                        Text(x.nome, fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 1)
                        Selo((if (p.comIcones) (if (x.eletrico) "⚡ " else "⛽⚡ ") else "") + if (x.eletrico) "100% elétrico" else "Super híbrido",
                            (if (x.eletrico) p.verde else p.azul).copy(alpha = .2f), if (x.eletrico) p.verde else p.azul)
                    }
                }
            }
        }
        item {
            CaixaQuantum {
                Lista("Modelo", modelos.map { it.nome }, m.nome, null) { n -> sel = modelos.indexOfFirst { it.nome == n } }
                Campo("Quilometragem por ano (km)", km, { km = it.filter(Char::isDigit).take(6) }, erros["km"],
                    teclado = androidx.compose.ui.text.input.KeyboardType.Number, dica = "Ex.: 20000")
                Campo("Preço da gasolina (R$/L)", gasolina, { gasolina = it.take(6) }, erros["gas"],
                    teclado = androidx.compose.ui.text.input.KeyboardType.Decimal, dica = "Ex.: 6,29")
                Campo("Preço da energia (R$/kWh)", "R$ 0,671", {}, somenteLeitura = true,
                    dica = "Valor de referência da média nacional. Com energia solar, o custo de recarga pode chegar a R$ 0,00.")
                if (!m.eletrico) {
                    Lista("% de rodagem no modo elétrico", (10 downTo 0).map { "${it * 10}%" + if (it == 5) "*" else "" },
                        "${(fracao * 100).toInt()}%" + if (fracao == 0.5) "*" else "", null) { fracao = it.filter(Char::isDigit).toInt() / 100.0 }
                    Text("* Proporção usada como padrão. Vale só para os super híbridos.", color = p.cinza2, fontSize = 12.sp)
                }
                BotaoQuantum("Ver economia", Modifier.fillMaxWidth()) {
                    erros = errosDe(lerKm(), lerGasolina())
                    if (erros.isNotEmpty()) vm.avisar(com.quantumdrive.app.ui.Aviso(com.quantumdrive.app.ui.Aviso.Tipo.ERRO, "Confira os dados", "Preencha a quilometragem e o preço da gasolina."))
                    else { calculado = true; vm.avisar(com.quantumdrive.app.ui.Aviso(com.quantumdrive.app.ui.Aviso.Tipo.SUCESSO, "Economia calculada", m.nome)) }
                }
            }
        }
        if (calculado) {
            val v = lerKm() to lerGasolina()
            if (errosDe(v.first, v.second).isEmpty()) item {
                val r = com.quantumdrive.app.data.Calculadora.calcular(m, v.first, v.second, fracao)
                CaixaQuantum {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.nome, fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.weight(1f))
                        Selo(if (m.eletrico) "100% elétrico" else "Super híbrido", if (m.eletrico) p.verde else p.azul)
                    }
                    Text(if (m.eletrico) "Consumo: ${num(m.kmPorKwh, 2)} km/kWh · comparado com ${m.comparativos.joinToString { "${it.nome} (${num(it.kmL, 1)} km/L)" }}"
                         else "Modo elétrico: ${num(m.kmPorKwh, 2)} km/kWh · modo combustão: ${num(m.kmPorLitro, 1)} km/L · ${(fracao * 100).toInt()}% elétrico",
                        color = p.cinza2, fontSize = 12.sp)
                    FotoCarro(m.foto, m.nome, Modifier.fillMaxWidth().height(170.dp))
                    CardValor("⚡", "${m.nome} — custo anual estimado", "Cálculo estimado com base no seu uso anual", brl(r.custoByd), "Rodagem anual: ${milhar(v.first)} km", Color.White)
                    CardValor("⛽", "Carro a combustão (média ref.)", "Consumo médio de veículos a combustão", brl(r.custoCombustao), "Média: ${num(r.refKmL, 1)} km/L", p.erro)
                    CardValor("💰", "Economia estimada por ano", "Comparação entre um carro a combustão e o seu BYD", brl(r.economia),
                        "${Math.round(r.percentual * 100)}% de economia estimada · ≈ ${brl(r.economia / 12)} por mês", p.lima, destaque = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Kpi("% de economia", "${Math.round(r.percentual * 100)}%", Modifier.weight(1f), p.verde)
                        Kpi("Eficiência vs. combustão", "${num(r.eficiencia, 2)}×", Modifier.weight(1f), p.verde)
                    }
                    Kpi("Equivalente de consumo no modo elétrico", "${num(r.equivalenteKmL, 1)} km/L", cor = p.verde)
                    if (m.idFrota != null) BotaoQuantum("Alugar este modelo", Modifier.fillMaxWidth()) { abrirCarro(m.idFrota) }
                    else Text("Modelo ainda não disponível na frota Quantum Drive.", color = p.cinza2)
                }
            }
        }
        item {
            Text("Critérios do cálculo — estimativa de custo médio por ano. Considera o consumo médio de carros a combustão da mesma categoria, " +
                "informado pelos fabricantes; a quilometragem anual informada; a proporção entre modo elétrico e combustão (nos super híbridos); " +
                "o custo médio residencial de energia em 2026; autonomia PBEV para os 100% elétricos e NEDC para os híbridos. " +
                "O resultado varia conforme a cidade, a rotina, o jeito de dirigir e a temperatura. Dados e fotos: calculadora de economia BYD Brasil 2026.",
                color = p.cinza2, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CardValor(icone: String, titulo: String, sub: String, valor: String, nota: String, cor: Color, destaque: Boolean = false) {
    val p = LocalPaleta.current
    CaixaQuantum(borda = if (destaque) p.verde else p.borda) {
        Text(icone, fontSize = 24.sp)
        Text(titulo.uppercase(), fontWeight = FontWeight.Black, fontSize = 13.sp)
        Text(sub, color = p.cinza2, fontSize = 12.sp)
        Text(valor, color = cor, fontWeight = FontWeight.Black, fontSize = 26.sp)
        Text(nota, color = p.cinza, fontSize = 12.sp)
    }
}
