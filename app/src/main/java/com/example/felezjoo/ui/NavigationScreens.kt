package com.example.felezjoo.ui

enum class Screen(val title: String, val category: String) {
    DASHBOARD("Dashboard", "Detection"),
    LIVE_WAVEFORM("Live Waveform", "Detection"),
    SIGNAL_LAB("Signal Lab", "Research"),
    DETECTOR_CONTROLS("Controls", "Settings"),
    TARGET_IRON("Target & Iron", "Research"),
    GROUND_NOISE("Ground & Noise", "Research"),
    DELAY_FINDER("Delay Finder", "Research"),
    INTEGRATION_ABC("Integration & ABC", "Research"),
    USB_DEVICE("USB & Device", "Hardware"),
    SERIAL_MONITOR("Serial Monitor", "Diagnostics"),
    COMMAND_CONSOLE("Command Console", "Hardware"),
    PACKET_MONITOR("Packet Monitor", "Diagnostics"),
    RECORDER_REPLAY("Recorder & Replay", "Data"),
    ALGORITHM_COMPARE("Algo Comparison", "Research"),
    CALIBRATION_DIAGNOSTICS("Diagnostics & Health", "Diagnostics"),
    FIELD_MODE("Field Mode", "Detection"),
    HELP_ABOUT("Help & Specs", "Info")
}
