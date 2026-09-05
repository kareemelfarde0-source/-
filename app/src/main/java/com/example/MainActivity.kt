package com.example

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.example.ui.theme.AlDwaarGold
import com.example.ui.theme.AlDwaarPrimary
import com.example.ui.theme.AlDwaarSuccess
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

  private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
  private var filePickerLauncher: ActivityResultLauncher<Intent>? = null
  private var webViewInstance: WebView? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
          if (fileUploadCallback == null) return@registerForActivityResult

          val uris: Array<Uri>? =
              if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val data = result.data
                val clipData = data?.clipData
                if (clipData != null && clipData.itemCount > 0) {
                  Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                } else if (data?.data != null) {
                  arrayOf(data.data!!)
                } else null
              } else null

          fileUploadCallback?.onReceiveValue(uris)
          fileUploadCallback = null
        }

    setContent {
      MyApplicationTheme {
        DashboardScreen(
            onSetupWebView = { wv -> webViewInstance = wv },
            onOpenFileChooser = { callback, params ->
              fileUploadCallback?.onReceiveValue(null)
              fileUploadCallback = callback

              val intent = params.createIntent().apply {
                if (params.acceptTypes.isNotEmpty() && params.acceptTypes[0].isNotBlank()) {
                  val types = params.acceptTypes
                  if (types.size == 1) {
                    type = types[0]
                  } else {
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, types)
                  }
                } else {
                  type = "*/*"
                }
              }

              try {
                filePickerLauncher?.launch(
                    Intent.createChooser(intent, "اختر ملفاً لرفعه إلى ألبان الدوار")
                )
              } catch (e: Exception) {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = null
                Toast.makeText(this@MainActivity, "تعذر فتح محدد الملفات", Toast.LENGTH_SHORT).show()
              }
            },
            onSaveFile = { filename, content, mimeType ->
              shareOrSaveTextFile(filename, content, mimeType)
            },
            onSaveBase64File = { filename, base64Data, mimeType ->
              shareOrSaveBinaryFile(filename, base64Data, mimeType)
            }
        )
      }
    }
  }

  private fun shareOrSaveTextFile(filename: String, content: String, mimeType: String) {
    try {
      val exportDir = File(cacheDir, "exports").apply { mkdirs() }
      val file = File(exportDir, filename)
      file.writeText(content, Charsets.UTF_8)

      val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
      val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, filename)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      startActivity(Intent.createChooser(intent, "مشاركة / حفظ $filename"))
    } catch (e: Exception) {
      Toast.makeText(this, "تعذر تصدير الملف: ${e.message}", Toast.LENGTH_LONG).show()
    }
  }

  private fun shareOrSaveBinaryFile(filename: String, base64Data: String, mimeType: String) {
    try {
      val exportDir = File(cacheDir, "exports").apply { mkdirs() }
      val file = File(exportDir, filename)
      val bytes = Base64.decode(base64Data, Base64.DEFAULT)
      FileOutputStream(file).use { it.write(bytes) }

      val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
      val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, filename)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      startActivity(Intent.createChooser(intent, "مشاركة / حفظ $filename"))
    } catch (e: Exception) {
      Toast.makeText(this, "تعذر تصدير الملف: ${e.message}", Toast.LENGTH_LONG).show()
    }
  }
}

