package com.example.cardiosimulator.data

import android.content.Context
import android.content.res.AssetManager
import java.io.File

/**
 * Seeds the bundled starter course (app `assets/sample-course/`) into a
 * writable directory (typically `filesDir/courses/`) so first-run authors
 * have a working example to open and edit — the "New course from template"
 * path (plan Phase 5).
 *
 * Wipes [targetDir] first, then copies the asset tree verbatim, preserving
 * the nested layout [FileCourseSource] expects (`<course-id>/lectures/…`).
 * Bytes are copied as-is (UTF-8).
 */
object SampleCourseSeeder {

    const val ASSET_ROOT: String = "sample-course"

    /** Returns true once the seed has produced a readable `manifest.txt`. */
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
            // AssetManager.list returns the children of a directory and an
            // empty array for a file — use that to distinguish the two.
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
