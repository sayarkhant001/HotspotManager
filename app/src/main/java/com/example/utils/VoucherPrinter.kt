package com.example.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import com.example.domain.models.Voucher
import java.io.File
import java.io.FileOutputStream
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintDocumentInfo

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

    fun printVouchers(context: Context, vouchers: List<Voucher>, style: Int) {
        val pdfFile = generatePdf(context, vouchers, style)
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "MikroTik Vouchers"
        printManager.print(jobName, VoucherPrintAdapter(context, pdfFile), null)
    }

    private fun generatePdf(context: Context, vouchers: List<Voucher>, style: Int): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size in PostScript points

        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 14f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val codePaint = Paint().apply {
            color = Color.BLACK
            textSize = 18f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val textPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 10f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        val borderPaint = Paint().apply {
            color = Color.LTGRAY
            this.style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            this.style = Paint.Style.STROKE
            strokeWidth = 0.5f
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(5f, 5f), 0f)
        }

        val mikrotikPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 12f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD_ITALIC)
            textAlign = Paint.Align.CENTER
        }

        val cols = 4 // 4 columns per A4
        val cardWidth = 142f // ~50mm wide (max 53mm allowed)
        val gap = 5f
        val margin = (595f - (cardWidth * cols) - ((cols - 1) * gap)) / 2f
        
        var currentX = margin
        var currentY = margin
        
        val cardHeight = when(style) {
            1 -> 32f // ~11mm tall
            else -> 85f // ~30mm tall
        }
        
        vouchers.forEachIndexed { index, voucher ->
            if (currentY + cardHeight > 842f - margin) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                currentX = margin
                currentY = margin
            }

            // Draw Card Background
            val rect = android.graphics.RectF(currentX, currentY, currentX + cardWidth, currentY + cardHeight)
            canvas.drawRoundRect(rect, 4f, 4f, borderPaint)

            val centerX = currentX + (cardWidth / 2f)

            when (style) {
                1 -> { // Compact one line
                    codePaint.textSize = 12f
                    canvas.drawText(voucher.code, centerX, currentY + 16f, codePaint)
                    textPaint.textSize = 7f
                    canvas.drawText("${voucher.profileName} | ${voucher.dataLimitMb}MB / ${voucher.durationMinutes}M", centerX, currentY + 26f, textPaint)
                }
                2, 3 -> { // Standard ticket & QR (Simplified without real QR bitmap)
                    canvas.drawText("MikroTik", centerX, currentY + 16f, mikrotikPaint)
                    canvas.drawLine(currentX + 5f, currentY + 22f, currentX + cardWidth - 5f, currentY + 22f, linePaint)
                    
                    textPaint.textSize = 7f
                    canvas.drawText("Login Code", centerX, currentY + 34f, textPaint)
                    
                    codePaint.textSize = 14f
                    canvas.drawText(voucher.code, centerX, currentY + 50f, codePaint)
                    
                    canvas.drawLine(currentX + 5f, currentY + 60f, currentX + cardWidth - 5f, currentY + 60f, linePaint)
                    
                    titlePaint.textSize = 8f
                    canvas.drawText("Profile: ${voucher.profileName}", centerX, currentY + 72f, titlePaint)
                    textPaint.textSize = 6.5f
                    canvas.drawText("Limit: ${voucher.dataLimitMb} MB / ${voucher.durationMinutes} Min", centerX, currentY + 81f, textPaint)
                }
            }

            // Move to next grid position
            currentX += cardWidth + gap
            if (currentX + cardWidth > 595f - 5f) {
                currentX = margin
                currentY += cardHeight + gap
            }
        }

        pdfDocument.finishPage(page)

        val file = File(context.cacheDir, "vouchers.pdf")
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()
        
        return file
    }
}
