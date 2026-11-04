package com.quantumdrive.app.data

/**
 * Financeiro do painel do dono — mesma regra do site.
 * Cada reserva entra no mês da RETIRADA.
 * Recebido = concluídas + em andamento · A receber = em análise + confirmadas · canceladas ficam fora do total.
 */
object Financeiro {
    val MESES = listOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")

    data class Resumo(
        val reservas: Int = 0,
        val diarias: Int = 0,
        val aluguel: Int = 0,
        val protecao: Int = 0,
        val descontos: Int = 0,
        val total: Int = 0,
        val recebido: Int = 0,
        val aReceber: Int = 0,
        val canceladas: Int = 0,
        val cancelado: Int = 0
    ) {
        val ticketMedio get() = if (reservas > 0) total.toDouble() / reservas else 0.0
    }

    fun resumir(lista: List<Reserva>): Resumo {
        val ok = lista.filter { it.status != StatusReserva.CANCELADA }
        val canc = lista.filter { it.status == StatusReserva.CANCELADA }
        return Resumo(
            reservas = ok.size,
            diarias = ok.sumOf { it.dias },
            aluguel = ok.sumOf { it.subtotal },
            protecao = ok.sumOf { it.valorProtecao },
            descontos = ok.sumOf { it.desconto },
            total = ok.sumOf { it.total },
            recebido = ok.filter { it.status == StatusReserva.CONCLUIDA || it.status == StatusReserva.ANDAMENTO }.sumOf { it.total },
            aReceber = ok.filter { it.status == StatusReserva.ANALISE || it.status == StatusReserva.CONFIRMADA }.sumOf { it.total },
            canceladas = canc.size,
            cancelado = canc.sumOf { it.total }
        )
    }

    fun doAno(lista: List<Reserva>, ano: Int) = lista.filter { it.inicio.year == ano }

    /** mes: 1 a 12 */
    fun doMes(lista: List<Reserva>, ano: Int, mes: Int) = lista.filter { it.inicio.year == ano && it.inicio.monthValue == mes }

    /** Totais por grupo (veículo, pagamento, loja) do período, maior primeiro. */
    fun agrupar(lista: List<Reserva>, chave: (Reserva) -> String): List<Triple<String, Int, Int>> =
        lista.filter { it.status != StatusReserva.CANCELADA }
            .groupBy(chave)
            .map { (k, v) -> Triple(k, v.size, v.sumOf { it.total }) }
            .sortedByDescending { it.third }

    /** Planilha CSV (abre no Excel) com os 12 meses e o total do ano. */
    fun csv(lista: List<Reserva>, ano: Int): String {
        fun moeda(v: Int) = "%.2f".format(v.toDouble()).replace('.', ',')
        val linhas = mutableListOf("Mes;Reservas;Diarias;Aluguel;Protecao;Descontos;Total;Recebido;A receber;Cancelado")
        val anoTodo = doAno(lista, ano)
        MESES.forEachIndexed { i, nome ->
            val r = resumir(doMes(lista, ano, i + 1))
            linhas += "$nome;${r.reservas};${r.diarias};${moeda(r.aluguel)};${moeda(r.protecao)};${moeda(r.descontos)};${moeda(r.total)};${moeda(r.recebido)};${moeda(r.aReceber)};${moeda(r.cancelado)}"
        }
        val t = resumir(anoTodo)
        linhas += "TOTAL;${t.reservas};${t.diarias};${moeda(t.aluguel)};${moeda(t.protecao)};${moeda(t.descontos)};${moeda(t.total)};${moeda(t.recebido)};${moeda(t.aReceber)};${moeda(t.cancelado)}"
        return "﻿" + linhas.joinToString("\n")
    }
}
