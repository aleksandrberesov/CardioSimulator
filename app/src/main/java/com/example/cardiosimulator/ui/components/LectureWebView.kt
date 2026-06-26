package com.example.cardiosimulator.ui.components

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.example.cardiosimulator.data.EcgSvgRenderer
import com.example.cardiosimulator.data.EcgTrace
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.Lecture
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Virtual origin served by [WebViewAssetLoader] (its default authority). */
private const val ASSET_DOMAIN = "https://appassets.androidplatform.net"

/**
 * Renders a whole [Lecture] as a single HTML document in one WebView
 * (Phase 2 of docs/plans/active/2026-05-course-constructor.md):
 *
 * - Body = [Lecture.rawHtml] with `<ecg>` elements rewritten to inline
 *   SVG by [EcgSvgRenderer].
 * - KaTeX `$…$` / `$$…$$` math auto-rendered in a single pass (no
 *   per-formula WebView).
 * - All subresources (KaTeX assets, course images) are served from one
 *   virtual origin via [WebViewAssetLoader]: `/assets/` → APK assets,
 *   `/course/` → `filesDir/courses/`. Same-origin serving avoids
 *   `file://` font-CORS pain and keeps remote loads impossible.
 *
 * @param resolveEcg maps `(pathologyId, lead)` → traces to draw (a null
 *   lead means "all 12 leads"); supplied by the screen from
 *   `PathologyRepository`. The default renders ECG placeholders.
 * @param onCellEdit when non-null, editable quiz-table `<input>`s report
 *   edits through a JS bridge (constructor mode). Null = read-only viewer.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LectureWebView(
    lecture: Lecture,
    modifier: Modifier = Modifier,
    resolveEcg: (pathologyId: String, leads: List<Lead>) -> List<EcgTrace> = { _, _ -> emptyList() },
    answers: Map<String, Map<String, String>> = emptyMap(),
    scrollToBlockId: String? = null,
    onCellEdit: ((quizId: String, row: Int, col: Int, value: String) -> Unit)? = null,
    onMonitorClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val bgArgb = colors.background.toArgb()

    val css = remember(colors) {
        themeCss(
            bg = colors.background.toArgb(),
            fg = colors.onBackground.toArgb(),
            surface = colors.surfaceVariant.toArgb(),
            primary = colors.primary.toArgb(),
            border = colors.outlineVariant.toArgb(),
            muted = colors.onSurfaceVariant.toArgb(),
        )
    }

    val interactive = onCellEdit != null
    // Build the document off the main thread: <ecg> resolution reads pathology
    // .dat files, so it must not block composition.
    val html by produceState<String?>(initialValue = null, lecture, css, interactive, onMonitorClick) {
        value = withContext(Dispatchers.IO) {
            val body = EcgSvgRenderer.substituteEcgTags(
                lecture.rawHtml,
                showMonitorButton = onMonitorClick != null,
                resolve = resolveEcg
            )
            if (lecture.isStandalone) {
                buildStandaloneDocument(body = body, css = css, interactive = interactive)
            } else {
                buildDocument(body = body, css = css, interactive = interactive)
            }
        }
    }

    // Saved quiz answers are injected after each page load (not folded into the
    // HTML), so editing a cell never triggers a reload. The factory's
    // WebViewClient reads the latest script through this ref.
    val injectScript = remember(answers) { buildAnswerInjectScript(answers) }
    val injectRef = remember { mutableStateOf(injectScript) }
    injectRef.value = injectScript

    val scrollRef = remember { mutableStateOf<String?>(null) }
    scrollRef.value = scrollToBlockId

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                val assetLoader = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
                    .addPathHandler(
                        "/course/",
                        WebViewAssetLoader.InternalStoragePathHandler(ctx, File(ctx.filesDir, "courses")),
                    )
                    .build()
                webViewClient = object : WebViewClientCompat() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

                    override fun onPageFinished(view: WebView, url: String) {
                        view.evaluateJavascript(injectRef.value, null)
                        scrollRef.value?.let { id ->
                            // Use behavior: 'auto' (instant jump) rather than 'smooth' to avoid
                            // distracting animations during every debounced keystroke update.
                            view.evaluateJavascript("document.getElementById('$id')?.scrollIntoView({behavior: 'auto'})", null)
                        }
                    }
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                addJavascriptInterface(LectureBridge(onCellEdit, onMonitorClick), "Android")
            }
        },
        update = { web ->
            // Defensive guard: if the view is being torn down, bail.
            if (web.handler == null) return@AndroidView
            
            web.setBackgroundColor(bgArgb)
            val current = html
            // Avoid redundant reloads (and flicker) when recomposition leaves the HTML unchanged.
            if (current != null && web.tag != current) {
                web.loadDataWithBaseURL(
                    "$ASSET_DOMAIN/course/${lecture.courseId}/",
                    current,
                    "text/html",
                    "utf-8",
                    null,
                )
                // Commit the cache ONLY after a successful call (mirroring Windows fix)
                web.tag = current
            } else if (current != null && scrollToBlockId != null) {
                // If the content didn't change but the scroll ID did, scroll now.
                web.evaluateJavascript("document.getElementById('$scrollToBlockId')?.scrollIntoView({behavior: 'auto'})", null)
            }
        },
        onRelease = { it.destroy() },
    )
}

/** Bridge for editable quiz cells and monitor integration. */
private class LectureBridge(
    private val onCell: ((quizId: String, row: Int, col: Int, value: String) -> Unit)? = null,
    private val onMonitor: (() -> Unit)? = null,
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onCell(quizId: String, row: Int, col: Int, value: String) {
        main.post { onCell?.invoke(quizId, row, col, value) }
    }

    @JavascriptInterface
    fun onMonitor() {
        main.post { onMonitor?.invoke() }
    }
}

