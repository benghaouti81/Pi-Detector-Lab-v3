package com.example.felezjoo.usb

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class UsbTransport(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val onStateChanged: (UsbConnectionState, String) -> Unit,
    private val onBytesReceived: (ByteArray, Int) -> Unit,
    private val onBytesSent: (ByteArray, Int) -> Unit,
    private val onError: (String) -> Unit
) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private var currentDevice: UsbDevice? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var dataInterface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null

    private var rxJob: Job? = null
    var currentState: UsbConnectionState = UsbConnectionState.DISCONNECTED
        private set

    val stats = UsbStatistics()

    fun updateState(newState: UsbConnectionState, message: String = "") {
        currentState = newState
        onStateChanged(newState, message)
    }

    suspend fun connect(device: UsbDevice, baudRate: Int = 115200): Boolean = withContext(Dispatchers.IO) {
        disconnect()

        currentDevice = device
        updateState(UsbConnectionState.OPENING, "Opening USB connection...")

        if (!usbManager.hasPermission(device)) {
            updateState(UsbConnectionState.PERMISSION_REQUIRED, "USB permission not granted by user")
            return@withContext false
        }

        try {
            val connection = usbManager.openDevice(device)
            if (connection == null) {
                updateState(UsbConnectionState.ERROR, "Failed to open USB device connection")
                return@withContext false
            }
            usbConnection = connection

            // Locate data interface and endpoints
            var foundIn: UsbEndpoint? = null
            var foundOut: UsbEndpoint? = null
            var selectedIface: UsbInterface? = null

            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                var epIn: UsbEndpoint? = null
                var epOut: UsbEndpoint? = null

                for (j in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(j)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (ep.direction == UsbConstants.USB_DIR_IN && epIn == null) {
                            epIn = ep
                        } else if (ep.direction == UsbConstants.USB_DIR_OUT && epOut == null) {
                            epOut = ep
                        }
                    }
                }

                if (epIn != null || epOut != null) {
                    if (connection.claimInterface(iface, true)) {
                        selectedIface = iface
                        foundIn = epIn
                        foundOut = epOut
                        break
                    }
                }
            }

            if (selectedIface == null || (foundIn == null && foundOut == null)) {
                // Fallback claim first interface
                if (device.interfaceCount > 0) {
                    val iface = device.getInterface(0)
                    connection.claimInterface(iface, true)
                    selectedIface = iface
                    for (k in 0 until iface.endpointCount) {
                        val ep = iface.getEndpoint(k)
                        if (ep.direction == UsbConstants.USB_DIR_IN && foundIn == null) foundIn = ep
                        if (ep.direction == UsbConstants.USB_DIR_OUT && foundOut == null) foundOut = ep
                    }
                }
            }

            dataInterface = selectedIface
            inEndpoint = foundIn
            outEndpoint = foundOut

            // Configure CDC-ACM baud rate and control lines
            configureCdcAcm(connection, baudRate)

            updateState(UsbConnectionState.CONNECTED, "USB Port opened successfully")
            startReading()
            true
        } catch (e: SecurityException) {
            updateState(UsbConnectionState.ERROR, "Security exception: ${e.message}")
            onError("Security Exception: ${e.message}")
            false
        } catch (e: Exception) {
            updateState(UsbConnectionState.ERROR, "USB connection failed: ${e.message}")
            onError("USB Error: ${e.message}")
            false
        }
    }

    private fun configureCdcAcm(conn: UsbDeviceConnection, baudRate: Int) {
        try {
            // SET_LINE_CODING (0x20)
            // 7 bytes: baud rate (4 bytes LE), stop bits (1), parity (1), data bits (1)
            val lineCoding = ByteArray(7)
            lineCoding[0] = (baudRate and 0xFF).toByte()
            lineCoding[1] = ((baudRate shr 8) and 0xFF).toByte()
            lineCoding[2] = ((baudRate shr 16) and 0xFF).toByte()
            lineCoding[3] = ((baudRate shr 24) and 0xFF).toByte()
            lineCoding[4] = 0 // 1 stop bit
            lineCoding[5] = 0 // No parity
            lineCoding[6] = 8 // 8 data bits

            // CDC Class interface request: 0x21
            conn.controlTransfer(0x21, 0x20, 0, 0, lineCoding, lineCoding.size, 1000)

            // SET_CONTROL_LINE_STATE (0x22): DTR = 1, RTS = 1
            conn.controlTransfer(0x21, 0x22, 0x03, 0, null, 0, 1000)
        } catch (e: Exception) {
            // Non-fatal if device is not standard CDC ACM
        }
    }

    private fun startReading() {
        rxJob?.cancel()
        rxJob = coroutineScope.launch(Dispatchers.IO) {
            val endpoint = inEndpoint ?: return@launch
            val conn = usbConnection ?: return@launch
            val buffer = ByteArray(2048)

            while (isActive && currentState != UsbConnectionState.DISCONNECTED) {
                try {
                    val read = conn.bulkTransfer(endpoint, buffer, buffer.size, 100)
                    if (read > 0) {
                        stats.rxBytes += read
                        stats.lastPacketTimeMs = System.currentTimeMillis()
                        val copy = buffer.copyOf(read)
                        onBytesReceived(copy, read)
                    } else if (read < 0) {
                        // Negative read code could indicate disconnect or bus error
                        if (read == -1 && !isActive) break
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        stats.lastError = e.message ?: "RX Error"
                        onError("USB RX Error: ${e.message}")
                    }
                    break
                }
            }
        }
    }

    suspend fun send(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val conn = usbConnection
        val ep = outEndpoint
        if (conn == null || ep == null) {
            return@withContext false
        }
        try {
            val sent = conn.bulkTransfer(ep, data, data.size, 1000)
            if (sent > 0) {
                stats.txBytes += sent
                onBytesSent(data, sent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            stats.lastError = e.message ?: "TX Error"
            onError("USB TX Error: ${e.message}")
            false
        }
    }

    suspend fun sendAscii(command: String): Boolean {
        val bytes = (command.trim() + "\r\n").toByteArray(Charsets.UTF_8)
        return send(bytes)
    }

    fun disconnect() {
        rxJob?.cancel()
        rxJob = null

        val conn = usbConnection
        val iface = dataInterface

        if (conn != null && iface != null) {
            try {
                conn.releaseInterface(iface)
            } catch (e: Exception) {
                // Ignore release errors on disconnect
            }
        }

        try {
            conn?.close()
        } catch (e: Exception) {
            // Ignore close errors
        }

        usbConnection = null
        dataInterface = null
        inEndpoint = null
        outEndpoint = null
        currentDevice = null

        updateState(UsbConnectionState.DISCONNECTED, "USB Disconnected")
    }
}
