package com.quantumdrive.app.util

import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.format.DateTimeFormatter

/**
 * Validações feitas no app ANTES de gravar ou enviar — as mesmas regras do site.
 * Cada função devolve null quando está tudo certo, ou a mensagem de erro.
 */
object Validadores {

    fun cpf(valor: String): String? {
        val d = valor.filter { it.isDigit() }
        if (d.length != 11 || d.all { it == d[0] }) return "CPF inválido. Confira os dígitos."
        for (t in 9..10) {
            var soma = 0
            for (i in 0 until t) soma += (d[i] - '0') * ((t + 1) - i)
            val dig = ((10 * soma) % 11) % 10
            if (dig != d[t] - '0') return "CPF inválido. Confira os dígitos."
        }
        return null
    }

    /** Aceita qualquer e-mail com @ (Gmail, Hotmail, institucional, sem .com…). */
    fun email(valor: String): String? =
        if (Regex("^[^\\s@]+@[^\\s@]+$").matches(valor.trim())) null else "Digite um e-mail com @. Ex.: nome@gmail.com"

    fun telefone(valor: String): String? {
        val d = valor.filter { it.isDigit() }
        return if (Regex("^[1-9]{2}9?\\d{8}$").matches(d)) null else "Telefone inválido. Use DDD + número."
    }

    fun nome(valor: String): String? =
        if (valor.trim().split(Regex("\\s+")).count { it.length > 1 } >= 2) null else "Informe nome e sobrenome."

    fun senha(valor: String): String? =
        if (valor.length >= 6) null else "A senha precisa ter pelo menos 6 caracteres."

    fun cnh(valor: String): String? =
        if (valor.filter { it.isDigit() }.length == 11) null else "A CNH tem 11 dígitos."

    fun idadeMinima(nascimento: LocalDate?, hoje: LocalDate = LocalDate.now()): String? {
        if (nascimento == null) return "Informe a data de nascimento."
        val idade = Period.between(nascimento, hoje).years
        return when {
            idade < 18 -> "É preciso ter 18 anos ou mais (idade mínima para CNH)."
            idade > 110 -> "Data de nascimento inválida."
            else -> null
        }
    }

    /** Período: retirada ≥ 1 h no futuro, devolução depois, entre 24 h e 30 dias. */
    fun periodo(inicio: LocalDateTime?, fim: LocalDateTime?, agora: LocalDateTime = LocalDateTime.now()): String? {
        if (inicio == null) return "Informe a data e hora de retirada."
        if (fim == null) return "Informe a data e hora de devolução."
        if (inicio.isBefore(agora.plusHours(1))) return "A retirada precisa ser pelo menos 1 hora a partir de agora."
        if (!fim.isAfter(inicio)) return "A devolução precisa ser depois da retirada."
        val horas = Duration.between(inicio, fim).toHours()
        if (horas < 24) return "O período mínimo de aluguel é de 1 diária (24 h)."
        if (horas > 30 * 24) return "O período máximo é de 30 dias. Para mais, fale com a loja."
        return null
    }

    /** Documento enviado: PDF/JPG/PNG de até 5 MB. */
    fun documento(nome: String?, tipo: String?, tamanho: Long): String? = when {
        nome == null -> "Envie o arquivo."
        !(tipo == "application/pdf" || tipo == "image/jpeg" || tipo == "image/png" ||
            Regex("\\.(pdf|jpe?g|png)$", RegexOption.IGNORE_CASE).containsMatchIn(nome)) -> "Formato inválido. Use PDF, JPG ou PNG."
        tamanho > 5L * 1024 * 1024 -> "Arquivo maior que 5 MB."
        else -> null
    }

    private val formatoData = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    fun lerData(texto: String): LocalDate? = runCatching { LocalDate.parse(texto, formatoData) }.getOrNull()
}

/** Máscaras de digitação. */
object Mascaras {
    fun cpf(v: String): String {
        val d = v.filter { it.isDigit() }.take(11)
        return buildString {
            d.forEachIndexed { i, c ->
                if (i == 3 || i == 6) append('.')
                if (i == 9) append('-')
                append(c)
            }
        }
    }

    fun telefone(v: String): String {
        val d = v.filter { it.isDigit() }.take(11)
        if (d.length <= 2) return d
        val ddd = "(${d.take(2)}) "
        val resto = d.drop(2)
        val corte = if (d.length == 11) 5 else 4
        return if (resto.length > corte) ddd + resto.take(corte) + "-" + resto.drop(corte) else ddd + resto
    }

    fun data(v: String): String {
        val d = v.filter { it.isDigit() }.take(8)
        return buildString { d.forEachIndexed { i, c -> if (i == 2 || i == 4) append('/'); append(c) } }
    }

    fun cpfMascarado(cpf: String): String =
        if (cpf.length == 11) "***.${cpf.substring(3, 6)}.***-${cpf.takeLast(2)}" else "—"
}

object Seguranca {
    /** Mesmo hash do site (SHA-256 de "qd::" + senha). A senha nunca é guardada em texto. */
    fun hash(senha: String): String =
        MessageDigest.getInstance("SHA-256").digest("qd::$senha".toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** Hash da senha inicial do dono (a senha não fica escrita no código). */
    const val SENHA_INICIAL_ADMIN = "ce1e5d8a30309df417c7a2ebcb4653690c9109f2ae8b7206fa5b62648fa31b55"
}
