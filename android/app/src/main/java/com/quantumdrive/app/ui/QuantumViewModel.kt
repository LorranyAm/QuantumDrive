package com.quantumdrive.app.ui

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quantumdrive.app.data.Armazenamento
import com.quantumdrive.app.data.Daltonismo
import com.quantumdrive.app.data.ErroApi
import com.quantumdrive.app.data.Financeiro
import com.quantumdrive.app.data.Preferencias
import com.quantumdrive.app.data.Repositorio
import com.quantumdrive.app.data.Reserva
import com.quantumdrive.app.data.StatusReserva
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime

/** Mensagem curta mostrada embaixo da tela (sucesso, erro, aviso). */
data class Aviso(val tipo: Tipo, val titulo: String, val texto: String = "") {
    enum class Tipo { SUCESSO, ERRO, INFO }
}

class QuantumViewModel(app: Application) : AndroidViewModel(app) {

    private val armazenamento = Armazenamento(app)
    val repo = Repositorio(armazenamento)

    val sessao = repo.sessao
    val frota = repo.frota
    val reservas = repo.reservas
    val emails = repo.emails
    val usuarios = repo.usuarios

    private val _preferencias = MutableStateFlow(armazenamento.lerPreferencias())
    val preferencias = _preferencias.asStateFlow()

    private val _avisos = Channel<Aviso>(Channel.BUFFERED)
    val avisos = _avisos.receiveAsFlow()

    /** Datas escolhidas nos detalhes, levadas para a reserva (sobrevivem ao login). */
    var rascunhoInicio: LocalDateTime? = null
    var rascunhoFim: LocalDateTime? = null

    /** Rota para voltar depois do login. */
    var voltarPara: String? = null

    /** Aba inicial do painel (usado nas capturas automáticas). */
    var abaInicialAdmin = 0

    init {
        // Reservas ainda em análise são aprovadas (simula o back-end conferindo os documentos)
        repo.reservas.value.filter { it.status == StatusReserva.ANALISE && !it.demo }.forEach { agendarConfirmacao(it.id) }
        // "Tempo real": a cada 30 s avança os status pelo relógio
        viewModelScope.launch {
            while (isActive) { repo.atualizarPeloRelogio(); delay(30_000) }
        }
    }

    fun avisar(a: Aviso) { viewModelScope.launch { _avisos.send(a) } }
    private fun erro(titulo: String, e: Throwable) = avisar(Aviso(Aviso.Tipo.ERRO, titulo, e.message ?: "Tente novamente."))
    private fun primeiroNome(n: String) = n.trim().substringBefore(' ')

    // ---------- Preferências de acessibilidade ----------
    fun mudarPreferencias(p: Preferencias) { _preferencias.value = p; armazenamento.salvarPreferencias(p) }
    fun daltonismo(d: Daltonismo) {
        mudarPreferencias(_preferencias.value.copy(daltonismo = d))
        avisar(Aviso(Aviso.Tipo.SUCESSO, "Modo daltônico", if (d == Daltonismo.DESLIGADO) "Desligado: cores originais." else "${d.nome} ativado."))
    }

    // ---------- Contas ----------
    fun cadastrar(nome: String, email: String, tel: String, cpf: String, nasc: String, senha: String,
                  aoCriar: () -> Unit, aoErrar: (String?, String) -> Unit) = viewModelScope.launch {
        try {
            val via = repo.cadastrar(nome, email, tel, cpf, nasc, senha)
            avisar(Aviso(Aviso.Tipo.SUCESSO, "Conta criada com sucesso!",
                if (via == "EmailJS") "Enviamos a confirmação para ${email.trim()}. Confira também o spam."
                else "Bem-vindo(a), ${primeiroNome(nome)}! (O e-mail de confirmação não saiu: confira a internet.)"))
            aoCriar()
        } catch (e: ErroApi) { aoErrar(e.campo, e.message ?: ""); erro("Não foi possível cadastrar", e) }
    }

    fun login(email: String, senha: String, aoEntrar: () -> Unit, aoErrar: (String) -> Unit) = viewModelScope.launch {
        try {
            val u = repo.login(email, senha)
            avisar(Aviso(Aviso.Tipo.SUCESSO, "Olá, ${primeiroNome(u.nome)}!", "Login realizado com sucesso."))
            aoEntrar()
        } catch (e: ErroApi) { aoErrar(e.message ?: ""); erro("Não foi possível entrar", e) }
    }

    /** aoPrimeiroAcesso recebe o id do dono quando a senha inicial precisa ser trocada. */
    fun loginAdmin(email: String, senha: String, aoEntrar: () -> Unit, aoPrimeiroAcesso: (String) -> Unit,
                   aoErrar: (String) -> Unit) = viewModelScope.launch {
        try {
            val (u, trocar) = repo.loginAdmin(email, senha)
            if (trocar) aoPrimeiroAcesso(u.id)
            else { avisar(Aviso(Aviso.Tipo.SUCESSO, "Bem-vindo(a), ${primeiroNome(u.nome)}!", "Você entrou no painel administrativo.")); aoEntrar() }
        } catch (e: ErroApi) { aoErrar(e.message ?: ""); erro("Acesso negado", e) }
    }

    fun concluirPrimeiroAcesso(id: String, senhaAtual: String, nova: String, aoEntrar: () -> Unit, aoErrar: (String) -> Unit) =
        viewModelScope.launch {
            try {
                repo.concluirPrimeiroAcesso(id, senhaAtual, nova)
                avisar(Aviso(Aviso.Tipo.SUCESSO, "Senha nova salva", "Use essa senha nos próximos acessos."))
                aoEntrar()
            } catch (e: ErroApi) { aoErrar(e.message ?: ""); erro("Senha não alterada", e) }
        }

