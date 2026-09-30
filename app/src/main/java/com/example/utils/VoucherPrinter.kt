package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.example.domain.models.UserProfile
import com.example.domain.models.Voucher
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream

class VoucherPrintAdapter(
    private val context: Context,
    private val pdfFile: File
) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder("Vouchers.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback
    ) {
        try {
            pdfFile.inputStream().use { input ->
                FileOutputStream(destination.fileDescriptor).use { output ->
                    input.copyTo(output)
                }
            }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        }
    }
}

object VoucherPrinter {

    enum class PaperFormat {
        THERMAL_58MM,
        THERMAL_80MM,
        A4_PAGE
    }

    fun printVouchers(
        context: Context,
        vouchers: List<Voucher>,
        style: Int = 1,
        format: PaperFormat = PaperFormat.THERMAL_58MM
    ) {
        if (vouchers.isEmpty()) return
        val pdfFile = generatePdf(context, vouchers, style, format)
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "MikroTik Hotspot Vouchers"
        printManager.print(jobName, VoucherPrintAdapter(context, pdfFile), null)
    }

    /**
     * Prints a test slip saying ONLY "PRINT SUCCESS" to save paper!
     */
    fun printTestReceipt(
        context: Context,
        format: PaperFormat = PaperFormat.THERMAL_58MM,
        profiles: List<UserProfile> = emptyList()
    ) {
        val pdfDocument = PdfDocument()
        val rollWidth = if (format == PaperFormat.THERMAL_80MM) 226 else 164
        val pageInfo = PdfDocument.PageInfo.Builder(rollWidth, 110, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 13f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 8.5f
            textAlign = Paint.Align.CENTER
        }
        val linePaint = Paint().apply {
            color = Color.DKGRAY
            strokeWidth = 0.8f
        }

        canvas.drawLine(8f, 18f, (rollWidth - 8).toFloat(), 18f, linePaint)
        canvas.drawText("PRINT SUCCESS", rollWidth / 2f, 48f, paint)
        canvas.drawText("Printer Connected & Operational", rollWidth / 2f, 68f, subPaint)
        canvas.drawLine(8f, 88f, (rollWidth - 8).toFloat(), 88f, linePaint)

        pdfDocument.finishPage(page)
        val file = File(context.cacheDir, "test_print_success.pdf")
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()

        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        printManager.print("Test Print", VoucherPrintAdapter(context, file), null)
    }

    private fun generatePdf(
        context: Context,
        vouchers: List<Voucher>,
        style: Int,
        format: PaperFormat
    ): File {
        val pdfDocument = PdfDocument()

        when (format) {
            PaperFormat.THERMAL_58MM -> renderThermalRoll(pdfDocument, vouchers, style, 164) // 58mm width
            PaperFormat.THERMAL_80MM -> renderThermalRoll(pdfDocument, vouchers, style, 226) // 80mm width
            PaperFormat.A4_PAGE -> renderA4Sheet(pdfDocument, vouchers, style)
        }

        val file = File(context.cacheDir, "hotspot_vouchers.pdf")
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()
        return file
    }

