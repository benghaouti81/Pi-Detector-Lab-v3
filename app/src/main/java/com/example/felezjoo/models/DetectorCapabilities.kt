package com.example.felezjoo.models

import java.io.Serializable

data class DetectorCapabilities(
    val supportsStart: Boolean = true,
    val supportsStop: Boolean = true,
    val supportsDelay: Boolean = true,
    val supportsPulseWidth: Boolean = true,
    val supportsPulseRate: Boolean = true,
    val supportsBinaryStream: Boolean = true,
    val supportsRawBlocks: Boolean = true,
    val supportsStatus: Boolean = true,
    val supportsConfig: Boolean = true,
    val supportsDeviceInfo: Boolean = true,
    val supportsResetStats: Boolean = true,
    val supportsResearchMode: Boolean = true
) : Serializable

data class DeviceInfo(
    val deviceName: String = "FelezJoo PI Research (Leonardo)",
    val mcu: String = "ATmega32U4",
    val clockFrequencyMhz: Int = 16,
    val firmwareVersion: String = "2.0-ETS",
    val protocolVersion: String = "V1",
    val sampleCount: Int = 70,
    val samplingMode: String = "ETS",
    val adcResolutionBits: Int = 10,
    val supportedCommands: List<String> = listOf("PING", "START", "STOP", "CONFIG?", "STATUS", "SET:DELAY=", "SET:PULSE=", "SET:FREQ=", "RESET")
) : Serializable
