package org.example.positioner.lidar

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import org.example.positioner.logging.AppLog
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

    private enum class State { SYNC0, SYNC1, SYNC2, LOCKED }

    companion object {
        private const val TAG = "LidarReader"
        private const val READ_TIMEOUT = 50

        /**
         * Open the first available USB serial port (CP210x) with default settings.
         * Returns null if no device is available or permission is missing.
         */
        fun openDefault(context: Context): LidarReader? {
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            AppLog.d(TAG, "Searching for USB serial drivers")
            val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
            AppLog.d(TAG, "Found ${drivers.size} drivers" + drivers)
            val driver = drivers.firstOrNull()
            if (driver == null) {
                AppLog.d(TAG, "No USB serial device available")
                return null
            }
            var connection = manager.openDevice(driver.device)
            if (connection == null) {
                AppLog.d(TAG, "Requesting permission for device")
                val pi = PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent("org.example.positioner.USB_PERMISSION"),
                    PendingIntent.FLAG_IMMUTABLE
                )
                manager.requestPermission(driver.device, pi)
                return null
            }
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
    override fun measurements(): Flow<LidarMeasurement> = flow {
        val packet = ByteArray(47)
        val buf = ByteArray(1)
        val header0 = 0x54.toByte()
        val header1 = 0x2C.toByte()

        suspend fun FlowCollector<LidarMeasurement>.emitPacket() {
            try {
                val measures = parser.parse(packet)
                AppLog.d(TAG, "Parsed packet with ${measures.size} measurements")
                for (m in measures) emit(m)
            } catch (e: IllegalArgumentException) {
                AppLog.d(TAG, "Malformed packet", e)
            }
        }

        var state = State.SYNC0
        AppLog.d(TAG, "Starting measurement loop")
        while (true) {
            when (state) {
                State.SYNC0 -> {
                    if (port.read(buf, READ_TIMEOUT) == 1 && buf[0] == header0) {
                        packet[0] = header0
                        state = State.SYNC1
                    }
                }
                State.SYNC1 -> {
                    if (port.read(buf, READ_TIMEOUT) == 1 && buf[0] == header1) {
                        packet[1] = header1
                        state = State.SYNC2
                    } else {
                        state = State.SYNC0
                    }
                }
                State.SYNC2 -> {
                    var offset = 2
                    while (offset < packet.size) {
                        val slice = ByteArray(packet.size - offset)
                        val r = port.read(slice, READ_TIMEOUT)
                        if (r <= 0) break
                        System.arraycopy(slice, 0, packet, offset, r)
                        offset += r
                    }
                    if (offset != packet.size) {
                        state = State.SYNC0
                        continue
                    }
                    emitPacket()
                    state = State.LOCKED
                }
                State.LOCKED -> {
                    var offset = 0
                    while (offset < packet.size) {
                        val slice = ByteArray(packet.size - offset)
                        val r = port.read(slice, READ_TIMEOUT)
                        if (r <= 0) break
                        System.arraycopy(slice, 0, packet, offset, r)
                        offset += r
                    }
                    if (offset != packet.size || packet[0] != header0) {
                        AppLog.d(TAG, "Serial sync lost")
                        state = State.SYNC0
                        continue
                    }
                    emitPacket()
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Debug helper that continuously reads raw bytes from the serial port and
     * logs them in hex format. This ignores packet boundaries and is useful
     * when verifying the raw lidar output.
     */
    fun debugReadHex(): Flow<Unit> = flow<Unit> {
        val buffer = ByteArray(64)
        AppLog.d(TAG, "Starting raw hex dump")
        while (true) {
            val n = port.read(buffer, READ_TIMEOUT)
            if (n > 0) {
                val hex = buffer.take(n).joinToString(" ") { b: Byte ->
                    String.format("%02x", b.toInt() and 0xFF)
                }
                AppLog.d(TAG, hex)
            }
        }
    }.flowOn(Dispatchers.IO)
}
