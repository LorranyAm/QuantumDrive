package com.quantumdrive.app

import com.quantumdrive.app.data.Calculadora
import com.quantumdrive.app.data.ErroApi
import com.quantumdrive.app.data.Financeiro
import com.quantumdrive.app.data.Orcamento
import com.quantumdrive.app.data.Papel
import com.quantumdrive.app.data.Repositorio
import com.quantumdrive.app.data.Reserva
import com.quantumdrive.app.data.StatusReserva
import com.quantumdrive.app.util.Mascaras
import com.quantumdrive.app.util.Seguranca
import com.quantumdrive.app.util.Validadores
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** Confere que o app segue exatamente as mesmas regras do site. */
class RegrasTest {

    // ---------- Validações ----------
    @Test fun cpf() {
        assertNull(Validadores.cpf("529.982.247-25"))
        assertNull(Validadores.cpf("39053344705"))
        assertNotNull(Validadores.cpf("123.456.789-00"))
        assertNotNull(Validadores.cpf("111.111.111-11"))
    }

    @Test fun emailAceitaQualquerUmComArroba() {
        assertNull(Validadores.email("maria@escola"))
        assertNull(Validadores.email("joana@hotmail.com"))
        assertNotNull(Validadores.email("joana.hotmail.com"))
    }

    @Test fun senhaIdadeTelefone() {
        assertNull(Validadores.senha("abc123")); assertNotNull(Validadores.senha("abc12"))
        val hoje = LocalDate.of(2026, 11, 4)
        assertNull(Validadores.idadeMinima(LocalDate.of(2008, 1, 1), hoje))
        assertNotNull(Validadores.idadeMinima(LocalDate.of(2010, 1, 1), hoje))
        assertNull(Validadores.telefone("(11) 98765-4321")); assertNotNull(Validadores.telefone("1234"))
    }

    @Test fun periodoDaReserva() {
        val agora = LocalDateTime.of(2026, 11, 4, 12, 0)
        assertNull(Validadores.periodo(agora.plusDays(1), agora.plusDays(4), agora))
        assertNotNull(Validadores.periodo(agora.minusHours(1), agora.plusDays(2), agora))
        assertNotNull(Validadores.periodo(agora.plusDays(2), agora.plusDays(1), agora))
        assertNotNull(Validadores.periodo(agora.plusDays(1), agora.plusDays(1).plusHours(5), agora))
        assertNotNull(Validadores.periodo(agora.plusDays(1), agora.plusDays(40), agora))
    }

    @Test fun documentos() {
        assertNull(Validadores.documento("cnh.pdf", "application/pdf", 1000))
        assertNotNull(Validadores.documento("cnh.docx", "application/msword", 1000))
        assertNotNull(Validadores.documento("cnh.pdf", "application/pdf", 6L * 1024 * 1024))
        assertNotNull(Validadores.documento(null, null, 0))
    }

    @Test fun mascarasEHashIgualAoSite() {
        assertEquals("529.982.247-25", Mascaras.cpf("52998224725"))
        assertEquals("(11) 91234-5678", Mascaras.telefone("11912345678"))
        assertEquals(Seguranca.SENHA_INICIAL_ADMIN, Seguranca.hash("Admin@123"))
    }

    // ---------- Preço ----------
    @Test fun orcamento() {
        val ini = LocalDateTime.of(2026, 12, 1, 10, 0)
        assertEquals(3 * 329 + 3 * 39, Orcamento.calcular(329, ini, ini.plusDays(3), true).total)
        val sete = Orcamento.calcular(329, ini, ini.plusDays(7), false)
        assertEquals(7 * 329 - Math.round(7 * 329 * 0.1).toInt(), sete.total)
        assertEquals(2, Orcamento.calcular(189, ini, ini.plusDays(1).plusHours(2), false).dias)
    }

    // ---------- Calculadora BYD (mesmos números do site) ----------
    @Test fun calculadoraSeal() {
        val seal = Calculadora.modelos.first { it.nome == "BYD SEAL" }
        val r = Calculadora.calcular(seal, 20000, 6.29)
        assertEquals(2975.61, r.custoByd, 0.01)
        assertEquals(11132.74, r.custoCombustao, 0.01)
        assertEquals(8157.13, r.economia, 0.01)
        assertEquals(73, Math.round(r.percentual * 100).toInt())
    }

    @Test fun calculadoraHibridoShark() {
        val shark = Calculadora.modelos.first { it.nome == "BYD SHARK" }
        val r = Calculadora.calcular(shark, 20000, 6.29, 0.5)
        assertEquals(8606.26, r.custoByd, 0.01)
        assertEquals(12.0, r.refKmL, 0.0001)
        assertEquals(17, Calculadora.modelos.size)
    }

