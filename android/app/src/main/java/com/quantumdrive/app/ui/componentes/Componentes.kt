package com.quantumdrive.app.ui.componentes

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quantumdrive.app.data.Carro
import com.quantumdrive.app.data.Frota
import com.quantumdrive.app.data.StatusReserva
import com.quantumdrive.app.ui.theme.Cores
import com.quantumdrive.app.ui.theme.LocalPaleta
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val formatoBrl: NumberFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
fun brl(v: Number): String = formatoBrl.format(v.toDouble())
val fmtDataHora: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
val fmtCurto: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")

@Composable
fun BotaoQuantum(texto: String, modifier: Modifier = Modifier, carregando: Boolean = false,
                 habilitado: Boolean = true, aoClicar: () -> Unit) {
    val p = LocalPaleta.current
    Box(
        modifier
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(50))
            .background(if (habilitado) p.gradienteBotao else SolidColor(Color(0xFF3A3F4B)))
            .clickable(enabled = habilitado && !carregando, onClick = aoClicar)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (carregando) CircularProgressIndicator(Modifier.size(22.dp), color = Cores.TextoEscuro, strokeWidth = 3.dp)
        else Text(texto.uppercase(), color = Cores.TextoEscuro, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp,
            fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun BotaoContorno(texto: String, modifier: Modifier = Modifier, cor: Color = Color.White, aoClicar: () -> Unit) {
    OutlinedButton(onClick = aoClicar, modifier = modifier.heightIn(min = 46.dp), shape = RoundedCornerShape(50),
        border = BorderStroke(2.dp, cor)) {
        Text(texto.uppercase(), color = cor, fontWeight = FontWeight.Black, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

/** Campo com a mensagem de erro logo abaixo (validação antes de enviar). */
@Composable
fun Campo(
    rotulo: String, valor: String, aoMudar: (String) -> Unit, erro: String? = null,
    modifier: Modifier = Modifier, teclado: KeyboardType = KeyboardType.Text, senha: Boolean = false,
    dica: String? = null, somenteLeitura: Boolean = false
) {
    val p = LocalPaleta.current
    OutlinedTextField(
        value = valor, onValueChange = aoMudar, label = { Text(rotulo) }, readOnly = somenteLeitura,
        isError = erro != null, singleLine = true, modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
        visualTransformation = if (senha) PasswordVisualTransformation() else VisualTransformation.None,
        supportingText = {
            when {
                erro != null -> Text((if (p.comIcones) "⚠ " else "") + erro, color = p.erro)
                dica != null -> Text(dica, color = p.cinza2)
            }
        },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = p.verde, unfocusedBorderColor = Color(0x22FFFFFF),
            focusedContainerColor = Cores.Campo, unfocusedContainerColor = Cores.Campo,
            focusedLabelColor = p.verde, errorBorderColor = p.erro, errorLabelColor = p.erro
        )
    )
}

@Composable
fun Etiqueta(texto: String, cor: Color = LocalPaleta.current.verde) =
    Text(texto.uppercase(), color = cor, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 2.sp)

@Composable
fun Titulo(texto: String, tamanho: Int = 28) =
    Text(texto.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = tamanho.sp, lineHeight = (tamanho + 2).sp)

@Composable
fun CaixaQuantum(modifier: Modifier = Modifier, borda: Color = LocalPaleta.current.borda, conteudo: @Composable ColumnScope.() -> Unit) {
    val p = LocalPaleta.current
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.card)
            .border(1.dp, borda, RoundedCornerShape(22.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = conteudo
    )
}

@Composable
fun Selo(texto: String, fundo: Color, cor: Color = Cores.TextoEscuro) {
    Text(texto.uppercase(), color = cor, fontWeight = FontWeight.Black, fontSize = 10.sp, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(fundo).padding(horizontal = 10.dp, vertical = 5.dp))
}

/** Disponível / Última unidade / Reservado / Em manutenção — com ícone no modo daltônico. */
@Composable
fun SeloDisponibilidade(carro: Carro, livres: Int, mostrarLivres: Boolean = false) {
    val p = LocalPaleta.current
    fun ic(s: String) = if (p.comIcones) "$s " else ""
    when {
        carro.unidadesOperando == 0 -> Selo(ic("🔧") + "Em manutenção", p.erro, Color.White)
        livres == 0 -> Selo(ic("⏳") + "Reservado", p.alerta, Color(0xFF1A1000))
        else -> Selo(ic("✓") + (if (livres == 1) "Última unidade" else "Disponível") +
            (if (mostrarLivres) " · $livres livre${if (livres > 1) "s" else ""}" else ""), p.verde)
    }
}

@Composable
fun SeloStatus(s: StatusReserva) {
    val p = LocalPaleta.current
    val cor = when (s) {
        StatusReserva.ANALISE -> p.azul
        StatusReserva.CONFIRMADA -> p.verde
        StatusReserva.ANDAMENTO -> p.lima
        StatusReserva.CONCLUIDA -> p.cinza
        StatusReserva.CANCELADA -> p.erro
    }
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(cor.copy(alpha = .16f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
        if (p.comIcones) Text(s.icone, color = cor, fontSize = 11.sp)
        else Box(Modifier.size(8.dp).clip(CircleShape).background(cor))
        Spacer(Modifier.width(6.dp))
        Text(s.rotulo.uppercase(), color = cor, fontWeight = FontWeight.Black, fontSize = 11.sp)
    }
}

/** Foto oficial da BYD com o brilho verde do site por trás. */
@Composable
fun FotoCarro(@DrawableRes foto: Int, nome: String, modifier: Modifier = Modifier, preenchimento: Dp = 10.dp) {
    val p = LocalPaleta.current
    Box(modifier.background(Brush.radialGradient(listOf(p.verde.copy(alpha = .22f), Color.Transparent))).background(Color(0x330D1220)),
        contentAlignment = Alignment.Center) {
        Image(painterResource(foto), contentDescription = "Foto do $nome", contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(preenchimento))
    }
}

@Composable
fun BarraAutonomia(km: Int) {
    val p = LocalPaleta.current
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x14FFFFFF))
        .semantics { contentDescription = "Autonomia relativa: $km km" }) {
        Box(Modifier.fillMaxWidth(km.toFloat() / Frota.maxAutonomia).fillMaxHeight().background(p.gradienteBotao))
    }
}

@Composable
fun Spec(valor: String, rotulo: String, modifier: Modifier = Modifier) {
    val p = LocalPaleta.current
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x0FFFFFFF)).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valor, color = p.verde, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(rotulo.uppercase(), color = p.cinza2, fontSize = 10.sp, maxLines = 1)
    }
}

