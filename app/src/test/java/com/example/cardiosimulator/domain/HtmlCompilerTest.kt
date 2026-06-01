package com.example.cardiosimulator.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlCompilerTest {

    @Test
    fun `test parsing basic elements`() {
        val html = """
            <h1>Title</h1>
            <p>Hello <strong>world</strong></p>
            <img src="assets/img.png" alt="Description">
        """.trimIndent()

        val blocks = HtmlCompiler.parse(html)
        
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is HtmlBlock.Header)
        assertEquals("Title", (blocks[0] as HtmlBlock.Header).text)
        assertEquals(1, (blocks[0] as HtmlBlock.Header).level)
        
        assertTrue(blocks[1] is HtmlBlock.Paragraph)
        assertEquals("Hello <strong>world</strong>", (blocks[1] as HtmlBlock.Paragraph).html)
        
        assertTrue(blocks[2] is HtmlBlock.Image)
        assertEquals("assets/img.png", (blocks[2] as HtmlBlock.Image).src)
        assertEquals("Description", (blocks[2] as HtmlBlock.Image).alt)
    }

    @Test
    fun `test parsing custom ecg tag`() {
        val html = """<ecg pathology="mi" lead="II" caption="MI Lead II"></ecg>"""
        val blocks = HtmlCompiler.parse(html)
        
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is HtmlBlock.Ecg)
        val ecg = blocks[0] as HtmlBlock.Ecg
        assertEquals("mi", ecg.pathology)
        assertEquals("II", ecg.lead)
        assertEquals("MI Lead II", ecg.caption)
    }

    @Test
    fun `test parsing katex display mode`() {
        val html = "<p>$$ E = mc^2 $$</p>"
        val blocks = HtmlCompiler.parse(html)
        
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is HtmlBlock.KaTeX)
        val katex = blocks[0] as HtmlBlock.KaTeX
        assertEquals("E = mc^2", katex.expression)
        assertTrue(katex.displayMode)
    }

    @Test
    fun `test isomorphism`() {
        val originalBlocks = listOf(
            HtmlBlock.Header(level = 1, text = "Test"),
            HtmlBlock.Paragraph(html = "Some <em>text</em>"),
            HtmlBlock.Image(src = "a.png", alt = "b"),
            HtmlBlock.Ecg(pathology = "p", lead = null, caption = "c")
        )
        
        val html = HtmlCompiler.compile(originalBlocks)
        val parsedBlocks = HtmlCompiler.parse(html)
        
        assertEquals(originalBlocks.size, parsedBlocks.size)
        // Note: IDs will differ, so we compare content
        originalBlocks.zip(parsedBlocks).forEach { (orig, parsed) ->
            when {
                orig is HtmlBlock.Header && parsed is HtmlBlock.Header -> {
                    assertEquals(orig.level, parsed.level)
                    assertEquals(orig.text, parsed.text)
                }
                orig is HtmlBlock.Paragraph && parsed is HtmlBlock.Paragraph -> {
                    assertEquals(orig.html, parsed.html)
                }
                orig is HtmlBlock.Image && parsed is HtmlBlock.Image -> {
                    assertEquals(orig.src, parsed.src)
                    assertEquals(orig.alt, parsed.alt)
                }
                orig is HtmlBlock.Ecg && parsed is HtmlBlock.Ecg -> {
                    assertEquals(orig.pathology, parsed.pathology)
                    assertEquals(orig.lead, parsed.lead)
                    assertEquals(orig.caption, parsed.caption)
                }
                else -> assertTrue("Block type mismatch", false)
            }
        }
    }
}
