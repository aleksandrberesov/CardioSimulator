package com.example.cardiosimulator.ui.viewmodels

import android.net.Uri
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardiosimulator.data.DataSourcePrefs
import com.example.cardiosimulator.data.PathologyRepository
import com.example.cardiosimulator.domain.EcgPointType
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.OperatingMode
import com.example.cardiosimulator.domain.PathologyFile
import com.example.cardiosimulator.domain.SignificantPoint
import com.example.cardiosimulator.domain.generators.EcgGenerators
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.*

enum class EditingAlgorithm {
    Cosine,
    Spline,
    Bezier,
    LOESS,
    MLS
}

enum class ToolMode {
    Select,
    Trace,
    Position,
    Points,
    Photo,
    Pan
}

/**
 * Manages constructing a single [PathologyFile].
 * Works with raw ADC samples directly.
 */
class ConstructorViewModel(
    private val repository: PathologyRepository,
    private val mode: OperatingMode,
    private val prefs: DataSourcePrefs? = null
) : ViewModel() {

    init {
        viewModelScope.launch {
            // Constructor uses its own specific preference for the last edited rhythm
            prefs?.lastEditorRhythmId?.first()?.let { id ->
                selectPathology(id, persist = false)
            }
        }
    }

    private val _targetFile = mutableStateOf<PathologyFile?>(null)
    val targetFile: State<PathologyFile?> = _targetFile

    private val _focusedLead = MutableStateFlow(Lead.II)
    val focusedLead: StateFlow<Lead> = _focusedLead.asStateFlow()

    private val _selectedIndex = MutableStateFlow(0)
    val selectedIndex: StateFlow<Int> = _selectedIndex.asStateFlow()

    private val _dirtyLeads = MutableStateFlow<Set<Lead>>(emptySet())
    val dirtyLeads: StateFlow<Set<Lead>> = _dirtyLeads.asStateFlow()

    private val _isMetadataDirty = MutableStateFlow(false)
    val isMetadataDirty: StateFlow<Boolean> = _isMetadataDirty.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _editingAlgorithm = MutableStateFlow(EditingAlgorithm.Cosine)
    val editingAlgorithm: StateFlow<EditingAlgorithm> = _editingAlgorithm.asStateFlow()

    private val _editingRadius = MutableStateFlow(DEFAULT_EDITING_RADIUS)
    val editingRadius: StateFlow<Int> = _editingRadius.asStateFlow()

    private val _referenceImageUri = MutableStateFlow<Uri?>(null)
    val referenceImageUri: StateFlow<Uri?> = _referenceImageUri.asStateFlow()

    private val _toolMode = MutableStateFlow(ToolMode.Select)
    val toolMode: StateFlow<ToolMode> = _toolMode.asStateFlow()

    private val _imageOffset = MutableStateFlow(Offset.Zero)
    val imageOffset: StateFlow<Offset> = _imageOffset.asStateFlow()

    private val _imageScale = MutableStateFlow(1f)
    val imageScale: StateFlow<Float> = _imageScale.asStateFlow()

    private val _imageRotationDeg = MutableStateFlow(0f)
    val imageRotationDeg: StateFlow<Float> = _imageRotationDeg.asStateFlow()

    private val _imageAlpha = MutableStateFlow(0.5f)
    val imageAlpha: StateFlow<Float> = _imageAlpha.asStateFlow()

    private val _imageLocked = MutableStateFlow(false)
    val imageLocked: StateFlow<Boolean> = _imageLocked.asStateFlow()

    private val _imageVisible = MutableStateFlow(true)
    val imageVisible: StateFlow<Boolean> = _imageVisible.asStateFlow()

    private val _ghostTrace = MutableStateFlow<IntArray?>(null)
    val ghostTrace: StateFlow<IntArray?> = _ghostTrace.asStateFlow()

    fun setToolMode(mode: ToolMode) {
        _toolMode.value = mode
        if (mode != ToolMode.Trace) {
            _ghostTrace.value = null
        }
    }

    fun setGhostTrace(trace: IntArray?) {
        _ghostTrace.value = trace
    }

    fun insertElement(samples: IntArray) {
        val lead = _focusedLead.value
        if (!isLeadEditable(lead)) return
        val currentFile = _targetFile.value ?: return
        val stream = currentFile.leads[lead] ?: return
        val startIndex = _selectedIndex.value

        if (startIndex !in stream.samples.indices) return

        startStroke(lead)

        val newSamples = stream.samples.copyOf()
        val floatBuf = floatBufferFor(lead, stream.samples)

        for (i in samples.indices) {
            val targetIndex = startIndex + i
            if (targetIndex >= newSamples.size) break
            newSamples[targetIndex] = samples[i].coerceIn(ADC_MIN, ADC_MAX)
            floatBuf[targetIndex] = newSamples[targetIndex].toFloat()
        }

        val newLeads = currentFile.leads.toMutableMap()
        newLeads[lead] = stream.copy(samples = newSamples)
        _targetFile.value = currentFile.copy(leads = newLeads)
        _dirtyLeads.value += lead

        // Move selection to the end of inserted segment
        _selectedIndex.value = (startIndex + samples.size).coerceAtMost(newSamples.size - 1)
    }

    fun insertP(width: Int = 50, height: Int = 100) {
        val baseline = repository.manifest()?.baseline ?: 1024
        insertElement(EcgGenerators.generateP(width, height, baseline))
    }

    fun insertQRS(
        qWidth: Int = 10, qHeight: Int = 50,
        rWidth: Int = 20, rHeight: Int = 600,
        sWidth: Int = 15, sHeight: Int = 100
    ) {
        val baseline = repository.manifest()?.baseline ?: 1024
        insertElement(EcgGenerators.generateQRS(qWidth, qHeight, rWidth, rHeight, sWidth, sHeight, baseline))
    }

    fun insertT(width: Int = 80, height: Int = 150) {
        val baseline = repository.manifest()?.baseline ?: 1024
        insertElement(EcgGenerators.generateT(width, height, baseline))
    }

    fun insertBaseline(width: Int = 100) {
        val baseline = repository.manifest()?.baseline ?: 1024
        insertElement(EcgGenerators.generateBaseline(width, baseline))
    }

    fun insertFullCycle() {
        insertP()
        insertBaseline(20)
        insertQRS()
        insertBaseline(30)
        insertT()
        insertBaseline(50)
    }

    fun generateSynthesizedBeat(
        bpm: Int,
        ap: Double,
        ar: Double,
        asVal: Double,
        at: Double,
        variance: Double,
        sampleRate: Double
    ) {
        val lead = _focusedLead.value
        if (!isLeadEditable(lead)) return
        val currentFile = _targetFile.value ?: return
        val stream = currentFile.leads[lead] ?: return
        val baseline = repository.manifest()?.baseline ?: 1024

        startStroke(lead)

        val samplesPerBeat = (sampleRate * 60.0 / bpm).toInt()
        val sumK = 130 + 100 + 40 + 25 + 5 + 40 + (30 - 5) + 100 + 220
        val fixedSamples = (sumK * sampleRate / 1000.0).toInt()
        val ki = ((samplesPerBeat - fixedSamples) * 1000.0 / sampleRate).toInt().coerceAtLeast(10)

        val (beat, _) = com.example.cardiosimulator.signals.biosppy.Synthesizer.generate(
            ap = ap,
            ar = ar,
            `as` = asVal,
            at = at,
            variance = variance,
            samplingRate = sampleRate,
            ki = ki
        )

        val totalSamples = stream.samples.size
        val newSamples = IntArray(totalSamples)

        for (i in 0 until totalSamples) {
            val beatIndex = i % beat.size
            val valMv = beat[beatIndex]
            newSamples[i] = (baseline + valMv * 1024.0).toInt().coerceIn(ADC_MIN, ADC_MAX)
        }

        val newLeads = currentFile.leads.toMutableMap()
        newLeads[lead] = stream.copy(samples = newSamples)
        _targetFile.value = currentFile.copy(leads = newLeads)
        _dirtyLeads.value += lead
        floatBuffers.remove(lead)
    }

    fun autoDetectLandmarks(lead: Lead, samplingRate: Double) {
        val currentFile = _targetFile.value ?: return
        val stream = currentFile.leads[lead] ?: return
        val baseline = repository.manifest()?.baseline ?: 1024

        viewModelScope.launch {
            val signal = stream.samples.map { (it - baseline).toDouble() }.toDoubleArray()
            val rpeaks = com.example.cardiosimulator.signals.biosppy.QrsSegmenters.hamiltonSegmenter(signal, samplingRate)
            val correctedR = com.example.cardiosimulator.signals.biosppy.QrsSegmenters.correctRPeaks(signal, rpeaks, samplingRate)

            val landmarks = com.example.cardiosimulator.signals.biosppy.Landmarks.getLandmarks(signal, correctedR, samplingRate)

            val newPoints = currentFile.significantPoints.toMutableList()

            for (lm in landmarks) {
                addPointIfValid(newPoints, lm.rPeak, EcgPointType.R_PEAK)
                addPointIfValid(newPoints, lm.qPeak, EcgPointType.Q_PEAK)
                addPointIfValid(newPoints, lm.sPeak, EcgPointType.S_PEAK)
                addPointIfValid(newPoints, lm.pPeak, EcgPointType.P_PEAK)
                addPointIfValid(newPoints, lm.tPeak, EcgPointType.T_PEAK)
                addPointIfValid(newPoints, lm.qrsStart, EcgPointType.QRS_START)
                addPointIfValid(newPoints, lm.qrsEnd, EcgPointType.QRS_END)
                addPointIfValid(newPoints, lm.pStart, EcgPointType.P_START)
                addPointIfValid(newPoints, lm.pEnd, EcgPointType.P_END)
                addPointIfValid(newPoints, lm.tStart, EcgPointType.T_START)
                addPointIfValid(newPoints, lm.tEnd, EcgPointType.T_END)
            }

            _targetFile.value = currentFile.copy(significantPoints = newPoints.distinctBy { it.index to it.type })
            _isMetadataDirty.value = true
        }
    }

    private fun addPointIfValid(list: MutableList<SignificantPoint>, index: Int, type: EcgPointType) {
        if (index >= 0) {
            list.removeAll { it.index == index && it.type == type }
            list.add(SignificantPoint(index, type))
        }
    }

    fun applyGhostTrace() {
        val trace = _ghostTrace.value ?: return
        val lead = _focusedLead.value
        startStroke(lead)
        val updates = trace.withIndex().associate { it.index to it.value }
        traceSamples(lead, updates)
        _ghostTrace.value = null
    }

    fun setImageOffset(offset: Offset) {
        if (!_imageLocked.value) _imageOffset.value = offset
    }

    fun setImageScale(scale: Float) {
        if (!_imageLocked.value) _imageScale.value = scale
    }

    fun setImageRotation(deg: Float) {
        if (!_imageLocked.value) _imageRotationDeg.value = deg
    }

    fun setImageAlpha(alpha: Float) {
        _imageAlpha.value = alpha
    }

    fun setImageLocked(locked: Boolean) {
        _imageLocked.value = locked
    }

    fun setImageVisible(visible: Boolean) {
        _imageVisible.value = visible
    }

    fun resetImageTransform() {
        _imageOffset.value = Offset.Zero
        _imageScale.value = 1f
        _imageRotationDeg.value = 0f
    }

    fun setReferenceImageUri(uri: Uri?) {
        _referenceImageUri.value = uri
        if (uri != null) {
            _toolMode.value = ToolMode.Photo
        }
        resetImageTransform()
    }

    /**
     * Per-lead float accumulator parallel to [LeadStream.samples].
     *
     * Sub-integer weighted contributions are accumulated here so that repeated
     * +/-1 nudges build up a smooth bump shape instead of being lost to
     * per-call rounding (which otherwise produces a "block of samples moving
     * synchronously" artifact at the cursor: every sample whose weight rounds
     * to >= 0.5 moves by the full delta, every sample whose weight rounds to
     * < 0.5 does not move at all).
     *
     * Invariant: when present, `floatBuffers[lead][i].roundToInt()` equals
     * `_targetFile.leads[lead].samples[i]`. Mutators that change samples
     * outside of [adjustSample] must keep this in sync (see [setSample],
     * [revertLead], [calculateDerivedLeads], [selectPathology]).
     */
    private val floatBuffers = mutableMapOf<Lead, FloatArray>()

    private fun floatBufferFor(lead: Lead, samples: IntArray): FloatArray {
        val existing = floatBuffers[lead]
        if (existing != null && existing.size == samples.size) return existing
        val fresh = FloatArray(samples.size) { samples[it].toFloat() }
        floatBuffers[lead] = fresh
        return fresh
    }

    private fun computeWeight(d: Int, radius: Int, algorithm: EditingAlgorithm): Float {
        val t = abs(d.toFloat() / radius)
        if (t > 1.0f) return 0f
        return when (algorithm) {
            EditingAlgorithm.Cosine -> 0.5f * (1f + cos(PI * d / radius).toFloat())
            // Smoothstep (Hermite h01): zero slope at center and at the edge.
            EditingAlgorithm.Spline -> 1.0f - (-2.0f * t.pow(3) + 3.0f * t.pow(2))
            // Smooth bump (1 - t^2)^2: zero slope at d=0 (no kink) and zero at the edge.
            // The previous (1 - t)^2 had slope -2 at d=0 and produced a tent-shaped peak.
            EditingAlgorithm.Bezier -> (1.0f - t.pow(2)).pow(2)
            EditingAlgorithm.LOESS  -> (1.0f - t.pow(3)).pow(3)
            EditingAlgorithm.MLS    -> {
                // Truncated Gaussian re-normalised so the kernel smoothly reaches 0
                // at |d| = radius. The raw Gaussian was ~4.4% of peak at the edge
                // (sigma=0.4), which produced a visible step at the influence boundary.
                val sigma = 0.4f
                val raw = exp(-t.pow(2) / (2 * sigma.pow(2)))
                val edge = exp(-1f / (2 * sigma.pow(2)))
                ((raw - edge) / (1f - edge)).coerceAtLeast(0f)
            }
        }
    }

    fun selectPathology(id: String, persist: Boolean = true) {
        viewModelScope.launch {
            var file = repository.readPathology(id)
            if (file != null && file.group == null) {
                val manifestEntry = repository.manifest()?.entries?.find { it.id == id }
                if (manifestEntry?.group != null) {
                    file = file.copy(group = manifestEntry.group)
                }
            }
            _targetFile.value = file
            // A new file is loaded — accumulators from the previous file are stale.
            floatBuffers.clear()
            _dirtyLeads.value = emptySet()
            _isMetadataDirty.value = false
            undoStacks.clear()
            redoStacks.clear()
            _focusedLead.value = Lead.II
            _selectedIndex.value = 0
            if (persist) {
                prefs?.setLastEditorRhythmId(id)
            }
        }
    }

    fun selectLead(lead: Lead) {
        _focusedLead.value = lead
        _selectedIndex.value = 0
    }

    fun selectIndex(index: Int) {
        val lead = _focusedLead.value
        val stream = _targetFile.value?.leads?.get(lead) ?: return
        if (index in stream.samples.indices) {
            _selectedIndex.value = index
        }
    }

    fun isLeadEditable(lead: Lead): Boolean {
        // According to requirements, dependent leads (III, aVR, aVL, aVF, V1, V3, V4, V5)
        // should be selected but not edited if they are derived.
        // For simplicity, we define which leads are independent in our system.
        return lead !in com.example.cardiosimulator.domain.DerivedLeads.DerivableFromIandII &&
               lead !in com.example.cardiosimulator.domain.DerivedLeads.DerivableFromV2andV6
    }

    fun selectSignificantPoint(type: EcgPointType) {
        val currentFile = _targetFile.value ?: return
        val pointsOfType = currentFile.significantPoints
            .filter { it.type == type }
            .sortedBy { it.index }
        
        if (pointsOfType.isEmpty()) return
        
        val currentIndex = _selectedIndex.value
        // Cycle through points of this type
        val nextPoint = pointsOfType.find { it.index > currentIndex } ?: pointsOfType.first()
        _selectedIndex.value = nextPoint.index
    }

    fun selectNext() {
        val stream = _targetFile.value?.leads?.get(_focusedLead.value) ?: return
        if (_selectedIndex.value < stream.samples.size - 1) {
            _selectedIndex.value++
        }
    }

    fun selectPrevious() {
        if (_selectedIndex.value > 0) {
            _selectedIndex.value--
        }
    }

    fun setEditingAlgorithm(algorithm: EditingAlgorithm) {
        _editingAlgorithm.value = algorithm
    }

    fun setEditingRadius(radius: Int) {
        _editingRadius.value = radius.coerceIn(MIN_EDITING_RADIUS, MAX_EDITING_RADIUS)
    }

    fun moveSelectedUp() {
        val lead = _focusedLead.value
        if (!isLeadEditable(lead)) return
        val index = _selectedIndex.value
        adjustSample(lead, index, 1)
    }

    fun moveSelectedDown() {
        val lead = _focusedLead.value
        if (!isLeadEditable(lead)) return
        val index = _selectedIndex.value
        adjustSample(lead, index, -1)
    }

    private fun adjustSample(lead: Lead, index: Int, delta: Int) {
        val currentFile = _targetFile.value ?: return
        val stream = currentFile.leads[lead] ?: return
        if (index !in stream.samples.indices) return

        val radius = _editingRadius.value
        val algorithm = _editingAlgorithm.value
        val deltaF = delta.toFloat()

        // Accumulate weighted contributions into the float buffer, then project
        // back to the displayed int samples. This is what turns a sequence of
        // +/-1 nudges into a genuinely smooth bump: a sample whose per-call
        // weight rounds to 0 still moves once enough nudges have accumulated.
        val floatBuf = floatBufferFor(lead, stream.samples)
        val newSamples = stream.samples.copyOf()

        for (d in -radius..radius) {
            val targetIndex = index + d
            if (targetIndex !in newSamples.indices) continue
            val weight = computeWeight(d, radius, algorithm)
            if (weight == 0f) continue
            floatBuf[targetIndex] = (floatBuf[targetIndex] + deltaF * weight)
                .coerceIn(ADC_MIN.toFloat(), ADC_MAX.toFloat())
            newSamples[targetIndex] = floatBuf[targetIndex].roundToInt()
        }

        val newLeads = currentFile.leads.toMutableMap()
        newLeads[lead] = stream.copy(samples = newSamples)

        _targetFile.value = currentFile.copy(leads = newLeads)
        _dirtyLeads.value += lead
    }

    fun setSample(lead: Lead, index: Int, adcValue: Int) {
        if (!isLeadEditable(lead)) return
        val currentFile = _targetFile.value ?: return
        val stream = currentFile.leads[lead] ?: return
        if (index !in stream.samples.indices) return
        val target = adcValue.coerceIn(ADC_MIN, ADC_MAX)
        if (stream.samples[index] == target) return

        // Apply the same weighted kernel as adjustSample so an absolute value
        // jump from the dialog produces a smooth bump instead of a single-sample
        // spike. At d=0 every kernel returns weight=1.0, so the center lands
        // exactly on target.
        val radius = _editingRadius.value
        val algorithm = _editingAlgorithm.value
        val deltaF = (target - stream.samples[index]).toFloat()

        val floatBuf = floatBufferFor(lead, stream.samples)
        val newSamples = stream.samples.copyOf()

        for (d in -radius..radius) {
            val targetIndex = index + d
            if (targetIndex !in newSamples.indices) continue
            val weight = computeWeight(d, radius, algorithm)
            if (weight == 0f) continue
            floatBuf[targetIndex] = (floatBuf[targetIndex] + deltaF * weight)
                .coerceIn(ADC_MIN.toFloat(), ADC_MAX.toFloat())
            newSamples[targetIndex] = floatBuf[targetIndex].roundToInt()
        }

        val newLeads = currentFile.leads.toMutableMap()
        newLeads[lead] = stream.copy(samples = newSamples)

        _targetFile.value = currentFile.copy(leads = newLeads)
        _dirtyLeads.value += lead
    }

    fun toggleSignificantPoint(lead: Lead, index: Int, type: EcgPointType) {
        val currentFile = _targetFile.value ?: return
        // Ensure index is valid for the currently focused lead (or at least one lead)
        val stream = currentFile.leads[lead] ?: return
        if (index !in stream.samples.indices) return

        val currentPoints = currentFile.significantPoints.toMutableList()
        val existing = currentPoints.find { it.index == index && it.type == type }

        if (existing != null) {
            currentPoints.remove(existing)
        } else {
            // Usually one sample has only one ECG label
            currentPoints.removeAll { it.index == index }
            currentPoints.add(SignificantPoint(index, type))
        }

        _targetFile.value = currentFile.copy(significantPoints = currentPoints)
        _isMetadataDirty.value = true // Since points are now global/metadata-like
    }

    fun revertLead(lead: Lead) {
        val id = _targetFile.value?.id ?: return
        viewModelScope.launch {
            val originalFile = repository.readPathology(id) ?: return@launch
            val originalStream = originalFile.leads[lead] ?: return@launch
            
            val currentFile = _targetFile.value ?: return@launch
            val newLeads = currentFile.leads.toMutableMap()
            newLeads[lead] = originalStream
            
            _targetFile.value = currentFile.copy(leads = newLeads)
            _dirtyLeads.value -= lead
            // Original samples are restored from disk — drop any accumulated edits
            // so the next adjust on this lead re-seeds from the restored baseline.
            floatBuffers.remove(lead)
        }
    }

    fun rename(newTitle: String, language: com.example.cardiosimulator.domain.Language) {
        val currentFile = _targetFile.value ?: return
        val updatedFile = if (language == com.example.cardiosimulator.domain.Language.RU) {
            currentFile.copy(nameRu = newTitle)
        } else {
            currentFile.copy(titleEn = newTitle)
        }
        if (updatedFile != currentFile) {
            _targetFile.value = updatedFile
            _isMetadataDirty.value = true
        }
    }

    fun setGroup(groupKey: String?) {
        val currentFile = _targetFile.value ?: return
        if (currentFile.group != groupKey) {
            _targetFile.value = currentFile.copy(group = groupKey)
            _isMetadataDirty.value = true
        }
    }

    fun createAndSetGroup(name: String) {
        val currentFile = _targetFile.value ?: return
        viewModelScope.launch {
            val key = name.lowercase().replace(Regex("[^a-z0-9]"), "_")
            val success = repository.createGroup(key, name)
            if (success) {
                setGroup(key)
            }
        }
    }

    fun deleteCurrentPathology() {
        val id = _targetFile.value?.id ?: return
        viewModelScope.launch {
            if (repository.deletePathology(id)) {
                _targetFile.value = null
                floatBuffers.clear()
                _dirtyLeads.value = emptySet()
                _isMetadataDirty.value = false
            }
        }
    }

    fun duplicateCurrentPathology() {
        val id = _targetFile.value?.id ?: return
        viewModelScope.launch {
            val newId = repository.duplicatePathology(id)
            if (newId != null) {
                selectPathology(newId)
            }
        }
    }

    fun createNewPathology() {
        viewModelScope.launch {
            val id = "new_pathology_" + System.currentTimeMillis().toString().takeLast(4)
            val newId = repository.createPathology(
                id = id,
                titleEn = "New Pathology",
                nameRu = "Новая патология"
            )
            if (newId != null) {
                selectPathology(newId)
            }
        }
    }

    fun importPathology(file: PathologyFile) {
        viewModelScope.launch {
            val id = repository.importPathology(file)
            if (id != null) {
                selectPathology(id)
            }
        }
    }

    fun save() {
        val file = _targetFile.value ?: return
        if (_dirtyLeads.value.isEmpty() && !_isMetadataDirty.value) return

        viewModelScope.launch {
            _isSaving.value = true
            try {
                val success = repository.writePathology(file)
                if (success) {
                    _dirtyLeads.value = emptySet()
                    _isMetadataDirty.value = false
                }
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun calculateDerivedLeads() {
        val currentFile = _targetFile.value ?: return
        val baseline = repository.manifest()?.baseline ?: 1024
        
        val leads = currentFile.leads.toMutableMap()
        val newlyDirty = mutableSetOf<Lead>()

        fun getZeroed(lead: Lead): List<Float>? =
            currentFile.leads[lead]?.samples?.map { (it - baseline).toFloat() }

        // Derive limb leads from I and II
        val i = getZeroed(Lead.I)
        val ii = getZeroed(Lead.II)
        if (i != null && ii != null) {
            com.example.cardiosimulator.domain.DerivedLeads.DerivableFromIandII.forEach { target ->
                val derived = com.example.cardiosimulator.domain.DerivedLeads.combineIII_aVR_aVL_aVF(i, ii, target)
                if (derived.isNotEmpty()) {
                    val samples = derived.map { (it + baseline).toInt() }.toIntArray()
                    leads[target] = com.example.cardiosimulator.domain.LeadStream(target, samples)
                    // Derived samples replace the previous content — any accumulated
                    // float edits for this lead are stale.
                    floatBuffers.remove(target)
                    newlyDirty.add(target)
                }
            }
        }

        // Derive precordial leads from V2 and V6
        val v2 = getZeroed(Lead.V2)
        val v6 = getZeroed(Lead.V6)
        if (v2 != null && v6 != null) {
            com.example.cardiosimulator.domain.DerivedLeads.DerivableFromV2andV6.forEach { target ->
                val derived = com.example.cardiosimulator.domain.DerivedLeads.combineV1_V3_V4_V5(v2, v6, target)
                if (derived.isNotEmpty()) {
                    val samples = derived.map { (it + baseline).toInt() }.toIntArray()
                    leads[target] = com.example.cardiosimulator.domain.LeadStream(target, samples)
                    // Derived samples replace the previous content — any accumulated
                    // float edits for this lead are stale.
                    floatBuffers.remove(target)
                    newlyDirty.add(target)
                }
            }
        }

        if (newlyDirty.isNotEmpty()) {
            _targetFile.value = currentFile.copy(leads = leads)
            _dirtyLeads.value += newlyDirty
        }
    }

    private val undoStacks = mutableMapOf<Lead, MutableList<IntArray>>()
    private val redoStacks = mutableMapOf<Lead, MutableList<IntArray>>()

    fun startStroke(lead: Lead) {
        val currentFile = _targetFile.value ?: return
        val stream = currentFile.leads[lead] ?: return
        
        val undoStack = undoStacks.getOrPut(lead) { mutableListOf() }
        undoStack.add(stream.samples.copyOf())
        if (undoStack.size > MAX_UNDO_DEPTH) undoStack.removeAt(0)
        
        redoStacks[lead]?.clear()
    }

    fun traceSamples(lead: Lead, updates: Map<Int, Int>) {
        if (!isLeadEditable(lead)) return
        val currentFile = _targetFile.value ?: return
        val stream = currentFile.leads[lead] ?: return
        
        val newSamples = stream.samples.copyOf()
        val floatBuf = floatBufferFor(lead, stream.samples)
        
        updates.forEach { (index, value) ->
            if (index in newSamples.indices) {
                val clampedValue = value.coerceIn(ADC_MIN, ADC_MAX)
                newSamples[index] = clampedValue
                floatBuf[index] = clampedValue.toFloat()
            }
        }

        val newLeads = currentFile.leads.toMutableMap()
        newLeads[lead] = stream.copy(samples = newSamples)
        _targetFile.value = currentFile.copy(leads = newLeads)
        _dirtyLeads.value += lead
    }

    fun undo(lead: Lead) {
        val undoStack = undoStacks[lead] ?: return
        if (undoStack.isEmpty()) return
        
        val currentFile = _targetFile.value ?: return
        val stream = currentFile.leads[lead] ?: return
        
        val redoStack = redoStacks.getOrPut(lead) { mutableListOf() }
        redoStack.add(stream.samples.copyOf())
        
        val previousSamples = undoStack.removeAt(undoStack.size - 1)
        restoreLeadSamples(lead, previousSamples)
    }

    fun redo(lead: Lead) {
        val redoStack = redoStacks[lead] ?: return
        if (redoStack.isEmpty()) return
        
        val currentFile = _targetFile.value ?: return
        val stream = currentFile.leads[lead] ?: return
        
        val undoStack = undoStacks.getOrPut(lead) { mutableListOf() }
        undoStack.add(stream.samples.copyOf())
        
        val nextSamples = redoStack.removeAt(redoStack.size - 1)
        restoreLeadSamples(lead, nextSamples)
    }

    private fun restoreLeadSamples(lead: Lead, samples: IntArray) {
        val currentFile = _targetFile.value ?: return
        val newLeads = currentFile.leads.toMutableMap()
        newLeads[lead] = currentFile.leads[lead]?.copy(samples = samples) ?: return
        
        _targetFile.value = currentFile.copy(leads = newLeads)
        _dirtyLeads.value += lead
        floatBuffers.remove(lead) // Force re-sync from restored ints
    }

    companion object {
        const val DEFAULT_EDITING_RADIUS = 100
        const val MIN_EDITING_RADIUS = 1
        const val MAX_EDITING_RADIUS = 1000
        const val MAX_UNDO_DEPTH = 20

        // Valid raw ADC range; edits can never push a sample outside this.
        const val ADC_MIN = 0
        const val ADC_MAX = 2048
    }
}
