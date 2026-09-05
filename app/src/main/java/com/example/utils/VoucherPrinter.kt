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
        val cols = if (style == 2) 3 else 3
        val cardWidth = 175f
        val cardHeight = if (style == 2) 50f else 115f
        val gapX = 10f
        val gapY = 10f
        val marginX = (pageWidth - (cols * cardWidth + (cols - 1) * gapX)) / 2f
        val marginY = 25f

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
            if (curX + cardWidth > pageWidth - marginX + 5f) {
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
        val cardHeight = if (style == 2) 65 else 135
        val totalHeight = (cardHeight + 10) * vouchers.size + 20

        val pageInfo = PdfDocument.PageInfo.Builder(rollWidth, Math.max(totalHeight, 200), 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        var curY = 10f
        vouchers.forEach { voucher ->
            drawVoucherCard(canvas, voucher, 5f, curY, (rollWidth - 10).toFloat(), cardHeight.toFloat(), style)
            curY += cardHeight + 10f
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
            strokeWidth = 1f
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            this.style = Paint.Style.STROKE
            strokeWidth = 0.8f
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f, 4f), 0f)
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
        canvas.drawRoundRect(rect, 6f, 6f, borderPaint)

        val centerX = x + (width / 2f)

        when (style) {
            2 -> {
                // Style 2: One-Line Compact
                codePaint.textSize = 12f
                canvas.drawText(voucher.code, centerX, y + 18f, codePaint)
                textPaint.textSize = 7.5f
                val info = "${voucher.profileName} • ${if (voucher.dataLimitMb > 0) "${voucher.dataLimitMb}MB" else "Unlim"} • \$${voucher.price}"
                canvas.drawText(info, centerX, y + 32f, textPaint)
                canvas.drawText("http://10.10.10.1", centerX, y + 44f, textPaint)
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
                canvas.drawText(if (voucher.price > 0) "\$${voucher.price}" else "FREE", rightCenterX, y + 88f, pricePaint)
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
                canvas.drawText(if (voucher.price > 0) "Price: \$${voucher.price}" else "Free Access", centerX, y + 106f, pricePaint)
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