private fun buildStandaloneDocument(body: String, css: String, interactive: Boolean): String {
    val bridge = if (interactive) QUIZ_BRIDGE_JS else ""
    val katexCss = """<link rel="stylesheet" href="/assets/katex/katex.min.css">"""
    val katexJs = """<script src="/assets/katex/katex.min.js"></script>
<script src="/assets/katex/contrib/auto-render.min.js"></script>"""
    val style = """<style>$css</style>"""

    var doc = body
    if (!doc.contains("<base", ignoreCase = true)) {
        val base = """<base href="$ASSET_DOMAIN/course/">"""
        doc = doc.replaceFirst("<head>", "<head>\n$base", ignoreCase = true)
    }

    doc = doc.replaceFirst("</head>", "$katexCss\n$style\n</head>", ignoreCase = true)

    val scripts = """
$katexJs
<script>
(function(){
  function render(){
    if (window.renderMathInElement) {
      renderMathInElement(document.body, {
        delimiters:[
          {left:"$$",right:"$$",display:true},
          {left:"$",right:"$",display:false},
          {left:"\\(",right:"\\)",display:false},
          {left:"\\[",right:"\\]",display:true}
        ],
        throwOnError:false
      });
    }
  }
  if (document.readyState!=="loading") render();
  else document.addEventListener("DOMContentLoaded", render);
$bridge
})();
</script>
""".trimIndent()

    return doc.replaceFirst("</body>", "$scripts\n</body>", ignoreCase = true)
}

private fun buildDocument(body: String, css: String, interactive: Boolean): String {
    val bridge = if (interactive) QUIZ_BRIDGE_JS else ""
    return """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href="/assets/katex/katex.min.css">
<style>$css</style>
</head>
<body>
$body
<script src="/assets/katex/katex.min.js"></script>
<script src="/assets/katex/contrib/auto-render.min.js"></script>
<script>
(function(){
  function render(){
    if (window.renderMathInElement) {
      renderMathInElement(document.body, {
        delimiters:[
          {left:"$$",right:"$$",display:true},
          {left:"$",right:"$",display:false},
          {left:"\\(",right:"\\)",display:false},
          {left:"\\[",right:"\\]",display:true}
        ],
        throwOnError:false
      });
    }
  }
  if (document.readyState!=="loading") render();
  else document.addEventListener("DOMContentLoaded", render);
$bridge
})();
</script>
</body>
</html>"""
}

