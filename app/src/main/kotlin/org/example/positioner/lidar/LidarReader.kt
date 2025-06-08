package org.example.positioner.lidar

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.InputStream

/**
 * Continuously read measurement packets from the serial port and emit
 * individual measurements as a [Flow]. The implementation searches for
 * packet headers (0x54, 0x2C) and uses [LidarParser] to decode the packet.
 */
class LidarReader(private val input: InputStream) {
    private val parser = LidarParser()

    companion object {
        private const val TAG = "LidarReader"

        /**
         * Open the first available USB serial port (CP210x) with default settings.
         * Returns null if no device is available or permission is missing.
         */
        fun openDefault(context: Context): LidarReader? {
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
            val driver = drivers.firstOrNull() ?: return null
            var connection = manager.openDevice(driver.device)
            if (connection == null) {
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
            return LidarReader(port.inputStream)
        }
    }

    fun measurements(): Flow<LidarMeasurement> = flow {
        val packet = ByteArray(47)
        val header = byteArrayOf(0x54.toByte(), 0x2C.toByte())
        val buffer = ByteArray(1)
        Log.d(TAG, "Starting measurement loop")
        while (true) {
            // search for header byte 0x54
            if (input.read(buffer) != 1) continue
            if (buffer[0] != header[0]) continue
            if (input.read(buffer) != 1 || buffer[0] != header[1]) continue
            // read the rest of the packet
            var read = 0
            while (read < packet.size - 2) {
                val r = input.read(packet, 2 + read, packet.size - 2 - read)
                if (r <= 0) break
                read += r
            }
            if (read != packet.size - 2) {
                Log.d(TAG, "Incomplete packet read: $read bytes")
                continue
            }
            packet[0] = header[0]
            packet[1] = header[1]
            try {
                val measures = parser.parse(packet)
                Log.d(TAG, "Parsed packet with ${'$'}{measures.size} measurements")
                for (m in measures) emit(m)
            } catch (e: IllegalArgumentException) {
                Log.d(TAG, "Malformed packet", e)
            }
        }
    }.flowOn(Dispatchers.IO)
}