    /**
     * Generates an exact, realistic preview bitmap of an A4 print sheet!
     * Shows high-density grid of vouchers with Excel solid borders and scissor cut lines.
     */
    fun renderA4PreviewBitmap(
        sampleVoucher: Voucher,
        style: Int,
        previewWidth: Int = 595,
        previewHeight: Int = 842
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val pageBorderPaint = Paint().apply {
            color = Color.parseColor("#CCCCCC")
            this.style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRect(0f, 0f, previewWidth.toFloat(), previewHeight.toFloat(), pageBorderPaint)

        // Header
        val headerPaint = Paint().apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 10f
            textAlign = Paint.Align.LEFT
        }
        val subHeaderPaint = Paint().apply {
            color = Color.DKGRAY
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 8f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("HOTSPOT VOUCHERS • A4 PRINT SHEET PREVIEW", 18f, 15f, headerPaint)
        val infoText = when (style) {
            2 -> "115 Vouchers / Page (5x23 Grid) • ✂️ Cut Along Lines"
            3 -> "33 Vouchers / Page (3x11 Grid) • ✂️ Cut Along Lines"
            else -> "68 Vouchers / Page (4x17 Grid) • ✂️ Cut Along Lines"
        }
        canvas.drawText(infoText, previewWidth - 18f, 15f, subHeaderPaint)

        val cols = when (style) {
            2 -> 5
            3 -> 3
            else -> 4
        }
        val cardWidth = when (style) {
            2 -> 108f
            3 -> 175f
            else -> 134f
        }
        val cardHeight = when (style) {
            2 -> 30f
            3 -> 60f
            else -> 42f
        }
        val gapX = if (style == 2) 5f else 6f
        val gapY = if (style == 2) 4f else 5f
        val marginX = (previewWidth - (cols * cardWidth + (cols - 1) * gapX)) / 2f
        val marginY = 22f

        val guidePaint = Paint().apply {
            color = Color.parseColor("#90A4AE")
            this.style = Paint.Style.STROKE
            strokeWidth = 0.6f
            pathEffect = DashPathEffect(floatArrayOf(3f, 3f), 0f)
        }

        var curY = marginY
        var seq = 101

        while (curY + cardHeight <= previewHeight - 14f) {
            for (col in 0 until cols) {
                val cellX = marginX + col * (cardWidth + gapX)
                val codePrefix = sampleVoucher.code.take(4).ifEmpty { "1819" }
                val v = sampleVoucher.copy(code = "$codePrefix-${seq++}")
                drawVoucherCard(canvas, v, cellX, curY, cardWidth, cardHeight, style)

                if (col < cols - 1) {
                    val cutX = cellX + cardWidth + (gapX / 2f)
                    canvas.drawLine(cutX, curY, cutX, curY + cardHeight, guidePaint)
                }
            }
            val cutY = curY + cardHeight + (gapY / 2f)
            canvas.drawLine(marginX, cutY, previewWidth - marginX, cutY, guidePaint)
            curY += cardHeight + gapY
        }

        return bitmap
    }

    private fun renderA4Sheet(pdfDocument: PdfDocument, vouchers: List<Voucher>, style: Int) {
        val pageWidth = 595 // A4 width in points
        val pageHeight = 842 // A4 height in points

        // High-density paper-saving grid for A4:
        // Style 1 (2-Compartment): 4 columns x 17 rows = 68 vouchers per page!
        // Style 2 (Ultra-Micro): 5 columns x 23 rows = 115 vouchers per page!
        // Style 3 (3-Tier Stacked): 3 columns x 11 rows = 33 vouchers per page!
        val cols = when (style) {
            2 -> 5
            3 -> 3
            else -> 4
        }
        val cardWidth = when (style) {
            2 -> 108f
            3 -> 175f
            else -> 134f
        }
        val cardHeight = when (style) {
            2 -> 30f
            3 -> 60f
            else -> 42f
        }
        val gapX = if (style == 2) 5f else 6f
        val gapY = if (style == 2) 4f else 5f
        val marginX = (pageWidth - (cols * cardWidth + (cols - 1) * gapX)) / 2f
        val marginY = 18f

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        var curX = marginX
        var curY = marginY

        vouchers.forEach { voucher ->
            if (curY + cardHeight > pageHeight - marginY) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                curX = marginX
                curY = marginY
            }

            drawVoucherCard(canvas, voucher, curX, curY, cardWidth, cardHeight, style)

            curX += cardWidth + gapX
            if (curX + cardWidth > pageWidth - marginX + 3f) {
                curX = marginX
                curY += cardHeight + gapY
            }
        }
        pdfDocument.finishPage(page)
    }

