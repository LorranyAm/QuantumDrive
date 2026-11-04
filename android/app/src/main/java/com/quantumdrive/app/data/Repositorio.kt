package com.quantumdrive.app.data

import com.quantumdrive.app.util.Seguranca
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDateTime
import kotlin.random.Random

class ErroApi(mensagem: String, val campo: String? = null) : Exception(mensagem)

/**
 * "API" do app — mesmas regras do site. Tudo fica em StateFlow: as telas se atualizam
 * sozinhas quando algo muda (é o "tempo real" do histórico e do painel do dono).
 *
 * @param armazenamento grava no celular (null nos testes)
 * @param enviarEmail função de envio (nos testes é trocada por uma falsa)
 */
class Repositorio(
    private val armazenamento: Armazenamento? = null,
    private val enviarEmail: suspend (para: String, nome: String, assunto: String, mensagem: String, ref: String) -> String =
        { p, n, a, m, r -> ServicoEmail.enviar(p, n, a, m, r) },
    private val atraso: Long = 400
) {
    private val _usuarios = MutableStateFlow(armazenamento?.lerUsuarios() ?: emptyList())
    val usuarios: StateFlow<List<Usuario>> = _usuarios.asStateFlow()

    private val _sessao = MutableStateFlow<Usuario?>(null)
    val sessao: StateFlow<Usuario?> = _sessao.asStateFlow()

    private val _frota = MutableStateFlow(Frota.carros.map { base ->
        armazenamento?.lerFrota()?.get(base.id)?.let { (p, u, m) -> base.copy(precoDia = p, unidades = u, manutencao = m) } ?: base
    })
    val frota: StateFlow<List<Carro>> = _frota.asStateFlow()

    private val _reservas = MutableStateFlow(armazenamento?.lerReservas() ?: emptyList())
    val reservas: StateFlow<List<Reserva>> = _reservas.asStateFlow()

    private val _emails = MutableStateFlow(armazenamento?.lerEmails() ?: emptyList())
    val emails: StateFlow<List<EmailEnviado>> = _emails.asStateFlow()

    init {
        // Contas iniciais: dono (senha inicial obrigatória de trocar) e um cliente de demonstração
        val lista = _usuarios.value.toMutableList()
        if (lista.none { it.papel == Papel.DONO }) lista += Usuario("admin", "Dono Quantum Drive", "admin@quantumdrive.com",
            "(11) 99999-0000", "52998224725", "1985-01-01", Papel.DONO, Seguranca.SENHA_INICIAL_ADMIN, trocarSenha = true)
        if (lista.none { it.email == "cliente@quantumdrive.com" }) lista += Usuario("cli-demo", "Cliente Demonstração",
            "cliente@quantumdrive.com", "(11) 91234-5678", "11144477735", "1995-05-10", Papel.CLIENTE, Seguranca.hash("Cliente@123"))
        salvarUsuarios(lista)
        armazenamento?.lerSessao()?.let { id -> _sessao.value = lista.find { it.id == id } }
    }

    // ===================== Contas =====================

    /** POST /auth/cadastro — cria a conta, entra e manda o e-mail de confirmação. Devolve o resultado do envio. */
    suspend fun cadastrar(nome: String, email: String, telefone: String, cpf: String, nascimento: String, senha: String): String {
        delay(atraso)
        val e = email.trim().lowercase(); val c = cpf.filter(Char::isDigit)
        if (_usuarios.value.any { it.email == e }) throw ErroApi("Este e-mail já está cadastrado.", "email")
        if (_usuarios.value.any { it.cpf == c }) throw ErroApi("Este CPF já está cadastrado.", "cpf")
        val u = Usuario("u${System.currentTimeMillis()}", nome.trim(), e, telefone, c, nascimento, Papel.CLIENTE, Seguranca.hash(senha))
        salvarUsuarios(_usuarios.value + u)
        entrar(u)
        val (assunto, msg) = ServicoEmail.mensagemCadastro(u)
        return registrarEmail(u.email, u.nome, assunto, msg, "")
    }

    /** POST /auth/login — clientes. O dono entra pela Área do administrador. */
    suspend fun login(email: String, senha: String): Usuario {
        delay(atraso)
        val u = conferir(email, senha)
        if (u.papel == Papel.DONO) throw ErroApi("Administradores entram pela Área do administrador.")
        entrar(u); return u
    }

    /** Login do dono. Se for o primeiro acesso, devolve precisaTrocar = true (sem entrar ainda). */
    suspend fun loginAdmin(email: String, senha: String): Pair<Usuario, Boolean> {
        delay(atraso)
        val u = conferir(email, senha)
        if (u.papel != Papel.DONO) throw ErroApi("Esta conta é de cliente. Use a entrada de cliente.")
        val precisaTrocar = u.trocarSenha || u.senhaHash == Seguranca.SENHA_INICIAL_ADMIN
        if (!precisaTrocar) entrar(u)
        return u to precisaTrocar
    }

    /** Primeiro acesso do dono: troca a senha inicial e entra. */
    suspend fun concluirPrimeiroAcesso(id: String, senhaAtual: String, nova: String) {
        if (Seguranca.hash(nova) == Seguranca.SENHA_INICIAL_ADMIN) throw ErroApi("Escolha uma senha diferente da inicial.", "nova")
        trocarSenha(id, senhaAtual, nova)
        _usuarios.value.find { it.id == id }?.let { entrar(it) }
    }

    suspend fun trocarSenha(id: String, atual: String, nova: String) {
        delay(atraso / 2)
        val u = _usuarios.value.find { it.id == id }
        if (u == null || u.senhaHash != Seguranca.hash(atual)) throw ErroApi("A senha atual está incorreta.", "atual")
        salvarUsuarios(_usuarios.value.map { if (it.id == id) it.copy(senhaHash = Seguranca.hash(nova), trocarSenha = false) else it })
    }

    suspend fun criarAdmin(nome: String, email: String, senha: String) {
        delay(atraso / 2)
        val e = email.trim().lowercase()
        if (_usuarios.value.any { it.email == e }) throw ErroApi("Já existe uma conta com este e-mail.", "email")
        salvarUsuarios(_usuarios.value + Usuario("adm${System.currentTimeMillis()}", nome.trim(), e, "", "", "",
            Papel.DONO, Seguranca.hash(senha)))
    }

    fun removerAdmin(id: String) {
        if (_usuarios.value.count { it.papel == Papel.DONO } <= 1) throw ErroApi("É preciso manter pelo menos um administrador.")
        salvarUsuarios(_usuarios.value.filterNot { it.id == id })
    }

    fun sair() { _sessao.value = null; armazenamento?.salvarSessao(null) }

    private fun conferir(email: String, senha: String): Usuario {
        val u = _usuarios.value.find { it.email == email.trim().lowercase() }
        if (u == null || u.senhaHash != Seguranca.hash(senha)) throw ErroApi("E-mail ou senha incorretos.")
        return u
    }

    private fun entrar(u: Usuario) { _sessao.value = u; armazenamento?.salvarSessao(u.id) }

    private fun salvarUsuarios(lista: List<Usuario>) {
        _usuarios.value = lista; armazenamento?.salvarUsuarios(lista)
        _sessao.value?.let { s -> _sessao.value = lista.find { it.id == s.id } }
    }

    // ===================== Frota e disponibilidade =====================

    fun carro(id: String) = _frota.value.find { it.id == id }

    /** Unidades livres nos próximos 7 dias (mesma regra do selo do site). */
    fun livres(c: Carro, agora: LocalDateTime = LocalDateTime.now()): Int {
        val semana = agora.plusDays(7)
        val emUso = _reservas.value.count { it.carroId == c.id && it.status.ativa && it.inicio.isBefore(semana) && it.fim.isAfter(agora) }
        return (c.unidadesOperando - emUso).coerceAtLeast(0)
    }

    fun temConflito(carroId: String, inicio: LocalDateTime, fim: LocalDateTime, ignorar: String? = null): Boolean {
        val c = carro(carroId) ?: return true
        val sobrepostas = _reservas.value.count {
            it.carroId == carroId && it.id != ignorar && it.status.ativa && it.inicio.isBefore(fim) && it.fim.isAfter(inicio)
        }
        return sobrepostas >= c.unidadesOperando
    }

    /** PATCH /veiculos/{id} — painel do dono. */
    fun atualizarCarro(id: String, preco: Int, unidades: Int, manutencao: Int) {
        _frota.update { l -> l.map { if (it.id == id) it.copy(precoDia = preco, unidades = unidades, manutencao = manutencao.coerceIn(0, unidades)) else it } }
        armazenamento?.salvarFrota(_frota.value)
    }

    // ===================== Reservas =====================

    /** POST /reservas — grava e manda o e-mail "Recebemos sua reserva". Devolve a reserva e o resultado do envio. */
    suspend fun criarReserva(r: Reserva): Pair<Reserva, String> {
        delay(atraso * 2)
        if (temConflito(r.carroId, r.inicio, r.fim)) throw ErroApi("Sem unidades livres neste período. Tente outras datas ou outro modelo.", "periodo")
        val nova = r.copy(id = "QD-" + (1..5).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"[Random.nextInt(32)] }.joinToString(""),
            status = StatusReserva.ANALISE, criadaEm = LocalDateTime.now())
        salvarReservas(listOf(nova) + _reservas.value)
        return nova to emailReserva(nova, StatusReserva.ANALISE, null)
    }

    /** PATCH /reservas/{id}/status — cada mudança manda e-mail ao cliente. */
    suspend fun mudarStatus(id: String, novo: StatusReserva, motivo: String? = null): String {
        var alterada: Reserva? = null
        salvarReservas(_reservas.value.map { if (it.id == id) it.copy(status = novo).also { n -> alterada = n } else it })
        val r = alterada ?: throw ErroApi("Reserva não encontrada.")
        return if (r.demo) "exemplo" else emailReserva(r, novo, motivo)
    }

    /** Avança pelo relógio: confirmada → em andamento → concluída. */
    suspend fun atualizarPeloRelogio(agora: LocalDateTime = LocalDateTime.now()): Boolean {
        var mudou = false
        _reservas.value.forEach {
            if (it.status == StatusReserva.CONFIRMADA && !it.inicio.isAfter(agora)) { mudarStatus(it.id, StatusReserva.ANDAMENTO); mudou = true }
            else if (it.status == StatusReserva.ANDAMENTO && !it.fim.isAfter(agora)) { mudarStatus(it.id, StatusReserva.CONCLUIDA); mudou = true }
        }
        return mudou
    }

    /** Cria ~14 meses de reservas fictícias para apresentar o painel financeiro. */
    fun gerarExemplos(agora: LocalDateTime = LocalDateTime.now()) {
        val nomes = listOf("Ana Souza", "Bruno Lima", "Carla Mendes", "Diego Rocha", "Elisa Prado",
            "Fábio Nunes", "Gabriela Reis", "Hugo Martins", "Isabela Costa", "João Pedro Alves")
        val inicioJanela = LocalDateTime.of(agora.year - 1, 1, 1, 10, 0)
        val fimJanela = agora.plusDays(45)
        val minutosJanela = java.time.Duration.between(inicioJanela, fimJanela).toMinutes()
        val novas = List(140) {
            val ini = inicioJanela.plusMinutes(Random.nextLong(minutosJanela)).withHour(10).withMinute(0)
            val dias = listOf(1, 2, 2, 3, 3, 3, 4, 5, 7, 10).random()
            val fim = ini.plusDays(dias.toLong())
            val c = _frota.value.random()
            val prot = if (Random.nextDouble() < .7) dias * Orcamento.PROTECAO_DIA else 0
            val sub = dias * c.precoDia
            val desc = if (dias >= 7) Math.round(sub * .1).toInt() else 0
            var st = when { fim.isBefore(agora) -> StatusReserva.CONCLUIDA; !ini.isAfter(agora) -> StatusReserva.ANDAMENTO; else -> StatusReserva.CONFIRMADA }
            if (Random.nextDouble() < .08) st = StatusReserva.CANCELADA
            val nome = nomes.random()
            Reserva("EX-" + (1..5).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"[Random.nextInt(32)] }.joinToString(""),
                "demo", c.id, nome, nome.substringBefore(' ').lowercase() + "@exemplo.com", "(11) 90000-0000", "", "",
                Frota.lojas.random(), Frota.pagamentos.random(), ini, fim, dias, prot > 0, sub, prot, desc, sub + prot - desc,
                st, ini, listOf("cnh.pdf", "rg.pdf", "comprovante.pdf"), demo = true)
        }
        salvarReservas((_reservas.value + novas).sortedByDescending { it.inicio })
    }

    fun removerExemplos() = salvarReservas(_reservas.value.filterNot { it.demo })

    /** Duas reservas de exemplo para o histórico de um cliente (sem e-mail). */
    fun exemplosDoCliente(u: Usuario, agora: LocalDateTime = LocalDateTime.now()) {
        if (_reservas.value.any { it.usuarioId == u.id }) return
        fun nova(carroId: String, ini: LocalDateTime, dias: Int, st: StatusReserva): Reserva {
            val c = carro(carroId)!!
            val o = Orcamento.calcular(c.precoDia, ini, ini.plusDays(dias.toLong()), true)
            return Reserva("EX-" + carroId.take(3).uppercase() + dias, u.id, carroId, u.nome, u.email, u.telefone, u.cpf,
                "12345678901", Frota.lojas[0], "Pix", ini, ini.plusDays(dias.toLong()), o.dias, true, o.subtotal,
                o.protecao, o.desconto, o.total, st, ini.minusDays(2), listOf("cnh.pdf", "rg.pdf", "comprovante.pdf"), demo = true)
        }
        val base = agora.withHour(10).withMinute(0).withSecond(0).withNano(0)
        salvarReservas(listOf(nova("seal", base.plusDays(2), 3, StatusReserva.CONFIRMADA),
            nova("dolphin", base.minusDays(20), 4, StatusReserva.CONCLUIDA)) + _reservas.value)
    }

    private fun salvarReservas(lista: List<Reserva>) { _reservas.value = lista; armazenamento?.salvarReservas(lista) }

    // ===================== E-mails =====================

    private suspend fun emailReserva(r: Reserva, status: StatusReserva, motivo: String?): String {
        val (assunto, msg) = ServicoEmail.mensagemReserva(r, status, carro(r.carroId)?.nome ?: r.carroId, motivo)
        return registrarEmail(r.email, r.nome, assunto, msg, r.id)
    }

    private suspend fun registrarEmail(para: String, nome: String, assunto: String, msg: String, ref: String): String {
        val via = enviarEmail(para, nome, assunto, msg, ref)
        _emails.value = (listOf(EmailEnviado(para, nome, assunto, msg, via)) + _emails.value).take(200)
        armazenamento?.salvarEmails(_emails.value)
        return via
    }
}
