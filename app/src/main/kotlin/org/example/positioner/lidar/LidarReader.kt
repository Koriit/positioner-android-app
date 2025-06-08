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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException

/**
 * Continuously read measurement packets from the serial port and emit
 * individual measurements as a [Flow]. The implementation searches for
 * packet headers (0x54, 0x2C) and uses [LidarParser] to decode the packet.
 */
class LidarReader(private val port: UsbSerialPort) : LidarDataSource {
    private val parser = LidarParser()

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
            port.open(connection)
            port.setParameters(230400, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            // start continuous scan mode (0xA5 0x60)
            AppLog.d(TAG, "Sending start scan command")
            port.write(byteArrayOf(0xA5.toByte(), 0x60.toByte()), READ_TIMEOUT)
            return LidarReader(port)
        }
    }

    // Stream measurements emitted by the lidar. The blocking serial reads are
    // executed on Dispatchers.IO thanks to [flowOn] below so the UI thread
    // never blocks.
    override fun measurements(): Flow<LidarMeasurement> = flow {
        val packet = ByteArray(47)
        val header = byteArrayOf(0x54.toByte(), 0x2C.toByte())
        val buffer = ByteArray(1)
        AppLog.d(TAG, "Starting measurement loop")
        while (true) {
            // Continuously search for packet header. This loop runs until the
            // coroutine is cancelled.
            // Search for header byte 0x54
            if (port.read(buffer, READ_TIMEOUT) != 1) continue
            if (buffer[0] != header[0]) continue
            if (port.read(buffer, READ_TIMEOUT) != 1 || buffer[0] != header[1]) continue
            // read the rest of the packet
            var read = 0
            while (read < packet.size - 2) {
                val slice = ByteArray(packet.size - 2 - read)
                val r = port.read(slice, READ_TIMEOUT)
                if (r <= 0) break
                System.arraycopy(slice, 0, packet, 2 + read, r)
                read += r
            }
            if (read != packet.size - 2) {
                AppLog.d(TAG, "Incomplete packet read: $read bytes")
                continue
            }
            packet[0] = header[0]
            packet[1] = header[1]
            try {
                val measures = parser.parse(packet)
                AppLog.d(
                    TAG,
                    "Parsed packet with ${'$'}{measures.size} measurements"
                )
                for (m in measures) emit(m)
            } catch (e: IllegalArgumentException) {
                // Skip malformed packets but log the error for debugging
                AppLog.d(TAG, "Malformed packet", e)
            }
        }
    }.flowOn(Dispatchers.IO)
}
