package com.quantumdrive.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.quantumdrive.app.data.Papel
import com.quantumdrive.app.ui.Aviso
import com.quantumdrive.app.ui.QuantumViewModel
import com.quantumdrive.app.ui.telas.*
import com.quantumdrive.app.ui.theme.Cores
import com.quantumdrive.app.ui.theme.LocalPaleta
import com.quantumdrive.app.ui.theme.TemaQuantum
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Só na versão de teste: abre direto numa tela (usado nas capturas automáticas)
        val rotaTeste = if (BuildConfig.DEBUG) intent?.getStringExtra("rota") else null
        val perfilTeste = if (BuildConfig.DEBUG) intent?.getStringExtra("perfil") else null
        val abaTeste = if (BuildConfig.DEBUG) intent?.getIntExtra("aba", 0) ?: 0 else 0
        val daltTeste = if (BuildConfig.DEBUG) intent?.getStringExtra("dalt") else null
        setContent {
            val vm: QuantumViewModel = viewModel()
            LaunchedEffect(Unit) {
                vm.abaInicialAdmin = abaTeste
                if (daltTeste != null) runCatching { com.quantumdrive.app.data.Daltonismo.valueOf(daltTeste) }
                    .getOrNull()?.let { vm.mudarPreferencias(vm.preferencias.value.copy(daltonismo = it)) }
            }
            val pref by vm.preferencias.collectAsStateWithLifecycle()
            TemaQuantum(pref) { AppQuantum(vm, rotaTeste, perfilTeste) }
        }
    }
}

