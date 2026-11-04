package com.quantumdrive.app

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File

/**
 * App Android do Quantum Drive.
 *
 * Abre o site oficial (sempre a versão mais nova) dentro do app e acrescenta
 * o que um app precisa: botão voltar, envio de documentos (CNH, RG, comprovante),
 * salvar a planilha do financeiro em Downloads, tela sem internet e links
 * externos (e-mail, WhatsApp, mapas) abrindo no app certo.
 */
class MainActivity : ComponentActivity() {

    companion object {
        const val SITE = "https://lorranyam.github.io/QuantumDrive/"
        private const val HOST = "lorranyam.github.io"
        private const val CAMINHO = "/QuantumDrive"
    }

    private lateinit var web: WebView
    private lateinit var carregando: ProgressBar
    private var callbackArquivo: ValueCallback<Array<Uri>>? = null

    // Seletor de arquivos para os campos de documento da reserva
    private val escolherArquivo = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        callbackArquivo?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
        callbackArquivo = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this).apply {
            setBackgroundColor(0xFF05070C.toInt())
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true          // contas e reservas ficam salvas no app
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false // avatar do VLibras
                loadWithOverviewMode = true
                useWideViewPort = true
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = "$userAgentString QuantumDriveApp/${BuildConfig.VERSION_NAME}"
            }
            addJavascriptInterface(Ponte(), "QuantumApp")
            webViewClient = Navegador()
            webChromeClient = Chrome()
        }
        carregando = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
        }
        setContentView(FrameLayout(this).apply {
            addView(web, ViewGroup.LayoutParams(-1, -1))
            addView(carregando, FrameLayout.LayoutParams(-1, 8))
        })

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl(intent?.data?.takeIf { eDoSite(it) }?.toString() ?: SITE)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.takeIf { eDoSite(it) }?.let { web.loadUrl(it.toString()) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    private fun eDoSite(uri: Uri) =
        uri.scheme == "https" && uri.host == HOST && (uri.path ?: "").startsWith(CAMINHO)

    private fun abrirFora(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Nenhum app encontrado para abrir este link.", Toast.LENGTH_SHORT).show()
        }
    }

    private inner class Navegador : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            if (eDoSite(uri)) return false          // páginas do Quantum Drive ficam no app
            if (!request.isForMainFrame) return false
            abrirFora(uri)                           // e-mail, WhatsApp, GitHub, etc.
            return true
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            carregando.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView, url: String?) {
            carregando.visibility = View.GONE
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) view.loadUrl("file:///android_asset/offline.html")
        }
    }

    private inner class Chrome : WebChromeClient() {
        override fun onShowFileChooser(
            webView: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams
        ): Boolean {
            callbackArquivo?.onReceiveValue(null)
            callbackArquivo = callback
            return try {
                val escolher = params.createIntent().apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "image/jpeg", "image/png"))
                }
                escolherArquivo.launch(Intent.createChooser(escolher, "Escolha o documento"))
                true
            } catch (e: ActivityNotFoundException) {
                callbackArquivo = null
                false
            }
        }
    }

    /** Funções que o site pode chamar quando está dentro do app (window.QuantumApp). */
    private inner class Ponte {
        @JavascriptInterface
        fun salvarArquivo(nome: String, conteudo: String, tipo: String) {
            val bytes = conteudo.toByteArray(Charsets.UTF_8)
            val ok = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val valores = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, nome)
                        put(MediaStore.Downloads.MIME_TYPE, tipo)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores)!!
                    contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
                } else {
                    val pasta = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
                    File(pasta, nome).writeBytes(bytes)
                }
            }.isSuccess
            runOnUiThread {
                Toast.makeText(this@MainActivity,
                    if (ok) "Salvo em Downloads: $nome" else "Não foi possível salvar o arquivo.",
                    Toast.LENGTH_LONG).show()
            }
        }

        @JavascriptInterface
        fun tentarDeNovo() {
            runOnUiThread { web.loadUrl(SITE) }
        }

        @JavascriptInterface
        fun versao(): String = BuildConfig.VERSION_NAME
    }
}
