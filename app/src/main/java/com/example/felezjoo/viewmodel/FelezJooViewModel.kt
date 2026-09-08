package com.example.felezjoo.viewmodel

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.felezjoo.audio.AudioMode
import com.example.felezjoo.audio.DetectorAudioManager
import com.example.felezjoo.diagnostics.RawPacketMonitorManager
import com.example.felezjoo.diagnostics.SystemDiagnostics
import com.example.felezjoo.dsp.DspCalculationResult
import com.example.felezjoo.dsp.DspPipeline
import com.example.felezjoo.models.DecayBlock
import com.example.felezjoo.models.DetectorCapabilities
import com.example.felezjoo.models.DeviceInfo
import com.example.felezjoo.models.DspProfile
import com.example.felezjoo.models.SamplingConfiguration
import com.example.felezjoo.models.TargetClassification
import com.example.felezjoo.models.TargetEvent
import com.example.felezjoo.protocol.ProtocolParser
import com.example.felezjoo.serial.CommandConsoleManager
import com.example.felezjoo.serial.SerialDirection
import com.example.felezjoo.serial.SerialMonitorManager
import com.example.felezjoo.simulation.SimulationEngine
import com.example.felezjoo.simulation.SimulationTargetType
import com.example.felezjoo.storage.AppDatabase
import com.example.felezjoo.storage.DataExportHelper
import com.example.felezjoo.storage.DecayBlockEntity
import com.example.felezjoo.storage.ReplayEngine
import com.example.felezjoo.storage.SessionEntity
import com.example.felezjoo.storage.TargetEventEntity
import com.example.felezjoo.ui.Screen
import com.example.felezjoo.usb.UsbConnectionState
import com.example.felezjoo.usb.UsbDriverDetector
import com.example.felezjoo.usb.UsbStatistics
import com.example.felezjoo.usb.UsbTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FelezJooViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    val database = AppDatabase.getInstance(context)

    // Current Navigation Screen
    private val _currentScreen = MutableStateFlow(Screen.DASHBOARD)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    // Hardware & Device State
    private val _samplingConfig = MutableStateFlow(SamplingConfiguration())
    val samplingConfig: StateFlow<SamplingConfiguration> = _samplingConfig.asStateFlow()

    private val _activeProfile = MutableStateFlow(DspProfile.STABLE)
    val activeProfile: StateFlow<DspProfile> = _activeProfile.asStateFlow()

    private val _deviceInfo = MutableStateFlow(DeviceInfo())
    val deviceInfo: StateFlow<DeviceInfo> = _deviceInfo.asStateFlow()

    private val _capabilities = MutableStateFlow(DetectorCapabilities())
    val capabilities: StateFlow<DetectorCapabilities> = _capabilities.asStateFlow()

    // Real USB Subsystem
    val serialMonitor = SerialMonitorManager()
    val packetMonitor = RawPacketMonitorManager()
    lateinit var commandConsole: CommandConsoleManager
    val dspPipeline = DspPipeline()
    val audioManager = DetectorAudioManager(context, viewModelScope)
    val replayEngine = ReplayEngine(viewModelScope) { onBlockReceived(it) }

    private val _usbState = MutableStateFlow(UsbConnectionState.DISCONNECTED)
    val usbState: StateFlow<UsbConnectionState> = _usbState.asStateFlow()

    private val _usbStatusMessage = MutableStateFlow("USB Disconnected")
    val usbStatusMessage: StateFlow<String> = _usbStatusMessage.asStateFlow()

    private val _availableUsbDevices = MutableStateFlow<List<UsbDevice>>(emptyList())
    val availableUsbDevices: StateFlow<List<UsbDevice>> = _availableUsbDevices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<UsbDevice?>(null)
    val selectedDevice: StateFlow<UsbDevice?> = _selectedDevice.asStateFlow()

    val usbStats = UsbStatistics()

    // Simulation Subsystem
    lateinit var simulationEngine: SimulationEngine
    private val _isSimulationMode = MutableStateFlow(true) // Defaults to true for instant out-of-the-box demo
    val isSimulationMode: StateFlow<Boolean> = _isSimulationMode.asStateFlow()

    // Streaming & Recording
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _activeSession = MutableStateFlow<SessionEntity?>(null)
    val activeSession: StateFlow<SessionEntity?> = _activeSession.asStateFlow()

    // Live DSP Results
    private val _currentBlock = MutableStateFlow(DecayBlock())
    val currentBlock: StateFlow<DecayBlock> = _currentBlock.asStateFlow()

    private val _dspResult = MutableStateFlow<DspCalculationResult?>(null)
    val dspResult: StateFlow<DspCalculationResult?> = _dspResult.asStateFlow()

    private val _historyScores = MutableStateFlow<List<Double>>(emptyList())
    val historyScores: StateFlow<List<Double>> = _historyScores.asStateFlow()

    private val _historySnr = MutableStateFlow<List<Double>>(emptyList())
    val historySnr: StateFlow<List<Double>> = _historySnr.asStateFlow()

    private val _historyNoise = MutableStateFlow<List<Double>>(emptyList())
    val historyNoise: StateFlow<List<Double>> = _historyNoise.asStateFlow()

    private val _targetEvents = MutableStateFlow<List<TargetEvent>>(emptyList())
    val targetEvents: StateFlow<List<TargetEvent>> = _targetEvents.asStateFlow()

    private val _autoDelayResult = MutableStateFlow<Triple<Int, Double, Double>?>(null)
    val autoDelayResult: StateFlow<Triple<Int, Double, Double>?> = _autoDelayResult.asStateFlow()

    private val _groundCaptureProgress = MutableStateFlow(0)
    val groundCaptureProgress: StateFlow<Int> = _groundCaptureProgress.asStateFlow()

    private val protocolParser = ProtocolParser(
        onDecayBlockParsed = { block -> onBlockReceived(block) },
        onRawPacketRecord = { record ->
            packetMonitor.addRecord(record)
            if (record.isValid) {
                usbStats.validPackets++
            } else {
                usbStats.invalidPackets++
            }
        },
        onAsciiLineParsed = { line ->
            serialMonitor.addEntry(SerialDirection.RX, line)
            commandConsole.onResponseReceived(line)
        },
        onSequenceGapDetected = { exp, act ->
            usbStats.sequenceGaps++
            SystemDiagnostics.warn("Protocol", "Sequence gap detected: expected $exp, actual $act")
        },
        onCrcErrorDetected = { exp, act ->
            usbStats.crcErrors++
            SystemDiagnostics.warn("Protocol", "CRC mismatch: expected 0x%04X, calculated 0x%04X".format(act, exp))
        }
    )

    private val usbTransport = UsbTransport(
        context = context,
        coroutineScope = viewModelScope,
        onStateChanged = { state, msg ->
            _usbState.value = state
            _usbStatusMessage.value = msg
            SystemDiagnostics.info("USB", "$state: $msg")
            if (state == UsbConnectionState.DISCONNECTED) {
                audioManager.triggerDisconnectHaptic()
            }
        },
        onBytesReceived = { bytes, len ->
            serialMonitor.addBytes(SerialDirection.RX, bytes, len)
            protocolParser.processIncomingBytes(bytes, len)
        },
        onBytesSent = { bytes, len ->
            serialMonitor.addBytes(SerialDirection.TX, bytes, len)
        },
        onError = { err ->
            SystemDiagnostics.error("USB", err)
        }
    )

    private val ACTION_USB_PERMISSION = "com.example.felezjoo.USB_PERMISSION"

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == ACTION_USB_PERMISSION) {
                synchronized(this) {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (device != null) {
                        if (granted) {
                            _usbState.value = UsbConnectionState.PERMISSION_GRANTED
                            _usbStatusMessage.value = "Permission granted for ${device.deviceName}"
                            connectUsb(device)
                        } else {
                            _usbState.value = UsbConnectionState.ERROR
                            _usbStatusMessage.value = "USB permission denied by user"
                        }
                    }
                }
            } else if (action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                if (device != null && device == _selectedDevice.value) {
                    disconnectUsb()
                    _usbState.value = UsbConnectionState.DEVICE_REMOVED
                    _usbStatusMessage.value = "USB device disconnected"
                }
                refreshUsbDevices()
            } else if (action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                refreshUsbDevices()
            }
        }
    }

    init {
        commandConsole = CommandConsoleManager(viewModelScope) { cmd ->
            if (_isSimulationMode.value) {
                handleSimulationCommand(cmd)
                true
            } else {
                usbTransport.sendAscii(cmd)
            }
        }

        simulationEngine = SimulationEngine(viewModelScope) { block, packetBytes ->
            if (_isSimulationMode.value && _isStreaming.value) {
                // Pipe simulated block into the same architecture
                serialMonitor.addBytes(SerialDirection.RX, packetBytes, packetBytes.size)
                protocolParser.processIncomingBytes(packetBytes, packetBytes.size)
            }
        }

        // Register USB broadcast receiver safely
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }

        refreshUsbDevices()

        // Generate initial demo block so Signal Lab immediately shows realistic waveform
        generateDemoDecayBlock()
    }

    fun refreshUsbDevices() {
        try {
            val list = usbManager.deviceList.values.toList()
            _availableUsbDevices.value = list
            if (_selectedDevice.value == null && list.isNotEmpty()) {
                _selectedDevice.value = list.first()
            }
        } catch (e: Exception) {
            SystemDiagnostics.error("USB", "Device enumeration error: ${e.message}")
        }
    }

    fun selectUsbDevice(device: UsbDevice?) {
        _selectedDevice.value = device
    }

    fun requestUsbPermission(device: UsbDevice) {
        _selectedDevice.value = device
        if (usbManager.hasPermission(device)) {
            connectUsb(device)
        } else {
            _usbState.value = UsbConnectionState.PERMISSION_REQUESTED
            _usbStatusMessage.value = "Requesting USB permission..."
            val permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            )
            usbManager.requestPermission(device, permissionIntent)
        }
    }

    fun connectUsb(device: UsbDevice, baudRate: Int = 115200) {
        viewModelScope.launch {
            _isSimulationMode.value = false
            simulationEngine.stop()

            val success = usbTransport.connect(device, baudRate)
            if (success) {
                // Handshake sequence
                performHandshake()
            }
        }
    }

    fun disconnectUsb() {
        usbTransport.disconnect()
        _isStreaming.value = false
    }

    private suspend fun performHandshake() {
        _usbState.value = UsbConnectionState.HANDSHAKE
        _usbStatusMessage.value = "Negotiating handshake with MCU..."

        // 1. PING
        commandConsole.send("PING")
        delay(150L)

        // 2. CONFIG?
        commandConsole.send("CONFIG?")
        delay(150L)

        // 3. STATUS
        commandConsole.send("STATUS")
        delay(100L)

        _usbState.value = UsbConnectionState.READY
        _usbStatusMessage.value = "Detector Ready (Click START to stream)"
    }

    fun startStreaming() {
        _isStreaming.value = true
        if (_isSimulationMode.value) {
            simulationEngine.start()
        } else {
            viewModelScope.launch {
                commandConsole.send("START")
                _usbState.value = UsbConnectionState.STREAMING
            }
        }
    }

    fun stopStreaming() {
        _isStreaming.value = false
        if (_isSimulationMode.value) {
            simulationEngine.stop()
        } else {
            viewModelScope.launch {
                commandConsole.send("STOP")
                _usbState.value = UsbConnectionState.READY
            }
        }
    }

    fun toggleSimulation(enabled: Boolean) {
        _isSimulationMode.value = enabled
        if (enabled) {
            disconnectUsb()
            _usbState.value = UsbConnectionState.DISCONNECTED
            _usbStatusMessage.value = "Simulation Mode Active"
        }
    }

    private fun handleSimulationCommand(cmd: String) {
        val upper = cmd.trim().uppercase()
        when {
            upper == "PING" -> serialMonitor.addEntry(SerialDirection.RX, "PONG (Simulation)")
            upper == "CONFIG?" -> {
                val cfg = "#CONFIG:70,1600,10,ETS,14,5"
                protocolParser.parseConfigLine(cfg)
                serialMonitor.addEntry(SerialDirection.RX, cfg)
            }
            upper == "STATUS" -> serialMonitor.addEntry(SerialDirection.RX, "#STATUS:MODE=SIM,TX=ON,ETS=ACTIVE,FPS=14")
            upper == "START" -> startStreaming()
            upper == "STOP" -> stopStreaming()
            upper.startsWith("SET:DELAY=") -> {
                val ticks = upper.substringAfter("=").toIntOrNull() ?: 10
                setDelayTicks(ticks)
                serialMonitor.addEntry(SerialDirection.RX, "OK: DELAY=$ticks")
            }
            upper.startsWith("SET:PULSE=") -> {
                val us = upper.substringAfter("=").toIntOrNull() ?: 150
                serialMonitor.addEntry(SerialDirection.RX, "OK: PULSE=$us")
            }
            upper.startsWith("SET:FREQ=") -> {
                val hz = upper.substringAfter("=").toIntOrNull() ?: 200
                serialMonitor.addEntry(SerialDirection.RX, "OK: FREQ=$hz")
            }
            else -> serialMonitor.addEntry(SerialDirection.RX, "OK (Simulation)")
        }
    }

    private fun onBlockReceived(block: DecayBlock) {
        viewModelScope.launch(Dispatchers.Default) {
            val startMs = System.currentTimeMillis()
            _currentBlock.value = block

            // Run DSP Pipeline
            val result = dspPipeline.processBlock(block, _activeProfile.value)
            val dspDuration = System.currentTimeMillis() - startMs
            SystemDiagnostics.lastDspDurationMs = dspDuration

            _dspResult.value = result

            // Update rolling history
            val currentScores = _historyScores.value.toMutableList()
            val currentSnr = _historySnr.value.toMutableList()
            val currentNoise = _historyNoise.value.toMutableList()

            currentScores.add(result.featureVector.targetScore)
            currentSnr.add(result.featureVector.snr)
            currentNoise.add(result.featureVector.noise)

            if (currentScores.size > 64) currentScores.removeAt(0)
            if (currentSnr.size > 64) currentSnr.removeAt(0)
            if (currentNoise.size > 64) currentNoise.removeAt(0)

            _historyScores.value = currentScores
            _historySnr.value = currentSnr
            _historyNoise.value = currentNoise

            // Update Non-blocking Audio
            audioManager.updateTargetState(
                targetScore = result.featureVector.targetScore,
                confidence = result.featureVector.targetConfidence,
                ironScore = result.featureVector.ironScore,
                targetId = result.featureVector.targetId,
                classification = result.targetClassification
            )

            // Track target events if stable target detected
            if (result.targetClassification == TargetClassification.STABLE_TARGET ||
                result.targetClassification == TargetClassification.NON_FERROUS ||
                result.targetClassification == TargetClassification.IRON
            ) {
                if (result.featureVector.targetScore > 50.0 && result.featureVector.targetConfidence > 50.0) {
                    recordTargetEvent(result)
                }
            }

            // Save to database if recording
            if (_isRecording.value) {
                val session = _activeSession.value
                if (session != null) {
                    val entity = DecayBlockEntity(
                        sessionId = session.id,
                        sequenceNumber = block.sequenceNumber,
                        timestamp = block.timestamp,
                        delayTicks = block.delayTicks,
                        delayUs = block.delayUs,
                        pulseRate = block.pulseRate,
                        pulseWidthUs = block.pulseWidthUs,
                        rawSamplesCsv = block.rawSamples.joinToString(","),
                        targetScore = result.featureVector.targetScore,
                        confidence = result.featureVector.targetConfidence,
                        ironScore = result.featureVector.ironScore,
                        targetId = result.featureVector.targetId,
                        classification = result.targetClassification.name
                    )
                    database.decayBlockDao().insertBlock(entity)
                }
            }
        }
    }

    private fun recordTargetEvent(result: DspCalculationResult) {
        val events = _targetEvents.value.toMutableList()
        val now = System.currentTimeMillis()
        val recent = events.lastOrNull()

        if (recent != null && (now - recent.endTimeMs) < 1500) {
            // Continuation of same physical sweep event
            recent.endTimeMs = now
        } else {
            // New event
            val ev = TargetEvent(
                startTimeMs = now,
                endTimeMs = now,
                peakScore = result.featureVector.targetScore,
                peakConfidence = result.featureVector.targetConfidence,
                ironScore = result.featureVector.ironScore,
                targetId = result.featureVector.targetId,
                classification = result.targetClassification,
                bestFeatureVector = result.featureVector
            )
            events.add(ev)
            if (events.size > 50) events.removeAt(0)
            _targetEvents.value = events

            // Persist event to DB
            viewModelScope.launch(Dispatchers.IO) {
                val sessId = _activeSession.value?.id ?: "live"
                database.targetEventDao().insertEvent(
                    TargetEventEntity(
                        sessionId = sessId,
                        startTimeMs = ev.startTimeMs,
                        endTimeMs = ev.endTimeMs,
                        peakScore = ev.peakScore,
                        peakConfidence = ev.peakConfidence,
                        ironScore = ev.ironScore,
                        targetId = ev.targetId,
                        classification = ev.classification.name
                    )
                )
            }
        }
    }

    // Profile & DSP parameter changes
    fun updateProfile(newProfile: DspProfile) {
        _activeProfile.value = newProfile
        audioManager.audioThreshold = newProfile.audioThreshold
        _dspResult.value?.let { currentRes ->
            // Re-evaluate immediately with new profile
            val updated = dspPipeline.processBlock(currentRes.block, newProfile)
            _dspResult.value = updated
        }
    }

    fun setDelayTicks(ticks: Int) {
        val safeTicks = ticks.coerceIn(1, 100)
        _samplingConfig.value = _samplingConfig.value.copy(
            delayUs = safeTicks * _samplingConfig.value.delayUnitUs
        )
        if (!_isSimulationMode.value) {
            commandConsole.send("SET:DELAY=$safeTicks")
        }
    }

    fun setPulseWidthUs(widthUs: Int) {
        if (!_isSimulationMode.value) {
            commandConsole.send("SET:PULSE=$widthUs")
        }
    }

    fun setPulseRateHz(freqHz: Int) {
        if (!_isSimulationMode.value) {
            commandConsole.send("SET:FREQ=$freqHz")
        }
    }

    fun captureAirBaseline() {
        val res = _dspResult.value ?: return
        dspPipeline.captureAirBaseline(res.filteredCurve)
        SystemDiagnostics.info("DSP", "Captured air baseline")
    }

    fun captureGround() {
        viewModelScope.launch {
            val res = _dspResult.value ?: return@launch
            for (p in 0..100 step 20) {
                _groundCaptureProgress.value = p
                delay(80L)
            }
            dspPipeline.setGroundDirect(res.filteredCurve)
            _groundCaptureProgress.value = 100
            delay(200L)
            _groundCaptureProgress.value = 0
            SystemDiagnostics.info("DSP", "Ground captured successfully")
        }
    }

    fun resetGround() {
        dspPipeline.resetGround()
        SystemDiagnostics.info("DSP", "Ground reset")
    }

    fun runAutoDelayFinder() {
        val block = _currentBlock.value
        val res = dspPipeline.findAutoDelay(block.rawSamples, block.samplingConfiguration)
        _autoDelayResult.value = res
    }

    fun applyAutoDelay() {
        val candidate = _autoDelayResult.value ?: return
        setDelayTicks(candidate.first)
    }

    // Recording & Sessions
    fun startRecording(sessionName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val session = SessionEntity(name = sessionName, deviceName = _deviceInfo.value.deviceName)
            database.sessionDao().insertSession(session)
            _activeSession.value = session
            _isRecording.value = true
        }
    }

    fun stopRecording() {
        _isRecording.value = false
    }

    fun loadSessionForReplay(session: SessionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val blocks = database.decayBlockDao().getBlocksForSession(session.id)
            withContext(Dispatchers.Main) {
                replayEngine.loadBlocks(blocks)
                navigateTo(Screen.RECORDER_REPLAY)
            }
        }
    }

    private fun generateDemoDecayBlock() {
        val count = 70
        val samples = IntArray(count)
        val config = SamplingConfiguration()
        // Synthesize nice clean copper coin decay
        for (i in 0 until count) {
            val tUs = i * config.sampleSpacingUs
            val flyback = 600.0 * kotlin.math.exp(-tUs / 2.0)
            val ground = 30.0 * kotlin.math.exp(-tUs / 15.0)
            val target = 110.0 * kotlin.math.exp(-tUs / 18.0)
            val noise = (i % 3) * 1.5
            samples[i] = (120.0 + flyback + ground + target + noise).toInt().coerceIn(0, 1023)
        }
        val block = DecayBlock(
            sequenceNumber = 1,
            sampleCount = count,
            rawSamples = samples,
            samplingConfiguration = config
        )
        onBlockReceived(block)
    }

    override fun onCleared() {
        super.onCleared()
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            // Ignore
        }
        usbTransport.disconnect()
        simulationEngine.stop()
        audioManager.release()
    }
}
