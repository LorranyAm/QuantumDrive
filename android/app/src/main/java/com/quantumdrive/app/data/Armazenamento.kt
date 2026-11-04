package com.quantumdrive.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/**
 * Guarda contas, reservas, e-mails, frota e preferências no próprio celular
 * (SharedPreferences em JSON) — o equivalente ao localStorage do site.
 */
class Armazenamento(context: Context) {
    private val prefs = context.getSharedPreferences("quantumdrive", Context.MODE_PRIVATE)

    // ---------- Usuários ----------
    fun lerUsuarios(): List<Usuario>? = lerLista("usuarios") { o ->
        Usuario(
            id = o.getString("id"), nome = o.getString("nome"), email = o.getString("email"),
            telefone = o.optString("telefone"), cpf = o.optString("cpf"), nascimento = o.optString("nascimento"),
            papel = Papel.valueOf(o.getString("papel")), senhaHash = o.getString("senha"),
            trocarSenha = o.optBoolean("trocarSenha"), criadoEm = data(o.optString("criadoEm"))
        )
    }

    fun salvarUsuarios(lista: List<Usuario>) = salvarLista("usuarios", lista) { u ->
        JSONObject().put("id", u.id).put("nome", u.nome).put("email", u.email).put("telefone", u.telefone)
            .put("cpf", u.cpf).put("nascimento", u.nascimento).put("papel", u.papel.name).put("senha", u.senhaHash)
            .put("trocarSenha", u.trocarSenha).put("criadoEm", u.criadoEm.toString())
    }

    // ---------- Reservas ----------
    fun lerReservas(): List<Reserva> = lerLista("reservas") { o ->
        Reserva(
            id = o.getString("id"), usuarioId = o.getString("usuarioId"), carroId = o.getString("carroId"),
            nome = o.getString("nome"), email = o.getString("email"), telefone = o.optString("telefone"),
            cpf = o.optString("cpf"), cnh = o.optString("cnh"), loja = o.getString("loja"), pagamento = o.getString("pagamento"),
            inicio = data(o.getString("inicio")), fim = data(o.getString("fim")), dias = o.getInt("dias"),
            protecao = o.optBoolean("protecao"), subtotal = o.getInt("subtotal"), valorProtecao = o.getInt("valorProtecao"),
            desconto = o.getInt("desconto"), total = o.getInt("total"), status = StatusReserva.valueOf(o.getString("status")),
            criadaEm = data(o.optString("criadaEm")),
            documentos = o.optJSONArray("documentos")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList(),
            demo = o.optBoolean("demo")
        )
    } ?: emptyList()

    fun salvarReservas(lista: List<Reserva>) = salvarLista("reservas", lista) { r ->
        JSONObject().put("id", r.id).put("usuarioId", r.usuarioId).put("carroId", r.carroId).put("nome", r.nome)
            .put("email", r.email).put("telefone", r.telefone).put("cpf", r.cpf).put("cnh", r.cnh).put("loja", r.loja)
            .put("pagamento", r.pagamento).put("inicio", r.inicio.toString()).put("fim", r.fim.toString())
            .put("dias", r.dias).put("protecao", r.protecao).put("subtotal", r.subtotal).put("valorProtecao", r.valorProtecao)
            .put("desconto", r.desconto).put("total", r.total).put("status", r.status.name)
            .put("criadaEm", r.criadaEm.toString()).put("documentos", JSONArray(r.documentos)).put("demo", r.demo)
    }

    // ---------- E-mails ----------
    fun lerEmails(): List<EmailEnviado> = lerLista("emails") { o ->
        EmailEnviado(o.getString("para"), o.optString("nome"), o.getString("assunto"), o.getString("mensagem"),
            o.optString("via"), data(o.optString("em")))
    } ?: emptyList()

    fun salvarEmails(lista: List<EmailEnviado>) = salvarLista("emails", lista.take(200)) { e ->
        JSONObject().put("para", e.para).put("nome", e.nome).put("assunto", e.assunto)
            .put("mensagem", e.mensagem).put("via", e.via).put("em", e.em.toString())
    }

    // ---------- Frota (alterações do dono) ----------
    fun lerFrota(): Map<String, Triple<Int, Int, Int>> {
        val txt = prefs.getString("frota", null) ?: return emptyMap()
        val o = runCatching { JSONObject(txt) }.getOrNull() ?: return emptyMap()
        return o.keys().asSequence().associateWith { k ->
            val c = o.getJSONObject(k); Triple(c.getInt("preco"), c.getInt("unidades"), c.getInt("manutencao"))
        }
    }

    fun salvarFrota(carros: List<Carro>) {
        val o = JSONObject()
        carros.forEach { o.put(it.id, JSONObject().put("preco", it.precoDia).put("unidades", it.unidades).put("manutencao", it.manutencao)) }
        prefs.edit().putString("frota", o.toString()).apply()
    }

    // ---------- Sessão e preferências ----------
    fun lerSessao(): String? = prefs.getString("sessao", null)
    fun salvarSessao(id: String?) = prefs.edit().putString("sessao", id).apply()

    fun lerPreferencias() = Preferencias(
        daltonismo = runCatching { Daltonismo.valueOf(prefs.getString("daltonismo", "DESLIGADO")!!) }.getOrDefault(Daltonismo.DESLIGADO),
        altoContraste = prefs.getBoolean("contraste", false),
        escalaFonte = prefs.getFloat("fonte", 1f)
    )

    fun salvarPreferencias(p: Preferencias) = prefs.edit()
        .putString("daltonismo", p.daltonismo.name).putBoolean("contraste", p.altoContraste).putFloat("fonte", p.escalaFonte).apply()

    // ---------- Ajudantes ----------
    private fun <T> lerLista(chave: String, ler: (JSONObject) -> T): List<T>? {
        val txt = prefs.getString(chave, null) ?: return null
        return runCatching {
            val a = JSONArray(txt)
            (0 until a.length()).mapNotNull { i -> runCatching { ler(a.getJSONObject(i)) }.getOrNull() }
        }.getOrNull()
    }

    private fun <T> salvarLista(chave: String, lista: List<T>, escrever: (T) -> JSONObject) {
        val a = JSONArray(); lista.forEach { a.put(escrever(it)) }
        prefs.edit().putString(chave, a.toString()).apply()
    }

    private fun data(txt: String): LocalDateTime =
        runCatching { LocalDateTime.parse(txt) }.getOrDefault(LocalDateTime.now())
}
