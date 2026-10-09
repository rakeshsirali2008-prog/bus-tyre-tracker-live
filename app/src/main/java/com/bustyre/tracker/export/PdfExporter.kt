package com.bustyre.tracker.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.bustyre.tracker.data.CONDITION_NEW
import com.bustyre.tracker.data.CONDITION_RESOLE
import com.bustyre.tracker.data.SPARE
import com.bustyre.tracker.data.TYRE_POSITIONS
import com.bustyre.tracker.data.TyreEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes one row per vehicle with all seven tyres across the page.
 * Each tyre cell shows N (New) or R (Resole), filled with its star colour.
 * Runs off the main thread (call from Dispatchers.IO).
 */
object PdfExporter {

    private const val PAGE_W = 842   // A4 landscape, points
    private const val PAGE_H = 595
    private const val MARGIN = 30f
    private const val NAME_W = 110f
    private const val ROW_H = 26f

    fun write(context: Context, rows: List<Pair<String, List<TyreEntity>>>): File {
        val doc = PdfDocument()
        val text = Paint().apply { isAntiAlias = true; textSize = 11f; color = Color.BLACK }
        val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        val cellW = (PAGE_W - 2 * MARGIN - NAME_W) / TYRE_POSITIONS.size

        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
        var canvas = page.canvas
        var y = MARGIN

        val stamp = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH).format(Date())
        text.textSize = 13f
        text.isFakeBoldText = true
        canvas.drawText("Bus Tyre Status — $stamp", MARGIN, y + 12, text)
        text.textSize = 11f
        y += 24f

        drawHeader(canvas, text, y, cellW)
        y += ROW_H

        for ((vehicle, tyres) in rows) {
            if (y + ROW_H > PAGE_H - MARGIN) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
                canvas = page.canvas
                y = MARGIN
                drawHeader(canvas, text, y, cellW)
                y += ROW_H
            }

            text.isFakeBoldText = true
            canvas.drawText(vehicle, MARGIN + 4, y + 17, text)
            text.isFakeBoldText = false

            TYRE_POSITIONS.forEachIndexed { i, position ->
                val tyre = tyres.firstOrNull { it.position == position }
                val left = MARGIN + NAME_W + i * cellW

                val colour = ratingFill(tyre?.rating)
                if (colour != null) {
                    fill.color = colour
                    canvas.drawRect(left + 1, y + 1, left + cellW - 1, y + ROW_H - 1, fill)
                }

                val label = cellLabel(tyre)
                if (label.isNotEmpty()) {
                    val labelW = text.measureText(label)
                    canvas.drawText(label, left + (cellW - labelW) / 2, y + 17, text)
                }
            }

            // Thin separator under each vehicle row
            fill.color = Color.LTGRAY
            canvas.drawRect(MARGIN, y + ROW_H - 0.5f, PAGE_W - MARGIN, y + ROW_H + 0.5f, fill)
            y += ROW_H
        }

        doc.finishPage(page)

        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        val fileStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
        val file = File(dir, "bus_tyre_status_$fileStamp.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    private fun drawHeader(canvas: Canvas, text: Paint, y: Float, cellW: Float) {
        text.isFakeBoldText = true
        canvas.drawText("Vehicle", MARGIN + 4, y + 17, text)
        TYRE_POSITIONS.forEachIndexed { i, position ->
            val left = MARGIN + NAME_W + i * cellW
            val labelW = text.measureText(position)
            canvas.drawText(position, left + (cellW - labelW) / 2, y + 17, text)
        }
        text.isFakeBoldText = false
    }

    private fun ratingFill(rating: Int?): Int? = when (rating) {
        1 -> Color.parseColor("#A5D6A7")
        2 -> Color.parseColor("#FFF59D")
        3 -> Color.parseColor("#EF9A9A")
        else -> null
    }

    private fun cellLabel(tyre: TyreEntity?): String {
        if (tyre == null) return ""
        if (tyre.position == SPARE && tyre.present != true) return "-"
        return when (tyre.condition) {
            CONDITION_NEW -> "N"
            CONDITION_RESOLE -> "R"
            else -> ""
        }
    }
}
