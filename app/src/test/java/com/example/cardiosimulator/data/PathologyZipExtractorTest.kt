package com.example.cardiosimulator.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PathologyZipExtractorTest {

    private lateinit var targetDir: File

    @Before
    fun setup() {
        targetDir = File(System.getProperty("java.io.tmpdir"), "extract_test_${System.currentTimeMillis()}")
        if (targetDir.exists()) targetDir.deleteRecursively()
        targetDir.mkdirs()
    }

    @After
    fun cleanup() {
        targetDir.deleteRecursively()
    }

    private fun createZip(entries: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            entries.forEach { (name, content) ->
                z.putNextEntry(ZipEntry(name))
                z.write(content.toByteArray())
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun extractStream_unpacksAllFilesFlat() = runBlocking {
        val zipData = createZip(mapOf(
            "js00001.dat" to "content1",
            "subdir/js00002.dat" to "content2",
            "nested/deep/js00003.dat" to "content3"
        ))

        val progress = mutableListOf<ZipProgress>()
        val ok = PathologyZipExtractor.extractStream(
            ByteArrayInputStream(zipData),
            targetDir,
            total = 3,
            onProgress = { progress.add(it) }
        )

        assertTrue(ok)
        assertEquals(3, targetDir.listFiles()?.size ?: 0)
        assertTrue(File(targetDir, "js00001.dat").exists())
        assertTrue(File(targetDir, "js00002.dat").exists())
        assertTrue(File(targetDir, "js00003.dat").exists())

        assertEquals("content1", File(targetDir, "js00001.dat").readText())
        assertEquals("content2", File(targetDir, "js00002.dat").readText())
        assertEquals("content3", File(targetDir, "js00003.dat").readText())

        // Throttled progress: should have at least the final 100% tick
        assertTrue(progress.isNotEmpty())
        assertEquals(3, progress.last().done)
        assertEquals(3, progress.last().total)
    }

    @Test
    fun extractStream_handlesCancellation() = runBlocking {
        val zipData = createZip((1..10).associate { "file$it.dat" to "content" })

        val job = launch {
            try {
                PathologyZipExtractor.extractStream(
                    ByteArrayInputStream(zipData),
                    targetDir,
                    total = 10,
                    onProgress = { p ->
                        if (p.done == 1) {
                            // Cancel as soon as we start
                            this@launch.cancel("Mid-extract cancel")
                        }
                    }
                )
                fail("Should have thrown CancellationException")
            } catch (e: CancellationException) {
                // Expected
            }
        }
        job.join()

        // Partial dir should be deleted
        assertFalse("Target dir should be cleaned up on cancel", targetDir.exists())
    }

    @Test
    fun extractStream_preCancelled() = runBlocking {
        val zipData = createZip(mapOf("file.dat" to "content"))
        val job = Job()
        job.cancel() // already cancelled

        val launchJob = launch(job) {
            PathologyZipExtractor.extractStream(
                ByteArrayInputStream(zipData),
                targetDir,
                total = 1
            )
        }
        launchJob.join()

        // Job didn't even start, or threw immediately
        assertFalse(targetDir.exists() && targetDir.listFiles()?.isNotEmpty() == true)
    }
}
