package com.quantumdrive.app.ui.telas

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumdrive.app.data.Daltonismo
import com.quantumdrive.app.ui.QuantumViewModel
import com.quantumdrive.app.ui.componentes.BotaoQuantum
import com.quantumdrive.app.ui.theme.LocalPaleta

/** Amostras das paletas (as mesmas do site). */
private val AMOSTRAS = mapOf(
    Daltonismo.DESLIGADO to listOf(0xFF00F5A0, 0xFF00D1FF, 0xFFFFB547, 0xFFFF5470),
    Daltonismo.PROTAN to listOf(0xFF7CC4FF, 0xFFB452FF, 0xFFFFE600, 0xFFE8513F),
    Daltonismo.DEUTAN to listOf(0xFF7CC4FF, 0xFFB452FF, 0xFFFFE600, 0xFFE8513F),
    Daltonismo.TRITAN to listOf(0xFF13ECDD, 0xFFEC13DD, 0xFFF8D5AA, 0xFFFF2E1A),
    Daltonismo.ACRO to listOf(0xFFFFFFFF, 0xFFC8C8C8, 0xFF8A8A8A, 0xFFFFFFFF)
)

/** Menu de acessibilidade (botão ♿): Libras, modo daltônico, alto contraste e tamanho da letra. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuAcessibilidade(vm: QuantumViewModel, textoDaTela: String, aoFechar: () -> Unit) {
    val p = LocalPaleta.current
    val pref by vm.preferencias.collectAsStateWithLifecycle()
    var libras by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = aoFechar, containerColor = Color(0xFF0D111A)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Acessibilidade", fontWeight = FontWeight.Black, fontSize = 22.sp)
            Button({ libras = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1351B4))) {
                Text("🤟 Traduzir esta tela em Libras (VLibras)", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Text("MODO DALTÔNICO", fontWeight = FontWeight.Black, fontSize = 13.sp)
            Text("O app troca as cores por outras que você distingue e mostra ícones nos avisos e status.", color = p.cinza2, fontSize = 12.sp)
            Daltonismo.entries.forEach { d ->
                val sel = pref.daltonismo == d
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (sel) Color(0xFF1A1F2B) else Color(0xFF0F121A))
                    .border(2.dp, if (sel) Color.White else Color(0x1FFFFFFF), RoundedCornerShape(14.dp))
                    .clickable { vm.daltonismo(d) }.semantics { role = Role.RadioButton; selected = sel }
                    .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.nome, fontWeight = FontWeight.Bold)
                        Text(d.descricao, color = p.cinza2, fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        AMOSTRAS.getValue(d).forEach { c -> Box(Modifier.size(16.dp).clip(CircleShape).background(Color(c)).border(1.dp, Color(0x4DFFFFFF), CircleShape)) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Alto contraste", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Switch(pref.altoContraste, { vm.mudarPreferencias(pref.copy(altoContraste = it)) })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tamanho da letra", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                OutlinedButton({ vm.mudarPreferencias(pref.copy(escalaFonte = (pref.escalaFonte - 0.1f).coerceAtLeast(0.8f))) }) { Text("A−") }
                Text("${(pref.escalaFonte * 100).toInt()}%")
                OutlinedButton({ vm.mudarPreferencias(pref.copy(escalaFonte = (pref.escalaFonte + 0.1f).coerceAtMost(1.5f))) }) { Text("A+") }
            }
        }
    }
    if (libras) DialogoLibras(textoDaTela) { libras = false }
}

/**
 * Tradutor de Libras: abre o VLibras (plugin oficial do Governo Federal) com o texto da tela atual.
 * Toque em um parágrafo para o avatar traduzir. Precisa de internet.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DialogoLibras(texto: String, aoFechar: () -> Unit) {
    val paragrafos = texto.split("\n").filter { it.isNotBlank() }.joinToString("") {
        "<p>" + it.replace("&", "&amp;").replace("<", "&lt;") + "</p>"
    }
    val html = """<!DOCTYPE html><html lang="pt-BR"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>body{margin:0;padding:18px 70px 18px 18px;background:#05070c;color:#fff;font-family:Arial,sans-serif;font-size:18px;line-height:1.6}
        h1{color:#00F5A0;font-size:20px}p{background:#151821;border:1px solid rgba(0,245,160,.25);border-radius:12px;padding:12px;cursor:pointer}
        .dica{color:#94a3b8;font-size:14px;background:none;border:none;padding:0}</style></head><body>
        <h1>🤟 Libras — Quantum Drive</h1><p class="dica">Toque em um texto para o avatar traduzir.</p>$paragrafos
        <script src="https://vlibras.gov.br/app/vlibras-plugin.js"></script>
        <script>try{new window.VLibras.Widget('https://vlibras.gov.br/app');setTimeout(function(){if(window.VLibrasWidget&&VLibrasWidget.open)VLibrasWidget.open();},1500);}
        catch(e){document.body.insertAdjacentHTML('beforeend','<p>O VLibras precisa de internet. Confira a conexão e tente de novo.</p>');}</script>
        </body></html>"""
    Dialog(onDismissRequest = aoFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF05070C))) {
            AndroidView(factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webViewClient = WebViewClient(); webChromeClient = WebChromeClient()
                    setBackgroundColor(0xFF05070C.toInt())
                    loadDataWithBaseURL("https://lorranyam.github.io/QuantumDrive/", html, "text/html", "UTF-8", null)
                }
            }, modifier = Modifier.weight(1f).fillMaxWidth())
            Box(Modifier.fillMaxWidth().padding(16.dp)) { BotaoQuantum("Fechar Libras", Modifier.fillMaxWidth(), aoClicar = aoFechar) }
        }
    }
}