    // ---------- Financeiro ----------
    private fun reserva(id: String, mes: Int, total: Int, st: StatusReserva) = Reserva(id, "u", "seal", "N", "n@x", "", "", "", "Loja",
        "Pix", LocalDateTime.of(2026, mes, 10, 10, 0), LocalDateTime.of(2026, mes, 12, 10, 0), 2, false, total, 0, 0, total, st)

    @Test fun financeiroPorMesEAno() {
        val l = listOf(reserva("a", 1, 100, StatusReserva.CONCLUIDA), reserva("b", 1, 200, StatusReserva.CONFIRMADA),
            reserva("c", 2, 300, StatusReserva.CANCELADA), reserva("d", 2, 400, StatusReserva.ANDAMENTO))
        val ano = Financeiro.resumir(Financeiro.doAno(l, 2026))
        assertEquals(700, ano.total); assertEquals(500, ano.recebido); assertEquals(200, ano.aReceber)
        assertEquals(300, ano.cancelado); assertEquals(1, ano.canceladas)
        assertEquals(300, Financeiro.resumir(Financeiro.doMes(l, 2026, 1)).total)
        assertTrue(Financeiro.csv(l, 2026).contains("TOTAL;3;6;700,00;0,00;0,00;700,00;500,00;200,00;300,00"))
    }

    // ---------- Fluxo completo (cadastro → login → reserva → status), com e-mail falso ----------
    @Test fun fluxoCompleto() = runBlocking {
        val enviados = mutableListOf<String>()
        val repo = Repositorio(null, enviarEmail = { para, _, assunto, _, _ -> enviados += "$para|$assunto"; "EmailJS" }, atraso = 0)

        // cadastro manda o e-mail de confirmação
        assertEquals("EmailJS", repo.cadastrar("Joana Silva", "Joana@Hotmail.com", "(11) 98765-4321", "39053344705", "1999-03-10", "abc123"))
        assertEquals("joana@hotmail.com", repo.sessao.value?.email)
        assertTrue(enviados.last().startsWith("joana@hotmail.com|[Quantum Drive] Cadastro confirmado"))
        try { repo.cadastrar("Outra Pessoa", "joana@hotmail.com", "(11) 98765-4321", "52998224725", "1990-01-01", "abc123"); fail() }
        catch (e: ErroApi) { assertEquals("email", e.campo) }

        // dono não entra pela tela de cliente e precisa trocar a senha inicial
        try { repo.login("admin@quantumdrive.com", "Admin@123"); fail() } catch (_: ErroApi) {}
        val (dono, trocar) = repo.loginAdmin("admin@quantumdrive.com", "Admin@123")
        assertTrue(trocar); assertEquals(Papel.DONO, dono.papel)
        repo.concluirPrimeiroAcesso(dono.id, "Admin@123", "MinhaLoja2026")
        try { repo.loginAdmin("admin@quantumdrive.com", "Admin@123"); fail() } catch (_: ErroApi) {}
        assertFalse(repo.loginAdmin("admin@quantumdrive.com", "MinhaLoja2026").second)

        // reserva + e-mail + conflito quando acabam as unidades
        val cliente = repo.login("joana@hotmail.com", "abc123")
        val ini = LocalDateTime.now().plusDays(2).withHour(10).withMinute(0)
        val o = Orcamento.calcular(459, ini, ini.plusDays(3), true)
        fun nova() = Reserva("", cliente.id, "tan", cliente.nome, cliente.email, cliente.telefone, cliente.cpf, "12345678901",
            "São Paulo — Paulista", "Pix", ini, ini.plusDays(3), o.dias, true, o.subtotal, o.protecao, o.desconto, o.total)
        val (r1, via) = repo.criarReserva(nova()); assertEquals("EmailJS", via)
        assertTrue(enviados.last().contains("Recebemos sua reserva — ${r1.id}"))
        repo.criarReserva(nova()) // o Tan EV tem 2 unidades
        try { repo.criarReserva(nova()); fail() } catch (e: ErroApi) { assertEquals("periodo", e.campo) }

        // mudança de status avisa o cliente
        repo.mudarStatus(r1.id, StatusReserva.CONFIRMADA)
        assertTrue(enviados.last().contains("Reserva confirmada"))
        assertEquals(StatusReserva.CONFIRMADA, repo.reservas.value.first { it.id == r1.id }.status)
        repo.atualizarPeloRelogio(ini.plusHours(1))
        assertEquals(StatusReserva.ANDAMENTO, repo.reservas.value.first { it.id == r1.id }.status)
    }
}
