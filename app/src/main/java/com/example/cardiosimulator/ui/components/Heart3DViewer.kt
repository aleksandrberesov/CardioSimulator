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

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.cardiosimulator.domain.ConductionNode
import com.example.cardiosimulator.domain.ConductionStore
import com.example.cardiosimulator.domain.ConductionSystem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Stable
class Heart3DController {
    private var webView: WebView? = null

    fun setWebView(view: WebView?) {
        webView = view
    }

    fun setConductionPlaying(playing: Boolean) {
        webView?.evaluateJavascript("window.setConductionPlaying($playing)", null)
    }

    fun setBpm(bpm: Int) {
        webView?.evaluateJavascript("window.setBpm($bpm)", null)
    }

    fun setXray(enabled: Boolean) {
        webView?.evaluateJavascript("window.setXray($enabled)", null)
    }

    fun setCutaway(enabled: Boolean) {
        webView?.evaluateJavascript("window.setCutaway($enabled)", null)
    }

    fun setCutPosition(pos: Float) {
        webView?.evaluateJavascript("window.setCutPosition($pos)", null)
    }

    fun setEditing(enabled: Boolean) {
        webView?.evaluateJavascript("window.setEditing($enabled)", null)
    }

    fun setInfarctProgress(progress: Float) {
        webView?.evaluateJavascript("window.setInfarctProgress($progress)", null)
    }

    fun playInfarct() {
        webView?.evaluateJavascript("window.playInfarct()", null)
    }

    fun stopInfarct() {
        webView?.evaluateJavascript("window.stopInfarct()", null)
    }

    fun setLeadsScheme(enabled: Boolean) {
        webView?.evaluateJavascript("window.setLeadsScheme($enabled)", null)
    }
}

