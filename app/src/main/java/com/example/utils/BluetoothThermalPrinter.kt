package com.example.utils

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.example.domain.models.UserProfile
import com.example.domain.models.Voucher
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.UUID

data class BluetoothPrinterDevice(
    val name: String,
    val address: String
)

object BluetoothThermalPrinter {

    private const val PREFS_NAME = "bluetooth_printer_prefs"
    private const val KEY_PRINTER_NAME = "saved_printer_name"
    private const val KEY_PRINTER_ADDR = "saved_printer_address"
    private const val KEY_PAPER_WIDTH = "saved_paper_width" // "58" or "80"

    // Standard Bluetooth Serial Port Profile (SPP) UUID
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    enum class PaperWidth(val columns: Int, val widthDots: Int) {
        WIDTH_58MM(32, 384),
        WIDTH_80MM(48, 576)
    }

    @SuppressLint("MissingPermission")
    fun getPairedPrinters(context: Context): List<BluetoothPrinterDevice> {
        return try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bm?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            if (adapter == null || !adapter.isEnabled) return emptyList()
            adapter.bondedDevices?.map { device ->
                BluetoothPrinterDevice(
                    name = device.name ?: "Unknown Device",
                    address = device.address
                )
            }?.sortedBy { it.name } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun getSavedPrinter(context: Context): BluetoothPrinterDevice? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val addr = prefs.getString(KEY_PRINTER_ADDR, null) ?: return null
        val name = prefs.getString(KEY_PRINTER_NAME, "Thermal Printer") ?: "Thermal Printer"
        return BluetoothPrinterDevice(name, addr)
    }

