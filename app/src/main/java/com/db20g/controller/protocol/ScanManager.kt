package com.db20g.controller.protocol

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.coroutineContext

class ScanManager {

    enum class ScanState {
        IDLE,
        SCANNING,
        PAUSED_ON_ACTIVITY,
        TALK_BACK_WAIT
    }

    enum class ScanSpeed(val dwellMs: Long, val label: String) {
        SLOW(500L, "Slow"),
        MEDIUM(300L, "Medium"),
        FAST(150L, "Fast")
    }

    data class ScanLogEntry(
        val timestamp: Long,
        val channelNumber: Int,
        val channelName: String,
        val frequency: String,
        val durationMs: Long
    )

    interface ScanListener {
        fun onChannelChange(channel: RadioChannel)
        fun onScanStateChanged(state: ScanState)
        fun onActivityDetected(channel: RadioChannel)
        fun onScanLogUpdated(log: List<ScanLogEntry>)
    }

    private val _state = MutableStateFlow(ScanState.IDLE)
    val state: StateFlow<ScanState> = _state

    private val _currentScanChannel = MutableStateFlow<RadioChannel?>(null)
    val currentScanChannel: StateFlow<RadioChannel?> = _currentScanChannel

    private val _scanLog = MutableStateFlow<List<ScanLogEntry>>(emptyList())
    val scanLog: StateFlow<List<ScanLogEntry>> = _scanLog

    var scanSpeed: ScanSpeed = ScanSpeed.MEDIUM
    var priorityChannelNumber: Int = -1
    var talkBackTimerMs: Long = 3000L
    var timeOperatedResumeMs: Long = 5000L
    var dualWatchChannel: Int = -1

    private var listener: ScanListener? = null
    private var scanJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val nuisanceSkipSet = mutableSetOf<Int>()
    private var channels: List<RadioChannel> = emptyList()
    private var scanMode: ScanMode = ScanMode.TIME_OPERATED
    private var rxActiveProvider: (() -> Boolean) = { false }
    private var pttActiveProvider: (() -> Boolean) = { false }
    private val logEntries = mutableListOf<ScanLogEntry>()
    private var priorityCheckCounter = 0

    fun setListener(l: ScanListener?) {
        listener = l
    }

    fun setRxActiveProvider(provider: () -> Boolean) {
        rxActiveProvider = provider
    }

    fun setPttActiveProvider(provider: () -> Boolean) {
        pttActiveProvider = provider
    }

    fun updateChannels(allChannels: List<RadioChannel>, mode: ScanMode) {
        channels = allChannels
        scanMode = mode
    }