/**
 * A 3D model viewer for the heart, using Three.js in a WebView.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Heart3DViewer(
    modifier: Modifier = Modifier,
    controller: Heart3DController? = null,
    modelPath: String = "heart3d/heart.glb",
    onLoaded: () -> Unit = {},
    onScaffoldAvailable: (Boolean) -> Unit = {},
    onError: () -> Unit = {}
) {
    val context = LocalContext.current
    val conductionStore = remember { ConductionStore(context) }
    
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                controller?.setWebView(this)
                val assetLoader = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
                    .build()

                webViewClient = object : WebViewClientCompat() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? {
                        return assetLoader.shouldInterceptRequest(request.url)
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: androidx.webkit.WebResourceErrorCompat
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request.isForMainFrame) {
                            Handler(Looper.getMainLooper()).post { onError() }
                        }
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        Log.d("Heart3DViewer", "${consoleMessage?.message()}")
                        return true
                    }
                }

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                }
                
                val bridge = Heart3DBridge(
                    onLoaded = onLoaded,
                    onScaffoldAvailable = onScaffoldAvailable,
                    onError = onError,
                    onSaveConduction = { json ->
                        try {
                            val nodes = Json.decodeFromString<List<ConductionNode>>(json)
                            conductionStore.save(nodes)
                        } catch (e: Exception) {
                            Log.e("Heart3DViewer", "Failed to save conduction", e)
                        }
                    }
                )
                addJavascriptInterface(bridge, "Android")

                setBackgroundColor(0)

                val locale = Locale.getDefault().language
                val initialPathway = Json.encodeToString(conductionStore.load() ?: emptyList())

                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="utf-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1">
                        <style>
                            body, html { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background: transparent; }
                            #container { width: 100%; height: 100%; }
                        </style>
                        <script type="importmap">
                        {
                            "imports": {
                                "three": "https://appassets.androidplatform.net/assets/heart3d/vendor/three.module.js",
                                "three/addons/": "https://appassets.androidplatform.net/assets/heart3d/vendor/"
                            }
                        }
                        </script>
                    </head>
                    <body>
                        <div id="container"></div>
                        <script type="module">
                            import * as THREE from 'three';
                            import { GLTFLoader } from 'three/addons/GLTFLoader.js';
                            import { OrbitControls } from 'three/addons/OrbitControls.js';
                            import { ConductionSystemRenderer } from 'https://appassets.androidplatform.net/assets/heart3d/conduction.js';

                            let scene, camera, renderer, controls, conductionRenderer;
                            window.currentLocale = '$locale';

                            function init() {
                                scene = new THREE.Scene();

                                camera = new THREE.PerspectiveCamera(45, window.innerWidth / window.innerHeight, 0.01, 1000);
                                camera.position.set(0, 0, 5);

                                renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true });
                                renderer.setPixelRatio(window.devicePixelRatio);
                                renderer.setSize(window.innerWidth, window.innerHeight);
                                renderer.localClippingEnabled = true;
                                document.getElementById('container').appendChild(renderer.domElement);

                                controls = new OrbitControls(camera, renderer.domElement);
                                controls.enableDamping = true;

                                const ambientLight = new THREE.AmbientLight(0xffffff, 1.5);
                                scene.add(ambientLight);

                                const directionalLight = new THREE.DirectionalLight(0xffffff, 2);
                                directionalLight.position.set(1, 1, 1);
                                scene.add(directionalLight);

                                conductionRenderer = new ConductionSystemRenderer(scene, camera, document.getElementById('container'), controls);
                                window.conductionTemplate = ${Json.encodeToString(ConductionSystem.Template)};
                                
                                const initialNodes = $initialPathway;
                                if (initialNodes && initialNodes.length > 0) {
                                    conductionRenderer.setPathway(initialNodes);
                                }

                                const loader = new GLTFLoader();
                                loader.load('https://appassets.androidplatform.net/assets/$modelPath', 
                                    (gltf) => {
                                        const model = gltf.scene;
                                        scene.add(model);
                                        conductionRenderer.setModel(model);

                                        if (initialNodes.length === 0) {
                                            const box = new THREE.Box3().setFromObject(model);
                                            createDefaultPathway(box);
                                        }

                                        if (typeof Android !== 'undefined') Android.onLoaded();
                                    },
                                    undefined,
                                    (error) => {
                                        if (typeof Android !== 'undefined') Android.onError();
                                    }
                                );

                                animate();
                            }

                            function createDefaultPathway(box) {
                                const min = box.min;
                                const max = box.max;
                                const center = box.getCenter(new THREE.Vector3());
                                
                                // Simple vertical layout: base to apex
                                const template = ${Json.encodeToString(ConductionSystem.Template)};
                                const nodes = template.map((node, i) => {
                                    const t = i / (template.length - 1);
                                    // SA node at top right, Apex at bottom
                                    const x = center.x + (i === 0 ? (max.x - center.x) * 0.5 : 0);
                                    const y = max.y - (max.y - min.y) * t;
                                    const z = center.z;
                                    return { ...node, anchor: [x, y, z] };
                                });
                                conductionRenderer.setPathway(nodes);
                                if (typeof Android !== 'undefined') Android.saveConduction(JSON.stringify(nodes));
                            }

                            window.setConductionPlaying = (playing) => conductionRenderer.setPlaying(playing);
                            window.setBpm = (bpm) => conductionRenderer.setBpm(bpm);
                            window.setPathway = (json) => conductionRenderer.setPathway(JSON.parse(json));
                            window.setXray = (enabled) => conductionRenderer.setXray(enabled);
                            window.setCutaway = (enabled) => conductionRenderer.setCutaway(enabled);
                            window.setCutPosition = (pos) => conductionRenderer.setCutPosition(pos);
                            window.setEditing = (enabled) => conductionRenderer.setEditing(enabled);
                            window.setInfarctProgress = (p) => conductionRenderer.setInfarctProgress(p);
                            window.playInfarct = () => conductionRenderer.playInfarct();
                            window.stopInfarct = () => conductionRenderer.stopInfarct();
                            window.setLeadsScheme = (enabled) => conductionRenderer.setLeadsScheme(enabled);

                            function animate() {
                                requestAnimationFrame(animate);
                                const time = performance.now();
                                controls.update();
                                if (conductionRenderer) conductionRenderer.update(time);
                                renderer.render(scene, camera);
                            }

                            window.addEventListener('resize', () => {
                                camera.aspect = window.innerWidth / window.innerHeight;
                                camera.updateProjectionMatrix();
                                renderer.setSize(window.innerWidth, window.innerHeight);
                            });

                            init();
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
    private val onScaffoldAvailable: (Boolean) -> Unit,
    private val onError: () -> Unit,
    private val onSaveConduction: (String) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onLoaded() {
        main.post { onLoaded() }
    }

    @JavascriptInterface
    fun onScaffoldAvailable(available: Boolean) {
        main.post { onScaffoldAvailable(available) }
    }

    @JavascriptInterface
    fun onError() {
        main.post { onError() }
    }

    @JavascriptInterface
    fun saveConduction(json: String) {
        main.post { onSaveConduction(json) }
    }
}
