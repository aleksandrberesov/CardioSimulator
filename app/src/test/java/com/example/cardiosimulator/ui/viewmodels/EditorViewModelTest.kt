package com.example.cardiosimulator.ui.viewmodels

import com.example.cardiosimulator.data.PathologyRepository
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.LeadStream
import com.example.cardiosimulator.domain.PathologyFile
import com.example.cardiosimulator.domain.PathologyManifest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {

    private val repository: PathologyRepository = mockk()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `calculateDerivedLeads should compute limb leads correctly`() = runTest {
        val viewModel = EditorViewModel(repository)
        
        val baseline = 1000
        val manifest = PathologyManifest("1.0", baseline, emptyList(), emptyList())
        every { repository.manifest() } returns manifest
        
        // Lead I: [1000, 1010, 990] -> Zeroed: [0, 10, -10]
        // Lead II: [1000, 1020, 980] -> Zeroed: [0, 20, -20]
        // III = II - I = [0, 10, -10] -> + Baseline: [1000, 1010, 990]
        
        val leadI = LeadStream(Lead.I, intArrayOf(1000, 1010, 990))
        val leadII = LeadStream(Lead.II, intArrayOf(1000, 1020, 980))
        
        val file = PathologyFile(
            id = "test",
            titleEn = "Test",
            nameRu = "Тест",
            leads = mapOf(Lead.I to leadI, Lead.II to leadII)
        )
        
        every { repository.readPathology("test") } returns file
        
        viewModel.selectPathology("test", persist = false)
        advanceUntilIdle()
        
        viewModel.calculateDerivedLeads()
        
        val updatedFile = viewModel.targetFile.value
        assertTrue(updatedFile?.leads?.containsKey(Lead.III) == true)
        
        val leadIII = updatedFile?.leads?.get(Lead.III)
        assertArrayEquals(intArrayOf(1000, 1010, 990), leadIII?.samples)
        
        assertTrue(viewModel.dirtyLeads.value.contains(Lead.III))
    }
}
