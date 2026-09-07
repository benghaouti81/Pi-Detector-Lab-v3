package com.example.felezjoo.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LabBorder
import com.example.ui.theme.LabPrimary
import com.example.ui.theme.LabSecondary
import com.example.ui.theme.LabSurface
import com.example.ui.theme.LabTertiary
import com.example.ui.theme.LabTextSecondary

@Composable
fun HelpSpecsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Text("SPECIFICATIONS & RESEARCH ARCHITECTURE", fontSize = 14.sp, fontWeight = FontWeight.Black, color = LabPrimary)
        Text("Technical reference for FelezJoo PI 2.0 pulse induction laboratory", fontSize = 11.sp, color = LabTextSecondary)

        Spacer(modifier = Modifier.height(10.dp))

        // Binary Protocol Specs
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("BINARY PROTOCOL PACKET V1 (162 BYTES)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabSecondary)
                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    """
• Header Sync: 0xF5 0x5A (2 bytes)
• Version: 0x01 (1 byte)
• Packet Type: 0x01 (RAW_BLOCK, 1 byte)
• Payload Length: 0x009A (154 bytes, uint16 LE)
• Sequence Number: uint32 LE (4 bytes)
• Timestamp: uint32 LE ms (4 bytes)
• Delay Ticks: uint8 (1 byte, base = 1.6 µs)
• Sample Count: uint8 = 70 (1 byte)
• Raw Samples: 70 × uint16 LE (140 bytes, ADC 0..1023)
• Status Flags: uint16 LE (2 bytes)
• Checksum: CRC-16-CCITT (2 bytes LE, poly 0x1021, init 0xFFFF)
Total Frame: exactly 162 bytes.
                    """.trimIndent(),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ETS Architecture
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("EQUIVALENT TIME SAMPLING (ETS 14×5 = 70)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabTertiary)
                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    """
ATmega32U4 onboard 10-bit ADC has an ~8 µs conversion limit. To achieve 1.6 µs equivalent resolution across the eddy current decay:
• The pulse sequence triggers 14 consecutive coil pulses per block.
• Each pulse shifts the ADC trigger time by a precise phase step (1.6 µs).
• 5 samples are acquired per sweep across the 14 sweeps.
• The Android app reconstructs all 70 samples into a single coherent high-resolution decay curve.
                    """.trimIndent(),
                    fontSize = 11.sp,
                    color = LabTextSecondary,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Arduino Leonardo Wiring
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("ARDUINO LEONARDO HARDWARE PINOUT", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabPrimary)
                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    """
• Pin 9 (OC1A): High-speed MOSFET gate drive pulse (Timer1 PWM)
• Pin A0 (ADC0): Damped preamp decay signal input (0 to 1.1V / 5.0V)
• Pin 8 (D8): Preamp clamping / blanking switch
• Pin GND: Connected to battery ground and coil driver ground
• Micro-USB: Connected to Android device via USB-OTG adapter
                    """.trimIndent(),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