    private fun renderThermalRoll(
        pdfDocument: PdfDocument,
        vouchers: List<Voucher>,
        style: Int,
        rollWidth: Int
    ) {
        val is80 = rollWidth >= 200
        val cardHeight = when (style) {
            2 -> if (is80) 42 else 34
            3 -> if (is80) 80 else 66
            else -> if (is80) 64 else 50
        }
        val gapY = if (style == 2) 2 else 4
        val totalHeight = (cardHeight + gapY) * vouchers.size + 12

        val pageInfo = PdfDocument.PageInfo.Builder(rollWidth, Math.max(totalHeight, 80), 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        var curY = 6f
        vouchers.forEach { voucher ->
            drawVoucherCard(canvas, voucher, 4f, curY, (rollWidth - 8).toFloat(), cardHeight.toFloat(), style)
            curY += cardHeight + gapY.toFloat()
        }
        pdfDocument.finishPage(page)
    }

    private fun drawVoucherCard(
        canvas: Canvas,
        voucher: Voucher,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        style: Int
    ) {
        val is80 = width >= 180f
        val borderPaint = Paint().apply {
            color = Color.BLACK
            this.style = Paint.Style.STROKE
            strokeWidth = if (is80) 1.4f else 1.0f
        }
        val linePaint = Paint().apply {
            color = Color.BLACK
            this.style = Paint.Style.STROKE
            strokeWidth = if (is80) 1.0f else 0.8f
        }
        val titlePaint = Paint().apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val codePaint = Paint().apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val textPaint = Paint().apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val pricePaint = Paint().apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        // Draw Card border (One box per ticket with solid black border)
        val rect = RectF(x, y, x + width, y + height)
        canvas.drawRoundRect(rect, 2f, 2f, borderPaint)

        val centerX = x + (width / 2f)

        when (style) {
            2 -> {
                // Style 2: Ultra-Compact Micro (Auto-fit to largest readable size)
                val splitX = x + (width * 0.58f)
                val maxW = (splitX - x) - 4f
                val leftCenter = x + (splitX - x) / 2f
                var cSize = if (is80) 22f else 17f
                codePaint.textSize = cSize
                while (codePaint.measureText(voucher.code) > maxW && cSize > 9.5f) {
                    cSize -= 0.5f
                    codePaint.textSize = cSize
                }
                canvas.drawText(voucher.code, leftCenter, y + (height / 2f) + (cSize * 0.35f), codePaint)

                val rightCenter = splitX + (x + width - splitX) / 2f
                val rightMaxW = (x + width - splitX) - 4f
                val profileText = if (voucher.price > 0) "${voucher.profileName} • ${"%,d".format(java.util.Locale.US, voucher.price.toLong())} Ks" else voucher.profileName
                var pSize = if (is80) 15f else 12f
                textPaint.textSize = pSize
                while (textPaint.measureText(profileText) > rightMaxW && pSize > 7.5f) {
                    pSize -= 0.5f
                    textPaint.textSize = pSize
                }
                canvas.drawText(profileText, rightCenter, y + (height / 2f) + (pSize * 0.35f), textPaint)
            }
            3 -> {
                // Style 3: 3-Tier Full-Width Stacked Excel Table (Giant Code, Replaces QR)
                val row1H = y + height * 0.25f
                val row2H = y + height * 0.72f
                canvas.drawLine(x, row1H, x + width, row1H, linePaint)
                canvas.drawLine(x, row2H, x + width, row2H, linePaint)

                // Row 1: Header (HOTSPOT VOUCHER | Profile)
                titlePaint.textSize = if (is80) 12f else 9.5f
                canvas.drawText("HOTSPOT VOUCHER • ${voucher.profileName}", centerX, y + (height * 0.17f), titlePaint)

                // Row 2: MASSIVE VOUCHER CODE
                val codeMaxW = width - 8f
                var cSize = if (is80) 32f else 24f
                codePaint.textSize = cSize
                while (codePaint.measureText(voucher.code) > codeMaxW && cSize > 12f) {
                    cSize -= 0.5f
                    codePaint.textSize = cSize
                }
                val midY = row1H + (row2H - row1H) / 2f
                canvas.drawText(voucher.code, centerX, midY + (cSize * 0.35f), codePaint)

                // Row 3: 3 Sub-cells for Quota, Validity, Price
                val col1W = width / 3f
                val col2W = col1W * 2f
                val subCellMaxW = col1W - 4f
                val row3H = y + height - row2H
                canvas.drawLine(x + col1W, row2H, x + col1W, y + height, linePaint)
                canvas.drawLine(x + col2W, row2H, x + col2W, y + height, linePaint)

                var subSize = (row3H * 0.55f).coerceIn(8.5f, if (is80) 13f else 10f)
                textPaint.textSize = subSize
                val quota = formatQuotaString(voucher.dataLimitMb)
                while (textPaint.measureText(quota) > subCellMaxW && subSize > 7f) {
                    subSize -= 0.5f
                    textPaint.textSize = subSize
                }
                val botMidY = row2H + (row3H / 2f) + (subSize * 0.35f)
                canvas.drawText(quota, x + col1W / 2f, botMidY, textPaint)

                val valStr = formatValidityString(voucher.durationMinutes, voucher.validityDays)
                canvas.drawText(valStr, x + col1W * 1.5f, botMidY, textPaint)

                pricePaint.textSize = subSize + 0.5f
                val prText = if (voucher.price > 0) "${"%,d".format(java.util.Locale.US, voucher.price.toLong())} Ks" else "Free"
                while (pricePaint.measureText(prText) > subCellMaxW && pricePaint.textSize > 7f) {
                    pricePaint.textSize -= 0.5f
                }
                canvas.drawText(prText, x + col1W * 2.5f, row2H + (row3H / 2f) + (pricePaint.textSize * 0.35f), pricePaint)
            }
            else -> {
                // Style 1 (DEFAULT): COMPACT 2-COMPARTMENT PAPER-SAVING BOX (Code | Profile & Limits)
                val splitX = x + (width * 0.58f)

                // Strong vertical divider line between the two compartments
                canvas.drawLine(splitX, y, splitX, y + height, linePaint)

                // Left compartment: Header banner + BIGGEST POSSIBLE VOUCHER CODE
                val leftMaxW = (splitX - x) - 6f
                val leftCenterX = x + (splitX - x) / 2f

                val headerH = (height * 0.28f).coerceIn(9f, 16f)
                canvas.drawLine(x, y + headerH, splitX, y + headerH, linePaint)

                titlePaint.textSize = (headerH * 0.65f).coerceIn(7f, 12f)
                canvas.drawText(
                    if (voucher.isAccount) "ACCOUNT LOGIN" else "VOUCHER CODE",
                    leftCenterX,
                    y + (headerH * 0.5f) + (titlePaint.textSize * 0.35f),
                    titlePaint
                )

                val codeAreaH = height - headerH
                if (voucher.isAccount) {
                    var codeSize = (codeAreaH * 0.36f).coerceIn(9f, if (is80) 22f else 16f)
                    codePaint.textSize = codeSize
                    while (codePaint.measureText(voucher.code) > leftMaxW && codeSize > 8f) {
                        codeSize -= 0.5f
                        codePaint.textSize = codeSize
                    }
                    canvas.drawText(
                        voucher.code,
                        leftCenterX,
                        y + headerH + (codeAreaH * 0.36f) + (codeSize * 0.32f),
                        codePaint
                    )

                    var passSize = (codeAreaH * 0.28f).coerceIn(8f, if (is80) 16f else 12f)
                    textPaint.textSize = passSize
                    val passText = "P: ${voucher.password}"
                    while (textPaint.measureText(passText) > leftMaxW && passSize > 7f) {
                        passSize -= 0.5f
                        textPaint.textSize = passSize
                    }
                    canvas.drawText(
                        passText,
                        leftCenterX,
                        y + headerH + (codeAreaH * 0.78f) + (passSize * 0.32f),
                        textPaint
                    )
                } else {
                    // Massive centered voucher code
                    var codeSize = (codeAreaH * 0.56f).coerceIn(11f, if (is80) 28f else 22f)
                    codePaint.textSize = codeSize
                    while (codePaint.measureText(voucher.code) > leftMaxW && codeSize > 9f) {
                        codeSize -= 0.5f
                        codePaint.textSize = codeSize
                    }
                    canvas.drawText(
                        voucher.code,
                        leftCenterX,
                        y + headerH + (codeAreaH / 2f) + (codeSize * 0.35f),
                        codePaint
                    )
                }

                // Right compartment: 3 neat Excel rows (Profile, Limits, Price)
                val rightMaxW = (x + width - splitX) - 6f
                val rightCenterX = splitX + (x + width - splitX) / 2f
                val rowH = height / 3f

                // Row dividers
                canvas.drawLine(splitX, y + rowH, x + width, y + rowH, linePaint)
                canvas.drawLine(splitX, y + rowH * 2f, x + width, y + rowH * 2f, linePaint)

                // Row 1: Profile Name
                var profSize = (rowH * 0.55f).coerceIn(8.5f, if (is80) 16f else 13f)
                titlePaint.textSize = profSize
                while (titlePaint.measureText(voucher.profileName) > rightMaxW && profSize > 7.5f) {
                    profSize -= 0.5f
                    titlePaint.textSize = profSize
                }
                canvas.drawText(
                    voucher.profileName,
                    rightCenterX,
                    y + (rowH * 0.5f) + (profSize * 0.35f),
                    titlePaint
                )

                // Row 2: Data Quota & Validity
                val quotaStr = formatQuotaString(voucher.dataLimitMb)
                val valStr = formatValidityString(voucher.durationMinutes, voucher.validityDays)
                val limitStr = "$quotaStr • $valStr"
                var limitSize = (rowH * 0.50f).coerceIn(8f, if (is80) 14f else 11.5f)
                textPaint.textSize = limitSize
                while (textPaint.measureText(limitStr) > rightMaxW && limitSize > 7f) {
                    limitSize -= 0.5f
                    textPaint.textSize = limitSize
                }
                canvas.drawText(
                    limitStr,
                    rightCenterX,
                    y + (rowH * 1.5f) + (limitSize * 0.35f),
                    textPaint
                )

                // Row 3: Price
                val priceStr = if (voucher.price > 0) "${"%,d".format(java.util.Locale.US, voucher.price.toLong())} Ks" else "Free"
                var prSize = (rowH * 0.60f).coerceIn(9f, if (is80) 18f else 14f)
                pricePaint.textSize = prSize
                while (pricePaint.measureText(priceStr) > rightMaxW && prSize > 8f) {
                    prSize -= 0.5f
                    pricePaint.textSize = prSize
                }
                canvas.drawText(
                    priceStr,
                    rightCenterX,
                    y + (rowH * 2.5f) + (prSize * 0.35f),
                    pricePaint
                )
            }
        }
    }

    private fun generateQrBitmap(content: String, size: Int): Bitmap? {
        if (size <= 0) return null
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

    fun formatQuotaString(dataLimitMb: Int): String {
        return when {
            dataLimitMb <= 0 -> "Unlim"
            dataLimitMb >= 1024 && dataLimitMb % 1024 == 0 -> "${dataLimitMb / 1024}GB"
            dataLimitMb >= 1024 -> "${"%.1f".format(dataLimitMb / 1024.0)}GB"
            else -> "${dataLimitMb}MB"
        }
    }

    fun formatValidityString(durationMinutes: Int, validityDays: Int): String {
        return when {
            durationMinutes in 1..59 -> "${durationMinutes}M"
            durationMinutes in 60..1439 && durationMinutes % 60 == 0 -> "${durationMinutes / 60}H"
            durationMinutes in 60..1439 -> "${durationMinutes}M"
            durationMinutes >= 1440 && durationMinutes % 1440 == 0 -> "${durationMinutes / 1440}D"
            durationMinutes >= 1440 -> "${durationMinutes}M"
            validityDays > 0 -> "${validityDays}D"
            else -> "1D"
        }
    }
}