/** Indicador do fluxo: Entrar → Veículos → Detalhes → Reserva → Histórico */
@Composable
fun Stepper(atual: Int) {
    val p = LocalPaleta.current
    val passos = listOf("Entrar", "Veículos", "Detalhes", "Reserva", "Histórico")
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        passos.forEachIndexed { i, nome ->
            val n = i + 1
            val feito = n < atual
            val cor = if (feito || n == atual) p.verde else p.cinza2
            Box(Modifier.size(26.dp).clip(CircleShape).background(if (feito) p.verde else Color.Transparent)
                .border(2.dp, cor, CircleShape), contentAlignment = Alignment.Center) {
                Text(if (feito) "✓" else "$n", color = if (feito) Cores.TextoEscuro else cor, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            if (n == atual) Text(" $nome", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            if (n < passos.size) Box(Modifier.padding(horizontal = 4.dp).width(12.dp).height(2.dp).background(Color(0x26FFFFFF)))
        }
    }
}

@Composable
fun LinhaResumo(rotulo: String, valor: String, destaque: Color = Color.White) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(rotulo, color = LocalPaleta.current.cinza, modifier = Modifier.weight(1f))
        Text(valor, color = destaque, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}

@Composable
fun Kpi(rotulo: String, valor: String, modifier: Modifier = Modifier, cor: Color = Color.White, nota: String? = null) {
    CaixaQuantum(modifier) {
        Text(rotulo.uppercase(), color = LocalPaleta.current.cinza2, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        Text(valor, color = cor, fontSize = 19.sp, fontWeight = FontWeight.Black, maxLines = 1)
        if (nota != null) Text(nota, color = LocalPaleta.current.cinza2, fontSize = 11.sp)
    }
}

@Composable
fun LinhaDoTempo(atual: StatusReserva) {
    val p = LocalPaleta.current
    val idx = StatusReserva.LINHA_DO_TEMPO.indexOf(atual)
    Column(Modifier.semantics { contentDescription = "Progresso: ${atual.rotulo}" }) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            StatusReserva.LINHA_DO_TEMPO.forEachIndexed { i, _ ->
                Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(5.dp))
                    .background(if (i <= idx) p.verde else Color(0x1AFFFFFF)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            StatusReserva.LINHA_DO_TEMPO.forEach { Text(it.rotulo, color = p.cinza2, fontSize = 9.sp) }
        }
    }
}

/** Gráfico de barras simples (faturamento por mês / reservas por modelo). */
@Composable
fun GraficoBarras(valores: List<Pair<String, Int>>, selecionado: Int = -1, altura: Dp = 150.dp,
                  rotuloValor: (Int) -> String = { "$it" }, aoTocar: ((Int) -> Unit)? = null) {
    val p = LocalPaleta.current
    val max = (valores.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(altura + 34.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom) {
        valores.forEachIndexed { i, (nome, v) ->
            Column(Modifier.weight(1f).let { if (aoTocar != null) it.clickable { aoTocar(i) } else it },
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (v > 0) rotuloValor(v) else "", color = Color.White, fontSize = 8.sp, maxLines = 1)
                Box(Modifier.fillMaxWidth().height((altura * (v.toFloat() / max)).coerceAtLeast(3.dp))
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .background(if (i == selecionado) Brush.verticalGradient(listOf(p.lima, p.verde))
                                else Brush.verticalGradient(listOf(p.azul, p.verde)))
                    .semantics { contentDescription = "$nome: ${rotuloValor(v)}" })
                Text(nome, color = if (i == selecionado) Color.White else p.cinza2, fontSize = 8.sp, maxLines = 1,
                    fontWeight = if (i == selecionado) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Lista(rotulo: String, opcoes: List<String>, valor: String, erro: String?, aoEscolher: (String) -> Unit) {
    val p = LocalPaleta.current
    var aberto by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(aberto, { aberto = it }) {
        OutlinedTextField(
            value = valor, onValueChange = {}, readOnly = true, label = { Text(rotulo) }, isError = erro != null,
            supportingText = { erro?.let { Text((if (p.comIcones) "⚠ " else "") + it, color = p.erro) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(aberto) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Cores.Campo, unfocusedContainerColor = Cores.Campo,
                focusedBorderColor = p.verde, unfocusedBorderColor = Color(0x22FFFFFF), errorBorderColor = p.erro)
        )
        ExposedDropdownMenu(aberto, { aberto = false }) {
            opcoes.forEach { DropdownMenuItem({ Text(it) }, { aoEscolher(it); aberto = false }) }
        }
    }
}

/** Data (calendário) + hora (horários da loja, 7h às 20h). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeletorDataHora(rotulo: String, data: LocalDate, hora: LocalTime, aoData: (LocalDate) -> Unit, aoHora: (LocalTime) -> Unit) {
    val p = LocalPaleta.current
    var abrirData by remember { mutableStateOf(false) }
    var abrirHora by remember { mutableStateOf(false) }
    Text(rotulo, fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({ abrirData = true }, Modifier.weight(1.4f), shape = RoundedCornerShape(14.dp)) {
            Text("📅 " + data.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), color = Color.White)
        }
        Box(Modifier.weight(1f)) {
            OutlinedButton({ abrirHora = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("🕙 $hora", color = Color.White)
            }
            DropdownMenu(abrirHora, { abrirHora = false }) {
                (7..20).forEach { h -> DropdownMenuItem({ Text("%02d:00".format(h)) }, { aoHora(LocalTime.of(h, 0)); abrirHora = false }) }
            }
        }
    }
    if (abrirData) {
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = data.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(LocalDate.now())
            }
        )
        DatePickerDialog(
            onDismissRequest = { abrirData = false },
            confirmButton = {
                TextButton({
                    estado.selectedDateMillis?.let { aoData(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    abrirData = false
                }) { Text("OK", color = p.verde) }
            },
            dismissButton = { TextButton({ abrirData = false }) { Text("Cancelar") } }
        ) { DatePicker(estado) }
    }
}

/** Abas de filtro (Todos / Hatch / SUV / Sedan …). */
@Composable
fun Abas(opcoes: List<String>, selecionada: String, aoEscolher: (String) -> Unit) {
    val p = LocalPaleta.current
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(opcoes.size) { i ->
            val o = opcoes[i]; val ativa = o == selecionada
            Text(o.uppercase(), fontWeight = FontWeight.Black, fontSize = 12.sp,
                color = if (ativa) Cores.TextoEscuro else Color.White,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (ativa) p.verde else Color.Transparent)
                    .border(1.dp, if (ativa) p.verde else Color(0x2EFFFFFF), RoundedCornerShape(50))
                    .clickable { aoEscolher(o) }.padding(horizontal = 18.dp, vertical = 10.dp))
        }
    }
}
