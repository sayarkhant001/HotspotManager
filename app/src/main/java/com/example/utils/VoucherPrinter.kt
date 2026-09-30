package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
        format: PaperFormat = PaperFormat.A4_PAGE
    ) {
        if (vouchers.isEmpty()) return
        val pdfFile = generatePdf(context, vouchers, style, format)
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "MikroTik Hotspot Vouchers"
        printManager.print(jobName, VoucherPrintAdapter(context, pdfFile), null)
    }

    fun printTestReceipt(
        context: Context,
        format: PaperFormat = PaperFormat.THERMAL_58MM,
        profiles: List<UserProfile> = emptyList()
    ) {
        val testVouchers = if (profiles.isNotEmpty()) {
            profiles.take(5).mapIndexed { idx, p ->
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
                Voucher(id = 2, code = "TEST-3GB-02", profileName = "3GB_6H", price = 1000.0, dataLimitMb = 3072, validityDays = 3),
                Voucher(id = 3, code = "TEST-7GB-03", profileName = "7GB_7D", price = 3000.0, dataLimitMb = 7168, validityDays = 7)
            )
        }
        val pdfFile = generatePdf(context, testVouchers, style = 2, format = format)
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        printManager.print("Hotspot Printer Test Slip", VoucherPrintAdapter(context, pdfFile), null)
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

    private fun renderA4Sheet(pdfDocument: PdfDocument, vouchers: List<Voucher>, style: Int) {
        val pageWidth = 595 // A4 width in points
        val pageHeight = 842 // A4 height in points
        val cols = if (style == 2) 5 else 3
        val cardWidth = if (style == 2) 111f else 175f
        val cardHeight = if (style == 2) 34f else 115f // 34 pt = 12mm
        val gapX = if (style == 2) 5f else 10f
        val gapY = if (style == 2) 4f else 10f
        val marginX = (pageWidth - (cols * cardWidth + (cols - 1) * gapX)) / 2f
        val marginY = 20f

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
        val cardHeight = if (style == 2) 34 else 125 // 34 pt = 12mm
        val gapY = if (style == 2) 2 else 8
        val totalHeight = (cardHeight + gapY) * vouchers.size + 16

        val pageInfo = PdfDocument.PageInfo.Builder(rollWidth, Math.max(totalHeight, 100), 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        var curY = 8f
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
        val borderPaint = Paint().apply {
            color = Color.DKGRAY
            this.style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            this.style = Paint.Style.STROKE
            strokeWidth = 0.8f
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(3f, 3f), 0f)
        }
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val codePaint = Paint().apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val textPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        val pricePaint = Paint().apply {
            color = Color.BLACK
            textSize = 9f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        // Draw Card border
        val rect = RectF(x, y, x + width, y + height)
        canvas.drawRoundRect(rect, if (style == 2) 2f else 5f, if (style == 2) 2f else 5f, borderPaint)

        val centerX = x + (width / 2f)

        when (style) {
            2 -> {
                // Style 2: Ultra-Compact (58mm x 12mm thermal / 5-col A4 grid)
                // Specification: Only voucher code and profile name
                codePaint.textSize = if (width < 120f) 10.5f else 12f
                codePaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                canvas.drawText(voucher.code, centerX, y + 14f, codePaint)

                textPaint.textSize = if (width < 120f) 7f else 8f
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                val profileText = if (voucher.price > 0) "${voucher.profileName} • ${"%,d".format(java.util.Locale.US, voucher.price.toLong())} Ks" else voucher.profileName
                canvas.drawText(profileText, centerX, y + 27f, textPaint)
            }
            3 -> {
                // Style 3: QR Scannable Voucher
                val qrSize = (height - 20).toInt()
                val qrBitmap = generateQrBitmap("http://10.10.10.1/login?username=${voucher.code}&password=${if (voucher.isAccount) voucher.password else voucher.code}", qrSize)

                if (qrBitmap != null) {
                    canvas.drawBitmap(qrBitmap, x + 8f, y + 10f, null)
                }

                val rightCenterX = x + qrSize + 15f + (width - qrSize - 20f) / 2f
                titlePaint.textAlign = Paint.Align.CENTER
                codePaint.textAlign = Paint.Align.CENTER
                textPaint.textAlign = Paint.Align.CENTER

                canvas.drawText("HOTSPOT VOUCHER", rightCenterX, y + 20f, titlePaint)
                canvas.drawLine(x + qrSize + 10f, y + 26f, x + width - 8f, y + 26f, linePaint)

                codePaint.textSize = 13f
                canvas.drawText(voucher.code, rightCenterX, y + 42f, codePaint)

                if (voucher.isAccount) {
                    textPaint.textSize = 7.5f
                    canvas.drawText("Pass: ${voucher.password}", rightCenterX, y + 54f, textPaint)
                }

                canvas.drawLine(x + qrSize + 10f, y + 62f, x + width - 8f, y + 62f, linePaint)

                textPaint.textSize = 7f
                canvas.drawText("Profile: ${voucher.profileName}", rightCenterX, y + 74f, textPaint)
                pricePaint.textSize = 8.5f
                canvas.drawText(if (voucher.price > 0) "${"%,d".format(java.util.Locale.US, voucher.price.toLong())} Ks" else "FREE", rightCenterX, y + 88f, pricePaint)
                textPaint.textSize = 6.5f
                canvas.drawText("Scan QR to Connect", rightCenterX, y + 100f, textPaint)
            }
            else -> {
                // Style 1: Standard Ticket (Voucher | Profile)
                canvas.drawText("★ HOTSPOT WIFI ★", centerX, y + 16f, titlePaint)
                canvas.drawLine(x + 8f, y + 22f, x + width - 8f, y + 22f, linePaint)

                textPaint.textSize = 7.5f
                canvas.drawText(if (voucher.isAccount) "Username" else "Voucher Code", centerX, y + 34f, textPaint)

                codePaint.textSize = 15f
                canvas.drawText(voucher.code, centerX, y + 50f, codePaint)

                if (voucher.isAccount) {
                    textPaint.textSize = 8f
                    canvas.drawText("Password: ${voucher.password}", centerX, y + 62f, textPaint)
                }

                canvas.drawLine(x + 8f, y + 70f, x + width - 8f, y + 70f, linePaint)

                titlePaint.textSize = 8.5f
                canvas.drawText("Plan: ${voucher.profileName}", centerX, y + 82f, titlePaint)

                val limitText = if (voucher.dataLimitMb > 0) "${voucher.dataLimitMb} MB" else "Unlimited Data"
                textPaint.textSize = 7f
                canvas.drawText("$limitText • ${voucher.validityDays} Day(s)", centerX, y + 93f, textPaint)

                pricePaint.textSize = 9f
                canvas.drawText(if (voucher.price > 0) "Price: ${"%,d".format(java.util.Locale.US, voucher.price.toLong())} Ks" else "Free Access", centerX, y + 106f, pricePaint)
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
}

