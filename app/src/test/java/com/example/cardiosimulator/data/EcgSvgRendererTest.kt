package com.example.cardiosimulator.data

import com.example.cardiosimulator.domain.Lead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EcgSvgRendererTest {

    private val sample = Points(listOf(0f, 1f, 0.5f, -0.5f, 0f))

    /** Resolves only "lad"; null lead falls back to II so the test is lead-agnostic. */
    private val resolve: (String, Lead?) -> List<EcgTrace> = { id, lead ->
        if (id == "lad") listOf(EcgTrace(lead ?: Lead.II, sample)) else emptyList()
    }

    @Test
    fun `ecg tag becomes an inline svg figure`() {
        val out = EcgSvgRenderer.substituteEcgTags(
            """<p>Before</p><ecg pathology="lad" lead="II" caption="LAD in II"></ecg><p>After</p>""",
            resolve,
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
            """<ecg pathology="lad" lead="aVR"/>""",
            resolve,
        )
        assertTrue(out.contains("aria-label=\"ECG lead aVR\""))
        assertFalse(out.contains("<ecg"))
    }

    @Test
    fun `unknown pathology yields a graceful placeholder, not an svg`() {
        val out = EcgSvgRenderer.substituteEcgTags(
            """<ecg pathology="does-not-exist" lead="V1"></ecg>""",
            resolve,
        )
        assertTrue(out.contains("ecg-missing"))
        assertTrue(out.contains("ECG unavailable: does-not-exist"))
        assertTrue(out.contains("(lead V1)"))
        assertFalse(out.contains("<svg"))
    }

    @Test
    fun `content without ecg tags is unchanged`() {
        val input = "<h1>Title</h1>\n<p>Inline math: \$x^2\$ stays put.</p>"
        assertEquals(input, EcgSvgRenderer.substituteEcgTags(input, resolve))
    }

    @Test
    fun `caption is html-escaped`() {
        val out = EcgSvgRenderer.substituteEcgTags(
            """<ecg pathology="lad" caption="a <b> &amp; c"></ecg>""",
            resolve,
        )
        // The raw "<b>" and "&" are escaped; the literal "<b>" tag must not survive.
        assertTrue(out.contains("a &lt;b&gt;"))
        assertFalse(out.contains("<b>"))
    }

    @Test
    fun `svg path uses a locale-independent decimal point`() {
        val out = EcgSvgRenderer.figureHtml(listOf(EcgTrace(Lead.II, sample)), caption = null)
        val path = out.substringAfter("<path d=\"").substringBefore("\"")
        assertTrue("path should start with a moveto", path.startsWith("M0 "))
        assertFalse("coordinates must not use a comma decimal separator", path.contains(","))
    }
}