class WebAppInterface(
    private val onSaveFile: (String, String, String) -> Unit,
    private val onSaveBase64File: (String, String, String) -> Unit,
    private val onToast: (String) -> Unit
) {
  @JavascriptInterface
  fun saveFile(filename: String, content: String, mimeType: String) {
    onSaveFile(filename, content, mimeType)
  }

  @JavascriptInterface
  fun saveBase64File(filename: String, base64Data: String, mimeType: String) {
    onSaveBase64File(filename, base64Data, mimeType)
  }

  @JavascriptInterface
  fun showToast(message: String) {
    onToast(message)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DashboardScreen(
    onSetupWebView: (WebView) -> Unit,
    onOpenFileChooser: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Unit,
    onSaveFile: (String, String, String) -> Unit,
    onSaveBase64File: (String, String, String) -> Unit
) {
  val context = LocalContext.current
  var webView by remember { mutableStateOf<WebView?>(null) }
  var progress by remember { mutableFloatStateOf(0f) }
  var isLoading by remember { mutableStateOf(true) }

  BackHandler(enabled = true) {
    webView?.let { wv ->
      wv.evaluateJavascript(
          "(function(){ " +
              "var modals = document.querySelectorAll('.modal-overlay.open'); " +
              "if (modals.length > 0) { " +
              "  for(var i=0; i<modals.length; i++){ modals[i].classList.remove('open'); } " +
              "  return 'modal_closed'; " +
              "} " +
              "var formView = document.getElementById('productFormView'); " +
              "if (formView && !formView.classList.contains('hidden')) { " +
              "  formView.classList.add('hidden'); " +
              "  var listView = document.getElementById('productsListView'); " +
              "  if (listView) listView.classList.remove('hidden'); " +
              "  return 'form_closed'; " +
              "} " +
              "return 'none'; " +
              "})()"
      ) { result ->
        if (result != null && (result.contains("modal_closed") || result.contains("form_closed"))) {
          // Handled inside webview
        } else if (wv.canGoBack()) {
          wv.goBack()
        } else {
          (context as? Activity)?.finish()
        }
      }
    } ?: (context as? Activity)?.finish()
  }

  Scaffold(
      modifier = Modifier.fillMaxSize(),
      topBar = {
        Surface(
            color = AlDwaarPrimary,
            shadowElevation = 4.dp
        ) {
          Column(
              modifier = Modifier
                  .fillMaxWidth()
                  .windowInsetsPadding(WindowInsets.statusBars)
          ) {
            TopAppBar(
                title = {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🥛", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                      Text(
                          text = "ألبان الدوار",
                          style = MaterialTheme.typography.titleMedium.copy(
                              fontWeight = FontWeight.ExtraBold,
                              color = Color.White
                          )
                      )
                      Text(
                          text = "لوحة التحكم وإدخال البيانات",
                          style = MaterialTheme.typography.bodySmall.copy(
                              color = Color.White.copy(alpha = 0.85f),
                              fontSize = 11.sp
                          )
                      )
                    }
                  }
                },
                actions = {
                  Box(
                      modifier = Modifier
                          .size(10.dp)
                          .background(AlDwaarSuccess, CircleShape)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                      text = "متصل",
                      color = AlDwaarGold,
                      fontWeight = FontWeight.Bold,
                      fontSize = 12.sp
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  IconButton(onClick = {
                    webView?.evaluateJavascript("reloadFromStorage(true);", null)
                  }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "تحديث البيانات",
                        tint = Color.White
                    )
                  }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            AnimatedVisibility(visible = isLoading && progress < 1f) {
              LinearProgressIndicator(
                  progress = { progress },
                  modifier = Modifier
                      .fillMaxWidth()
                      .height(3.dp),
                  color = AlDwaarGold,
                  trackColor = AlDwaarPrimary
              )
            }
          }
        }
      }
  ) { innerPadding ->
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
      AndroidView(
          modifier = Modifier.fillMaxSize(),
          factory = { ctx ->
            WebView(ctx).apply {
              layoutParams = ViewGroup.LayoutParams(
                  ViewGroup.LayoutParams.MATCH_PARENT,
                  ViewGroup.LayoutParams.MATCH_PARENT
              )
              settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                loadsImagesAutomatically = true
                useWideViewPort = true
                loadWithOverviewMode = true
                cacheMode = WebSettings.LOAD_DEFAULT
              }

              addJavascriptInterface(
                  WebAppInterface(
                      onSaveFile = onSaveFile,
                      onSaveBase64File = onSaveBase64File,
                      onToast = { msg ->
                        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                      }
                  ),
                  "AndroidBridge"
              )

              webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                  progress = newProgress / 100f
                  if (newProgress >= 100) {
                    isLoading = false
                  }
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                  if (filePathCallback != null && fileChooserParams != null) {
                    onOpenFileChooser(filePathCallback, fileChooserParams)
                    return true
                  }
                  return false
                }
              }

              webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                  isLoading = true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                  isLoading = false
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                  val url = request?.url?.toString() ?: return false
                  if (url.startsWith("file://") || url.startsWith("http://") || url.startsWith("https://")) {
                    return false
                  }
                  return try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    ctx.startActivity(intent)
                    true
                  } catch (e: Exception) {
                    false
                  }
                }
              }

              loadUrl("file:///android_asset/dashboard.html")
              webView = this
              onSetupWebView(this)
            }
          },
          update = { wv ->
            webView = wv
            onSetupWebView(wv)
          }
      )
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      webView?.destroy()
    }
  }
}
