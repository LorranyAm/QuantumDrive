package com.quantumdrive.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.quantumdrive.app.data.Daltonismo
import com.quantumdrive.app.data.Preferencias

/** Cores fixas do Quantum Drive (iguais às do site). */
object Cores {
    val Fundo = Color(0xFF05070C)
    val Fundo2 = Color(0xFF0A1B2E)
    val Campo = Color(0xFF0F121A)
    val TextoEscuro = Color(0xFF00110C)
    val gradienteFundo = Brush.linearGradient(listOf(Fundo, Fundo2))
}

/**
 * Paleta semântica. No modo daltônico ela é trocada por cores que cada tipo
 * consegue distinguir (as mesmas do site, testadas com simulação de daltonismo).
 */
@Immutable
data class Paleta(
    val verde: Color,      // ok / disponível / destaque da marca
    val azul: Color,       // informação / em análise
    val lima: Color,       // preços e valores
    val alerta: Color,     // reservado / aviso
    val erro: Color,       // erro / manutenção / cancelado
    val cinza: Color = Color(0xFFCBD5E1),
    val cinza2: Color = Color(0xFF94A3B8),
    val card: Color = Color(0xFA151821),
    val borda: Color = Color(0x4000F5A0),
    val comIcones: Boolean = false   // mostra ícones nos status (modo daltônico)
) {
    val gradienteBotao get() = Brush.horizontalGradient(listOf(verde, azul))

    companion object {
        val ORIGINAL = Paleta(Color(0xFF00F5A0), Color(0xFF00D1FF), Color(0xFFA8FF00), Color(0xFFFFB547), Color(0xFFFF5470))
        private val VERMELHO_VERDE = Paleta(Color(0xFF7CC4FF), Color(0xFFB452FF), Color.White, Color(0xFFFFE600), Color(0xFFE8513F),
            borda = Color(0x527CC4FF), comIcones = true)
        private val AZUL_AMARELO = Paleta(Color(0xFF13ECDD), Color(0xFFEC13DD), Color.White, Color(0xFFF8D5AA), Color(0xFFFF2E1A),
            borda = Color(0x5213ECDD), comIcones = true)
        private val SEM_CORES = Paleta(Color.White, Color(0xFFC8C8C8), Color.White, Color(0xFFC8C8C8), Color.White,
            cinza = Color(0xFFE6E6E6), borda = Color(0x59FFFFFF), comIcones = true)

        fun para(p: Preferencias): Paleta {
            val base = when (p.daltonismo) {
                Daltonismo.DESLIGADO -> ORIGINAL
                Daltonismo.PROTAN, Daltonismo.DEUTAN -> VERMELHO_VERDE
                Daltonismo.TRITAN -> AZUL_AMARELO
                Daltonismo.ACRO -> SEM_CORES
            }
            return if (p.altoContraste) base.copy(cinza = Color.White, cinza2 = Color(0xFFE5E5E5), card = Color.Black, borda = Color.White) else base
        }
    }
}

val LocalPaleta = staticCompositionLocalOf { Paleta.ORIGINAL }

@Composable
fun TemaQuantum(preferencias: Preferencias, conteudo: @Composable () -> Unit) {
    val p = Paleta.para(preferencias)
    val esquema = darkColorScheme(
        primary = p.verde, onPrimary = Cores.TextoEscuro, secondary = p.azul, onSecondary = Cores.TextoEscuro,
        tertiary = p.lima, background = Cores.Fundo, onBackground = Color.White, surface = p.card, onSurface = Color.White,
        surfaceVariant = Cores.Campo, onSurfaceVariant = p.cinza, error = p.erro, outline = p.borda,
        surfaceContainerHigh = Color(0xFF151A26), surfaceContainer = Color(0xFF111521)
    )
    val densidade = LocalDensity.current
    CompositionLocalProvider(
        LocalPaleta provides p,
        // A+ / A− do menu de acessibilidade
        LocalDensity provides Density(densidade.density, densidade.fontScale * preferencias.escalaFonte)
    ) {
        MaterialTheme(colorScheme = esquema, content = conteudo)
    }
}
