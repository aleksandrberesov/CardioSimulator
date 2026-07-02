package com.example.cardiosimulator.ui.components

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
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
    modelPath: String = "heart3d/heart.glb", // Expected in app/src/main/assets/
    onLoaded: () -> Unit = {},
    onError: () -> Unit = {}
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
                    ): WebResourceResponse? {
                        Log.d("Heart3DViewer", "Requesting: ${request.url}")
                        return assetLoader.shouldInterceptRequest(request.url)
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: androidx.webkit.WebResourceErrorCompat
                    ) {
                        super.onReceivedError(view, request, error)
                        Log.e("Heart3DViewer", "WebView error: ${error.description}")
                        if (request.isForMainFrame) {
                            Handler(Looper.getMainLooper()).post { onError() }
                        }
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        Log.d("Heart3DViewer", "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                        return true
                    }
                }

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                }
                
                addJavascriptInterface(Heart3DBridge(onLoaded, onError), "Android")

                // Allow transparency
                setBackgroundColor(0)

                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="utf-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1">
                        <script type="module" src="https://ajax.googleapis.com/ajax/libs/model-viewer/3.5.0/model-viewer.min.js"></script>
                        <style>
                            body, html { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background: transparent; }
                            model-viewer { 
                                width: 100%; 
                                height: 100%; 
                                --progress-bar-color: #5B9BD5;
                                background-color: transparent;
                            }
                            #error-message {
                                display: none;
                                position: absolute;
                                top: 50%;
                                left: 50%;
                                transform: translate(-50%, -50%);
                                color: #d32f2f;
                                font-family: sans-serif;
                                text-align: center;
                                padding: 20px;
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
                                <div style="text-align: center;">
                                    <p style="font-family: sans-serif; color: #666; font-size: 14px;">Loading 3D Heart...</p>
                                    <p style="font-family: sans-serif; color: #999; font-size: 10px;">(Large models may take a moment)</p>
                                </div>
                            </div>
                        </model-viewer>
                        <div id="error-message">
                            <p>Failed to load 3D model.</p>
                            <p style="font-size: 12px;" id="error-details"></p>
                        </div>
                        <script>
                            const modelViewer = document.querySelector("model-viewer");
                            const errorMsg = document.getElementById("error-message");
                            const errorDetails = document.getElementById("error-details");

                            modelViewer.addEventListener('error', (event) => {
                                console.error("ModelViewer Error:", event.detail);
                                errorMsg.style.display = "block";
                                errorDetails.textContent = "Error: " + (event.detail.type || "Unknown error");
                                if (typeof Android !== 'undefined') Android.onError();
                            });

                            modelViewer.addEventListener('load', () => {
                                console.log("Model loaded successfully");
                                if (typeof Android !== 'undefined') Android.onLoaded();
                            });

                            // Timeout for loading
                            setTimeout(() => {
                                if (!modelViewer.loaded) {
                                    console.warn("Model loading is taking a long time. It might be too large for the device memory.");
                                }
                            }, 10000);
                        </script>
                    </body>
                    </html>
                """.trimIndent()

                loadDataWithBaseURL("https://appassets.androidplatform.net/", html, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() }
    )
}

private class Heart3DBridge(
    private val onLoaded: () -> Unit,
    private val onError: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onLoaded() {
        main.post { onLoaded() }
    }

    @JavascriptInterface
    fun onError() {
        main.post { onError() }
    }
}
