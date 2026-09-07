package com.example.felezjoo.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

enum class UsbDriverType(val displayName: String) {
    CDC_ACM("Native USB CDC-ACM (Leonardo / STM32)"),
    CH340("WCH CH340 / CH341"),
    CP210X("Silicon Labs CP210x"),
    FTDI("FTDI FT232 / FTDI-compatible"),
    PL2303("Prolific PL2303"),
    GENERIC_USB("Generic USB Device"),
    UNKNOWN("Unknown USB Interface")
}

object UsbDriverDetector {

    fun identifyDriver(device: UsbDevice): UsbDriverType {
        val vid = device.vendorId
        val pid = device.productId

        return when {
            // Arduino Leonardo (ATmega32U4)
            vid == 0x2341 && pid == 0x8036 -> UsbDriverType.CDC_ACM
            vid == 0x2341 && pid == 0x0036 -> UsbDriverType.CDC_ACM
            // Arduino LLC / SRL
            vid == 0x2341 || vid == 0x2A03 -> UsbDriverType.CDC_ACM
            // STM32 Virtual COM Port
            vid == 0x0483 && pid == 0x5740 -> UsbDriverType.CDC_ACM
            // FTDI
            vid == 0x0403 -> UsbDriverType.FTDI
            // Silicon Labs CP210x
            vid == 0x10C4 -> UsbDriverType.CP210X
            // WCH CH340 / CH341
            vid == 0x1A86 -> UsbDriverType.CH340
            // Prolific
            vid == 0x067B -> UsbDriverType.PL2303
            // CDC ACM generic class
            device.deviceClass == 2 -> UsbDriverType.CDC_ACM
            else -> UsbDriverType.GENERIC_USB
        }
    }

    /**
     * Safely reads serial number ONLY if permission has been granted.
     * Prevents SecurityException crash.
     */
    fun getSafeSerialNumber(usbManager: UsbManager, device: UsbDevice): String {
        return try {
            if (usbManager.hasPermission(device)) {
                device.serialNumber ?: "N/A"
            } else {
                "Requires Permission"
            }
        } catch (e: SecurityException) {
            "Permission Denied"
        } catch (e: Exception) {
            "Unavailable"
        }
    }

    fun getSafeManufacturerName(usbManager: UsbManager, device: UsbDevice): String {
        return try {
            if (usbManager.hasPermission(device)) {
                device.manufacturerName ?: "Unknown Mfr"
            } else {
                "Requires Permission"
            }
        } catch (e: Exception) {
            "Unknown Mfr"
        }
    }

    fun getSafeProductName(usbManager: UsbManager, device: UsbDevice): String {
        return try {
            if (usbManager.hasPermission(device)) {
                device.productName ?: "USB Serial Device"
            } else {
                "USB Device (${String.format("0x%04X:0x%04X", device.vendorId, device.productId)})"
            }
        } catch (e: Exception) {
            "USB Device"
        }
    }
}
