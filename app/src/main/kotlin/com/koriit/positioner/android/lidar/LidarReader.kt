package com.koriit.positioner.android.lidar

import android.content.Context
import android.hardware.usb.UsbManager
import com.koriit.positioner.android.logging.AppLog
import com.koriit.positioner.android.usb.UsbPermissionHelper
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Continuously read measurement packets from the serial port and emit
 * individual measurements as a [Flow]. The implementation searches for
 * packet headers (0x54, 0x2C) and uses [LidarParser] to decode the packet.
 */
class LidarReader(private val port: UsbSerialPort) : LidarDataSource {
    private val parser = LidarParser()

    private enum class State { SYNC0, SYNC1, SYNC2 }

    companion object {
        private const val TAG = "LidarReader"
        private const val TAG_DEBUG = "LidarReaderDebug"
        private const val READ_TIMEOUT = 50

        private const val RAW_DEBUG = false

        /**
         * Open the first available USB serial port (CP210x) with default settings.
         * Returns null if no device is available or permission is missing.
         */
        suspend fun openDefault(context: Context): LidarReader? {
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            AppLog.d(TAG, "Searching for USB serial drivers")
            val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
            AppLog.d(TAG, "Found ${drivers.size} drivers" + drivers)
            val driver = drivers.firstOrNull()
            if (driver == null) {
                AppLog.d(TAG, "No USB serial device available")
                return null
            }
            if (!manager.hasPermission(driver.device)) {
                AppLog.d(TAG, "Requesting permission for device")
                UsbPermissionHelper.requestPermissionAsync(context, manager, driver.device)
                return null
            }
            val connection = manager.openDevice(driver.device) ?: return null
            val port: UsbSerialPort = driver.ports[0]
            AppLog.d(TAG, "Opening serial connection")
            port.open(connection)
            port.setParameters(230400, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            // LD06 streams measurements continuously once powered so
            // no initialization command is required. Simply open the
            // serial connection and start reading packets.
            return LidarReader(port)
        }
    }

    // Stream measurements emitted by the lidar. The blocking serial reads are
    // executed on Dispatchers.IO thanks to [flowOn] below so the UI thread
    // never blocks.
    // Create a flow of bytes read from the serial port, which can be intercepted or debugged.
    // This flow emits bytes as they are read from the port.
    private fun byteFlow(): Flow<Byte> = flow {
        val buffer = ByteArray(64)
        AppLog.d(TAG, "Starting raw hex dump")
        while (true) {
            val n = port.read(buffer, READ_TIMEOUT)
            if (n > 0) {
                if (RAW_DEBUG) {
                    val hex = buffer.take(n).joinToString(" ") { b: Byte ->
                        String.format("%02x", b.toInt() and 0xFF)
                    }
                    AppLog.d(TAG_DEBUG, hex)
                }
                buffer.take(n).forEach {
                    emit(it)
                }
            }
        }
    }

    // Refactored measurements() to use byteStream instead of directly reading port
    override fun measurements(): Flow<LidarMeasurement> = flow {
        val packet = ByteArray(47)
        val header0 = 0x54.toByte()
        val header1 = 0x2C.toByte()

        suspend fun FlowCollector<LidarMeasurement>.emitPacket(packet: ByteArray) {
            try {
                val measures = parser.parse(packet)
//                AppLog.d(TAG, "Parsed packet with ${measures.size} measurements")
                measures.forEach { emit(it) }
            } catch (e: IllegalArgumentException) {
                AppLog.d(TAG, "Malformed packet", e)
            }
        }

        var state = State.SYNC0
        var offset = 0

        AppLog.d(TAG, "Starting measurement loop (byteStream version)")

        byteFlow().flowOn(Dispatchers.IO).collect { byte ->
            when (state) {
                State.SYNC0 -> {
                    if (byte == header0) {
                        packet[offset++] = header0
                        state = State.SYNC1
                    }
                }

                State.SYNC1 -> {
                    if (byte == header1) {
                        packet[offset++] = header1
                        state = State.SYNC2
                    } else {
                        state = State.SYNC0
                    }
                }

                State.SYNC2 -> {
                    packet[offset++] = byte
                    if (offset >= packet.size) {
                        emitPacket(packet)
                        offset = 0
                        state = State.SYNC0
                    }
                }
            }
        }
    }
}
