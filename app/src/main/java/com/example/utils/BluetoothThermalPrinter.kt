package com.example.utils

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import androidx.core.content.ContextCompat
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.example.domain.models.UserProfile
import com.example.domain.models.Voucher
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.UUID

data class BluetoothPrinterDevice(
    val name: String,
    val address: String,
    val isConnected: Boolean = false
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

    fun hasBluetoothPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        return try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bm?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            adapter != null && adapter.isEnabled
        } catch (e: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun isDeviceConnected(device: BluetoothDevice): Boolean {
        return try {
            val method = device.javaClass.getMethod("isConnected")
            (method.invoke(device) as? Boolean) ?: false
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun getPairedPrinters(context: Context): List<BluetoothPrinterDevice> {
        return try {
            if (!hasBluetoothPermission(context)) return emptyList()
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bm?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            if (adapter == null || !adapter.isEnabled) return emptyList()
            val bonded = adapter.bondedDevices ?: return emptyList()
            val list = bonded.map { device ->
                BluetoothPrinterDevice(
                    name = device.name ?: "Unknown Device",
                    address = device.address,
                    isConnected = isDeviceConnected(device)
                )
            }
            // Put actively connected devices first, then sort alphabetically by name
            list.sortedWith(
                compareByDescending<BluetoothPrinterDevice> { it.isConnected }
                    .thenBy { it.name.lowercase(Locale.getDefault()) }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Resolves the printer to use on this phone:
     * 1. Actively connected Bluetooth printer takes top priority.
     * 2. If no printer is actively connected, use the user's previously saved printer if paired.
     * 3. If there is only 1 paired printer on this phone, use that printer automatically.
     * 4. Otherwise returns null so the user is prompted to connect or select a printer.
     */
    fun getActivePrinter(context: Context): BluetoothPrinterDevice? {
        val paired = getPairedPrinters(context)
        if (paired.isEmpty()) return null

        // 1. If any paired printer is currently connected via Bluetooth, use it
        val connectedDevice = paired.firstOrNull { it.isConnected }
        if (connectedDevice != null) {
            savePrinter(context, connectedDevice)
            return connectedDevice
        }

        // 2. Check saved printer from preferences if still paired
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val addr = prefs.getString(KEY_PRINTER_ADDR, null)
        if (!addr.isNullOrBlank()) {
            val matching = paired.firstOrNull { it.address.equals(addr, ignoreCase = true) }
            if (matching != null) return matching
        }

        // 3. If only one printer is paired on this phone, use it automatically
        if (paired.size == 1) {
            val single = paired.first()
            savePrinter(context, single)
            return single
        }

        return null
    }

    fun getSavedPrinter(context: Context): BluetoothPrinterDevice? {
        return getActivePrinter(context) ?: run {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val addr = prefs.getString(KEY_PRINTER_ADDR, null)
            val name = prefs.getString(KEY_PRINTER_NAME, null)
            if (!addr.isNullOrBlank() && !name.isNullOrBlank()) {
                BluetoothPrinterDevice(name = name, address = addr, isConnected = false)
            } else {
                null
            }
        }
    }

    fun savePrinter(context: Context, device: BluetoothPrinterDevice) {
        if (device.address.isBlank()) return
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

    fun getSavedAutoCut(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("saved_auto_cut", false) // Default to false (Compact Continuous Strip / Saves Paper!)
    }

    fun saveAutoCut(context: Context, autoCut: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("saved_auto_cut", autoCut).apply()
    }

    fun getSavedDefaultStyle(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt("saved_default_style", 2) // Default to Style 2 (Ultra-Micro)
    }

    fun saveDefaultStyle(context: Context, style: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt("saved_default_style", style).apply()
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
        paperWidth: PaperWidth = getSavedPaperWidth(context),
        style: Int = 2,
        autoCutEachVoucher: Boolean = getSavedAutoCut(context),
        routerSsid: String? = null,
        loginUrl: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (vouchers.isEmpty()) return@withContext Result.failure(Exception("No vouchers to print"))

        val targetAddr = deviceAddress ?: getActivePrinter(context)?.address ?: getSavedPrinter(context)?.address
        if (targetAddr.isNullOrBlank()) {
            return@withContext Result.failure(Exception("No connected or paired Bluetooth printer found. Please connect to your printer."))
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
            val escposData = buildVouchersEscPos(vouchers, paperWidth, style, autoCutEachVoucher, routerSsid, loginUrl)
            outputStream.write(escposData)
            outputStream.flush()

            // Dynamic delay based on voucher count to ensure printer hardware buffer completely
            // finishes burning dots on paper before closing socket (prevents cutting off the last ticket!)
            val bufferDrainDelayMs = maxOf(2000L, vouchers.size * 650L)
            Thread.sleep(bufferDrainDelayMs)
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
     * Prints a short test slip saying ONLY "PRINT SUCCESS" to save maximum paper!
     */
    @SuppressLint("MissingPermission")
    suspend fun printTestReceiptDirect(
        context: Context,
        profiles: List<UserProfile> = emptyList(),
        deviceAddress: String? = null,
        paperWidth: PaperWidth = getSavedPaperWidth(context)
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val targetAddr = deviceAddress ?: getActivePrinter(context)?.address ?: getSavedPrinter(context)?.address
        if (targetAddr.isNullOrBlank()) {
            return@withContext Result.failure(Exception("No connected or paired Bluetooth printer found. Please connect to your printer."))
        }

        var socket: BluetoothSocket? = null
        try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bm?.adapter ?: BluetoothAdapter.getDefaultAdapter()
                ?: return@withContext Result.failure(Exception("Bluetooth not supported"))

            if (!adapter.isEnabled) {
                return@withContext Result.failure(Exception("Bluetooth is disabled"))
            }

            val device = adapter.getRemoteDevice(targetAddr)
            adapter.cancelDiscovery()

            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()

            val stream = ByteArrayOutputStream()
            stream.write(byteArrayOf(0x1B, 0x40)) // ESC @ (Initialize)
            stream.write(byteArrayOf(0x1B, 0x61, 0x01)) // Center

            val border = "-".repeat(paperWidth.columns) + "\n"
            stream.write(border.toByteArray(Charsets.US_ASCII))
            stream.write(byteArrayOf(0x1B, 0x45, 0x01)) // Bold ON
            stream.write(byteArrayOf(0x1D, 0x21, 0x11)) // Double width & height
            stream.write("PRINT SUCCESS\n".toByteArray(Charsets.US_ASCII))
            stream.write(byteArrayOf(0x1D, 0x21, 0x00)) // Normal
            stream.write(byteArrayOf(0x1B, 0x45, 0x00)) // Bold OFF
            stream.write(border.toByteArray(Charsets.US_ASCII))
            stream.write("\n\n".toByteArray(Charsets.US_ASCII))
            stream.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00)) // Cut

            val outputStream = socket.outputStream
            outputStream.write(stream.toByteArray())
            outputStream.flush()
            Thread.sleep(200)
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("Test Print Failed: ${e.localizedMessage ?: "Printer Error"}"))
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    // =========================================================================
    // EXCEL TABLE GRAPHICS BUILDER (Crisp Solid Black Borders & Giant Auto-Scaled Fonts)
    // =========================================================================

    private fun buildVouchersEscPos(
        vouchers: List<Voucher>,
        paperWidth: PaperWidth,
        style: Int = 2,
        autoCutEachVoucher: Boolean = false,
        routerSsid: String? = null,
        loginUrl: String? = null
    ): ByteArray {
        val stream = ByteArrayOutputStream()

        // 1. Initialize printer
        stream.write(byteArrayOf(0x1B, 0x40)) // ESC @ (Initialize)
        stream.write(byteArrayOf(0x1B, 0x61, 0x01)) // Center raster images

        vouchers.forEachIndexed { index, voucher ->
            // Render crisp, high-contrast monochrome Excel table bitmap
            val bmp = renderVoucherExcelBitmap(voucher, paperWidth, style, routerSsid, loginUrl)
            stream.write(bitmapToEscPosRaster(bmp))

            if (index < vouchers.size - 1) {
                if (autoCutEachVoucher) {
                    if (paperWidth == PaperWidth.WIDTH_80MM) {
                        // Desktop POS-80 with hardware guillotine cutter: advance to blade & partial cut
                        stream.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00)) // GS V B 0
                    } else {
                        // 58mm portable printers: feed ~12mm (3 lines) to align with manual tear teeth
                        stream.write(byteArrayOf(0x1B, 0x64, 0x03)) // ESC d 3
                    }
                } else {
                    // COMPACT CONTINUOUS STRIP (Default / Maximum Paper Saving):
                    // Only 20 dots (~2.5mm) between consecutive vouchers! Saves over 80% paper!
                    stream.write(byteArrayOf(0x1B, 0x4A, 0x14)) // ESC J 20
                }
            }
        }

        // Final feed at the end of the entire print batch:
        // Advance paper ~16mm (4 lines) past the tear bar so the final voucher
        // feeds completely out and can be torn off cleanly without getting stuck or cut in half!
        stream.write(byteArrayOf(0x1B, 0x64, 0x04)) // ESC d 4
        if (autoCutEachVoucher && paperWidth == PaperWidth.WIDTH_80MM) {
            stream.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00)) // GS V B 0 (Final cut)
        }

        return stream.toByteArray()
    }

    private fun autoFitTextSize(
        paint: Paint,
        text: String,
        maxWidth: Float,
        preferredSize: Float,
        minSize: Float = 12f
    ): Float {
        paint.textSize = preferredSize
        while (paint.measureText(text) > maxWidth && paint.textSize > minSize) {
            paint.textSize -= 0.5f
        }
        return paint.textSize
    }

    /**
     * Renders a voucher as a true Excel table with solid black borders and giant typography!
     * Perfectly tailored for 58mm (384px) and 80mm (576px) printhead widths.
     * Typography maximized for crystal-clear readability even on compact paper.
     */
    fun renderVoucherExcelBitmap(
        voucher: Voucher,
        paperWidth: PaperWidth,
        style: Int,
        routerSsid: String? = null,
        loginUrl: String? = null
    ): Bitmap {
        val is80 = paperWidth == PaperWidth.WIDTH_80MM
        val totalWidth = if (is80) 576 else 384
        val scale = if (is80) 1.5f else 1.0f

        val boxHeight = when (style) {
            2 -> if (is80) 116 else 78 // Slim Compact Excel row
            3 -> if (is80) 196 else 130 // 3-Tier Full-Width Stacked Excel Table
            else -> if (is80) 156 else 104 // Standard 2-Compartment Excel Table
        }

        val hasUrl = !loginUrl.isNullOrBlank()
        val totalCardHeight = boxHeight

        val bitmap = Bitmap.createBitmap(totalWidth, totalCardHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val borderPaint = Paint().apply {
            color = Color.BLACK
            this.style = Paint.Style.STROKE
            strokeWidth = if (is80) 3.5f else 3f
            isAntiAlias = false
        }
        val linePaint = Paint().apply {
            color = Color.BLACK
            this.style = Paint.Style.STROKE
            strokeWidth = if (is80) 2.5f else 2f
            isAntiAlias = false
        }
        val textPaint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
            isFakeBoldText = true // Extra bold so thermal dots are thick & solid (prevents faint strokes on 4/7/A)
            textAlign = Paint.Align.CENTER
        }

        val marginX = 4f
        val boxW = totalWidth - 8f
        val rect = RectF(marginX, 2f, marginX + boxW, boxHeight - 2f)
        canvas.drawRect(rect, borderPaint)

        when (style) {
            2 -> {
                // Style 2: Slim Excel Box (2 Cells, Maximum Paper Saving with Robust 4-12 Digit Scaling)
                val splitX = marginX + boxW * 0.55f
                canvas.drawLine(splitX, 2f, splitX, boxHeight - 2f, borderPaint)

                // Left Cell: Bold Code or Account (User + Pass) - Centered with full vertical space
                val leftCenter = marginX + (splitX - marginX) / 2f
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                val codeMaxW = (splitX - marginX) - 10f
                val isAcc = voucher.isAccount || (voucher.password.isNotBlank() && voucher.password != voucher.username)
                
                if (isAcc) {
                    val uText = "U: ${voucher.username}"
                    val pText = "P: ${voucher.password}"
                    val uSize = autoFitTextSize(textPaint, uText, codeMaxW, if (is80) 34f else 22f, 11f)
                    canvas.drawText(uText, leftCenter, if (is80) 44f else 29f, textPaint)
                    val pSize = autoFitTextSize(textPaint, pText, codeMaxW, if (is80) 34f else 22f, 11f)
                    canvas.drawText(pText, leftCenter, if (is80) 88f else 58f, textPaint)
                } else {
                    // Adaptively fill box to biggest available font size without exceeding borders
                    val maxAllowedH = (boxHeight - 4f) * 0.70f
                    val preferredCodeSize = if (is80) maxOf(maxAllowedH, 60f) else maxOf(maxAllowedH, 40f)
                    val cSize = autoFitTextSize(textPaint, voucher.code, codeMaxW, preferredCodeSize, if (is80) 15f else 10.5f)
                    canvas.drawText(voucher.code, leftCenter, boxHeight / 2f + (cSize * 0.35f), textPaint)
                }

                // Right Cell: Profile & Price (Clean, no SSID)
                val rightCenter = splitX + (marginX + boxW - splitX) / 2f
                val rightMaxW = (marginX + boxW - splitX) - 10f
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                val profLabel = voucher.profileName
                val profSize = autoFitTextSize(textPaint, profLabel, rightMaxW, if (is80) 28f else 18f, 10f)
                canvas.drawText(profLabel, rightCenter, if (is80) 42f else 28f, textPaint)

                val quota = VoucherPrinter.formatQuotaString(voucher.dataLimitMb)
                val effPrice = VoucherPrinter.resolveVoucherPrice(voucher)
                val priceStr = if (effPrice > 0) "${"%,d".format(Locale.US, effPrice.toLong())} Ks" else "Free"
                val limitPriceText = "$quota • $priceStr"
                val lpSize = autoFitTextSize(textPaint, limitPriceText, rightMaxW, if (is80) 26f else 16.5f, 10f)
                canvas.drawText(limitPriceText, rightCenter, if (is80) 86f else 57f, textPaint)
            }
            3 -> {
                // Style 3: 3-Tier Full-Width Stacked Excel Table (Full-Width Giant Code)
                val row1H = if (is80) 40f else 27f
                val row2H = if (is80) 138f else 90f
                canvas.drawLine(marginX, row1H, marginX + boxW, row1H, linePaint)
                canvas.drawLine(marginX, row2H, marginX + boxW, row2H, linePaint)

                val isAcc = voucher.isAccount || (voucher.password.isNotBlank() && voucher.password != voucher.username)

                // Row 1: Header (HOTSPOT VOUCHER | PROFILE)
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                val titleHeader = if (isAcc) "HOTSPOT ACCOUNT" else "HOTSPOT VOUCHER"
                val hText = "$titleHeader   |   ${voucher.profileName}"
                val hSize = autoFitTextSize(textPaint, hText, boxW - 12f, if (is80) 24f else 15.5f, 10f)
                canvas.drawText(hText, totalWidth / 2f, if (is80) 28f else 19f, textPaint)

                // Row 2: ENORMOUS VOUCHER CODE or USER + PASS - Centered with full vertical room
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                val codeMaxW = boxW - 16f
                val codeAreaH = row2H - row1H
                val codeCenterY = row1H + (codeAreaH / 2f)

                if (isAcc) {
                    val uText = "U: ${voucher.username}"
                    val pText = "P: ${voucher.password}"
                    val uSize = autoFitTextSize(textPaint, uText, codeMaxW, if (is80) 38f else 25f, 13f)
                    canvas.drawText(uText, totalWidth / 2f, row1H + (codeAreaH * 0.36f) + (uSize * 0.32f), textPaint)
                    val pSize = autoFitTextSize(textPaint, pText, codeMaxW, if (is80) 38f else 25f, 13f)
                    canvas.drawText(pText, totalWidth / 2f, row1H + (codeAreaH * 0.78f) + (pSize * 0.32f), textPaint)
                } else {
                    // Adaptively fill box to biggest available font size without exceeding borders
                    val maxAllowedH = codeAreaH * 0.72f
                    val preferredCodeSize = if (is80) maxOf(maxAllowedH, 80f) else maxOf(maxAllowedH, 54f)
                    val cSize = autoFitTextSize(textPaint, voucher.code, codeMaxW, preferredCodeSize, if (is80) 18f else 12f)
                    canvas.drawText(voucher.code, totalWidth / 2f, codeCenterY + (cSize * 0.35f), textPaint)
                }

                // Row 3: 3 Sub-cells for Quota, Validity, Price
                val col1W = boxW / 3f
                val col2W = col1W * 2f
                canvas.drawLine(marginX + col1W, row2H, marginX + col1W, boxHeight - 2f, linePaint)
                canvas.drawLine(marginX + col2W, row2H, marginX + col2W, boxHeight - 2f, linePaint)

                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                val subCellMaxW = col1W - 6f
                val botMidY = row2H + (boxHeight - 2f - row2H) / 2f

                val quota = VoucherPrinter.formatQuotaString(voucher.dataLimitMb)
                val qSize = autoFitTextSize(textPaint, quota, subCellMaxW, if (is80) 26f else 17f, 12f)
                canvas.drawText(quota, marginX + col1W / 2f, botMidY + (qSize * 0.35f), textPaint)

                val valStr = VoucherPrinter.formatValidityString(voucher.durationMinutes, voucher.validityDays)
                val vSize = autoFitTextSize(textPaint, valStr, subCellMaxW, if (is80) 26f else 17f, 12f)
                canvas.drawText(valStr, marginX + col1W * 1.5f, botMidY + (vSize * 0.35f), textPaint)

                val effPrice = VoucherPrinter.resolveVoucherPrice(voucher)
                val priceStr = if (effPrice > 0) "${"%,d".format(Locale.US, effPrice.toLong())} Ks" else "Free"
                val pSize = autoFitTextSize(textPaint, priceStr, subCellMaxW, if (is80) 32f else 20f, 13f)
                canvas.drawText(priceStr, marginX + col1W * 2.5f, botMidY + (pSize * 0.35f), textPaint)
            }
            else -> {
                // Style 1 (DEFAULT): Standard 2-Compartment Excel Table (Code | Profile & Limits)
                val splitX = marginX + boxW * 0.55f
                canvas.drawLine(splitX, 2f, splitX, boxHeight - 2f, borderPaint)

                val isAcc = voucher.isAccount || (voucher.password.isNotBlank() && voucher.password != voucher.username)

                // Left Cell: Header label + Giant Centered Code or Account (Clean, No URL)
                val leftCenter = marginX + (splitX - marginX) / 2f
                val headerH = if (is80) 30f else 20f
                canvas.drawLine(marginX, headerH, splitX, headerH, linePaint)

                val leftMaxW = (splitX - marginX) - 10f
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                val headerText = if (isAcc) "ACCOUNT LOGIN" else "VOUCHER CODE"
                val hSize = autoFitTextSize(textPaint, headerText, leftMaxW, if (is80) 15f else 10.5f, 8.5f)
                canvas.drawText(headerText, leftCenter, if (is80) 21f else 14.5f, textPaint)

                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

                val codeAreaH = boxHeight - 2f - headerH
                val codeCenterY = headerH + (codeAreaH / 2f)

                if (isAcc) {
                    val uText = "U: ${voucher.username}"
                    val accSize = autoFitTextSize(textPaint, uText, leftMaxW, if (is80) 32f else 21f, 12f)
                    canvas.drawText(uText, leftCenter, headerH + (codeAreaH * 0.36f) + (accSize * 0.32f), textPaint)
                    val passText = "P: ${voucher.password}"
                    val passSize = autoFitTextSize(textPaint, passText, leftMaxW, if (is80) 28f else 18f, 11f)
                    canvas.drawText(passText, leftCenter, headerH + (codeAreaH * 0.78f) + (passSize * 0.32f), textPaint)
                } else {
                    // Adaptively fill box to biggest available font size without exceeding borders
                    val maxAllowedH = codeAreaH * 0.72f
                    val preferredCodeSize = if (is80) maxOf(maxAllowedH, 70f) else maxOf(maxAllowedH, 48f)
                    val cSize = autoFitTextSize(textPaint, voucher.code, leftMaxW, preferredCodeSize, if (is80) 18f else 12f)
                    canvas.drawText(voucher.code, leftCenter, codeCenterY + (cSize * 0.35f), textPaint)
                }

                // Right Cell: 3 neat Excel rows
                val rowH = (boxHeight - 4f) / 3f
                canvas.drawLine(splitX, 2f + rowH, marginX + boxW, 2f + rowH, linePaint)
                canvas.drawLine(splitX, 2f + rowH * 2f, marginX + boxW, 2f + rowH * 2f, linePaint)

                val rightCenter = splitX + (marginX + boxW - splitX) / 2f
                val rightMaxW = (marginX + boxW - splitX) - 10f

                // Row 1: Profile Name (Large Bold)
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                val profSize = autoFitTextSize(textPaint, voucher.profileName, rightMaxW, if (is80) 30f else 20f, 13f)
                val r1Center = 2f + (rowH / 2f)
                canvas.drawText(voucher.profileName, rightCenter, r1Center + (profSize * 0.35f), textPaint)

                // Row 2: Quota & Validity
                val quota = VoucherPrinter.formatQuotaString(voucher.dataLimitMb)
                val valStr = VoucherPrinter.formatValidityString(voucher.durationMinutes, voucher.validityDays)
                val limitText = "$quota • $valStr"
                val qvSize = autoFitTextSize(textPaint, limitText, rightMaxW, if (is80) 28f else 18f, 12f)
                val r2Center = 2f + rowH + (rowH / 2f)
                canvas.drawText(limitText, rightCenter, r2Center + (qvSize * 0.35f), textPaint)

                // Row 3: Price in Ks (Extra Bold & Large)
                val effPrice = VoucherPrinter.resolveVoucherPrice(voucher)
                val priceStr = if (effPrice > 0) "${"%,d".format(Locale.US, effPrice.toLong())} Ks" else "Free"
                val prSize = autoFitTextSize(textPaint, priceStr, rightMaxW, if (is80) 34f else 22f, 13f)
                val r3Center = 2f + (rowH * 2f) + (rowH / 2f)
                canvas.drawText(priceStr, rightCenter, r3Center + (prSize * 0.35f), textPaint)
            }
        }

        return bitmap
    }

    /**
     * Generates a preview bitmap of a thermal paper roll showing 2 consecutive vouchers
     * and the dashed auto-cut line between them!
     */
    fun renderThermalPreviewBitmap(
        voucher: Voucher,
        paperWidth: PaperWidth,
        style: Int,
        showAutoCut: Boolean = true,
        routerSsid: String? = null,
        loginUrl: String? = null
    ): Bitmap {
        val is80 = paperWidth == PaperWidth.WIDTH_80MM
        val singleBmp = renderVoucherExcelBitmap(voucher, paperWidth, style, routerSsid, loginUrl)
        val width = singleBmp.width
        val singleH = singleBmp.height

        val cutSpacing = if (showAutoCut) (44 * (if (is80) 1.3f else 1.0f)).toInt() else (12 * (if (is80) 1.3f else 1.0f)).toInt()
        val totalHeight = singleH * 2 + cutSpacing + 20

        val previewBmp = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(previewBmp)
        canvas.drawColor(Color.WHITE)

        // Draw voucher #1
        canvas.drawBitmap(singleBmp, 0f, 6f, null)

        var currentY = 6f + singleH

        // Draw cut indicator between vouchers
        if (showAutoCut) {
            val cutY = currentY + cutSpacing / 2f
            val cutPaint = Paint().apply {
                color = Color.parseColor("#D32F2F")
                strokeWidth = 2f
                this.style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
            }
            canvas.drawLine(10f, cutY, width - 10f, cutY, cutPaint)

            val textPaint = Paint().apply {
                color = Color.parseColor("#C62828")
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = if (is80) 17f else 12.5f
                textAlign = Paint.Align.CENTER
            }
            val pillPaint = Paint().apply {
                color = Color.WHITE
                this.style = Paint.Style.FILL
            }
            val label = "✂️ TEAR / CUT SPACING ✂️"
            val textW = textPaint.measureText(label)
            canvas.drawRect(width / 2f - textW / 2f - 8f, cutY - 11f, width / 2f + textW / 2f + 8f, cutY + 11f, pillPaint)
            canvas.drawText(label, width / 2f, cutY + (if (is80) 6f else 4.5f), textPaint)
        } else {
            val cutY = currentY + cutSpacing / 2f
            val cutPaint = Paint().apply {
                color = Color.parseColor("#9E9E9E")
                strokeWidth = 1.5f
                this.style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
            }
            canvas.drawLine(14f, cutY, width - 14f, cutY, cutPaint)
        }

        currentY += cutSpacing

        // Draw voucher #2 with next sequential code
        val nextDigit = ((voucher.code.lastOrNull()?.digitToIntOrNull() ?: 3) + 1) % 10
        val v2 = voucher.copy(code = voucher.code.dropLast(1) + nextDigit)
        val singleBmp2 = renderVoucherExcelBitmap(v2, paperWidth, style, routerSsid, loginUrl)
        canvas.drawBitmap(singleBmp2, 0f, currentY, null)

        return previewBmp
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
                        // If darker than threshold, print black dot (high contrast for thermal heads, prevents dropped crossbars)
                        if (luminance < 195) {
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
