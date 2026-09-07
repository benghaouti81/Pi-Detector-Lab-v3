package com.example.felezjoo.usb

enum class UsbConnectionState(val displayName: String) {
    DISCONNECTED("Disconnected"),
    DEVICE_FOUND("Device Found"),
    PERMISSION_REQUIRED("Permission Required"),
    PERMISSION_REQUESTED("Permission Requested"),
    PERMISSION_GRANTED("Permission Granted"),
    OPENING("Opening Port"),
    CONNECTED("Connected"),
    HANDSHAKE("Handshake in Progress"),
    READY("Ready"),
    STREAMING("Streaming"),
    ERROR("Error"),
    DEVICE_REMOVED("Device Removed"),
    RECONNECTING("Reconnecting"),
    TIMEOUT("Connection Timeout")
}

data class UsbStatistics(
    var rxBytes: Long = 0L,
    var txBytes: Long = 0L,
    var packets: Long = 0L,
    var validPackets: Long = 0L,
    var invalidPackets: Long = 0L,
    var crcErrors: Long = 0L,
    var sequenceGaps: Long = 0L,
    var droppedFrames: Long = 0L,
    var reconnectCount: Long = 0L,
    var lastPacketTimeMs: Long = 0L,
    var lastError: String = ""
) {
    fun reset() {
        rxBytes = 0L
        txBytes = 0L
        packets = 0L
        validPackets = 0L
        invalidPackets = 0L
        crcErrors = 0L
        sequenceGaps = 0L
        droppedFrames = 0L
        reconnectCount = 0L
        lastPacketTimeMs = 0L
        lastError = ""
    }
}
