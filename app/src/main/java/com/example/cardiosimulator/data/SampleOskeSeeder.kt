package com.example.cardiosimulator.data

import android.content.Context
import android.content.res.AssetManager
import java.io.File

object SampleOskeSeeder {
    const val ASSET_ROOT: String = "oske"

    fun seed(context: Context, targetDir: File): Boolean = runCatching {
        if (targetDir.exists()) targetDir.deleteRecursively()
        targetDir.mkdirs()
        copyTree(context.assets, ASSET_ROOT, targetDir)
        File(targetDir, "manifest.txt").canRead()
    }.getOrDefault(false)

    private fun copyTree(assets: AssetManager, path: String, outDir: File) {
        for (name in assets.list(path).orEmpty()) {
            val childPath = "$path/$name"
            val outChild = File(outDir, name)
            if (assets.list(childPath).isNullOrEmpty()) {
                assets.open(childPath).use { input ->
                    outChild.outputStream().use { output -> input.copyTo(output) }
                }
            } else {
                outChild.mkdirs()
                copyTree(assets, childPath, outChild)
            }
        }
    }
}
