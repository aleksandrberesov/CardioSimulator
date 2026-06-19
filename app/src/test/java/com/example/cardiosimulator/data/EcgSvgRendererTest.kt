package com.example.cardiosimulator.data

import com.example.cardiosimulator.domain.Lead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EcgSvgRendererTest {

    private val sample = Points(listOf(0f, 1f, 0.5f, -0.5f, 0f))

    /** Resolves only "lad"; empty leads fallback to II so the test is lead-agnostic. */
    private val resolve: (String, List<Lead>) -> List<EcgTrace> = { id, leads ->
        if (id == "lad") {
            val requested = leads.ifEmpty { listOf(Lead.II) }
            requested.map { EcgTrace(it, sample) }
        } else emptyList()
    }

    @Test
    fun `ecg tag becomes an inline svg figure`() {
        val out = EcgSvgRenderer.substituteEcgTags(
            html = """<p>Before</p><ecg pathology="lad" lead="II" caption="LAD in II"></ecg><p>After</p>""",
            resolve = resolve,
        )
        assertFalse("raw <ecg> tag must be replaced", out.contains("<ecg"))
        assertTrue(out.contains("<figure class=\"ecg-figure\">"))
        assertTrue(out.contains("<svg"))
        assertTrue(out.contains("aria-label=\"ECG lead II\""))
        assertTrue(out.contains("<figcaption>LAD in II</figcaption>"))
        // Surrounding markdown/HTML is left intact.
        assertTrue(out.contains("<p>Before</p>"))
        assertTrue(out.contains("<p>After</p>"))
    }

    @Test
    fun `self-closing ecg tag is handled`() {
        val out = EcgSvgRenderer.substituteEcgTags(
            html = """<ecg pathology="lad" lead="aVR"/>""",
            resolve = resolve,
        )
        assertTrue(out.contains("aria-label=\"ECG lead aVR\""))
        assertFalse(out.contains("<ecg"))
    }

    @Test
    fun `unknown pathology yields a graceful placeholder, not an svg`() {
        val out = EcgSvgRenderer.substituteEcgTags(
            html = """<ecg pathology="does-not-exist" lead="V1"></ecg>""",
            resolve = resolve,
        )
        assertTrue(out.contains("ecg-missing"))
        assertTrue(out.contains("ECG unavailable: does-not-exist"))
        assertTrue(out.contains("(lead V1)"))
        assertFalse(out.contains("<svg"))
    }

    @Test
    fun `content without ecg tags is unchanged`() {
        val input = "<h1>Title</h1>\n<p>Inline math: \$x^2\$ stays put.</p>"
        assertEquals(input, EcgSvgRenderer.substituteEcgTags(input, resolve = resolve))
    }

    @Test
    fun `caption is html-escaped`() {
        val out = EcgSvgRenderer.substituteEcgTags(
            html = """<ecg pathology="lad" caption="a <b> &amp; c"></ecg>""",
            resolve = resolve,
        )
        // The raw "<b>" and "&" are escaped; the literal "<b>" tag must not survive.
        assertTrue(out.contains("a &lt;b&gt;"))
        assertFalse(out.contains("<b>"))
    }

    @Test
    fun `svg path uses a locale-independent decimal point`() {
        val out = EcgSvgRenderer.figureHtml(listOf(EcgTrace(Lead.II, sample)), caption = null)
        // The trace path is the one with TRACE_COLOR (#111111)
        val path = out.substringAfter("stroke=\"#111111\"").substringBeforeLast("<path d=\"")
        // Wait, that's not right. substringAfter finds what's AFTER.
        // Let's use a more robust way to find the trace path.
        val tracePath = out.split("<path d=\"").last().substringBefore("\"")
        assertTrue("path should start with a moveto", tracePath.startsWith("M0 "))
        assertFalse("coordinates must not use a comma decimal separator", tracePath.contains(","))
    }
}