/** Rotas — o mesmo fluxo do site: Login/Cadastro → Veículos → Detalhes → Reserva → Histórico (+ painel do dono). */
object Rotas {
    const val INICIO = "inicio"
    const val ECONOMIA = "economia"
    const val LOGIN = "login"
    const val CADASTRO = "cadastro"
    const val ADMIN_LOGIN = "admin-login"
    const val VEICULOS = "veiculos"
    const val DETALHES = "detalhes/{id}"
    const val RESERVA = "reserva/{id}"
    const val HISTORICO = "historico"
    const val ADMIN = "admin"
    fun detalhes(id: String) = "detalhes/$id"
    fun reserva(id: String) = "reserva/$id"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppQuantum(vm: QuantumViewModel, rotaTeste: String? = null, perfilTeste: String? = null) {
    val p = LocalPaleta.current
    val nav = rememberNavController()
    val sessao by vm.sessao.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var ultimoAviso by remember { mutableStateOf<Aviso?>(null) }
    var menuAcess by remember { mutableStateOf(false) }
    var menuConta by remember { mutableStateOf(false) }
    val rotaAtual = nav.currentBackStackEntryAsState().value?.destination?.route

    // Mensagens claras de sucesso / erro / informação
    LaunchedEffect(Unit) {
        vm.avisos.collect { a ->
            ultimoAviso = a
            val icone = when (a.tipo) { Aviso.Tipo.SUCESSO -> "✅"; Aviso.Tipo.ERRO -> "⛔"; Aviso.Tipo.INFO -> "ℹ️" }
            snackbar.showSnackbar("$icone ${a.titulo}${if (a.texto.isNotBlank()) "\n" + a.texto else ""}", withDismissAction = true)
        }
    }
    if (rotaTeste != null) LaunchedEffect(Unit) {
        if (perfilTeste != null) { vm.prepararDemonstracao(perfilTeste).join(); delay(300) }
        nav.navigate(rotaTeste)
    }

    Scaffold(
        containerColor = Cores.Fundo,
        topBar = {
            TopAppBar(
                title = {
                    Row(Modifier.semantics { contentDescription = "Quantum Drive" }) {
                        Text("Quantum", color = p.verde, fontWeight = FontWeight.Black)
                        Text("Drive", color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xEB000000)),
                actions = {
                    IconButton({ menuAcess = true }) { Text("♿", fontSize = 22.sp) }
                    val s = sessao
                    if (s == null) TextButton({ nav.navigate(Rotas.LOGIN) }) { Text("ENTRAR", color = Color.White, fontWeight = FontWeight.Black) }
                    else Box {
                        IconButton({ menuConta = true }) {
                            Box(Modifier.size(32.dp).clip(CircleShape).background(p.gradienteBotao), contentAlignment = Alignment.Center) {
                                Text(s.nome.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase(),
                                    color = Cores.TextoEscuro, fontWeight = FontWeight.Black, fontSize = 12.sp)
                            }
                        }
                        DropdownMenu(menuConta, { menuConta = false }) {
                            Text("${s.email}\n${if (s.papel == Papel.DONO) "Dono do site" else "Cliente"}",
                                color = p.cinza2, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                            if (s.papel == Papel.DONO) DropdownMenuItem({ Text("📊 Painel do dono") }, { menuConta = false; nav.navigate(Rotas.ADMIN) })
                            DropdownMenuItem({ Text("📅 Minhas reservas") }, { menuConta = false; nav.navigate(Rotas.HISTORICO) })
                            DropdownMenuItem({ Text("🚗 Nova reserva") }, { menuConta = false; nav.navigate(Rotas.VEICULOS) })
                            DropdownMenuItem({ Text("↩ Sair") }, {
                                menuConta = false; vm.sair(); nav.navigate(Rotas.INICIO) { popUpTo(0) }
                            })
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xF2000000)) {
                ItemNav(nav, rotaAtual, Rotas.INICIO, "Início", Icons.Filled.Home)
                ItemNav(nav, rotaAtual, Rotas.VEICULOS, "Veículos", Icons.Filled.DirectionsCar)
                ItemNav(nav, rotaAtual, Rotas.ECONOMIA, "Economia", Icons.Filled.AttachMoney)
                ItemNav(nav, rotaAtual, Rotas.HISTORICO, "Reservas", Icons.Filled.History)
                if (sessao?.papel == Papel.DONO) ItemNav(nav, rotaAtual, Rotas.ADMIN, "Painel", Icons.Filled.Dashboard)
            }
        },
        snackbarHost = {
            SnackbarHost(snackbar) { d ->
                val cor = when (ultimoAviso?.tipo) { Aviso.Tipo.ERRO -> p.erro; Aviso.Tipo.INFO -> p.azul; else -> p.verde }
                Snackbar(d, containerColor = Color(0xFF0D111A), contentColor = Color.White, actionColor = cor, dismissActionContentColor = cor)
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().background(Cores.gradienteFundo).padding(pad)) {
            NavHost(nav, startDestination = Rotas.INICIO) {
                composable(Rotas.INICIO) {
                    TelaInicio(vm, abrirCarro = { nav.navigate(Rotas.detalhes(it)) }, verTodos = { nav.navigate(Rotas.VEICULOS) },
                        abrirEconomia = { nav.navigate(Rotas.ECONOMIA) })
                }
                composable(Rotas.ECONOMIA) { TelaEconomia(vm) { id -> nav.navigate(Rotas.detalhes(id)) } }
                composable(Rotas.LOGIN) {
                    TelaLogin(vm, aoEntrar = { entrar(nav, vm) }, irCadastro = { nav.navigate(Rotas.CADASTRO) },
                        irAdmin = { nav.navigate(Rotas.ADMIN_LOGIN) })
                }
                composable(Rotas.CADASTRO) { TelaCadastro(vm, aoCriar = { entrar(nav, vm) }, irLogin = { nav.popBackStack() }) }
                composable(Rotas.ADMIN_LOGIN) {
                    TelaAdminLogin(vm, aoEntrar = { nav.navigate(Rotas.ADMIN) { popUpTo(Rotas.INICIO) } }, souCliente = { nav.navigate(Rotas.LOGIN) })
                }
                composable(Rotas.VEICULOS) { TelaVeiculos(vm) { id -> nav.navigate(Rotas.detalhes(id)) } }
                composable(Rotas.DETALHES) { e ->
                    val id = e.arguments?.getString("id") ?: return@composable
                    TelaDetalhes(vm, id,
                        continuar = {
                            // login só quando precisa — e volta para a reserva depois
                            if (vm.sessao.value == null) {
                                vm.voltarPara = Rotas.reserva(id); nav.navigate(Rotas.LOGIN)
                                vm.avisar(Aviso(Aviso.Tipo.INFO, "Entre para continuar", "Faça login para finalizar sua reserva — suas datas ficam salvas."))
                            } else nav.navigate(Rotas.reserva(id))
                        },
                        voltar = { nav.popBackStack() }, abrirOutro = { nav.navigate(Rotas.detalhes(it)) })
                }
                composable(Rotas.RESERVA) { e ->
                    val id = e.arguments?.getString("id") ?: return@composable
                    if (sessao == null) { LaunchedEffect(Unit) { vm.voltarPara = Rotas.reserva(id); nav.navigate(Rotas.LOGIN) }; return@composable }
                    TelaReserva(vm, id, aoConcluir = { nav.navigate(Rotas.HISTORICO) { popUpTo(Rotas.INICIO) } }, voltar = { nav.popBackStack() })
                }
                composable(Rotas.HISTORICO) {
                    if (sessao == null) {
                        LaunchedEffect(Unit) {
                            vm.voltarPara = Rotas.HISTORICO; nav.navigate(Rotas.LOGIN)
                            vm.avisar(Aviso(Aviso.Tipo.INFO, "Entre para continuar", "Esta área precisa de login."))
                        }
                        return@composable
                    }
                    TelaHistorico(vm, novaReserva = { nav.navigate(Rotas.VEICULOS) }, alugarDeNovo = { nav.navigate(Rotas.detalhes(it)) })
                }
                composable(Rotas.ADMIN) {
                    if (sessao?.papel != Papel.DONO) {
                        LaunchedEffect(Unit) {
                            if (sessao != null) vm.avisar(Aviso(Aviso.Tipo.ERRO, "Acesso negado", "Somente o administrador acessa o painel."))
                            nav.navigate(Rotas.ADMIN_LOGIN)
                        }
                        return@composable
                    }
                    TelaAdmin(vm)
                }
            }
        }
    }

    if (menuAcess) MenuAcessibilidade(vm, textoDaTela(rotaAtual, nav, vm)) { menuAcess = false }
}

/** Após o login, volta para onde a pessoa estava (ex.: a reserva). */
private fun entrar(nav: NavHostController, vm: QuantumViewModel) {
    val destino = vm.voltarPara ?: Rotas.VEICULOS
    vm.voltarPara = null
    nav.navigate(destino) { popUpTo(Rotas.INICIO) }
}

/** Texto da tela atual, para o tradutor de Libras. */
private fun textoDaTela(rota: String?, nav: NavHostController, vm: QuantumViewModel): String {
    val id = nav.currentBackStackEntry?.arguments?.getString("id")
    val c = id?.let { vm.repo.carro(it) }
    return when (rota) {
        Rotas.DETALHES, Rotas.RESERVA -> if (c != null) "${c.nome}\n${c.descricao}\nAutonomia de ${c.autonomiaKm} quilômetros, " +
            "${c.potenciaCv} cavalos, ${c.lugares} lugares.\nDiária de ${c.precoDia} reais." +
            (if (rota == Rotas.RESERVA) "\nPreencha datas, loja, pagamento, seus dados e envie os documentos: CNH, RG e comprovante de residência." else "")
            else "Detalhes do veículo."
        Rotas.VEICULOS -> "Escolha seu carro elétrico.\nFiltre por categoria: hatch, SUV ou sedan.\n" +
            vm.frota.value.joinToString("\n") { "${it.nome}: autonomia de ${it.autonomiaKm} quilômetros, diária de ${it.precoDia} reais." }
        Rotas.ECONOMIA -> "Calculadora de economia.\nEscolha o modelo, informe quantos quilômetros você roda por ano e o preço da gasolina.\n" +
            "O app mostra quanto você economiza por ano com um carro elétrico BYD."
        Rotas.HISTORICO -> "Histórico de reservas.\nAqui você acompanha o status: em análise, confirmada, em andamento e concluída.\nVocê também pode cancelar uma reserva."
        Rotas.LOGIN -> "Entrar.\nDigite seu e-mail e sua senha.\nSe ainda não tem conta, toque em cadastre-se."
        Rotas.CADASTRO -> "Cadastro.\nInforme nome, e-mail, telefone, CPF, data de nascimento e uma senha.\nVocê recebe a confirmação por e-mail."
        Rotas.ADMIN, Rotas.ADMIN_LOGIN -> "Área do administrador.\nPainel do dono com reservas, frota, clientes e financeiro."
        else -> "Quantum Drive.\nAluguel de carros cem por cento elétricos BYD.\nEscolha o carro, veja os detalhes, reserve e acompanhe tudo pelo app."
    }
}

@Composable
private fun RowScope.ItemNav(nav: NavHostController, atual: String?, rota: String, rotulo: String, icone: ImageVector) {
    val p = LocalPaleta.current
    val selecionado = atual == rota || (rota == Rotas.VEICULOS && (atual == Rotas.DETALHES || atual == Rotas.RESERVA))
    NavigationBarItem(
        selected = selecionado,
        onClick = { nav.navigate(rota) { popUpTo(Rotas.INICIO); launchSingleTop = true } },
        icon = { Icon(icone, rotulo) }, label = { Text(rotulo, fontSize = 11.sp) },
        colors = NavigationBarItemDefaults.colors(selectedIconColor = Cores.TextoEscuro, indicatorColor = p.verde,
            selectedTextColor = p.verde, unselectedIconColor = p.cinza2, unselectedTextColor = p.cinza2)
    )
}
