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
                    address = device.address
                )
            }
            // Prioritize printers with "micro" in the name to the top of the list!
            list.sortedWith(
                compareByDescending<BluetoothPrinterDevice> { it.name.contains("micro", ignoreCase = true) }
                    .thenBy { it.name }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun getSavedPrinter(context: Context): BluetoothPrinterDevice? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val addr = prefs.getString(KEY_PRINTER_ADDR, null)
        val name = prefs.getString(KEY_PRINTER_NAME, null)
        val paired = getPairedPrinters(context)

        // 1. If saved printer is valid and exists among paired devices, return it
        if (addr != null && name != null) {
            val matching = paired.firstOrNull { it.address.equals(addr, ignoreCase = true) }
            if (matching != null) return matching

            val microInPaired = paired.firstOrNull { it.name.contains("micro", ignoreCase = true) }
            if (microInPaired != null && !name.contains("micro", ignoreCase = true)) {
                savePrinter(context, microInPaired)
                return microInPaired
            }
            return BluetoothPrinterDevice(name, addr)
        }

        // 2. If nothing saved yet, prioritize device with "micro" in its name as DEFAULT!
        val microDevice = paired.firstOrNull { it.name.contains("micro", ignoreCase = true) }
            ?: paired.firstOrNull()
        if (microDevice != null) {
            savePrinter(context, microDevice)
            return microDevice
        }

        // 3. Fallback default entry so user always sees Micro as default even before Bluetooth is paired
        return BluetoothPrinterDevice(name = "Micro (Default)", address = "")
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

    fun getSavedAutoCut(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("saved_auto_cut", true)
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
        style: Int = 1,
        autoCutEachVoucher: Boolean = true
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
            val escposData = buildVouchersEscPos(vouchers, paperWidth, style, autoCutEachVoucher)
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
     * Prints a short test slip saying ONLY "PRINT SUCCESS" to save maximum paper!
     */
    @SuppressLint("MissingPermission")
    suspend fun printTestReceiptDirect(
        context: Context,
        profiles: List<UserProfile> = emptyList(),
        deviceAddress: String? = null,
        paperWidth: PaperWidth = getSavedPaperWidth(context)
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val targetAddr = deviceAddress ?: getSavedPrinter(context)?.address
        if (targetAddr.isNullOrBlank()) {
            return@withContext Result.failure(Exception("No Bluetooth printer selected."))
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
        style: Int = 1,
        autoCutEachVoucher: Boolean = true
    ): ByteArray {
        val stream = ByteArrayOutputStream()

        // 1. Initialize printer
        stream.write(byteArrayOf(0x1B, 0x40)) // ESC @ (Initialize)
        stream.write(byteArrayOf(0x1B, 0x61, 0x01)) // Center raster images

        vouchers.forEachIndexed { index, voucher ->
            // Render crisp, high-contrast monochrome Excel table bitmap
            val bmp = renderVoucherExcelBitmap(voucher, paperWidth, style)
            stream.write(bitmapToEscPosRaster(bmp))

            if (autoCutEachVoucher) {
                // Auto-cut on each voucher requested by user!
                // 1. Advance paper past thermal cutter blade (typically 12-16mm = 4 text lines)
                stream.write(byteArrayOf(0x1B, 0x64, 0x04)) // ESC d 4
                // 2. Standard ESC/POS partial cut (Function A: GS V 1)
                stream.write(byteArrayOf(0x1D, 0x56, 0x01))
                // 3. Desktop POS-80 partial cut (Function B: GS V B 0)
                stream.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00))
                // 4. Initial feed spacing for the next voucher
                stream.write(byteArrayOf(0x1B, 0x4A, 0x10))
            } else {
                // Minimal paper spacing between vouchers (only 1 line)
                if (index < vouchers.size - 1) {
                    stream.write(byteArrayOf(0x1B, 0x64, 0x01)) // Feed 1 line
                }
            }
        }

        // If not cutting each voucher, cut once at the very end
        if (!autoCutEachVoucher) {
            stream.write(byteArrayOf(0x1B, 0x64, 0x04)) // Feed 4 lines
            stream.write(byteArrayOf(0x1D, 0x56, 0x01))
            stream.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00))
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
        style: Int
    ): Bitmap {
        val is80 = paperWidth == PaperWidth.WIDTH_80MM
        val totalWidth = if (is80) 576 else 384
        val scale = if (is80) 1.5f else 1.0f

        val cardHeight = when (style) {
            2 -> if (is80) 110 else 74 // Slim Compact Excel row
            3 -> if (is80) 196 else 130 // 3-Tier Full-Width Stacked Excel Table
            else -> if (is80) 156 else 104 // Standard 2-Compartment Excel Table
        }

        val bitmap = Bitmap.createBitmap(totalWidth, cardHeight, Bitmap.Config.ARGB_8888)
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
            textAlign = Paint.Align.CENTER
        }

        val marginX = 4f
        val boxW = totalWidth - 8f
        val rect = RectF(marginX, 2f, marginX + boxW, cardHeight - 2f)
        canvas.drawRect(rect, borderPaint)

        when (style) {
            2 -> {
                // Style 2: Slim Excel Box (2 Cells, Maximum Paper Saving with Huge Code)
                val splitX = marginX + boxW * 0.58f
                canvas.drawLine(splitX, 2f, splitX, cardHeight - 2f, borderPaint)

                // Left Cell: Huge Bold Code
                val leftCenter = marginX + (splitX - marginX) / 2f
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                val codeMaxW = (splitX - marginX) - 10f
                val cSize = autoFitTextSize(textPaint, voucher.code, codeMaxW, if (is80) 58f else 36f, 18f)
                canvas.drawText(voucher.code, leftCenter, cardHeight / 2f + (cSize * 0.35f), textPaint)

                // Right Cell: Profile & Price
                val rightCenter = splitX + (marginX + boxW - splitX) / 2f
                val rightMaxW = (marginX + boxW - splitX) - 10f
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                autoFitTextSize(textPaint, voucher.profileName, rightMaxW, if (is80) 30f else 19f, 13f)
                canvas.drawText(voucher.profileName, rightCenter, if (is80) 44f else 29f, textPaint)

                val quota = VoucherPrinter.formatQuotaString(voucher.dataLimitMb)
                val priceStr = if (voucher.price > 0) "${"%,d".format(Locale.US, voucher.price.toLong())} Ks" else "Free"
                autoFitTextSize(textPaint, "$quota • $priceStr", rightMaxW, if (is80) 28f else 18f, 13f)
                canvas.drawText("$quota • $priceStr", rightCenter, if (is80) 88f else 58f, textPaint)
            }
            3 -> {
                // Style 3: 3-Tier Full-Width Stacked Excel Table (Full-Width Giant Code)
                val row1H = if (is80) 40f else 27f
                val row2H = if (is80) 138f else 90f
                canvas.drawLine(marginX, row1H, marginX + boxW, row1H, linePaint)
                canvas.drawLine(marginX, row2H, marginX + boxW, row2H, linePaint)

                // Row 1: Header (HOTSPOT VOUCHER | PROFILE)
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textPaint.textSize = if (is80) 25f else 16f
                canvas.drawText("HOTSPOT VOUCHER   |   ${voucher.profileName}", totalWidth / 2f, if (is80) 28f else 19f, textPaint)

                // Row 2: ENORMOUS VOUCHER CODE (Full Width, Centered)
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                val codeMaxW = boxW - 16f
                val cSize = autoFitTextSize(textPaint, voucher.code, codeMaxW, if (is80) 74f else 48f, 24f)
                val midY = row1H + (row2H - row1H) / 2f
                canvas.drawText(voucher.code, totalWidth / 2f, midY + (cSize * 0.35f), textPaint)

                // Row 3: 3 Sub-cells for Quota, Validity, Price
                val col1W = boxW / 3f
                val col2W = col1W * 2f
                canvas.drawLine(marginX + col1W, row2H, marginX + col1W, cardHeight - 2f, linePaint)
                canvas.drawLine(marginX + col2W, row2H, marginX + col2W, cardHeight - 2f, linePaint)

                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                val subCellMaxW = col1W - 6f
                val botMidY = row2H + (cardHeight - 2f - row2H) / 2f

                val quota = VoucherPrinter.formatQuotaString(voucher.dataLimitMb)
                val qSize = autoFitTextSize(textPaint, quota, subCellMaxW, if (is80) 28f else 18f, 13f)
                canvas.drawText(quota, marginX + col1W / 2f, botMidY + (qSize * 0.35f), textPaint)

                val valStr = VoucherPrinter.formatValidityString(voucher.durationMinutes, voucher.validityDays)
                val vSize = autoFitTextSize(textPaint, valStr, subCellMaxW, if (is80) 28f else 18f, 13f)
                canvas.drawText(valStr, marginX + col1W * 1.5f, botMidY + (vSize * 0.35f), textPaint)

                val priceStr = if (voucher.price > 0) "${"%,d".format(Locale.US, voucher.price.toLong())} Ks" else "Free"
                val pSize = autoFitTextSize(textPaint, priceStr, subCellMaxW, if (is80) 34f else 22f, 14f)
                canvas.drawText(priceStr, marginX + col1W * 2.5f, botMidY + (pSize * 0.35f), textPaint)
            }
            else -> {
                // Style 1 (DEFAULT): Standard 2-Compartment Excel Table (Code | Profile & Limits)
                val splitX = marginX + boxW * 0.58f
                canvas.drawLine(splitX, 2f, splitX, cardHeight - 2f, borderPaint)

                // Left Cell: Header label + Giant Centered Code
                val leftCenter = marginX + (splitX - marginX) / 2f
                val headerH = if (is80) 30f else 20f
                canvas.drawLine(marginX, headerH, splitX, headerH, linePaint)

                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textPaint.textSize = if (is80) 16f else 11.5f
                canvas.drawText(if (voucher.isAccount) "ACCOUNT LOGIN" else "VOUCHER CODE", leftCenter, if (is80) 21f else 14.5f, textPaint)

                val leftMaxW = (splitX - marginX) - 10f
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

                val codeAreaH = cardHeight - 2f - headerH
                val codeCenterY = headerH + (codeAreaH / 2f)

                if (voucher.isAccount) {
                    val accSize = autoFitTextSize(textPaint, voucher.code, leftMaxW, if (is80) 44f else 28f, 18f)
                    canvas.drawText(voucher.code, leftCenter, headerH + (codeAreaH * 0.40f) + (accSize * 0.32f), textPaint)
                    val passSize = autoFitTextSize(textPaint, "P: ${voucher.password}", leftMaxW, if (is80) 32f else 20f, 14f)
                    canvas.drawText("P: ${voucher.password}", leftCenter, headerH + (codeAreaH * 0.82f) + (passSize * 0.32f), textPaint)
                } else {
                    // Massive, centered voucher code
                    val cSize = autoFitTextSize(textPaint, voucher.code, leftMaxW, if (is80) 66f else 44f, 22f)
                    canvas.drawText(voucher.code, leftCenter, codeCenterY + (cSize * 0.35f), textPaint)
                }

                // Right Cell: 3 neat Excel rows
                val rowH = (cardHeight - 4f) / 3f
                canvas.drawLine(splitX, 2f + rowH, marginX + boxW, 2f + rowH, linePaint)
                canvas.drawLine(splitX, 2f + rowH * 2f, marginX + boxW, 2f + rowH * 2f, linePaint)

                val rightCenter = splitX + (marginX + boxW - splitX) / 2f
                val rightMaxW = (marginX + boxW - splitX) - 10f

                // Row 1: Profile Name (Large Bold)
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                val profSize = autoFitTextSize(textPaint, voucher.profileName, rightMaxW, if (is80) 34f else 22f, 14f)
                val r1Center = 2f + (rowH / 2f)
                canvas.drawText(voucher.profileName, rightCenter, r1Center + (profSize * 0.35f), textPaint)

                // Row 2: Quota & Validity
                val quota = VoucherPrinter.formatQuotaString(voucher.dataLimitMb)
                val valStr = VoucherPrinter.formatValidityString(voucher.durationMinutes, voucher.validityDays)
                val limitText = "$quota • $valStr"
                val qvSize = autoFitTextSize(textPaint, limitText, rightMaxW, if (is80) 30f else 19f, 13f)
                val r2Center = 2f + rowH + (rowH / 2f)
                canvas.drawText(limitText, rightCenter, r2Center + (qvSize * 0.35f), textPaint)

                // Row 3: Price in Ks (Extra Bold & Large)
                val priceStr = if (voucher.price > 0) "${"%,d".format(Locale.US, voucher.price.toLong())} Ks" else "Free"
                val prSize = autoFitTextSize(textPaint, priceStr, rightMaxW, if (is80) 38f else 24f, 15f)
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
        showAutoCut: Boolean = true
    ): Bitmap {
        val is80 = paperWidth == PaperWidth.WIDTH_80MM
        val singleBmp = renderVoucherExcelBitmap(voucher, paperWidth, style)
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
            val label = "✂️ AUTO-CUT ON EACH VOUCHER ✂️"
            val textW = textPaint.measureText(label)
            canvas.drawRect(width / 2f - textW / 2f - 8f, cutY - 11f, width / 2f + textW / 2f + 8f, cutY + 11f, pillPaint)
            canvas.drawText(label, width / 2f, cutY + (if (is80) 6f else 4.5f), textPaint)
        }

        currentY += cutSpacing

        // Draw voucher #2 with next sequential code
        val nextDigit = ((voucher.code.lastOrNull()?.digitToIntOrNull() ?: 3) + 1) % 10
        val v2 = voucher.copy(code = voucher.code.dropLast(1) + nextDigit)
        val singleBmp2 = renderVoucherExcelBitmap(v2, paperWidth, style)
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