private fun themeCss(bg: Int, fg: Int, surface: Int, primary: Int, border: Int, muted: Int): String {
    fun hex(argb: Int) = "#%06X".format(0xFFFFFF and argb)
    return """
:root{
  --bg:${hex(bg)}; --fg:${hex(fg)}; --surface:${hex(surface)};
  --primary:${hex(primary)}; --border:${hex(border)}; --muted:${hex(muted)};
}
html,body{margin:0;padding:0}
body{background:var(--bg);color:var(--fg);
  font-family:-apple-system,Roboto,"Segoe UI",sans-serif;
  font-size:16px;line-height:1.55;padding:16px;-webkit-text-size-adjust:100%}
h1,h2,h3{line-height:1.25}
a{color:var(--primary)}
img{max-width:100%;height:auto}
table{border-collapse:collapse;width:100%;margin:1em 0}
th,td{border:1px solid var(--border);padding:6px 10px;text-align:left;vertical-align:top}
th{background:var(--surface)}
input,textarea{font:inherit;color:inherit;background:transparent;
  border:1px solid var(--border);border-radius:4px;padding:2px 6px;
  width:100%;box-sizing:border-box}
figure.ecg-figure, figure.image-figure{margin:1.5em 0}
svg.ecg-monitor{max-width:100%;height:auto;display:block;margin:0 auto;border:1px solid var(--border);border-radius:4px}
.monitor-btn{display:block;margin:8px auto;padding:6px 16px;background:var(--primary);color:white;border:none;border-radius:4px;font-size:14px;cursor:pointer}
figcaption{font-size:.9em;color:var(--muted);margin-top:6px;text-align:center;font-style:italic}
.ecg-missing figcaption{color:#b00020}
""".trimIndent()
}

/** Wires editable quiz `<input>`s to the [QuizBridge]. Keys mirror `.answers.json`
 *  (0-based row over data `<tr>`s only, 0-based col over all cells). */
private val QUIZ_BRIDGE_JS = """
  document.querySelectorAll('table[data-quiz-id][data-editable="true"]').forEach(function(tbl){
    var quizId = tbl.getAttribute('data-quiz-id');
    var rows = tbl.querySelectorAll('tr');
    var dataRow = -1;
    for (var r=0; r<rows.length; r++){
      if (rows[r].querySelector('th')) continue;
      dataRow++;
      var cells = rows[r].children;
      for (var c=0; c<cells.length; c++){
        var input = cells[c].querySelector('input, textarea');
        if (input){
          (function(qid, row, col, inp){
            inp.addEventListener('input', function(){
              if (window.Android && Android.onCell) Android.onCell(qid, row, col, inp.value);
            });
          })(quizId, dataRow, c, input);
        }
      }
    }
  });
""".trimIndent()

/**
 * Builds JS that injects saved [answers] into editable quiz `<input>`s on
 * page load (mirrors the key scheme in QUIZ_BRIDGE_JS: 0-based data-row,
 * 0-based cell column). Empty answers produce a harmless no-op.
 */
private fun buildAnswerInjectScript(answers: Map<String, Map<String, String>>): String {
    val json = JSONObject()
    for ((quizId, cells) in answers) {
        val obj = JSONObject()
        for ((key, value) in cells) obj.put(key, value)
        json.put(quizId, obj)
    }
    return """
(function(){
  try {
    var a = $json;
    document.querySelectorAll('table[data-quiz-id]').forEach(function(tbl){
      var m = a[tbl.getAttribute('data-quiz-id')]; if (!m) return;
      var rows = tbl.querySelectorAll('tr'); var dr = -1;
      for (var r=0; r<rows.length; r++){
        if (rows[r].querySelector('th')) continue; dr++;
        var cells = rows[r].children;
        for (var c=0; c<cells.length; c++){
          var inp = cells[c].querySelector('input, textarea');
          if (inp){ var v = m[dr+','+c]; if (v !== undefined && v !== null) inp.value = v; }
        }
      }
    });
  } catch(e) {}
})();
""".trimIndent()
}

