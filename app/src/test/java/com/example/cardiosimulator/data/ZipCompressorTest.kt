package com.example.cardiosimulator.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

class ZipCompressorTest {

    private lateinit var tempDir: File

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "zip_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
    }

    @After
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun writeArchive_isRecursive() {
        // Build temp tree:
        // manifest.txt
        // subdir/lecture.html
        val manifest = File(tempDir, "manifest.txt").apply { writeText("manifest content") }
        val subdir = File(tempDir, "subdir").apply { mkdirs() }
        val lecture = File(subdir, "lecture.html").apply { writeText("lecture content") }

        val output = ByteArrayOutputStream()
        ZipCompressor.writeArchive(tempDir, output)

        val entryNames = mutableListOf<String>()
        val contents = mutableMapOf<String, String>()

        ZipInputStream(output.toByteArray().inputStream()).use { zis ->
            var entry = zis.getNextEntry()
            while (entry != null) {
                entryNames.add(entry.name)
                if (!entry.isDirectory) {
                    contents[entry.name] = zis.bufferedReader().readText()
                }
                entry = zis.getNextEntry()
            }
        }

        // Android's ZipCompressor emits both files and directory entries.
        // We care that the files are present with their relative paths.
        assertTrue("Should contain manifest.txt", entryNames.contains("manifest.txt"))
        assertTrue("Should contain subdir/lecture.html", entryNames.contains("subdir/lecture.html"))

        assertEquals("manifest content", contents["manifest.txt"])
        assertEquals("lecture content", contents["subdir/lecture.html"])
    }
}
