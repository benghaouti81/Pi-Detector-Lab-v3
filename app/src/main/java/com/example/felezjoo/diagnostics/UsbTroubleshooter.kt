package com.example.felezjoo.diagnostics

data class TroubleshootingStep(
    val title: String,
    val description: String,
    val actionGuide: String,
    val isOk: Boolean
)

object UsbTroubleshooter {

    fun generateSteps(
        deviceFound: Boolean,
        hasPermission: Boolean,
        isConnected: Boolean,
        rxCount: Long,
        validPacketCount: Long,
        crcErrors: Long
    ): List<TroubleshootingStep> {
        return listOf(
            TroubleshootingStep(
                title = "1. USB OTG & Cable Detection",
                description = if (deviceFound) "USB hardware detected on phone port." else "No USB device found by Android OS.",
                actionGuide = "Ensure your OTG adapter is fully inserted. Some phones require turning on 'OTG Connection' in system settings (e.g. OnePlus, Oppo, Vivo).",
                isOk = deviceFound
            ),
            TroubleshootingStep(
                title = "2. Android USB Permission",
                description = if (hasPermission) "USB Host permission granted." else "Android permission pending or denied.",
                actionGuide = "Click 'CONNECT' and tap 'ALLOW' on the system dialog. Check 'Always open for this device' for seamless reconnect.",
                isOk = hasPermission
            ),
            TroubleshootingStep(
                title = "3. Port & Driver Enumeration",
                description = if (isConnected) "Port open (CDC ACM / UART driver active)." else "Connection not established.",
                actionGuide = "For Arduino Leonardo (ATmega32U4), native USB CDC does not require physical baud clocks. For FTDI/CH340, verify baud setting (115200 default).",
                isOk = isConnected
            ),
            TroubleshootingStep(
                title = "4. RX Data Stream",
                description = if (rxCount > 0) "Data bytes arriving from detector ($rxCount bytes)." else "No bytes received yet.",
                actionGuide = "Check detector power supply. Send 'PING' or 'START' command from Command Console. Verify MCU TX is connected to USB bridge RX.",
                isOk = rxCount > 0
            ),
            TroubleshootingStep(
                title = "5. Protocol Validation (Packet V1)",
                description = if (validPacketCount > 0) "$validPacketCount valid 162-byte RAW_BLOCK packets received." else "No valid protocol packets decoded.",
                actionGuide = "Verify detector firmware is running FelezJoo 2.0 protocol with 0xF5 0x5A sync header and CRC-16-CCITT checksum.",
                isOk = validPacketCount > 0
            ),
            TroubleshootingStep(
                title = "6. Signal Integrity & CRC Health",
                description = if (crcErrors == 0L) "Zero CRC errors detected." else "$crcErrors corrupted packets dropped.",
                actionGuide = "Keep USB cable short (< 1 meter), ensure common ground between MCU and coil power stages, and avoid running USB cable over high-voltage flyback traces.",
                isOk = crcErrors == 0L
            )
        )
    }
}
