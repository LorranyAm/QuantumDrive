package com.quantumdrive.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * E-mails automáticos para o cliente (cadastro, reserva e cada mudança de status).
 * Usa a mesma conta EmailJS do site (Gmail adminquantumdrive.com@gmail.com).
 *
 * No painel do EmailJS, deixe ligado: Account › Security ›
 * "Allow EmailJS API for non-browser applications" — sem isso o EmailJS recusa envios do app.
 */
object ServicoEmail {
    private const val PUBLIC_KEY = "ICI0WX5QKiDmoO_dn"
    private const val SERVICE_ID = "service_oa0znvf"
    private const val TEMPLATE_ID = "template_grbxig2"

    private val brl: NumberFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    private val f = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    fun mensagemReserva(r: Reserva, status: StatusReserva, nomeCarro: String, motivo: String?): Pair<String, String> {
        val assunto = "[Quantum Drive] ${status.assunto} — ${r.id}"
        val texto = buildString {
            appendLine("Olá, ${r.nome.substringBefore(' ')}!")
            appendLine()
            appendLine(status.corpo)
            if (motivo != null) appendLine("Motivo: $motivo")
            appendLine()
            appendLine("Resumo da reserva ${r.id}")
            appendLine("• Veículo: $nomeCarro")
            appendLine("• Retirada: ${r.inicio.format(f)} — ${r.loja}")
            appendLine("• Devolução: ${r.fim.format(f)}")
            appendLine("• Diárias: ${r.dias}")
            appendLine("• Pagamento: ${r.pagamento}")
            appendLine("• Total: ${brl.format(r.total)}")
            appendLine("• Status: ${status.rotulo}")
            appendLine()
            appendLine("Acompanhe em tempo real em \"Minhas reservas\".")
            append("Equipe Quantum Drive")
        }
        return assunto to texto
    }

    fun mensagemCadastro(u: Usuario): Pair<String, String> {
        val texto = buildString {
            appendLine("Olá, ${u.nome.substringBefore(' ')}!")
            appendLine()
            appendLine("Seu cadastro na Quantum Drive foi confirmado. 🎉")
            appendLine()
            appendLine("Dados da conta")
            appendLine("• Nome: ${u.nome}")
            appendLine("• E-mail de acesso: ${u.email}")
            appendLine("• Telefone: ${u.telefone}")
            appendLine("• Data do cadastro: ${LocalDateTime.now().format(f)}")
            appendLine()
            appendLine("Agora é só escolher seu carro 100% elétrico BYD e fazer a reserva online.")
            appendLine("Você receberá um e-mail a cada etapa do aluguel.")
            appendLine()
            appendLine("Se não foi você que criou esta conta, responda este e-mail.")
            append("Equipe Quantum Drive")
        }
        return "[Quantum Drive] Cadastro confirmado — bem-vindo(a)!" to texto
    }

    /** Envia pela API REST do EmailJS. Devolve "EmailJS" se saiu, ou "falhou". */
    suspend fun enviar(para: String, nome: String, assunto: String, mensagem: String, ref: String = ""): String =
        withContext(Dispatchers.IO) {
            fun j(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
            val corpo = """{"service_id":${j(SERVICE_ID)},"template_id":${j(TEMPLATE_ID)},"user_id":${j(PUBLIC_KEY)},""" +
                """"template_params":{"to_email":${j(para)},"to_name":${j(nome)},"subject":${j(assunto)},"message":${j(mensagem)},"reserva_id":${j(ref)}}}"""
            runCatching {
                val con = URL("https://api.emailjs.com/api/v1.0/email/send").openConnection() as HttpURLConnection
                try {
                    con.requestMethod = "POST"
                    con.doOutput = true
                    con.connectTimeout = 15000; con.readTimeout = 15000
                    con.setRequestProperty("Content-Type", "application/json")
                    con.outputStream.use { it.write(corpo.toByteArray()) }
                    if (con.responseCode !in 200..299) {
                        val motivo = runCatching { con.errorStream?.bufferedReader()?.readText() }.getOrNull().orEmpty()
                        error("${con.responseCode} ${motivo.take(160)}")
                    }
                } finally { con.disconnect() }
            }.fold(onSuccess = { "EmailJS" }, onFailure = { e ->
                // Guarda o motivo (aparece em "E-mails" no painel e no histórico)
                val m = e.message.orEmpty()
                when {
                    "non-browser" in m -> "falhou: o EmailJS bloqueia o app — ative \"Allow EmailJS API for non-browser applications\" (Account › Security)"
                    e is java.net.UnknownHostException || e is java.net.SocketTimeoutException -> "falhou: sem internet"
                    else -> "falhou: $m"
                }
            })
        }
}