    fun startScan() {
        if (_state.value != ScanState.IDLE) return
        val scanList = buildScanList()
        if (scanList.isEmpty()) return

        nuisanceSkipSet.clear()
        _state.value = ScanState.SCANNING
        listener?.onScanStateChanged(ScanState.SCANNING)

        scanJob = scope.launch {
            runScanLoop(scanList)
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _state.value = ScanState.IDLE
        _currentScanChannel.value = null
        listener?.onScanStateChanged(ScanState.IDLE)
    }

    fun nuisanceDelete(channelNumber: Int) {
        nuisanceSkipSet.add(channelNumber)
    }

    fun clearNuisanceList() {
        nuisanceSkipSet.clear()
    }

    fun clearScanLog() {
        logEntries.clear()
        _scanLog.value = emptyList()
        listener?.onScanLogUpdated(emptyList())
    }

    fun getScanListChannels(): List<RadioChannel> = buildScanList()

    fun destroy() {
        stopScan()
        scope.cancel()
    }

    private fun buildScanList(): List<RadioChannel> {
        return channels.filter { !it.isEmpty && it.scan && it.number !in nuisanceSkipSet }
    }

    private suspend fun runScanLoop(initialScanList: List<RadioChannel>) {
        var scanList = initialScanList
        var index = 0

        while (coroutineContext.isActive) {
            // Rebuild scan list each cycle to respect nuisance deletes
            scanList = buildScanList()
            if (scanList.isEmpty()) {
                stopScan()
                return
            }
            if (index >= scanList.size) index = 0

            // Priority channel check — every 3rd step
            if (priorityChannelNumber >= 0) {
                priorityCheckCounter++
                if (priorityCheckCounter >= 3) {
                    priorityCheckCounter = 0
                    val priorityCh = channels.firstOrNull {
                        it.number == priorityChannelNumber && !it.isEmpty
                    }
                    if (priorityCh != null) {
                        switchToChannel(priorityCh)
                        delay(scanSpeed.dwellMs)
                        if (rxActiveProvider()) {
                            handleActivityDetected(priorityCh)
                            if (!coroutineContext.isActive) return
                            continue
                        }
                    }
                }
            }

            // Dual-watch: alternate between current scan channel and dual-watch channel
            if (dualWatchChannel >= 0) {
                val dwCh = channels.firstOrNull {
                    it.number == dualWatchChannel && !it.isEmpty
                }
                if (dwCh != null) {
                    switchToChannel(dwCh)
                    delay(scanSpeed.dwellMs)
                    if (rxActiveProvider()) {
                        handleActivityDetected(dwCh)
                        if (!coroutineContext.isActive) return
                        continue
                    }
                }
            }

            // Normal scan step
            val channel = scanList[index]
            switchToChannel(channel)
            delay(scanSpeed.dwellMs)

            if (rxActiveProvider()) {
                handleActivityDetected(channel)
                if (!coroutineContext.isActive) return
            }

            // Check if PTT was pressed — enter talk-back mode
            if (pttActiveProvider()) {
                _state.value = ScanState.TALK_BACK_WAIT
                listener?.onScanStateChanged(ScanState.TALK_BACK_WAIT)

                // Wait for PTT release
                while (coroutineContext.isActive && pttActiveProvider()) {
                    delay(100)
                }
                // Talk-back timer — stay on channel
                delay(talkBackTimerMs)

                _state.value = ScanState.SCANNING
                listener?.onScanStateChanged(ScanState.SCANNING)
            }

            index = (index + 1) % scanList.size
        }
    }

    private suspend fun switchToChannel(channel: RadioChannel) {
        _currentScanChannel.value = channel
        withContext(Dispatchers.Main) {
            listener?.onChannelChange(channel)
        }
    }

    private suspend fun handleActivityDetected(channel: RadioChannel) {
        val activityStart = System.currentTimeMillis()
        _state.value = ScanState.PAUSED_ON_ACTIVITY
        withContext(Dispatchers.Main) {
            listener?.onActivityDetected(channel)
            listener?.onScanStateChanged(ScanState.PAUSED_ON_ACTIVITY)
        }

        when (scanMode) {
            ScanMode.TIME_OPERATED -> {
                // Wait for the timeout, then resume regardless
                delay(timeOperatedResumeMs)
            }
            ScanMode.CARRIER_OPERATED -> {
                // Wait until carrier drops
                while (coroutineContext.isActive && rxActiveProvider()) {
                    delay(100)
                }
                // Small delay after carrier drops before resuming
                delay(500)
            }
            ScanMode.SEARCH -> {
                // Stop scanning — stay on this channel
                val duration = System.currentTimeMillis() - activityStart
                addLogEntry(channel, duration)

                withContext(Dispatchers.Main) {
                    // Cancel scan but preserve the found channel
                    scanJob?.cancel()
                    scanJob = null
                    _state.value = ScanState.IDLE
                    // Don't null _currentScanChannel — keep the found channel visible
                    listener?.onScanStateChanged(ScanState.IDLE)
                }
                return
            }
        }

        val duration = System.currentTimeMillis() - activityStart
        addLogEntry(channel, duration)

        _state.value = ScanState.SCANNING
        withContext(Dispatchers.Main) {
            listener?.onScanStateChanged(ScanState.SCANNING)
        }
    }

    private fun addLogEntry(channel: RadioChannel, durationMs: Long) {
        val entry = ScanLogEntry(
            timestamp = System.currentTimeMillis(),
            channelNumber = channel.number,
            channelName = channel.name.ifEmpty { "Channel ${channel.number}" },
            frequency = channel.formattedRxFreq,
            durationMs = durationMs
        )
        logEntries.add(0, entry) // Newest first
        if (logEntries.size > 100) {
            logEntries.removeAt(logEntries.lastIndex)
        }
        val snapshot = logEntries.toList()
        _scanLog.value = snapshot
        listener?.onScanLogUpdated(snapshot)
    }

    companion object {
        fun formatLogEntry(entry: ScanLogEntry): String {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
            val time = sdf.format(Date(entry.timestamp))
            val dur = if (entry.durationMs > 0) " (${entry.durationMs / 1000}s)" else ""
            return "$time CH${entry.channelNumber} ${entry.channelName} ${entry.frequency}$dur"
        }
    }
}