    fun trocarSenha(atual: String, nova: String, aoConcluir: () -> Unit) = viewModelScope.launch {
        val id = sessao.value?.id ?: return@launch
        try { repo.trocarSenha(id, atual, nova); avisar(Aviso(Aviso.Tipo.SUCESSO, "Senha alterada", "Use a nova senha no próximo acesso.")); aoConcluir() }
        catch (e: ErroApi) { erro("Senha não alterada", e) }
    }

    fun criarAdmin(nome: String, email: String, senha: String, aoConcluir: () -> Unit) = viewModelScope.launch {
        try { repo.criarAdmin(nome, email, senha); avisar(Aviso(Aviso.Tipo.SUCESSO, "Administrador adicionado", "${email.trim()} já pode entrar na Área do administrador.")); aoConcluir() }
        catch (e: ErroApi) { erro("Não foi possível adicionar", e) }
    }

    fun removerAdmin(id: String) = try { repo.removerAdmin(id); avisar(Aviso(Aviso.Tipo.SUCESSO, "Administrador removido")) }
    catch (e: ErroApi) { erro("Não foi possível remover", e) }

    fun sair() { repo.sair(); avisar(Aviso(Aviso.Tipo.SUCESSO, "Você saiu da conta", "Até a próxima!")) }

    // ---------- Reservas ----------
    fun reservar(r: Reserva, aoConcluir: () -> Unit, aoErrar: (String) -> Unit) = viewModelScope.launch {
        try {
            val (nova, via) = repo.criarReserva(r)
            rascunhoInicio = null; rascunhoFim = null
            avisar(Aviso(Aviso.Tipo.SUCESSO, "Reserva ${nova.id} enviada!",
                if (via == "EmailJS") "Enviamos os detalhes para ${nova.email}. Confira também o spam." else "Acompanhe o status em Minhas reservas."))
            aoConcluir()
            agendarConfirmacao(nova.id)
        } catch (e: ErroApi) { aoErrar(e.message ?: ""); erro("Reserva não concluída", e) }
    }

    /** Simula o back-end aprovando os documentos em ~8 s (o dono também pode aprovar no painel). */
    private fun agendarConfirmacao(id: String) = viewModelScope.launch {
        delay(8_000)
        if (repo.reservas.value.find { it.id == id }?.status == StatusReserva.ANALISE) {
            repo.mudarStatus(id, StatusReserva.CONFIRMADA, "Documentos aprovados automaticamente")
            avisar(Aviso(Aviso.Tipo.INFO, "Reserva $id atualizada", "Novo status: Confirmada"))
        }
    }

    fun mudarStatus(id: String, s: StatusReserva, motivo: String? = null) = viewModelScope.launch {
        try {
            val via = repo.mudarStatus(id, s, motivo)
            avisar(Aviso(Aviso.Tipo.SUCESSO, "Status de $id: ${s.rotulo}",
                when (via) { "EmailJS" -> "Cliente avisado por e-mail."; "exemplo" -> "Reserva de exemplo (sem e-mail)."; else -> "O e-mail não saiu: confira a internet." }))
        } catch (e: ErroApi) { erro("Falha ao atualizar", e) }
    }

    fun atualizarCarro(id: String, preco: Int, unidades: Int, manutencao: Int) {
        repo.atualizarCarro(id, preco, unidades, manutencao)
        avisar(Aviso(Aviso.Tipo.SUCESSO, "Frota atualizada", repo.carro(id)?.nome ?: id))
    }

    fun gerarExemplos() { repo.gerarExemplos(); avisar(Aviso(Aviso.Tipo.SUCESSO, "Dados de exemplo criados", "140 reservas fictícias de janeiro do ano passado até o próximo mês.")) }
    fun removerExemplos() { repo.removerExemplos(); avisar(Aviso(Aviso.Tipo.SUCESSO, "Dados de exemplo removidos")) }

    /** Salva a planilha do financeiro na pasta Downloads do celular. */
    fun exportarCsv(ano: Int) = viewModelScope.launch {
        val nome = "financeiro_quantumdrive_$ano.csv"
        val conteudo = Financeiro.csv(reservas.value, ano).toByteArray(Charsets.UTF_8)
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                val ctx = getApplication<Application>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val v = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, nome)
                        put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)!!
                    ctx.contentResolver.openOutputStream(uri)!!.use { it.write(conteudo) }
                } else {
                    File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), nome).writeBytes(conteudo)
                }
            }.isSuccess
        }
        avisar(if (ok) Aviso(Aviso.Tipo.SUCESSO, "Planilha salva", "$nome está na pasta Downloads (abre no Excel).")
               else Aviso(Aviso.Tipo.ERRO, "Não foi possível salvar", "Tente de novo."))
    }

    /** Usado só para gerar as capturas de tela automáticas (versão de teste). */
    fun prepararDemonstracao(perfil: String) = viewModelScope.launch {
        if (repo.reservas.value.none { it.demo }) repo.gerarExemplos()
        when (perfil) {
            "cliente" -> runCatching {
                val u = repo.login("cliente@quantumdrive.com", "Cliente@123")
                repo.exemplosDoCliente(u)
            }
            "dono" -> runCatching {
                val (u, trocar) = repo.loginAdmin("admin@quantumdrive.com", "Admin@123")
                if (trocar) repo.concluirPrimeiroAcesso(u.id, "Admin@123", "Demonstracao2026")
                Unit
            }.recoverCatching { repo.loginAdmin("admin@quantumdrive.com", "Demonstracao2026"); Unit }
        }
    }
}
