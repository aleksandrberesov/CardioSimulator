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
    resolveEcg: (pathologyId: String, lead: Lead?) -> List<EcgTrace> = { _, _ -> emptyList() },
    answers: Map<String, Map<String, String>> = emptyMap(),
    onCellEdit: ((quizId: String, row: Int, col: Int, value: String) -> Unit)? = null,
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
    val html by produceState<String?>(initialValue = null, lecture, css, interactive) {
        value = withContext(Dispatchers.IO) {
            val body = EcgSvgRenderer.substituteEcgTags(lecture.rawHtml, resolveEcg)
            buildDocument(body = body, css = css, interactive = interactive)
        }
    }

    // Saved quiz answers are injected after each page load (not folded into the
    // HTML), so editing a cell never triggers a reload. The factory's
    // WebViewClient reads the latest script through this ref.
    val injectScript = remember(answers) { buildAnswerInjectScript(answers) }
    val injectRef = remember { mutableStateOf(injectScript) }
    injectRef.value = injectScript

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
                    }
                }
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                onCellEdit?.let { addJavascriptInterface(QuizBridge(it), "Android") }
            }
        },
        update = { web ->
            web.setBackgroundColor(bgArgb)
            val current = html
            // Avoid redundant reloads (and flicker) when recomposition leaves the HTML unchanged.
            if (current != null && web.tag != current) {
                web.tag = current
                web.loadDataWithBaseURL(
                    "$ASSET_DOMAIN/course/${lecture.courseId}/",
                    current,
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        },
        onRelease = { it.destroy() },
    )
}

/** Bridge for editable quiz cells. JS callbacks arrive on a binder thread. */
private class QuizBridge(
    private val callback: (quizId: String, row: Int, col: Int, value: String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onCell(quizId: String, row: Int, col: Int, value: String) {
        main.post { callback(quizId, row, col, value) }
    }
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
<script src="/assets/katex/auto-render.min.js"></script>
<script>
(function(){
  function render(){
    if (window.renderMathInElement) {
      renderMathInElement(document.body, {
        delimiters:[
          {left:"$$",right:"$$",display:true},
          {left:"$",right:"$",display:false}
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
figure.ecg-figure{margin:1em 0}
svg.ecg-lead{max-width:100%;height:auto;display:block;margin:2px 0}
figure.ecg-figure figcaption{font-size:.9em;color:var(--muted);margin-top:4px}
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

