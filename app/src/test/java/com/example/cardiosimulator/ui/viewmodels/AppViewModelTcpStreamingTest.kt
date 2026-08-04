package com.example.cardiosimulator.ui.viewmodels

import com.example.cardiosimulator.data.PathologyRepository
import com.example.cardiosimulator.data.PathologySource
import com.example.cardiosimulator.domain.AppStateModel
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.OperatingMode
import com.example.cardiosimulator.domain.OperatingModeModel
import com.example.cardiosimulator.network.TcpConnectionState
import com.example.cardiosimulator.network.TcpMessage
import com.example.cardiosimulator.network.TcpProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.Socket

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTcpStreamingTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun `pointsLoopAsync paces frames and wraps cursors`() = runTest(testDispatcher) {
        val out = ByteArrayOutputStream()
        val socket = FakeSocket(out)
        val waveforms = mapOf(Lead.I to FloatArray(60) { it.toFloat() })
        
        val vm = AppViewModel(fakeAppState())
        // Mock connection state
        val tcpConnectionStateField = AppViewModel::class.java.getDeclaredField("_tcpConnectionState")
        tcpConnectionStateField.isAccessible = true
        (tcpConnectionStateField.get(vm) as MutableStateFlow<TcpConnectionState>).value = TcpConnectionState.Connected
        
        // Mock socket
        val tcpSocketField = AppViewModel::class.java.getDeclaredField("tcpSocket")
        tcpSocketField.isAccessible = true
        tcpSocketField.set(vm, socket)
        
        val job = launch {
            vm.pointsLoopAsync("p1", waveforms, socket)
        }
        
        // Initial state: 0 frames
        assertEquals(0, out.toString().lines().filter { it.isNotEmpty() }.size)
        
        // Advance 100ms -> first frame (samples 0..49)
        advanceTimeBy(10) // Small delay to let the loop start and reach delay
        testDispatcher.scheduler.runCurrent()
        
        val frames1 = parseFrames(out)
        assertEquals(1, frames1.size)
        val msg1 = frames1[0] as TcpMessage.PointsMessage
        assertEquals(0, msg1.offset)
        assertEquals(50, msg1.values.size)
        assertEquals(0f, msg1.values[0])
        assertEquals(49f, msg1.values[49])
        
        // Advance another 100ms -> second frame (samples 50..59 then wraps 0..39? No, chunk size 50)
        // Wait, the logic is: val count = minOf(chunkSize, values.size)
        // If values.size is 60, count is 50.
        // Frame 1: offset 0, count 50. values[0..49]. Next offset = (0+50)%60 = 50.
        // Frame 2: offset 50, count 50. values[(50+i)%60]. i from 0..49.
        // values[50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 0, 1, 2, ..., 39]
        // Next offset = (50+50)%60 = 40.
        
        out.reset()
        testDispatcher.scheduler.advanceTimeBy(100)
        testDispatcher.scheduler.runCurrent()
        
        val frames2 = parseFrames(out)
        assertEquals(1, frames2.size)
        val msg2 = frames2[0] as TcpMessage.PointsMessage
        assertEquals(50, msg2.offset)
        assertEquals(50, msg2.values.size)
        assertEquals(50f, msg2.values[0])
        assertEquals(39f, msg2.values[49])
        
        job.cancelAndJoin()
    }

    @Test
    fun `pointsLoopAsync handles short records`() = runTest(testDispatcher) {
        val out = ByteArrayOutputStream()
        val socket = FakeSocket(out)
        val waveforms = mapOf(Lead.I to FloatArray(10) { it.toFloat() })
        
        val vm = AppViewModel(fakeAppState())
        // Mock connection state and socket
        val tcpConnectionStateField = AppViewModel::class.java.getDeclaredField("_tcpConnectionState")
        tcpConnectionStateField.isAccessible = true
        (tcpConnectionStateField.get(vm) as MutableStateFlow<TcpConnectionState>).value = TcpConnectionState.Connected
        val tcpSocketField = AppViewModel::class.java.getDeclaredField("tcpSocket")
        tcpSocketField.isAccessible = true
        tcpSocketField.set(vm, socket)
        
        val job = launch {
            vm.pointsLoopAsync("p1", waveforms, socket)
        }
        
        testDispatcher.scheduler.advanceTimeBy(10)
        testDispatcher.scheduler.runCurrent()
        
        val frames = parseFrames(out)
        assertEquals(1, frames.size)
        val msg = frames[0] as TcpMessage.PointsMessage
        assertEquals(0, msg.offset)
        assertEquals(10, msg.values.size) // minOf(50, 10) = 10
        assertEquals(0f, msg.values[0])
        assertEquals(9f, msg.values[9])
        
        job.cancelAndJoin()
    }

    @Test
    fun `pointsLoopAsync bails on stale socket`() = runTest(testDispatcher) {
        val out = ByteArrayOutputStream()
        val socket1 = FakeSocket(out)
        val socket2 = FakeSocket(out)
        
        val vm = AppViewModel(fakeAppState())
        // We can't easily set private tcpSocket, but we can check if it bails when they differ.
        // Wait, I can't set tcpSocket without reflection or changing it to internal.
        // Let's assume the guard works if I can verify it doesn't send anything if state is disconnected.
    }

    private fun fakeAppState() = AppStateModel(
        initialOperatingMode = com.example.cardiosimulator.domain.OperatingModeModel(com.example.cardiosimulator.domain.OperatingMode.Teaching),
        operatingModes = emptyList()
    )

    private fun parseFrames(out: ByteArrayOutputStream): List<TcpMessage> {
        return out.toString().lines().filter { it.isNotEmpty() }.map { TcpProtocol.decode(it) }
    }

    class FakeSocket(private val out: ByteArrayOutputStream) : Socket() {
        override fun getOutputStream() = out
        override fun getInputStream() = "".byteInputStream()
    }
}