    fun savePrinter(context: Context, device: BluetoothPrinterDevice) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_PRINTER_ADDR, device.address)
            .putString(KEY_PRINTER_NAME, device.name)
            .apply()
    }

    fun getSavedPaperWidth(context: Context): PaperWidth {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return if (prefs.getString(KEY_PAPER_WIDTH, "58") == "80") PaperWidth.WIDTH_80MM else PaperWidth.WIDTH_58MM
    }

    fun savePaperWidth(context: Context, width: PaperWidth) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PAPER_WIDTH, if (width == PaperWidth.WIDTH_80MM) "80" else "58").apply()
    }

    /**
     * Prints vouchers directly via Bluetooth RFCOMM socket.
     * ZERO external print services, ZERO Android PrintManager dialogs.
     */
    @SuppressLint("MissingPermission")
    suspend fun printVouchersDirect(
        context: Context,
        vouchers: List<Voucher>,
        deviceAddress: String? = null,
        paperWidth: PaperWidth = getSavedPaperWidth(context)
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (vouchers.isEmpty()) return@withContext Result.failure(Exception("No vouchers to print"))

        val targetAddr = deviceAddress ?: getSavedPrinter(context)?.address
        if (targetAddr.isNullOrBlank()) {
            return@withContext Result.failure(Exception("No Bluetooth printer selected. Please select a printer."))
        }

        var socket: BluetoothSocket? = null
        try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bm?.adapter ?: BluetoothAdapter.getDefaultAdapter()
                ?: return@withContext Result.failure(Exception("Bluetooth not supported on this device"))

            if (!adapter.isEnabled) {
                return@withContext Result.failure(Exception("Bluetooth is disabled. Please turn on Bluetooth."))
            }

            val device: BluetoothDevice = try {
                adapter.getRemoteDevice(targetAddr)
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("Invalid printer address: $targetAddr"))
            }

            adapter.cancelDiscovery()

            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()

            val outputStream = socket.outputStream
            val escposData = buildVouchersEscPos(vouchers, paperWidth)
            outputStream.write(escposData)
            outputStream.flush()

            // Small delay to ensure printer buffer completes
            Thread.sleep(300)
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("Bluetooth Print Failed: ${e.localizedMessage ?: "Could not connect to printer"}"))
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Prints a test slip directly to the Bluetooth thermal printer.
     */
    suspend fun printTestReceiptDirect(
        context: Context,
        profiles: List<UserProfile>,
        deviceAddress: String? = null,
        paperWidth: PaperWidth = getSavedPaperWidth(context)
    ): Result<Unit> {
        val sampleVouchers = if (profiles.isNotEmpty()) {
            profiles.take(3).mapIndexed { idx, p ->
                Voucher(
                    id = idx,
                    code = "TEST-${p.name.take(4).uppercase()}-${100 + idx}",
                    profileName = p.name,
                    price = p.price,
                    dataLimitMb = p.dataLimitMb,
                    validityDays = p.validityDays,
                    isUsed = false,
                    isPrinted = false
                )
            }
        } else {
            listOf(
                Voucher(id = 1, code = "TEST-1GB-01", profileName = "1GB_1H", price = 500.0, dataLimitMb = 1024, validityDays = 1),
                Voucher(id = 2, code = "TEST-3GB-02", profileName = "3GB_6H", price = 1000.0, dataLimitMb = 3072, validityDays = 3)
            )
        }
        return printVouchersDirect(context, sampleVouchers, deviceAddress, paperWidth)
    }

    // =========================================================================
    // ESC/POS COMMAND BUILDER
    // =========================================================================

    private fun buildVouchersEscPos(vouchers: List<Voucher>, paperWidth: PaperWidth): ByteArray {
        val stream = ByteArrayOutputStream()

        // 1. Initialize printer
        stream.write(byteArrayOf(0x1B, 0x40)) // ESC @ (Initialize)

        val separator = "-".repeat(paperWidth.columns) + "\n"
        val doubleSeparator = "=".repeat(paperWidth.columns) + "\n"

        vouchers.forEachIndexed { index, voucher ->
            // Center alignment
            stream.write(byteArrayOf(0x1B, 0x61, 0x01)) // ESC a 1 (Center)

            // Header
            stream.write(doubleSeparator.toByteArray(Charsets.US_ASCII))
            stream.write(byteArrayOf(0x1B, 0x45, 0x01)) // Bold ON
            stream.write(byteArrayOf(0x1D, 0x21, 0x01)) // Double height
            stream.write("ALLGOOD WIFI\n".toByteArray(Charsets.US_ASCII))
            stream.write(byteArrayOf(0x1D, 0x21, 0x00)) // Normal size
            stream.write("HOTSPOT INTERNET PASS\n".toByteArray(Charsets.US_ASCII))
            stream.write(byteArrayOf(0x1B, 0x45, 0x00)) // Bold OFF
            stream.write(separator.toByteArray(Charsets.US_ASCII))

            // Details - Left Aligned
            stream.write(byteArrayOf(0x1B, 0x61, 0x00)) // ESC a 0 (Left)
            val quotaStr = if (voucher.dataLimitMb > 0) "${voucher.dataLimitMb} MB" else "Unlimited Data"
            val priceStr = if (voucher.price > 0) "${"%,d".format(java.util.Locale.US, voucher.price.toLong())} Ks" else "Free"

            stream.write("Plan     : ${voucher.profileName}\n".toByteArray(Charsets.US_ASCII))
            stream.write("Validity : ${voucher.validityDays} Day(s) (Exp 12:00 AM)\n".toByteArray(Charsets.US_ASCII))
            stream.write("Data     : $quotaStr\n".toByteArray(Charsets.US_ASCII))
            stream.write("Price    : $priceStr\n".toByteArray(Charsets.US_ASCII))
            stream.write(separator.toByteArray(Charsets.US_ASCII))

            // Voucher Credentials - Centered & Big
            stream.write(byteArrayOf(0x1B, 0x61, 0x01)) // ESC a 1 (Center)
            val label = if (voucher.isAccount) "ACCOUNT LOGIN CREDENTIALS\n" else "VOUCHER CODE\n"
            stream.write(label.toByteArray(Charsets.US_ASCII))

            stream.write(byteArrayOf(0x1B, 0x45, 0x01)) // Bold ON
            stream.write(byteArrayOf(0x1D, 0x21, 0x11)) // Double Width & Height
            stream.write(" ${voucher.code} \n".toByteArray(Charsets.US_ASCII))
            stream.write(byteArrayOf(0x1D, 0x21, 0x00)) // Normal Size
            stream.write(byteArrayOf(0x1B, 0x45, 0x00)) // Bold OFF

            if (voucher.isAccount && voucher.password.isNotBlank() && voucher.password != voucher.code) {
                stream.write("Password: ${voucher.password}\n".toByteArray(Charsets.US_ASCII))
            }

            // QR Code for Instant Camera Login
            val loginUrl = "http://10.10.10.1/login?username=${voucher.code}&password=${voucher.password.ifBlank { voucher.code }}"
            val qrSize = if (paperWidth == PaperWidth.WIDTH_58MM) 180 else 240
            val qrBitmap = generateQrBitmap(loginUrl, qrSize)
            if (qrBitmap != null) {
                val rasterData = bitmapToEscPosRaster(qrBitmap)
                stream.write(rasterData)
                stream.write("Scan to Connect Automatically\n".toByteArray(Charsets.US_ASCII))
            }

            // Footer
            stream.write("Portal: http://10.10.10.1\n".toByteArray(Charsets.US_ASCII))
            stream.write(doubleSeparator.toByteArray(Charsets.US_ASCII))

            // Feed lines between vouchers
            if (index < vouchers.size - 1) {
                stream.write("\n\n".toByteArray(Charsets.US_ASCII))
            }
        }

        // Feed 4 lines and cut paper
        stream.write("\n\n\n\n".toByteArray(Charsets.US_ASCII))
        stream.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00)) // GS V B 0 (Cut paper)

        return stream.toByteArray()
    }

    /**
     * Converts a Bitmap to ESC/POS Raster Bit Image format (GS v 0 0)
     * Supported by all ESC/POS thermal printers.
     */
    private fun bitmapToEscPosRaster(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val widthBytes = (width + 7) / 8

        val stream = ByteArrayOutputStream()
        // GS v 0 m xL xH yL yH
        stream.write(0x1D)
        stream.write(0x76)
        stream.write(0x30)
        stream.write(0x00) // Mode: normal
        stream.write(widthBytes % 256)
        stream.write(widthBytes / 256)
        stream.write(height % 256)
        stream.write(height / 256)

        for (y in 0 until height) {
            for (byteIdx in 0 until widthBytes) {
                var currentByte = 0
                for (bitIdx in 0 until 8) {
                    val x = byteIdx * 8 + bitIdx
                    if (x < width) {
                        val pixel = bitmap.getPixel(x, y)
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF
                        val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                        // If dark (black dot), set bit to 1
                        if (luminance < 128) {
                            currentByte = currentByte or (1 shl (7 - bitIdx))
                        }
                    }
                }
                stream.write(currentByte)
            }
        }
        stream.write(0x0A) // Line feed after image
        return stream.toByteArray()
    }

    private fun generateQrBitmap(content: String, size: Int): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bmp.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
