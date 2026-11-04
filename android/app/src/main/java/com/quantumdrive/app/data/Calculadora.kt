package com.quantumdrive.app.data

import androidx.annotation.DrawableRes
import com.quantumdrive.app.R
import kotlin.math.max

/**
 * Calculadora de economia — mesmos dados e fórmula da calculadora BYD 2026 (igual ao site).
 * Elétricos: km/kWh e os carros a combustão usados na comparação.
 * Super híbridos: km/L no modo combustão e km/kWh no modo elétrico.
 * Sem comparativo → média de referência de 12,0 km/L. Energia fixa em R$ 0,671/kWh.
 */
object Calculadora {
    const val PRECO_ENERGIA = 0.671
    const val REF_KML_PADRAO = 12.0

    data class Comparativo(val nome: String, val kmL: Double)

    data class Modelo(
        val nome: String,
        val eletrico: Boolean,
        @DrawableRes val foto: Int,
        val kmPorKwh: Double,
        val kmPorLitro: Double = 0.0,
        val comparativos: List<Comparativo> = emptyList(),
        val idFrota: String? = null
    ) {
        val refKmL get() = if (comparativos.isEmpty()) REF_KML_PADRAO else comparativos.sumOf { it.kmL } / comparativos.size
    }

    val modelos = listOf(
        Modelo("BYD ATTO 2", false, R.drawable.byd_atto_2, 6.10, 20.8),
        Modelo("BYD ATTO 8", false, R.drawable.byd_atto_8, 3.38, 9.5),
        Modelo("BYD DOLPHIN", true, R.drawable.byd_dolphin, 6.00, comparativos = listOf(Comparativo("HB20", 13.3)), idFrota = "dolphin"),
        Modelo("BYD DOLPHIN MINI", true, R.drawable.byd_dolphin_mini, 6.50, comparativos = listOf(Comparativo("MOBI", 13.0)), idFrota = "dolphin-mini"),
        Modelo("BYD DOLPHIN PLUS", true, R.drawable.byd_dolphin_plus, 5.79, comparativos = listOf(Comparativo("HB20", 13.3)), idFrota = "dolphin-plus"),
        Modelo("BYD DOLPHIN SE", true, R.drawable.byd_dolphin_se, 6.03, comparativos = listOf(Comparativo("HB20", 13.3))),
        Modelo("BYD HAN", true, R.drawable.byd_han, 6.36, comparativos = listOf(Comparativo("SÉRIE 5", 6.5)), idFrota = "han"),
        Modelo("BYD KING", false, R.drawable.byd_king, 4.37, 16.8),
        Modelo("BYD SEALION 7", true, R.drawable.byd_sealion_7, 4.36, comparativos = listOf(Comparativo("SÉRIE 3", 11.3)), idFrota = "sealion-7"),
        Modelo("BYD SEAL", true, R.drawable.byd_seal, 4.51, comparativos = listOf(Comparativo("SÉRIE 3", 11.3)), idFrota = "seal"),
        Modelo("BYD SONG PLUS", false, R.drawable.byd_song_plus, 3.71, 18.1),
        Modelo("BYD SONG PLUS PREMIUM", false, R.drawable.byd_song_plus_premium, 2.78, 18.1),
        Modelo("BYD SONG PRO FLEX", false, R.drawable.byd_song_pro_flex, 3.93, 19.9),
        Modelo("BYD SHARK", false, R.drawable.byd_shark, 3.38, 9.5),
        Modelo("BYD TAN", true, R.drawable.byd_tan, 3.95, comparativos = listOf(Comparativo("XC90", 7.8)), idFrota = "tan"),
        Modelo("BYD YUAN PLUS AWD", true, R.drawable.byd_yuan_plus_awd, 5.05, comparativos = listOf(Comparativo("RENEGADE", 11.0)), idFrota = "yuan-plus"),
        Modelo("BYD YUAN PRO", true, R.drawable.byd_yuan_pro, 5.54, comparativos = listOf(Comparativo("T-CROSS", 13.1)), idFrota = "yuan-pro")
    )

    data class Resultado(
        val custoByd: Double,
        val custoCombustao: Double,
        val economia: Double,
        val percentual: Double,
        val eficiencia: Double,
        val equivalenteKmL: Double,
        val refKmL: Double
    )

    /** @param fracaoEletrica 0.0 a 1.0 (só vale para os super híbridos). */
    fun calcular(m: Modelo, kmAno: Int, precoGasolina: Double, fracaoEletrica: Double = 0.5): Resultado {
        val share = if (m.eletrico) 1.0 else fracaoEletrica.coerceIn(0.0, 1.0)
        val ref = m.refKmL
        val custoEle = if (m.kmPorKwh > 0) (kmAno / m.kmPorKwh) * PRECO_ENERGIA * share else 0.0
        val custoComb = if (!m.eletrico && m.kmPorLitro > 0) (kmAno / m.kmPorLitro) * precoGasolina * (1 - share) else 0.0
        val totalByd = custoEle + custoComb
        val totalRef = if (ref > 0) (kmAno / ref) * precoGasolina else 0.0
        val economia = max(0.0, totalRef - totalByd)
        val pct = if (totalRef > 0) economia / totalRef else 0.0
        val eq = if (m.kmPorKwh > 0) m.kmPorKwh * (precoGasolina / PRECO_ENERGIA) else 0.0
        return Resultado(totalByd, totalRef, economia, pct, if (ref > 0) eq / ref else 0.0, eq, ref)
    }
}
