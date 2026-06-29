package com.example.cardiosimulator.ui.components

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat

/**
 * A 3D model viewer for the heart, using Google's <model-viewer> web component.
 * It provides orbit, zoom, and pan controls by default.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Heart3DViewer(
    modifier: Modifier = Modifier,
    modelPath: String = "heart.glb" // Expected in app/src/main/assets/
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                val assetLoader = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
                    .build()

                webViewClient = object : WebViewClientCompat() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
                }

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // Allow transparency if needed
                setBackgroundColor(0)

                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="utf-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1">
                        <!-- Use model-viewer from CDN. For offline use, download and place in assets/ -->
                        <script type="module" src="https://ajax.googleapis.com/ajax/libs/model-viewer/4.0.0/model-viewer.min.js"></script>
                        <style>
                            body, html { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background: transparent; }
                            model-viewer { 
                                width: 100%; 
                                height: 100%; 
                                --progress-bar-color: #5B9BD5;
                                background-color: transparent;
                            }
                        </style>
                    </head>
                    <body>
                        <model-viewer 
                            src="https://appassets.androidplatform.net/assets/$modelPath" 
                            alt="3D Heart Model"
                            shadow-intensity="1" 
                            camera-controls 
                            auto-rotate 
                            touch-action="pan-y">
                            <div slot="poster" style="display: flex; align-items: center; justify-content: center; height: 100%;">
                                <p style="font-family: sans-serif; color: #666; font-size: 12px;">Loading 3D Heart...</p>
                            </div>
                        </model-viewer>
                    </body>
                    </html>
                """.trimIndent()

                loadDataWithBaseURL("https://appassets.androidplatform.net/", html, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() }
    )
}
