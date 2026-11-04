package com.quantumdrive.app.data

import androidx.annotation.DrawableRes
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.roundToInt

enum class Categoria { Hatch, SUV, Sedan }

enum class Papel { CLIENTE, DONO }

/** Ciclo de vida da reserva — mesmos textos e e-mails do site. */
enum class StatusReserva(val rotulo: String, val icone: String, val assunto: String, val corpo: String) {
    ANALISE("Em análise", "⏳", "Recebemos sua reserva",
        "Recebemos sua reserva e estamos conferindo seus documentos. Você receberá a confirmação em instantes."),
    CONFIRMADA("Confirmada", "✓", "Reserva confirmada ✅",
        "Sua reserva está confirmada! Leve CNH e documento com foto no dia da retirada. O carro estará com bateria acima de 90%."),
    ANDAMENTO("Em andamento", "▶", "Boa viagem! Seu aluguel começou",
        "Seu aluguel está em andamento. Em caso de dúvidas na estrada, responda este e-mail ou ligue para a central."),
    CONCLUIDA("Concluída", "■", "Obrigado por dirigir elétrico",
        "Seu aluguel foi concluído. Obrigado por escolher a Quantum Drive — e por rodar sem emissões!"),
    CANCELADA("Cancelada", "✕", "Reserva cancelada",
        "Sua reserva foi cancelada. Se houve cobrança, o estorno será feito em até 7 dias úteis.");

    val ativa get() = this == ANALISE || this == CONFIRMADA || this == ANDAMENTO

    companion object { val LINHA_DO_TEMPO = listOf(ANALISE, CONFIRMADA, ANDAMENTO, CONCLUIDA) }
}

data class Carro(
    val id: String,
    val nome: String,
    val categoria: Categoria,
    @DrawableRes val foto: Int,
    val descricao: String,
    val autonomiaKm: Int,
    val potenciaCv: Int,
    val bateriaKwh: Double,
    val lugares: Int,
    val portaMalasL: Int,
    val recarga: String,
    val kmPorKwh: Double,
    val precoDia: Int,
    val unidades: Int,
    val manutencao: Int = 0,
    val itens: List<String>
) {
    val unidadesOperando get() = (unidades - manutencao).coerceAtLeast(0)
}

data class Usuario(
    val id: String,
    val nome: String,
    val email: String,
    val telefone: String,
    val cpf: String,
    val nascimento: String,
    val papel: Papel,
    val senhaHash: String,
    val trocarSenha: Boolean = false,
    val criadoEm: LocalDateTime = LocalDateTime.now()
)

data class Reserva(
    val id: String,
    val usuarioId: String,
    val carroId: String,
    val nome: String,
    val email: String,
    val telefone: String,
    val cpf: String,
    val cnh: String,
    val loja: String,
    val pagamento: String,
    val inicio: LocalDateTime,
    val fim: LocalDateTime,
    val dias: Int,
    val protecao: Boolean,
    val subtotal: Int,
    val valorProtecao: Int,
    val desconto: Int,
    val total: Int,
    val status: StatusReserva = StatusReserva.ANALISE,
    val criadaEm: LocalDateTime = LocalDateTime.now(),
    val documentos: List<String> = emptyList(),
    val demo: Boolean = false
)

data class EmailEnviado(
    val para: String,
    val nome: String,
    val assunto: String,
    val mensagem: String,
    val via: String,
    val em: LocalDateTime = LocalDateTime.now()
)

/** Preço — mesma regra do site: proteção R$ 39/dia e 10% de desconto a partir de 7 diárias. */
data class Orcamento(val dias: Int, val subtotal: Int, val protecao: Int, val desconto: Int) {
    val total get() = subtotal + protecao - desconto

    companion object {
        const val PROTECAO_DIA = 39
        fun calcular(precoDia: Int, inicio: LocalDateTime, fim: LocalDateTime, comProtecao: Boolean): Orcamento {
            val minutos = Duration.between(inicio, fim).toMinutes().coerceAtLeast(0)
            val dias = ((minutos + 24 * 60 - 1) / (24 * 60)).toInt().coerceAtLeast(1)
            val subtotal = dias * precoDia
            val prot = if (comProtecao) dias * PROTECAO_DIA else 0
            val desc = if (dias >= 7) (subtotal * 0.10).roundToInt() else 0
            return Orcamento(dias, subtotal, prot, desc)
        }
    }
}

/** Tipos de daltonismo do modo acessível (iguais aos do site). */
enum class Daltonismo(val nome: String, val descricao: String) {
    DESLIGADO("Desligado", "Cores originais do Quantum Drive."),
    PROTAN("Protanopia", "Dificuldade para ver o vermelho."),
    DEUTAN("Deuteranopia", "Dificuldade para ver o verde — o tipo mais comum."),
    TRITAN("Tritanopia", "Dificuldade para ver o azul e o amarelo."),
    ACRO("Acromatopsia", "Não enxerga cores: usa só claro/escuro, ícones e formas.")
}

data class Preferencias(
    val daltonismo: Daltonismo = Daltonismo.DESLIGADO,
    val altoContraste: Boolean = false,
    val escalaFonte: Float = 1f
)
