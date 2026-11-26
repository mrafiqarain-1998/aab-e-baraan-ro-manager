package com.example.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.R
import com.example.model.BottleSize
import com.example.model.Customer
import com.example.model.Delivery
import com.example.model.DeliveryStatus
import com.example.model.Expense
import com.example.model.Invoice
import com.example.model.Payment
import com.example.model.PaymentSource
import com.example.viewmodel.DashboardStats
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object PdfGenerator {

    @Volatile
    var defaultAdminPhone: String = "+92 300 1234567"

    @Volatile
    private var cachedHeaderLogo: Bitmap? = null

    @Volatile
    private var cachedWatermarkLogo: Bitmap? = null

    private fun resolvedAdminPhone(): String = defaultAdminPhone.trim().ifBlank { "+92 300 1234567" }

    private fun computeInSampleSize(srcWidth: Int, srcHeight: Int, reqSize: Int): Int {
        var inSampleSize = 1
        if (srcHeight > reqSize || srcWidth > reqSize) {
            val halfHeight = srcHeight / 2
            val halfWidth = srcWidth / 2
            while ((halfHeight / inSampleSize) >= reqSize && (halfWidth / inSampleSize) >= reqSize) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    internal fun getCompactLogoBitmaps(context: Context): Pair<Bitmap?, Bitmap?> {
        val existingHeader = cachedHeaderLogo?.takeIf { !it.isRecycled }
        val existingWatermark = cachedWatermarkLogo?.takeIf { !it.isRecycled }
        if (existingHeader != null && existingWatermark != null) {
            return existingHeader to existingWatermark
        }

        return try {
            val boundsOpts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeResource(context.resources, R.drawable.aab_e_baraan_logo, boundsOpts)

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = computeInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, 128)
                inPreferredConfig = Bitmap.Config.RGB_565
                inDither = true
            }
            val sampled = BitmapFactory.decodeResource(context.resources, R.drawable.aab_e_baraan_logo, decodeOpts)
                ?: return null to null

            val ratio = sampled.height.toFloat() / sampled.width.coerceAtLeast(1)

            // Compact 64px opaque RGB_565 header badge logo (keeps PDF tiny & fast to share)
            val hW = 64
            val hH = (hW * ratio).toInt().coerceIn(40, 72)
            val headerBmp = Bitmap.createBitmap(hW, hH, Bitmap.Config.RGB_565).also { bmp ->
                val c = Canvas(bmp)
                c.drawColor(Color.WHITE)
                val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                c.drawBitmap(sampled, Rect(0, 0, sampled.width, sampled.height), RectF(0f, 0f, hW.toFloat(), hH.toFloat()), p)
            }

            // Compact 96px opaque RGB_565 pre-faded watermark (zero alpha mask overhead in PdfDocument)
            val wW = 96
            val wH = (wW * ratio).toInt().coerceIn(60, 110)
            val watermarkBmp = Bitmap.createBitmap(wW, wH, Bitmap.Config.RGB_565).also { bmp ->
                val c = Canvas(bmp)
                c.drawColor(Color.WHITE)
                val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    alpha = 26
                }
                c.drawBitmap(sampled, Rect(0, 0, sampled.width, sampled.height), RectF(0f, 0f, wW.toFloat(), wH.toFloat()), p)
            }

            sampled.recycle()
            cachedHeaderLogo = headerBmp
            cachedWatermarkLogo = watermarkBmp
            headerBmp to watermarkBmp
        } catch (_: Throwable) {
            null to null
        }
    }

    /**
     * Draws a compact logo badge in the header and a soft centered brand logo watermark on the A4 canvas.
     */
    private fun drawHeaderLogoAndWatermark(context: Context, canvas: Canvas) {
        val (headerLogo, watermarkLogo) = getCompactLogoBitmaps(context)
        if (headerLogo == null && watermarkLogo == null) return

        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }

        if (headerLogo != null && !headerLogo.isRecycled) {
            // Top-right white circular/rounded badge inside the Navy header
            val badgePaint = Paint().apply {
                isAntiAlias = true
                color = Color.WHITE
            }
            val badgeRect = RectF(503f, 17f, 559f, 73f)
            canvas.drawRoundRect(badgeRect, 10f, 10f, badgePaint)
            val headerDst = RectF(507f, 21f, 555f, 69f)
            canvas.drawBitmap(headerLogo, Rect(0, 0, headerLogo.width, headerLogo.height), headerDst, paint)
        }

        if (watermarkLogo != null && !watermarkLogo.isRecycled) {
            // Centered page watermark behind text/tables (pre-blended on white, no alpha stream needed)
            val wmWidth = 280f
            val wmHeight = (wmWidth * watermarkLogo.height.toFloat() / watermarkLogo.width.coerceAtLeast(1)).coerceAtMost(300f)
            val left = (595f - wmWidth) / 2f
            val top = (842f - wmHeight) / 2f
            val wmDst = RectF(left, top, left + wmWidth, top + wmHeight)
            canvas.drawBitmap(watermarkLogo, Rect(0, 0, watermarkLogo.width, watermarkLogo.height), wmDst, paint)
        }
    }

    /**
     * Exact token match so e.g. "DEL-501" NEVER falsely matches "DEL-5010".
     */
    fun matchesDeliveryNumberToken(text: String, deliveryNumber: String): Boolean {
        val token = deliveryNumber.trim()
        if (token.isBlank() || text.isBlank()) return false
        val pattern = Regex("(^|[^A-Za-z0-9])${Regex.escape(token)}($|[^A-Za-z0-9])", RegexOption.IGNORE_CASE)
        return pattern.containsMatchIn(text)
    }

    /**
     * Returns the exact allocated portion of [payment] that belongs to [delivery].
     */
    fun allocatedAmountForDelivery(delivery: Delivery, payment: Payment): Double {
        val matched = payment.allocations.filter { alloc ->
            (delivery.invoiceId.isNotBlank() && alloc.invoiceId == delivery.invoiceId) ||
                alloc.invoiceId == delivery.id ||
                alloc.invoiceId == "inv_${delivery.id}" ||
                (delivery.deliveryNumber.isNotBlank() && alloc.invoiceNumber.equals(delivery.deliveryNumber, ignoreCase = true))
        }.sumOf { it.allocatedAmount }
        if (matched > 0.0) return matched
        if (payment.allocations.isEmpty()) return payment.amount
        // If payment has explicit allocations to other invoices only, do not count full amount unless matched by exact note
        return if (delivery.deliveryNumber.isNotBlank() &&
            (delivery.customerId.isBlank() || payment.customerId == delivery.customerId) &&
            matchesDeliveryNumberToken(payment.notes, delivery.deliveryNumber)
        ) {
            payment.amount
        } else {
            0.0
        }
    }

    fun findRelatedPaymentsForDelivery(delivery: Delivery, payments: List<Payment>): List<Payment> {
        return payments.filter { p ->
            val matchedByAlloc = p.allocations.any { alloc ->
                (delivery.invoiceId.isNotBlank() && alloc.invoiceId == delivery.invoiceId) ||
                    alloc.invoiceId == delivery.id ||
                    alloc.invoiceId == "inv_${delivery.id}" ||
                    (delivery.deliveryNumber.isNotBlank() && alloc.invoiceNumber.equals(delivery.deliveryNumber, ignoreCase = true))
            }
            val matchedByNote = delivery.deliveryNumber.isNotBlank() &&
                (delivery.customerId.isBlank() || p.customerId == delivery.customerId) &&
                matchesDeliveryNumberToken(p.notes, delivery.deliveryNumber)
            matchedByAlloc || matchedByNote
        }.sortedWith(compareBy<Payment> { it.date }.thenBy { it.createdAt })
    }

    private fun truncateText(text: String, maxChars: Int): String {
        val clean = text.replace("\n", " ").trim()
        return if (clean.length <= maxChars) clean else clean.take((maxChars - 2).coerceAtLeast(1)) + ".."
    }

    /**
     * Generates a clean, professional Delivery Voucher / Cash Receipt PDF with logo watermark & installment history.
     * Supports automatic pagination if a delivery has many items or payment installments.
     * Note: Customer Address is explicitly omitted as requested by the user.
     */
    fun generateDeliveryVoucherPdf(
        context: Context,
        delivery: Delivery,
        payments: List<Payment>,
        bottleSizes: List<BottleSize>
    ): File {
        val document = PdfDocument()
        try {
            var pageNum = 1
            var page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum).create())
            var canvas: Canvas = page.canvas
            val paint = Paint().apply { isAntiAlias = true }

            fun drawVoucherHeader(c: Canvas, isContinuation: Boolean) {
                paint.color = Color.rgb(10, 37, 64) // WaterNavyPrimary
                c.drawRect(0f, 0f, 595f, 90f, paint)
                drawHeaderLogoAndWatermark(context, c)

                paint.color = Color.WHITE
                paint.textSize = 18.5f
                paint.isFakeBoldText = true
                c.drawText("AAB-E-BARAAN RO DRINKING WATER PLANT", 36f, 40f, paint)

                paint.textSize = 9.5f
                paint.isFakeBoldText = false
                paint.color = Color.rgb(200, 230, 255)
                val subtitle = if (isContinuation) {
                    "Delivery Slip & Receipt (Continued - Page $pageNum) • Voucher #${delivery.deliveryNumber}"
                } else {
                    "Clean, Pure & Mineral Enriched Drinking Water • Delivery Slip & Receipt"
                }
                c.drawText(subtitle, 36f, 58f, paint)
                c.drawText("آب باراں منرل واٹر پلانٹ - آفیشل ڈیلیوری و کیش وصولی سلپ", 36f, 75f, paint)
            }

            drawVoucherHeader(canvas, isContinuation = false)
            var y = 118f

            fun ensureSpace(neededHeight: Float) {
                if (y + neededHeight > 795f) {
                    document.finishPage(page)
                    pageNum += 1
                    page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum).create())
                    canvas = page.canvas
                    drawVoucherHeader(canvas, isContinuation = true)
                    y = 116f
                }
            }

            // Document Info Row
            paint.color = Color.rgb(30, 41, 59)
            paint.textSize = 12f
            paint.isFakeBoldText = true
            canvas.drawText("Voucher #: ${delivery.deliveryNumber}", 40f, y, paint)

            paint.isFakeBoldText = false
            paint.textSize = 10.5f
            paint.color = Color.rgb(71, 85, 105)
            canvas.drawText("Date: ${delivery.date}  ${delivery.time}", 375f, y, paint)

            y += 18f
            paint.color = Color.rgb(226, 232, 240)
            canvas.drawLine(40f, y, 555f, y, paint)

            y += 18f
            // Customer Info Card (Without Address as requested)
            paint.color = Color.rgb(241, 245, 249)
            val infoRect = RectF(40f, y, 555f, y + 54f)
            canvas.drawRoundRect(infoRect, 8f, 8f, paint)

            paint.color = Color.rgb(15, 23, 42)
            paint.textSize = 11.5f
            paint.isFakeBoldText = true
            canvas.drawText("Customer: ${truncateText(delivery.customerName, 34)}", 54f, y + 22f, paint)

            paint.isFakeBoldText = false
            paint.textSize = 10.5f
            paint.color = Color.rgb(51, 65, 85)
            canvas.drawText("Contact: ${delivery.customerPhone.ifEmpty { "N/A" }}", 54f, y + 42f, paint)
            canvas.drawText("Staff: ${truncateText(delivery.staffName.ifEmpty { "Staff" }, 20)}", 340f, y + 22f, paint)
            canvas.drawText("Payment: ${delivery.paymentMethod}", 340f, y + 42f, paint)

            y += 72f

            // Table Header
            fun drawItemsTableHeader() {
                paint.color = Color.rgb(224, 242, 254)
                val tableHeaderRect = RectF(40f, y, 555f, y + 26f)
                canvas.drawRoundRect(tableHeaderRect, 4f, 4f, paint)

                paint.color = Color.rgb(10, 37, 64)
                paint.textSize = 10.5f
                paint.isFakeBoldText = true
                canvas.drawText("Item / Bottle Size", 52f, y + 17f, paint)
                canvas.drawText("Qty", 255f, y + 17f, paint)
                canvas.drawText("Return", 315f, y + 17f, paint)
                canvas.drawText("Rate", 385f, y + 17f, paint)
                canvas.drawText("Total (PKR)", 468f, y + 17f, paint)

                y += 36f
                paint.isFakeBoldText = false
            }

            drawItemsTableHeader()

            // Table Rows
            var returnableEmptiesSummary = ""
            delivery.items.forEach { item ->
                if (y + 34f > 795f) {
                    ensureSpace(64f)
                    drawItemsTableHeader()
                }
                val sizeObj = bottleSizes.find { it.id == item.bottleSizeId }
                val isReturnable = sizeObj?.isReturnableBottle ?: (item.bottleSizeId in listOf("size_19l", "size_12l"))

                paint.color = Color.rgb(15, 23, 42)
                val safeBottleName = truncateText(item.bottleName, 36)
                paint.textSize = when {
                    safeBottleName.length > 30 -> 8.4f
                    safeBottleName.length > 22 -> 9.4f
                    else -> 10.5f
                }
                canvas.drawText(safeBottleName, 52f, y, paint)
                paint.textSize = 10.5f
                canvas.drawText("${item.quantity}", 255f, y, paint)
                canvas.drawText(if (isReturnable) "${item.emptyCollected}" else "0", 315f, y, paint)
                canvas.drawText("Rs. ${item.unitPrice.toInt()}", 385f, y, paint)
                paint.isFakeBoldText = true
                canvas.drawText("Rs. ${item.lineTotal.toInt()}", 468f, y, paint)
                paint.isFakeBoldText = false

                if (isReturnable && item.emptyCollected > 0) {
                    returnableEmptiesSummary += "${item.bottleName}: ${item.emptyCollected}  "
                }

                y += 16f
                paint.color = Color.rgb(241, 245, 249)
                canvas.drawLine(40f, y, 555f, y, paint)
                y += 14f
            }

            // Returnable Empties Note (If applicable)
            if (returnableEmptiesSummary.isNotBlank()) {
                ensureSpace(40f)
                y += 4f
                paint.color = Color.rgb(234, 242, 250)
                val retRect = RectF(40f, y, 555f, y + 24f)
                canvas.drawRoundRect(retRect, 4f, 4f, paint)

                paint.color = Color.rgb(2, 132, 199)
                paint.textSize = 9.5f
                paint.isFakeBoldText = true
                canvas.drawText("Empty Bottles Returned: ${truncateText(returnableEmptiesSummary, 64)}", 52f, y + 16f, paint)
                y += 34f
            } else {
                y += 6f
            }

            // Payment Calculation Box — single source of truth from delivery.paymentCollected
            val relatedPayments = findRelatedPaymentsForDelivery(delivery, payments)
            val totalPaid = delivery.paymentCollected.coerceIn(0.0, delivery.totalAmount)
            val outstandingBal = (delivery.totalAmount - totalPaid).coerceAtLeast(0.0)

            val hasCharges = delivery.deliveryCharges > 0.0
            val hasDiscount = delivery.discount > 0.0
            val extraRows = (if (hasCharges || hasDiscount) 1 else 0) +
                (if (hasCharges) 1 else 0) +
                (if (hasDiscount) 1 else 0)
            val boxHeight = 84f + (extraRows * 18f)
            ensureSpace(boxHeight + 40f)

            paint.color = Color.rgb(248, 250, 252)
            val billBox = RectF(290f, y, 555f, y + boxHeight)
            canvas.drawRoundRect(billBox, 6f, 6f, paint)
            paint.color = Color.rgb(203, 213, 225)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRoundRect(billBox, 6f, 6f, paint)
            paint.style = Paint.Style.FILL

            var boxY = y + 22f
            if (hasCharges || hasDiscount) {
                paint.textSize = 10.5f
                paint.isFakeBoldText = false
                paint.color = Color.rgb(71, 85, 105)
                canvas.drawText("Bottles Subtotal:", 306f, boxY, paint)
                canvas.drawText("Rs. ${delivery.subtotal.toInt()}", 468f, boxY, paint)
                boxY += 18f

                if (hasCharges) {
                    canvas.drawText("Delivery Charges:", 306f, boxY, paint)
                    canvas.drawText("Rs. ${delivery.deliveryCharges.toInt()}", 468f, boxY, paint)
                    boxY += 18f
                }

                if (hasDiscount) {
                    paint.color = Color.rgb(22, 101, 52)
                    canvas.drawText("Discount:", 306f, boxY, paint)
                    canvas.drawText("- Rs. ${delivery.discount.toInt()}", 468f, boxY, paint)
                    boxY += 18f
                }
            }

            paint.textSize = 11f
            paint.isFakeBoldText = false
            paint.color = Color.rgb(51, 65, 85)
            canvas.drawText("Total Delivery Bill:", 306f, boxY, paint)
            paint.isFakeBoldText = true
            canvas.drawText("Rs. ${delivery.totalAmount.toInt()}", 468f, boxY, paint)
            boxY += 24f

            paint.isFakeBoldText = false
            paint.color = Color.rgb(22, 101, 52)
            canvas.drawText("Total Cash Received:", 306f, boxY, paint)
            paint.isFakeBoldText = true
            canvas.drawText("Rs. ${totalPaid.toInt()}", 468f, boxY, paint)
            boxY += 24f

            paint.isFakeBoldText = false
            paint.color = if (outstandingBal > 0) Color.rgb(185, 28, 28) else Color.rgb(22, 101, 52)
            canvas.drawText("Remaining Due (بقایا):", 306f, boxY, paint)
            paint.isFakeBoldText = true
            canvas.drawText("Rs. ${outstandingBal.toInt()}", 468f, boxY, paint)

            y += boxHeight + 18f

            if (delivery.notes.isNotBlank()) {
                ensureSpace(24f)
                paint.isFakeBoldText = false
                paint.textSize = 10f
                paint.color = Color.rgb(71, 85, 105)
                canvas.drawText("Note: ${truncateText(delivery.notes, 80)}", 40f, y, paint)
                y += 18f
            }

            // Payment Record Log
            if (relatedPayments.isNotEmpty()) {
                ensureSpace(36f)
                paint.isFakeBoldText = true
                paint.textSize = 10.5f
                paint.color = Color.rgb(10, 37, 64)
                canvas.drawText("Payment Record (وصولی تاریخ وار تفصیل):", 40f, y, paint)
                y += 16f

                paint.isFakeBoldText = false
                paint.textSize = 9.5f
                relatedPayments.forEachIndexed { idx, p ->
                    ensureSpace(18f)
                    val allocAmt = allocatedAmountForDelivery(delivery, p)
                    if (allocAmt <= 0.0) return@forEachIndexed
                    paint.color = Color.rgb(51, 65, 85)
                    canvas.drawText(
                        "${idx + 1}. ${p.receiptNumber} (${p.date}) • Rs. ${allocAmt.toInt()} via ${p.paymentMethod.label}",
                        48f,
                        y,
                        paint
                    )
                    y += 14f
                }
                y += 10f
            }

            // Footer Note
            ensureSpace(46f)
            paint.color = Color.rgb(226, 232, 240)
            canvas.drawLine(40f, y, 555f, y, paint)
            y += 18f

            paint.textSize = 9f
            paint.color = Color.rgb(148, 163, 184)
            paint.isFakeBoldText = false
            canvas.drawText("This is a computer generated delivery slip from Aab-e-Baraan RO Plant Management System.", 40f, y, paint)
            canvas.drawText("For plant inquiries or deliveries contact: ${resolvedAdminPhone()}", 40f, y + 14f, paint)

            document.finishPage(page)

            val outputDir = context.cacheDir
            val safeNum = delivery.deliveryNumber.ifBlank { delivery.id }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val file = File(outputDir, "Delivery_Slip_$safeNum.pdf")
            FileOutputStream(file).use { fos ->
                document.writeTo(fos)
                fos.flush()
            }
            return file
        } finally {
            try { document.close() } catch (_: Throwable) {}
        }
    }

    /**
     * Generates a clean, professional Payment Receipt PDF with logo watermark.
     */
    fun generatePaymentReceiptPdf(
        context: Context,
        payment: Payment,
        customer: Customer?
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true }

        // Header Background
        paint.color = Color.rgb(10, 37, 64)
        canvas.drawRect(0f, 0f, 595f, 90f, paint)

        drawHeaderLogoAndWatermark(context, canvas)

        paint.color = Color.WHITE
        paint.textSize = 18.5f
        paint.isFakeBoldText = true
        canvas.drawText("AAB-E-BARAAN RO DRINKING WATER PLANT", 36f, 40f, paint)

        paint.textSize = 9.5f
        paint.isFakeBoldText = false
        paint.color = Color.rgb(200, 230, 255)
        canvas.drawText("Official Payment Receipt Voucher (کیش / آن لائن وصولی رسید)", 36f, 62f, paint)

        var y = 122f

        paint.color = Color.rgb(30, 41, 59)
        paint.textSize = 13f
        paint.isFakeBoldText = true
        canvas.drawText("Receipt #: ${payment.receiptNumber}", 40f, y, paint)

        paint.isFakeBoldText = false
        paint.textSize = 11f
        paint.color = Color.rgb(71, 85, 105)
        canvas.drawText("Date: ${payment.date}", 420f, y, paint)

        y += 18f
        paint.color = Color.rgb(226, 232, 240)
        canvas.drawLine(40f, y, 555f, y, paint)

        y += 22f
        paint.color = Color.rgb(241, 245, 249)
        val infoRect = RectF(40f, y, 555f, y + 66f)
        canvas.drawRoundRect(infoRect, 8f, 8f, paint)

        paint.color = Color.rgb(15, 23, 42)
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("Customer: ${payment.customerName}", 54f, y + 24f, paint)

        paint.isFakeBoldText = false
        paint.textSize = 10.5f
        paint.color = Color.rgb(51, 65, 85)
        canvas.drawText("Contact: ${customer?.phone?.ifBlank { "N/A" } ?: "N/A"}", 54f, y + 46f, paint)
        canvas.drawText("Method: ${payment.paymentMethod.label}", 330f, y + 24f, paint)
        canvas.drawText("Received By: ${payment.receivedBy.ifBlank { "Staff" }}", 330f, y + 46f, paint)

        y += 92f

        // Payment Amount Box
        paint.color = Color.rgb(240, 253, 244)
        val amtRect = RectF(40f, y, 555f, y + 64f)
        canvas.drawRoundRect(amtRect, 8f, 8f, paint)
        paint.color = Color.rgb(187, 247, 208)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(amtRect, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.rgb(22, 101, 52)
        paint.textSize = 11f
        paint.isFakeBoldText = true
        canvas.drawText("Amount Received (وصول شدہ رقم):", 56f, y + 26f, paint)
        paint.textSize = 18f
        canvas.drawText("Rs. ${payment.amount.toInt()}", 56f, y + 50f, paint)

        if (customer != null) {
            paint.textSize = 11f
            paint.color = if (customer.currentBalance > 0) Color.rgb(185, 28, 28) else Color.rgb(22, 101, 52)
            canvas.drawText("Current Account Due: Rs. ${customer.currentBalance.coerceAtLeast(0.0).toInt()}", 330f, y + 38f, paint)
        }

        y += 88f

        if (payment.allocations.isNotEmpty()) {
            paint.color = Color.rgb(10, 37, 64)
            paint.textSize = 11f
            paint.isFakeBoldText = true
            canvas.drawText("Allocated Against Invoices / Deliveries:", 40f, y, paint)
            y += 18f
            paint.isFakeBoldText = false
            paint.textSize = 10f
            payment.allocations.forEachIndexed { idx, alloc ->
                paint.color = Color.rgb(51, 65, 85)
                canvas.drawText("${idx + 1}. ${alloc.invoiceNumber} — Rs. ${alloc.allocatedAmount.toInt()}", 52f, y, paint)
                y += 16f
            }
            y += 8f
        }

        if (payment.notes.isNotBlank()) {
            paint.color = Color.rgb(71, 85, 105)
            paint.textSize = 10f
            canvas.drawText("Note: ${payment.notes}", 40f, y, paint)
            y += 20f
        }

        paint.color = Color.rgb(226, 232, 240)
        canvas.drawLine(40f, y, 555f, y, paint)
        y += 18f
        paint.textSize = 9f
        paint.color = Color.rgb(148, 163, 184)
        canvas.drawText("Thank you for your payment • Aab-e-Baraan RO Drinking Water Plant • Contact: ${resolvedAdminPhone()}", 40f, y, paint)

        document.finishPage(page)

        val outputDir = context.cacheDir
        val safeNum = payment.receiptNumber.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val file = File(outputDir, "Payment_Receipt_$safeNum.pdf")
        val fos = FileOutputStream(file)
        document.writeTo(fos)
        fos.close()
        document.close()

        return file
    }

    /**
     * Generates a Plant Business & Sales Summary Report PDF.
     */
    fun generateSalesReportPdf(
        context: Context,
        title: String,
        periodLabel: String,
        stats: DashboardStats
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true }

        // Header Background
        paint.color = Color.rgb(10, 37, 64)
        canvas.drawRect(0f, 0f, 595f, 90f, paint)

        drawHeaderLogoAndWatermark(context, canvas)

        // Brand Title
        paint.color = Color.WHITE
        paint.textSize = 18.5f
        paint.isFakeBoldText = true
        canvas.drawText("AAB-E-BARAAN RO DRINKING WATER PLANT", 36f, 42f, paint)

        paint.textSize = 10.5f
        paint.isFakeBoldText = false
        paint.color = Color.rgb(200, 230, 255)
        canvas.drawText("Executive Financial & Operational Summary Report ($periodLabel)", 36f, 64f, paint)

        var y = 125f

        // Report Title
        paint.color = Color.rgb(15, 23, 42)
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText(title, 40f, y, paint)

        y += 24f
        paint.color = Color.rgb(226, 232, 240)
        canvas.drawLine(40f, y, 555f, y, paint)

        y += 30f

        // Stats Grid in PDF
        fun drawStatCard(label: String, value: String, x: Float, yPos: Float, isPositive: Boolean? = null) {
            paint.color = Color.rgb(248, 250, 252)
            val r = RectF(x, yPos, x + 230f, yPos + 60f)
            canvas.drawRoundRect(r, 8f, 8f, paint)

            paint.color = Color.rgb(226, 232, 240)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRoundRect(r, 8f, 8f, paint)
            paint.style = Paint.Style.FILL

            paint.textSize = 10f
            paint.color = Color.rgb(100, 116, 139)
            paint.isFakeBoldText = false
            canvas.drawText(label, x + 16f, yPos + 22f, paint)

            paint.textSize = 16f
            paint.isFakeBoldText = true
            paint.color = when (isPositive) {
                true -> Color.rgb(22, 101, 52)
                false -> Color.rgb(185, 28, 28)
                else -> Color.rgb(15, 23, 42)
            }
            canvas.drawText(value, x + 16f, yPos + 48f, paint)
        }

        drawStatCard("Total Delivered Bottles", "${stats.delivered19L + stats.deliveredOther} Units", 40f, y)
        drawStatCard("Empty Bottles Recovered", "${stats.emptyBottlesCollected} Units", 315f, y)

        y += 75f
        drawStatCard("Gross Sales (مجموعی بل)", "Rs. ${stats.todaySales.toInt()}", 40f, y)
        drawStatCard("Cash Recovered (کل وصولی)", "Rs. ${stats.todayTotalCollected.toInt()}", 315f, y, isPositive = true)

        y += 75f
        drawStatCard("Operating Expenses (اخراجات)", "Rs. ${stats.todayExpenses.toInt()}", 40f, y, isPositive = false)
        drawStatCard("Net Plant Profit (خالص نفع)", "Rs. ${stats.estimatedProfit.toInt()}", 315f, y, isPositive = stats.estimatedProfit >= 0)

        y += 90f
        paint.color = Color.rgb(226, 232, 240)
        canvas.drawLine(40f, y, 555f, y, paint)

        y += 24f
        paint.textSize = 10f
        paint.color = Color.rgb(148, 163, 184)
        paint.isFakeBoldText = false
        canvas.drawText("Aab-e-Baraan RO Drinking Water Plant", 40f, y, paint)

        document.finishPage(page)

        val outputDir = context.cacheDir
        val file = File(outputDir, "Plant_Report_${System.currentTimeMillis()}.pdf")
        val fos = FileOutputStream(file)
        document.writeTo(fos)
        fos.close()
        document.close()

        return file
    }

    /**
     * Generates a clean, professional Customer Invoice PDF Voucher.
     */
    fun generateInvoicePdf(
        context: Context,
        invoice: Invoice,
        customer: Customer
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 points
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true }

        // Header Background
        paint.color = Color.rgb(10, 37, 64) // WaterNavyPrimary
        canvas.drawRect(0f, 0f, 595f, 90f, paint)

        drawHeaderLogoAndWatermark(context, canvas)

        // Brand Title
        paint.color = Color.WHITE
        paint.textSize = 18.5f
        paint.isFakeBoldText = true
        canvas.drawText("AAB-E-BARAAN RO DRINKING WATER PLANT", 36f, 40f, paint)

        paint.textSize = 9.5f
        paint.isFakeBoldText = false
        paint.color = Color.rgb(200, 230, 255)
        canvas.drawText("Clean, Pure & Mineral Enriched Drinking Water • Customer Invoice & Bill", 36f, 58f, paint)
        canvas.drawText("آب باراں منرل واٹر پلانٹ - کسٹمر انوائس اور بل واؤچر", 36f, 75f, paint)

        var y = 120f

        // Invoice Info Row
        paint.color = Color.rgb(30, 41, 59)
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("Invoice #: ${invoice.invoiceNumber}", 40f, y, paint)

        paint.isFakeBoldText = false
        paint.textSize = 11f
        paint.color = Color.rgb(71, 85, 105)
        canvas.drawText("Date: ${invoice.date}", 380f, y, paint)

        y += 20f
        paint.color = Color.rgb(226, 232, 240)
        canvas.drawLine(40f, y, 555f, y, paint)

        y += 24f
        // Customer Info Card
        paint.color = Color.rgb(241, 245, 249)
        val infoRect = RectF(40f, y, 555f, y + 54f)
        canvas.drawRoundRect(infoRect, 8f, 8f, paint)

        paint.color = Color.rgb(15, 23, 42)
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("Customer: ${customer.name}", 56f, y + 22f, paint)

        paint.isFakeBoldText = false
        paint.textSize = 11f
        paint.color = Color.rgb(51, 65, 85)
        canvas.drawText("Contact: ${customer.phone.ifEmpty { "N/A" }}", 56f, y + 42f, paint)
        canvas.drawText("Area: ${customer.area}", 340f, y + 22f, paint)
        canvas.drawText("Status: ${invoice.status.name}", 340f, y + 42f, paint)

        y += 80f

        // Table Header
        paint.color = Color.rgb(224, 242, 254)
        val tableHeaderRect = RectF(40f, y, 555f, y + 26f)
        canvas.drawRoundRect(tableHeaderRect, 4f, 4f, paint)

        paint.color = Color.rgb(10, 37, 64)
        paint.textSize = 11f
        paint.isFakeBoldText = true
        canvas.drawText("Description / Delivered Items", 52f, y + 17f, paint)
        canvas.drawText("Total Amount (PKR)", 420f, y + 17f, paint)

        y += 38f

        // Items row
        paint.color = Color.rgb(30, 41, 59)
        paint.textSize = 11f
        paint.isFakeBoldText = false
        canvas.drawText(invoice.itemsSummary.ifEmpty { "Water Supply & Refill Bottles" }, 52f, y, paint)
        canvas.drawText("Rs. ${invoice.totalAmount.toInt()}", 420f, y, paint)

        y += 24f
        paint.color = Color.rgb(241, 245, 249)
        canvas.drawLine(40f, y, 555f, y, paint)

        y += 30f

        // Totals Box
        paint.color = Color.rgb(248, 250, 252)
        val totalsRect = RectF(300f, y, 555f, y + 105f)
        canvas.drawRoundRect(totalsRect, 8f, 8f, paint)

        paint.color = Color.rgb(30, 41, 59)
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("Grand Total:", 320f, y + 25f, paint)
        canvas.drawText("Rs. ${invoice.totalAmount.toInt()}", 450f, y + 25f, paint)

        paint.color = Color.rgb(22, 101, 52)
        canvas.drawText("Paid Amount:", 320f, y + 55f, paint)
        canvas.drawText("Rs. ${invoice.paidAmount.toInt()}", 450f, y + 55f, paint)

        paint.color = if (invoice.balanceDue > 0) Color.rgb(220, 38, 38) else Color.rgb(22, 101, 52)
        canvas.drawText("Balance Due:", 320f, y + 85f, paint)
        canvas.drawText("Rs. ${invoice.balanceDue.toInt()}", 450f, y + 85f, paint)

        y += 135f
        paint.color = Color.rgb(226, 232, 240)
        canvas.drawLine(40f, y, 555f, y, paint)

        y += 24f
        paint.textSize = 10f
        paint.color = Color.rgb(148, 163, 184)
        paint.isFakeBoldText = false
        canvas.drawText("Thank you for choosing Aab-e-Baraan RO Drinking Water Plant • Contact: ${resolvedAdminPhone()}", 40f, y, paint)

        document.finishPage(page)

        val outputDir = context.cacheDir
        val file = File(outputDir, "Invoice_${invoice.invoiceNumber}.pdf")
        val fos = FileOutputStream(file)
        document.writeTo(fos)
        fos.close()
        document.close()

        return file
    }

    /**
     * Generates a clean, professional Expense Voucher PDF for sharing.
     */
    fun generateExpensePdf(
        context: Context,
        expense: Expense
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true }

        // Header Background
        paint.color = Color.rgb(10, 37, 64)
        canvas.drawRect(0f, 0f, 595f, 90f, paint)

        drawHeaderLogoAndWatermark(context, canvas)

        // Brand Title
        paint.color = Color.WHITE
        paint.textSize = 18.5f
        paint.isFakeBoldText = true
        canvas.drawText("AAB-E-BARAAN RO DRINKING WATER PLANT", 36f, 40f, paint)

        paint.textSize = 10.5f
        paint.isFakeBoldText = false
        paint.color = Color.rgb(200, 230, 255)
        canvas.drawText("Official Plant Expense Voucher (${expense.expenseType.name})", 36f, 64f, paint)

        var y = 125f

        // Voucher Info Row
        paint.color = Color.rgb(30, 41, 59)
        paint.textSize = 13f
        paint.isFakeBoldText = true
        canvas.drawText("Voucher #: ${expense.voucherNumber}", 40f, y, paint)

        paint.isFakeBoldText = false
        paint.textSize = 11f
        paint.color = Color.rgb(71, 85, 105)
        canvas.drawText("Date: ${expense.date}", 420f, y, paint)

        y += 20f
        paint.color = Color.rgb(226, 232, 240)
        canvas.drawLine(40f, y, 555f, y, paint)

        y += 24f
        // Summary Meta Card
        paint.color = Color.rgb(241, 245, 249)
        val infoRect = RectF(40f, y, 555f, y + 76f)
        canvas.drawRoundRect(infoRect, 8f, 8f, paint)

        val sourceText = if (expense.paymentSource == PaymentSource.PAY_FROM_INVESTMENT) "Investment Account" else "From Sale"
        val modeText = if (expense.paymentMethod.isCash) "Cash" else "Online"

        paint.color = Color.rgb(15, 23, 42)
        paint.textSize = 11.5f
        paint.isFakeBoldText = true
        canvas.drawText("Category: ${expense.categoryName}", 56f, y + 24f, paint)
        canvas.drawText("Type: ${expense.expenseType.name}", 340f, y + 24f, paint)

        paint.isFakeBoldText = false
        paint.textSize = 11f
        paint.color = Color.rgb(51, 65, 85)
        canvas.drawText("Vendor / Payee: ${expense.payee.ifBlank { "General" }}", 56f, y + 46f, paint)
        canvas.drawText("Source: $sourceText", 340f, y + 46f, paint)
        canvas.drawText("Entered By: ${expense.enteredBy.ifBlank { "Staff" }}", 56f, y + 66f, paint)
        canvas.drawText("Mode: $modeText", 340f, y + 66f, paint)

        y += 100f

        // Details Table Header
        val invItems = expense.effectiveInventoryItems
        paint.color = Color.rgb(224, 242, 254)
        val tableHeaderRect = RectF(40f, y, 555f, y + 26f)
        canvas.drawRoundRect(tableHeaderRect, 4f, 4f, paint)

        paint.color = Color.rgb(10, 37, 64)
        paint.textSize = 11f
        paint.isFakeBoldText = true
        if (invItems.isNotEmpty()) {
            canvas.drawText("Inventory Item & Category", 52f, y + 17f, paint)
            canvas.drawText("Qty x Rate", 315f, y + 17f, paint)
            canvas.drawText("Amount (PKR)", 445f, y + 17f, paint)
        } else {
            canvas.drawText("Expense Description", 52f, y + 17f, paint)
            canvas.drawText("Amount (PKR)", 440f, y + 17f, paint)
        }

        y += 40f

        if (invItems.isNotEmpty()) {
            invItems.forEachIndexed { idx, line ->
                val qtyStr = if (line.quantity % 1.0 == 0.0) line.quantity.toInt().toString()
                else String.format(Locale.ENGLISH, "%.2f", line.quantity)
                val rateStr = if (line.unitRate % 1.0 == 0.0) line.unitRate.toInt().toString()
                else String.format(Locale.ENGLISH, "%.2f", line.unitRate)
                val lineTot = if (line.lineTotal > 0.0) line.lineTotal else line.quantity * line.unitRate
                val catLabel = line.categoryName.ifBlank { "Uncategorized" }

                paint.color = Color.rgb(15, 23, 42)
                paint.textSize = 11f
                paint.isFakeBoldText = true
                canvas.drawText("${idx + 1}. ${line.itemName.take(32)}", 52f, y, paint)

                paint.isFakeBoldText = false
                paint.textSize = 10f
                paint.color = Color.rgb(51, 65, 85)
                canvas.drawText("$qtyStr x Rs. $rateStr", 315f, y, paint)

                paint.isFakeBoldText = true
                paint.textSize = 11f
                paint.color = Color.rgb(185, 28, 28)
                canvas.drawText("Rs. ${lineTot.toInt()}", 445f, y, paint)

                y += 14f
                paint.isFakeBoldText = false
                paint.textSize = 9f
                paint.color = Color.rgb(100, 116, 139)
                canvas.drawText("Category: $catLabel (${if (line.itemType == "BOTTLE") "Bottle Stock" else "Material Stock"})", 64f, y, paint)
                y += 18f
            }
        } else {
            paint.color = Color.rgb(30, 41, 59)
            paint.textSize = 11.5f
            paint.isFakeBoldText = false
            canvas.drawText(expense.description.ifBlank { expense.categoryName }, 52f, y, paint)

            paint.isFakeBoldText = true
            paint.color = Color.rgb(185, 28, 28)
            canvas.drawText("Rs. ${expense.amount.toInt()}", 440f, y, paint)

            y += 22f
        }

        if (expense.invoiceNumber.isNotBlank()) {
            paint.isFakeBoldText = false
            paint.textSize = 10f
            paint.color = Color.rgb(71, 85, 105)
            canvas.drawText("Invoice / Bill #: ${expense.invoiceNumber}", 52f, y, paint)
            y += 18f
        }
        if (expense.notes.isNotBlank()) {
            paint.isFakeBoldText = false
            paint.textSize = 10f
            paint.color = Color.rgb(71, 85, 105)
            canvas.drawText("Note: ${expense.notes}", 52f, y, paint)
            y += 20f
        }

        paint.color = Color.rgb(226, 232, 240)
        canvas.drawLine(40f, y, 555f, y, paint)

        y += 28f
        // Total Highlight Box
        paint.color = Color.rgb(254, 242, 242)
        val totalRect = RectF(320f, y, 555f, y + 48f)
        canvas.drawRoundRect(totalRect, 8f, 8f, paint)
        paint.color = Color.rgb(185, 28, 28)
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("Total Voucher Amount:", 334f, y + 20f, paint)
        paint.textSize = 15f
        canvas.drawText("Rs. ${expense.amount.toInt()}", 334f, y + 39f, paint)

        y += 74f
        if (expense.isAcknowledged) {
            paint.color = Color.rgb(22, 101, 52)
            paint.textSize = 10f
            paint.isFakeBoldText = false
            val verifiedBySuffix = if (expense.acknowledgedBy.isNotBlank()) " by ${expense.acknowledgedBy}" else ""
            canvas.drawText("Status: Verified$verifiedBySuffix", 40f, y, paint)
            y += 20f
        }

        paint.color = Color.rgb(148, 163, 184)
        paint.textSize = 9f
        canvas.drawText("Aab-e-Baraan RO Drinking Water Plant • Contact: ${resolvedAdminPhone()}", 40f, y, paint)

        document.finishPage(page)

        val outputDir = context.cacheDir
        val safeNum = expense.voucherNumber.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val file = File(outputDir, "Expense_Voucher_$safeNum.pdf")
        val fos = FileOutputStream(file)
        document.writeTo(fos)
        fos.close()
        document.close()

        return file
    }

    fun buildExpenseShareSummary(expense: Expense): String {
        val sourceTxt = if (expense.paymentSource == PaymentSource.PAY_FROM_INVESTMENT) "Investment Account" else "From Sale"
        val modeTxt = if (expense.paymentMethod.isCash) "Cash" else "Online"
        val itemsBlock = if (expense.effectiveInventoryItems.isNotEmpty()) {
            val lines = expense.effectiveInventoryItems.mapIndexed { idx, line ->
                val qtyStr = if (line.quantity % 1.0 == 0.0) line.quantity.toInt().toString()
                else String.format(Locale.ENGLISH, "%.2f", line.quantity)
                val lineTot = if (line.lineTotal > 0.0) line.lineTotal else line.quantity * line.unitRate
                "  ${idx + 1}. ${line.itemName} (${line.categoryName.ifBlank { "Uncategorized" }}): $qtyStr x Rs.${line.unitRate.toInt()} = Rs.${lineTot.toInt()}"
            }.joinToString("\n")
            "\nItems:\n$lines"
        } else ""
        val invoiceLine = if (expense.invoiceNumber.isNotBlank()) "\nInvoice #: ${expense.invoiceNumber}" else ""
        return """
            *AAB-E-BARAAN RO PLANT - EXPENSE VOUCHER*
            Voucher #: ${expense.voucherNumber}
            Date: ${expense.date}
            Type: ${expense.expenseType.name} (${if (expense.hasInventoryItems) "Inventory" else "Non-Inventory"})
            Category: ${expense.categoryName.ifBlank { "Uncategorized" }}
            Description: ${expense.description}$itemsBlock$invoiceLine
            Total Amount: Rs. ${expense.amount.toInt()}
            Vendor: ${expense.payee.ifBlank { "General" }}
            Payment Source: $sourceTxt
            Payment Mode: $modeTxt
            Note: ${expense.notes.ifBlank { "-" }}
        """.trimIndent()
    }

    /**
     * Generates a comprehensive, multi-page A4 Daily / Weekly / Monthly Business Statement PDF.
     * Contains complete day-by-day & customer-wise delivery details, customer-wise cash/online received,
     * detailed daily expenses, AND explicitly includes & tags all unapproved deliveries/payments/expenses.
     */
    fun generatePeriodStatementPdf(
        context: Context,
        periodLabel: String,
        dateRangeLabel: String,
        deliveries: List<Delivery>,
        payments: List<Payment>,
        expenses: List<Expense>,
        bottleSizes: List<BottleSize> = emptyList()
    ): File {
        val document = PdfDocument()
        try {
            val sortedDeliveries = deliveries.sortedWith(compareBy<Delivery> { it.date }.thenBy { it.createdAt })
            val sortedPayments = payments.sortedWith(compareBy<Payment> { it.date }.thenBy { it.createdAt })
            val sortedExpenses = expenses.sortedWith(compareBy<Expense> { it.date }.thenBy { it.createdAt })

            // Compute comprehensive statement KPIs
            val totalBilledSales = sortedDeliveries.sumOf { it.totalAmount }
            val totalBottlesDelivered = sortedDeliveries.sumOf { it.totalFullDelivered }
            val totalEmptiesReturned = sortedDeliveries.sumOf { it.totalEmptyCollected }

            // Cash & Online received (avoid double counting delivery spot payments if a Payment record already exists for that delivery)
            val paymentLinkedDeliveryIds = sortedPayments.flatMap { p -> p.allocations.map { it.invoiceId } }.toSet()
            val directDeliveryCash = sortedDeliveries
                .filter { d ->
                    d.paymentCollected > 0.0 &&
                        d.paymentMethod.equals("Cash", ignoreCase = true) &&
                        d.invoiceId !in paymentLinkedDeliveryIds &&
                        d.id !in paymentLinkedDeliveryIds &&
                        "inv_${d.id}" !in paymentLinkedDeliveryIds
                }
                .sumOf { it.paymentCollected }
            val directDeliveryOnline = sortedDeliveries
                .filter { d ->
                    d.paymentCollected > 0.0 &&
                        !d.paymentMethod.equals("Cash", ignoreCase = true) &&
                        !d.paymentMethod.equals("Unpaid", ignoreCase = true) &&
                        d.invoiceId !in paymentLinkedDeliveryIds &&
                        d.id !in paymentLinkedDeliveryIds &&
                        "inv_${d.id}" !in paymentLinkedDeliveryIds
                }
                .sumOf { it.paymentCollected }

            val totalCashReceived = sortedPayments.filter { it.paymentMethod.isCash }.sumOf { it.amount } + directDeliveryCash
            val totalOnlineReceived = sortedPayments.filter { !it.paymentMethod.isCash }.sumOf { it.amount } + directDeliveryOnline
            val totalReceived = totalCashReceived + totalOnlineReceived
            val totalCreditDue = (totalBilledSales - totalReceived).coerceAtLeast(0.0)

            val totalExpenses = sortedExpenses.sumOf { it.amount }
            val saleExpenses = sortedExpenses.filter { it.paymentSource == PaymentSource.PAY_FROM_SALE }.sumOf { it.amount }
            val investmentExpenses = sortedExpenses.filter { it.paymentSource == PaymentSource.PAY_FROM_INVESTMENT }.sumOf { it.amount }
            val netCashBalance = totalReceived - saleExpenses
            val netOperatingProfit = totalBilledSales - totalExpenses

            val unapprovedDeliveriesCount = sortedDeliveries.count { d ->
                d.status == DeliveryStatus.PENDING ||
                    d.status == DeliveryStatus.ASSIGNED ||
                    findRelatedPaymentsForDelivery(d, sortedPayments).any { !it.isAcknowledged }
            }
            val unapprovedPayments = sortedPayments.filter { !it.isAcknowledged }
            val unapprovedExpenses = sortedExpenses.filter { !it.isAcknowledged || !it.isApprovedByOwner }

            val paint = Paint().apply { isAntiAlias = true }
            var pageNum = 1
            var page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum).create())
            var canvas = page.canvas
            var y = 104f

            fun drawPageHeader(c: Canvas, currentPage: Int) {
                paint.color = Color.rgb(10, 37, 64)
                c.drawRect(0f, 0f, 595f, 84f, paint)
                drawHeaderLogoAndWatermark(context, c)

                paint.color = Color.WHITE
                paint.textSize = 17f
                paint.isFakeBoldText = true
                c.drawText("AAB-E-BARAAN RO DRINKING WATER PLANT", 30f, 34f, paint)

                paint.textSize = 10f
                paint.isFakeBoldText = true
                paint.color = Color.rgb(186, 230, 253)
                c.drawText("$periodLabel — Detailed Statement ($dateRangeLabel)", 30f, 52f, paint)

                paint.textSize = 8.8f
                paint.isFakeBoldText = false
                paint.color = Color.rgb(224, 242, 254)
                c.drawText("مکمل تفصیلی اسٹیٹمنٹ: ترسیلات، روزانہ کیش وصولی اور اخراجات  |  Page $currentPage", 30f, 70f, paint)
            }

            fun drawPageFooter(c: Canvas, currentPage: Int) {
                paint.color = Color.rgb(226, 232, 240)
                c.drawLine(30f, 814f, 565f, 814f, paint)
                paint.color = Color.rgb(100, 116, 139)
                paint.textSize = 8f
                paint.isFakeBoldText = false
                c.drawText("Aab-e-Baraan RO Plant • $periodLabel Statement", 30f, 828f, paint)
                c.drawText("Page $currentPage", 528f, 828f, paint)
            }

            fun startNewPage(onNewPageHeader: (() -> Unit)? = null) {
                drawPageFooter(canvas, pageNum)
                document.finishPage(page)
                pageNum += 1
                page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum).create())
                canvas = page.canvas
                drawPageHeader(canvas, pageNum)
                y = 104f
                onNewPageHeader?.invoke()
            }

            fun ensureSpace(needed: Float, onNewPageHeader: (() -> Unit)? = null) {
                if (y + needed > 802f) {
                    startNewPage(onNewPageHeader)
                }
            }

            fun drawSectionTitle(title: String, subtitle: String, accentColor: Int) {
                ensureSpace(44f)
                paint.color = accentColor
                canvas.drawRoundRect(RectF(30f, y, 565f, y + 24f), 4f, 4f, paint)
                paint.color = Color.WHITE
                paint.textSize = 10f
                paint.isFakeBoldText = true
                canvas.drawText(title, 40f, y + 15.5f, paint)
                paint.textSize = 8.5f
                paint.isFakeBoldText = false
                paint.color = Color.rgb(224, 242, 254)
                canvas.drawText(subtitle, 330f, y + 15.5f, paint)
                y += 30f
            }

            drawPageHeader(canvas, pageNum)

            // KPI Summary Grid (2 rows x 3 compact cards)
            fun drawMiniKpi(label: String, value: String, sub: String, x: Float, yPos: Float, w: Float, valColor: Int) {
                paint.color = Color.rgb(248, 250, 252)
                val rect = RectF(x, yPos, x + w, yPos + 46f)
                canvas.drawRoundRect(rect, 6f, 6f, paint)
                paint.color = Color.rgb(203, 213, 225)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.8f
                canvas.drawRoundRect(rect, 6f, 6f, paint)
                paint.style = Paint.Style.FILL

                paint.color = Color.rgb(100, 116, 139)
                paint.textSize = 8f
                paint.isFakeBoldText = false
                canvas.drawText(label, x + 8f, yPos + 13f, paint)

                paint.color = valColor
                paint.textSize = 12f
                paint.isFakeBoldText = true
                canvas.drawText(value, x + 8f, yPos + 30f, paint)

                paint.color = Color.rgb(100, 116, 139)
                paint.textSize = 7.5f
                paint.isFakeBoldText = false
                canvas.drawText(sub, x + 8f, yPos + 41f, paint)
            }

            val cardW = 172f
            drawMiniKpi(
                "Total Billed Sales",
                "Rs. ${totalBilledSales.toInt()}",
                "${sortedDeliveries.size} Deliveries • $totalBottlesDelivered Full",
                30f, y, cardW, Color.rgb(10, 37, 64)
            )
            drawMiniKpi(
                "Cash Received (Naqd)",
                "Rs. ${totalCashReceived.toInt()}",
                "Online/Bank: Rs. ${totalOnlineReceived.toInt()}",
                211f, y, cardW, Color.rgb(22, 101, 52)
            )
            drawMiniKpi(
                "Total Collected / Due",
                "Rs. ${totalReceived.toInt()}",
                "Period Unpaid Due: Rs. ${totalCreditDue.toInt()}",
                392f, y, cardW, Color.rgb(2, 132, 199)
            )
            y += 54f

            drawMiniKpi(
                "Total Expenses",
                "Rs. ${totalExpenses.toInt()}",
                "Sale: ${saleExpenses.toInt()} | Inv: ${investmentExpenses.toInt()}",
                30f, y, cardW, Color.rgb(185, 28, 28)
            )
            drawMiniKpi(
                "Net Operating Profit",
                "Rs. ${netOperatingProfit.toInt()}",
                "Cash After Sale Exp: Rs. ${netCashBalance.toInt()}",
                211f, y, cardW, if (netOperatingProfit >= 0) Color.rgb(22, 101, 52) else Color.rgb(185, 28, 28)
            )
            drawMiniKpi(
                "Empties Returned",
                "$totalEmptiesReturned Units",
                "Full Delivered: $totalBottlesDelivered Units",
                392f, y, cardW,
                Color.rgb(2, 132, 199)
            )
            y += 58f

            // ==========================================================
            // SECTION 1: BOTTLE SIZE-WISE SALES & PAYMENT SUMMARY
            // ==========================================================
            data class BottleSizeSummaryAcc(
                val sizeName: String,
                var totalQty: Int = 0,
                var totalAmount: Double = 0.0,
                var paidAmount: Double = 0.0,
                var unpaidAmount: Double = 0.0,
                val ratesUsed: MutableSet<Int> = linkedSetOf()
            )

            val sizeSummaryMap = linkedMapOf<String, BottleSizeSummaryAcc>()
            val sizeNameLookup = bottleSizes.associate { it.id to it.name }
            sortedDeliveries.forEach { del ->
                val relPays = findRelatedPaymentsForDelivery(del, sortedPayments)
                val allocPaid = relPays.sumOf { p ->
                    val m = p.allocations.filter {
                        (del.invoiceId.isNotBlank() && it.invoiceId == del.invoiceId) ||
                            it.invoiceId == del.id || it.invoiceId == "inv_${del.id}"
                    }.sumOf { it.allocatedAmount }
                    if (m > 0.0) m else p.amount
                }
                val delPaid = maxOf(del.paymentCollected, allocPaid).coerceAtMost(del.totalAmount)
                val delPaidRatio = if (del.totalAmount > 0.0) (delPaid / del.totalAmount).coerceIn(0.0, 1.0) else 0.0
                val itemsSubtotal = del.items.sumOf { if (it.lineTotal > 0.0) it.lineTotal else it.quantity * it.unitPrice }

                if (del.items.isNotEmpty()) {
                    del.items.forEach { item ->
                        val resolvedName = item.bottleName.ifBlank { sizeNameLookup[item.bottleSizeId] ?: "Bottle" }
                        val lineAmt = if (item.lineTotal > 0.0) item.lineTotal else item.quantity * item.unitPrice
                        val effectiveAmt = if (itemsSubtotal > 0.0 && del.deliveryCharges > 0.0) {
                            lineAmt * (del.totalAmount / itemsSubtotal)
                        } else lineAmt
                        val linePaid = effectiveAmt * delPaidRatio
                        val lineUnpaid = (effectiveAmt - linePaid).coerceAtLeast(0.0)

                        val acc = sizeSummaryMap.getOrPut(resolvedName) { BottleSizeSummaryAcc(resolvedName) }
                        acc.totalQty += item.quantity
                        acc.totalAmount += effectiveAmt
                        acc.paidAmount += linePaid
                        acc.unpaidAmount += lineUnpaid
                        if (item.unitPrice > 0.0) acc.ratesUsed.add(item.unitPrice.toInt())
                    }
                } else if (del.totalFullDelivered > 0) {
                    val acc = sizeSummaryMap.getOrPut("Water Bottles") { BottleSizeSummaryAcc("Water Bottles") }
                    acc.totalQty += del.totalFullDelivered
                    acc.totalAmount += del.totalAmount
                    acc.paidAmount += delPaid
                    acc.unpaidAmount += (del.totalAmount - delPaid).coerceAtLeast(0.0)
                }
            }
            val sizeSummaryRows = sizeSummaryMap.values.toList()

            fun drawBottleSizeSummaryHeader() {
                paint.color = Color.rgb(224, 242, 254)
                canvas.drawRect(30f, y, 565f, y + 20f, paint)
                paint.color = Color.rgb(10, 37, 64)
                paint.textSize = 8.2f
                paint.isFakeBoldText = true
                canvas.drawText("Bottle Size", 36f, y + 13.5f, paint)
                canvas.drawText("Total Qty", 200f, y + 13.5f, paint)
                canvas.drawText("Rate", 270f, y + 13.5f, paint)
                canvas.drawText("Total Amount", 342f, y + 13.5f, paint)
                canvas.drawText("Paid", 434f, y + 13.5f, paint)
                canvas.drawText("Unpaid", 500f, y + 13.5f, paint)
                y += 24f
            }

            drawSectionTitle(
                "1. BOTTLE SIZE-WISE SUMMARY (${periodLabel.uppercase(Locale.ENGLISH)})",
                "Period: $dateRangeLabel",
                Color.rgb(2, 132, 199)
            )
            drawBottleSizeSummaryHeader()

            if (sizeSummaryRows.isEmpty()) {
                paint.color = Color.rgb(100, 116, 139)
                paint.textSize = 9f
                paint.isFakeBoldText = false
                canvas.drawText("No bottle deliveries recorded for this period.", 38f, y + 10f, paint)
                y += 24f
            } else {
                sizeSummaryRows.forEachIndexed { idx, row ->
                    ensureSpace(22f) { drawBottleSizeSummaryHeader() }
                    if (idx % 2 == 1) {
                        paint.color = Color.rgb(248, 250, 252)
                        canvas.drawRect(30f, y - 9f, 565f, y + 11f, paint)
                    }
                    val rateText = when {
                        row.ratesUsed.size == 1 -> "Rs.${row.ratesUsed.first()}"
                        row.totalQty > 0 -> "~Rs.${(row.totalAmount / row.totalQty).toInt()}"
                        else -> "-"
                    }
                    paint.textSize = 8.2f
                    paint.isFakeBoldText = true
                    paint.color = Color.rgb(15, 23, 42)
                    canvas.drawText(truncateText(row.sizeName, 26), 36f, y + 2f, paint)

                    paint.isFakeBoldText = true
                    paint.color = Color.rgb(10, 37, 64)
                    canvas.drawText("${row.totalQty}", 206f, y + 2f, paint)

                    paint.isFakeBoldText = false
                    paint.color = Color.rgb(51, 65, 85)
                    canvas.drawText(rateText, 270f, y + 2f, paint)

                    paint.isFakeBoldText = true
                    paint.color = Color.rgb(15, 23, 42)
                    canvas.drawText("Rs.${row.totalAmount.toInt()}", 342f, y + 2f, paint)

                    paint.color = Color.rgb(22, 101, 52)
                    canvas.drawText("Rs.${row.paidAmount.toInt()}", 434f, y + 2f, paint)

                    paint.color = if (row.unpaidAmount > 0.5) Color.rgb(185, 28, 28) else Color.rgb(100, 116, 139)
                    canvas.drawText("Rs.${row.unpaidAmount.toInt()}", 500f, y + 2f, paint)

                    paint.color = Color.rgb(226, 232, 240)
                    canvas.drawLine(30f, y + 11f, 565f, y + 11f, paint)
                    y += 19f
                }

                // Grand Total Row for Bottle Size-Wise Summary
                ensureSpace(24f)
                paint.color = Color.rgb(241, 245, 249)
                canvas.drawRect(30f, y - 9f, 565f, y + 13f, paint)
                paint.textSize = 8.5f
                paint.isFakeBoldText = true
                paint.color = Color.rgb(10, 37, 64)
                canvas.drawText("GRAND TOTAL", 36f, y + 3f, paint)
                canvas.drawText("${sizeSummaryRows.sumOf { it.totalQty }}", 206f, y + 3f, paint)
                canvas.drawText("-", 270f, y + 3f, paint)
                canvas.drawText("Rs.${sizeSummaryRows.sumOf { it.totalAmount }.toInt()}", 342f, y + 3f, paint)
                paint.color = Color.rgb(22, 101, 52)
                canvas.drawText("Rs.${sizeSummaryRows.sumOf { it.paidAmount }.toInt()}", 434f, y + 3f, paint)
                paint.color = Color.rgb(185, 28, 28)
                canvas.drawText("Rs.${sizeSummaryRows.sumOf { it.unpaidAmount }.toInt()}", 500f, y + 3f, paint)
                y += 22f
            }

            y += 8f

            // ==========================================================
            // SECTION 2: DELIVERY DETAILS (CUSTOMER & DATE WISE)
            // ==========================================================
            fun drawDeliveryTableHeader() {
                paint.color = Color.rgb(224, 242, 254)
                canvas.drawRect(30f, y, 565f, y + 20f, paint)
                paint.color = Color.rgb(10, 37, 64)
                paint.textSize = 8.2f
                paint.isFakeBoldText = true
                canvas.drawText("Date", 34f, y + 13.5f, paint)
                canvas.drawText("Customer / Voucher", 88f, y + 13.5f, paint)
                canvas.drawText("Delivered Items (Size Wise)", 214f, y + 13.5f, paint)
                canvas.drawText("Bill", 374f, y + 13.5f, paint)
                canvas.drawText("Paid", 420f, y + 13.5f, paint)
                canvas.drawText("Unpaid", 464f, y + 13.5f, paint)
                canvas.drawText("Status", 506f, y + 13.5f, paint)
                y += 24f
            }

            drawSectionTitle(
                "2. DELIVERY DETAILS (CUSTOMER & SIZE WISE)",
                "Total: ${sortedDeliveries.size} | Empties Ret: $totalEmptiesReturned",
                Color.rgb(10, 37, 64)
            )
            drawDeliveryTableHeader()

            if (sortedDeliveries.isEmpty()) {
                paint.color = Color.rgb(100, 116, 139)
                paint.textSize = 9f
                paint.isFakeBoldText = false
                canvas.drawText("No deliveries recorded for this period.", 38f, y + 10f, paint)
                y += 24f
            } else {
                sortedDeliveries.forEachIndexed { idx, del ->
                    ensureSpace(26f) { drawDeliveryTableHeader() }
                    if (idx % 2 == 1) {
                        paint.color = Color.rgb(248, 250, 252)
                        canvas.drawRect(30f, y - 9f, 565f, y + 15f, paint)
                    }

                    val relPays = findRelatedPaymentsForDelivery(del, sortedPayments)
                    val allocPaid = relPays.sumOf { p ->
                        val m = p.allocations.filter {
                            (del.invoiceId.isNotBlank() && it.invoiceId == del.invoiceId) ||
                                it.invoiceId == del.id || it.invoiceId == "inv_${del.id}"
                        }.sumOf { it.allocatedAmount }
                        if (m > 0.0) m else p.amount
                    }
                    val paidAmt = maxOf(del.paymentCollected, allocPaid).coerceAtMost(del.totalAmount)
                    val dueAmt = (del.totalAmount - paidAmt).coerceAtLeast(0.0)
                    val hasUnapprovedPay = relPays.any { !it.isAcknowledged }
                    val isUnapprovedDelivery = del.status == DeliveryStatus.PENDING ||
                        del.status == DeliveryStatus.ASSIGNED ||
                        hasUnapprovedPay

                    val itemsText = del.items.joinToString(", ") { "${it.quantity}x ${it.bottleName}" }
                        .ifBlank { "${del.totalFullDelivered} Bottles" }

                    paint.isFakeBoldText = false
                    paint.textSize = 7.8f
                    paint.color = Color.rgb(51, 65, 85)
                    canvas.drawText(del.date.takeLast(8), 34f, y + 2f, paint)

                    paint.isFakeBoldText = true
                    paint.color = Color.rgb(15, 23, 42)
                    canvas.drawText(truncateText(del.customerName, 20), 88f, y + 1f, paint)
                    paint.isFakeBoldText = false
                    paint.textSize = 6.8f
                    paint.color = Color.rgb(100, 116, 139)
                    canvas.drawText(truncateText("${del.deliveryNumber} • ${del.staffName}", 24), 88f, y + 10.5f, paint)

                    paint.textSize = 7.6f
                    paint.color = Color.rgb(30, 41, 59)
                    canvas.drawText(truncateText(itemsText, 30), 214f, y + 1f, paint)
                    paint.textSize = 6.8f
                    paint.color = Color.rgb(100, 116, 139)
                    canvas.drawText("Full: ${del.totalFullDelivered} | Ret: ${del.totalEmptyCollected}", 214f, y + 10.5f, paint)

                    paint.textSize = 8f
                    paint.isFakeBoldText = true
                    paint.color = Color.rgb(15, 23, 42)
                    canvas.drawText("${del.totalAmount.toInt()}", 374f, y + 3f, paint)

                    paint.color = Color.rgb(22, 101, 52)
                    canvas.drawText("${paidAmt.toInt()}", 420f, y + 3f, paint)

                    paint.color = if (dueAmt > 0) Color.rgb(185, 28, 28) else Color.rgb(100, 116, 139)
                    canvas.drawText("${dueAmt.toInt()}", 464f, y + 3f, paint)

                    val statusLabel = when {
                        del.status == DeliveryStatus.CANCELLED -> "Cancelled"
                        dueAmt <= 0.0 -> "Paid"
                        paidAmt > 0.0 -> "Partial Due"
                        else -> "Credit Due"
                    }
                    paint.textSize = 7.4f
                    paint.isFakeBoldText = true
                    paint.color = when {
                        del.status == DeliveryStatus.CANCELLED -> Color.rgb(100, 116, 139)
                        dueAmt <= 0.0 -> Color.rgb(22, 101, 52)
                        else -> Color.rgb(185, 28, 28)
                    }
                    canvas.drawText(statusLabel, 506f, y + 3f, paint)

                    paint.color = Color.rgb(226, 232, 240)
                    canvas.drawLine(30f, y + 15f, 565f, y + 15f, paint)
                    y += 24f
                }
            }

            y += 10f

            // ==========================================================
            // SECTION 2: DAILY CASH & ONLINE RECEIVED (CUSTOMER-WISE)
            // ==========================================================
            fun drawPaymentTableHeader() {
                paint.color = Color.rgb(220, 252, 231)
                canvas.drawRect(30f, y, 565f, y + 20f, paint)
                paint.color = Color.rgb(22, 101, 52)
                paint.textSize = 8.2f
                paint.isFakeBoldText = true
                canvas.drawText("Date", 34f, y + 13.5f, paint)
                canvas.drawText("Receipt #", 90f, y + 13.5f, paint)
                canvas.drawText("Customer Name", 168f, y + 13.5f, paint)
                canvas.drawText("Mode", 312f, y + 13.5f, paint)
                canvas.drawText("Received By", 376f, y + 13.5f, paint)
                canvas.drawText("Amount", 448f, y + 13.5f, paint)
                canvas.drawText("Status", 506f, y + 13.5f, paint)
                y += 24f
            }

            drawSectionTitle(
                "3. ${periodLabel.uppercase(Locale.ENGLISH)} PAYMENTS (CASH & ONLINE)",
                "Cash: Rs.${totalCashReceived.toInt()} | Online: Rs.${totalOnlineReceived.toInt()}",
                Color.rgb(22, 101, 52)
            )
            drawPaymentTableHeader()

            // Combine explicit Payment records plus any direct delivery collections without Payment rows
            data class StatementReceiptRow(
                val date: String,
                val refNo: String,
                val customerName: String,
                val mode: String,
                val receivedBy: String,
                val amount: Double,
                val isApproved: Boolean
            )

            val receiptRows = buildList {
                sortedPayments.forEach { p ->
                    add(
                        StatementReceiptRow(
                            date = p.date,
                            refNo = p.receiptNumber.ifBlank { p.id.take(10) },
                            customerName = p.customerName,
                            mode = p.paymentMethod.label.substringBefore(" ("),
                            receivedBy = p.receivedBy.ifBlank { "Staff" },
                            amount = p.amount,
                            isApproved = p.isAcknowledged
                        )
                    )
                }
                sortedDeliveries.filter { d ->
                    d.paymentCollected > 0.0 &&
                        d.invoiceId !in paymentLinkedDeliveryIds &&
                        d.id !in paymentLinkedDeliveryIds &&
                        "inv_${d.id}" !in paymentLinkedDeliveryIds
                }.forEach { d ->
                    add(
                        StatementReceiptRow(
                            date = d.date,
                            refNo = d.deliveryNumber,
                            customerName = d.customerName,
                            mode = d.paymentMethod,
                            receivedBy = d.staffName.ifBlank { "Staff" },
                            amount = d.paymentCollected,
                            isApproved = d.status == DeliveryStatus.DELIVERED
                        )
                    )
                }
            }.sortedBy { it.date }

            if (receiptRows.isEmpty()) {
                paint.color = Color.rgb(100, 116, 139)
                paint.textSize = 9f
                paint.isFakeBoldText = false
                canvas.drawText("No cash or online receipts recorded for this period.", 38f, y + 10f, paint)
                y += 24f
            } else {
                receiptRows.forEachIndexed { idx, row ->
                    ensureSpace(22f) { drawPaymentTableHeader() }
                    if (idx % 2 == 1) {
                        paint.color = Color.rgb(248, 250, 252)
                        canvas.drawRect(30f, y - 9f, 565f, y + 11f, paint)
                    }

                    paint.textSize = 7.8f
                    paint.isFakeBoldText = false
                    paint.color = Color.rgb(51, 65, 85)
                    canvas.drawText(row.date.takeLast(8), 34f, y + 2f, paint)
                    canvas.drawText(truncateText(row.refNo, 14), 90f, y + 2f, paint)

                    paint.isFakeBoldText = true
                    paint.color = Color.rgb(15, 23, 42)
                    canvas.drawText(truncateText(row.customerName, 24), 168f, y + 2f, paint)

                    paint.isFakeBoldText = false
                    paint.color = Color.rgb(51, 65, 85)
                    canvas.drawText(truncateText(row.mode, 12), 312f, y + 2f, paint)
                    canvas.drawText(truncateText(row.receivedBy, 12), 376f, y + 2f, paint)

                    paint.isFakeBoldText = true
                    paint.color = Color.rgb(22, 101, 52)
                    canvas.drawText("Rs.${row.amount.toInt()}", 448f, y + 2f, paint)

                    paint.textSize = 7.4f
                    paint.color = Color.rgb(22, 101, 52)
                    canvas.drawText("Received", 506f, y + 2f, paint)

                    paint.color = Color.rgb(226, 232, 240)
                    canvas.drawLine(30f, y + 11f, 565f, y + 11f, paint)
                    y += 19f
                }
            }

            y += 10f

            // ==========================================================
            // SECTION 3: DAILY EXPENSE DETAILS
            // ==========================================================
            fun drawExpenseTableHeader() {
                paint.color = Color.rgb(254, 226, 226)
                canvas.drawRect(30f, y, 565f, y + 20f, paint)
                paint.color = Color.rgb(153, 27, 27)
                paint.textSize = 8.2f
                paint.isFakeBoldText = true
                canvas.drawText("Date", 34f, y + 13.5f, paint)
                canvas.drawText("Category / Voucher", 88f, y + 13.5f, paint)
                canvas.drawText("Description & Payee", 204f, y + 13.5f, paint)
                canvas.drawText("Source/Mode", 374f, y + 13.5f, paint)
                canvas.drawText("Amount", 448f, y + 13.5f, paint)
                canvas.drawText("Type", 506f, y + 13.5f, paint)
                y += 24f
            }

            drawSectionTitle(
                "4. ${periodLabel.uppercase(Locale.ENGLISH)} EXPENSE DETAILS (اخراجات کی تفصیل)",
                "Total Expenses: Rs.${totalExpenses.toInt()} (${sortedExpenses.size} Vouchers)",
                Color.rgb(185, 28, 28)
            )
            drawExpenseTableHeader()

            if (sortedExpenses.isEmpty()) {
                paint.color = Color.rgb(100, 116, 139)
                paint.textSize = 9f
                paint.isFakeBoldText = false
                canvas.drawText("No expenses recorded for this period.", 38f, y + 10f, paint)
                y += 24f
            } else {
                sortedExpenses.forEachIndexed { idx, exp ->
                    ensureSpace(26f) { drawExpenseTableHeader() }
                    if (idx % 2 == 1) {
                        paint.color = Color.rgb(248, 250, 252)
                        canvas.drawRect(30f, y - 9f, 565f, y + 15f, paint)
                    }

                    val isVerified = exp.isAcknowledged && exp.isApprovedByOwner
                    val srcShort = if (exp.paymentSource == PaymentSource.PAY_FROM_INVESTMENT) "Invest" else "Sale"
                    val modeShort = if (exp.paymentMethod.isCash) "Cash" else "Online"

                    paint.textSize = 7.8f
                    paint.isFakeBoldText = false
                    paint.color = Color.rgb(51, 65, 85)
                    canvas.drawText(exp.date.takeLast(8), 34f, y + 2f, paint)

                    paint.isFakeBoldText = true
                    paint.color = Color.rgb(15, 23, 42)
                    canvas.drawText(truncateText(exp.categoryName, 19), 88f, y + 1f, paint)
                    paint.isFakeBoldText = false
                    paint.textSize = 6.8f
                    paint.color = Color.rgb(100, 116, 139)
                    canvas.drawText(truncateText("${exp.voucherNumber} • ${exp.enteredBy}", 22), 88f, y + 10.5f, paint)

                    paint.textSize = 7.6f
                    paint.color = Color.rgb(30, 41, 59)
                    canvas.drawText(truncateText(exp.description.ifBlank { exp.categoryName }, 30), 204f, y + 1f, paint)
                    paint.textSize = 6.8f
                    paint.color = Color.rgb(100, 116, 139)
                    canvas.drawText(truncateText("Payee: ${exp.payee.ifBlank { "General" }}", 28), 204f, y + 10.5f, paint)

                    paint.textSize = 7.6f
                    paint.color = Color.rgb(51, 65, 85)
                    canvas.drawText("$srcShort/$modeShort", 374f, y + 3f, paint)

                    paint.textSize = 8f
                    paint.isFakeBoldText = true
                    paint.color = Color.rgb(185, 28, 28)
                    canvas.drawText("Rs.${exp.amount.toInt()}", 448f, y + 3f, paint)

                    paint.textSize = 7.4f
                    paint.color = Color.rgb(51, 65, 85)
                    canvas.drawText(exp.expenseType.name, 506f, y + 3f, paint)

                    paint.color = Color.rgb(226, 232, 240)
                    canvas.drawLine(30f, y + 15f, 565f, y + 15f, paint)
                    y += 24f
                }
            }

            // Grand Summary Closing Box
            ensureSpace(74f)
            y += 8f
            paint.color = Color.rgb(241, 245, 249)
            val sumRect = RectF(30f, y, 565f, y + 58f)
            canvas.drawRoundRect(sumRect, 6f, 6f, paint)
            paint.color = Color.rgb(10, 37, 64)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRoundRect(sumRect, 6f, 6f, paint)
            paint.style = Paint.Style.FILL

            paint.textSize = 9.5f
            paint.isFakeBoldText = true
            paint.color = Color.rgb(10, 37, 64)
            canvas.drawText("GRAND STATEMENT SUMMARY ($periodLabel)", 42f, y + 17f, paint)

            paint.textSize = 8.6f
            paint.isFakeBoldText = false
            paint.color = Color.rgb(30, 41, 59)
            canvas.drawText(
                "Billed Sales: Rs. ${totalBilledSales.toInt()}   |   Cash Received: Rs. ${totalCashReceived.toInt()}   |   Online: Rs. ${totalOnlineReceived.toInt()}",
                42f, y + 34f, paint
            )
            paint.isFakeBoldText = true
            paint.color = if (netOperatingProfit >= 0) Color.rgb(22, 101, 52) else Color.rgb(185, 28, 28)
            canvas.drawText(
                "Total Expenses: Rs. ${totalExpenses.toInt()}   |   Net Cash After Sale Exp: Rs. ${netCashBalance.toInt()}   |   Net Profit: Rs. ${netOperatingProfit.toInt()}",
                42f, y + 49f, paint
            )

            drawPageFooter(canvas, pageNum)
            document.finishPage(page)

            val safePeriod = periodLabel.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val file = File(context.cacheDir, "Statement_${safePeriod}_${System.currentTimeMillis()}.pdf")
            FileOutputStream(file).use { fos ->
                document.writeTo(fos)
                fos.flush()
            }
            return file
        } finally {
            try { document.close() } catch (_: Throwable) {}
        }
    }

    /**
     * Generates a clean, multi-page A4 Customer Ledger Statement PDF for a custom date range.
     * Shows Deliveries, Payments, Period Totals, and Account Balance with Admin Contact Number.
     */
    fun generateCustomerLedgerPdf(
        context: Context,
        customer: Customer,
        startDate: String,
        endDate: String,
        deliveries: List<Delivery>,
        payments: List<Payment>
    ): File {
        val document = PdfDocument()
        try {
            val rangeDeliveries = deliveries
                .filter { it.customerId == customer.id && it.date in startDate..endDate }
                .sortedWith(compareBy<Delivery> { it.date }.thenBy { it.createdAt })
            val rangePayments = payments
                .filter { it.customerId == customer.id && it.date in startDate..endDate }
                .sortedWith(compareBy<Payment> { it.date }.thenBy { it.createdAt })

            val totalDelivered = rangeDeliveries.sumOf { it.totalAmount }
            val totalBottles = rangeDeliveries.sumOf { it.totalFullDelivered }
            val totalEmpties = rangeDeliveries.sumOf { it.totalEmptyCollected }
            val totalPaid = rangePayments.sumOf { it.amount }
            val periodNet = totalDelivered - totalPaid

            data class LedgerPdfEntry(
                val date: String,
                val createdAt: Long,
                val ref: String,
                val description: String,
                val debit: Double,
                val credit: Double
            )

            val entries = buildList {
                rangeDeliveries.forEach { d ->
                    val itemsDesc = d.items.joinToString(", ") { "${it.quantity}x ${it.bottleName}" }
                        .ifBlank { "${d.totalFullDelivered} Bottles" }
                    add(
                        LedgerPdfEntry(
                            date = d.date,
                            createdAt = d.createdAt,
                            ref = d.deliveryNumber,
                            description = "Delivery: $itemsDesc (Ret: ${d.totalEmptyCollected})",
                            debit = d.totalAmount,
                            credit = 0.0
                        )
                    )
                }
                rangePayments.forEach { p ->
                    add(
                        LedgerPdfEntry(
                            date = p.date,
                            createdAt = p.createdAt,
                            ref = p.receiptNumber,
                            description = "Payment Received (${p.paymentMethod.label.substringBefore(" (")})",
                            debit = 0.0,
                            credit = p.amount
                        )
                    )
                }
            }.sortedWith(compareBy<LedgerPdfEntry> { it.date }.thenBy { it.createdAt })

            val paint = Paint().apply { isAntiAlias = true }
            var pageNum = 1
            var page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum).create())
            var canvas = page.canvas
            var y = 104f

            fun drawHeader(c: Canvas, pNum: Int) {
                paint.color = Color.rgb(10, 37, 64)
                c.drawRect(0f, 0f, 595f, 84f, paint)
                drawHeaderLogoAndWatermark(context, c)

                paint.color = Color.WHITE
                paint.textSize = 17.5f
                paint.isFakeBoldText = true
                c.drawText("AAB-E-BARAAN RO DRINKING WATER PLANT", 30f, 34f, paint)

                paint.textSize = 10f
                paint.isFakeBoldText = true
                paint.color = Color.rgb(186, 230, 253)
                val rangeLabel = if (startDate == endDate) startDate else "$startDate to $endDate"
                c.drawText("Customer Ledger Statement (کسٹمر کھاتہ) • $rangeLabel", 30f, 54f, paint)

                paint.textSize = 8.6f
                paint.isFakeBoldText = false
                paint.color = Color.rgb(224, 242, 254)
                c.drawText("Page $pNum", 30f, 71f, paint)
            }

            fun drawFooter(c: Canvas, pNum: Int) {
                paint.color = Color.rgb(226, 232, 240)
                c.drawLine(30f, 814f, 565f, 814f, paint)
                paint.color = Color.rgb(100, 116, 139)
                paint.textSize = 8f
                paint.isFakeBoldText = false
                c.drawText("Aab-e-Baraan RO Drinking Water Plant", 30f, 828f, paint)
                c.drawText("Page $pNum", 528f, 828f, paint)
            }

            fun drawLedgerTableHeader() {
                paint.color = Color.rgb(224, 242, 254)
                canvas.drawRect(30f, y, 565f, y + 22f, paint)
                paint.color = Color.rgb(10, 37, 64)
                paint.textSize = 8.5f
                paint.isFakeBoldText = true
                canvas.drawText("Date", 36f, y + 14.5f, paint)
                canvas.drawText("Ref #", 94f, y + 14.5f, paint)
                canvas.drawText("Particulars (تفصیل)", 168f, y + 14.5f, paint)
                canvas.drawText("Bill (Debit)", 405f, y + 14.5f, paint)
                canvas.drawText("Paid (Credit)", 485f, y + 14.5f, paint)
                y += 28f
            }

            fun ensureSpace(needed: Float, redrawTableHeader: Boolean = false) {
                if (y + needed > 800f) {
                    drawFooter(canvas, pageNum)
                    document.finishPage(page)
                    pageNum += 1
                    page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum).create())
                    canvas = page.canvas
                    drawHeader(canvas, pageNum)
                    y = 104f
                    if (redrawTableHeader) drawLedgerTableHeader()
                }
            }

            drawHeader(canvas, pageNum)

            // Customer Card
            paint.color = Color.rgb(241, 245, 249)
            canvas.drawRoundRect(RectF(30f, y, 565f, y + 52f), 8f, 8f, paint)
            paint.color = Color.rgb(15, 23, 42)
            paint.textSize = 11.5f
            paint.isFakeBoldText = true
            canvas.drawText("Customer: ${truncateText(customer.name, 36)}", 44f, y + 20f, paint)
            paint.isFakeBoldText = false
            paint.textSize = 9.8f
            paint.color = Color.rgb(51, 65, 85)
            canvas.drawText("Area: ${customer.area.ifBlank { "-" }}   |   Code: ${customer.customerCode.ifBlank { "-" }}", 44f, y + 39f, paint)
            paint.isFakeBoldText = true
            paint.color = if (customer.currentBalance > 0) Color.rgb(185, 28, 28) else Color.rgb(22, 101, 52)
            canvas.drawText("Current Balance: Rs. ${customer.currentBalance.toInt()}", 380f, y + 22f, paint)
            paint.isFakeBoldText = false
            paint.color = Color.rgb(71, 85, 105)
            canvas.drawText("Bottles: $totalBottles Full / $totalEmpties Ret", 380f, y + 39f, paint)
            y += 66f

            // 3 Summary Cards
            fun drawSummaryPill(label: String, value: String, x: Float, valColor: Int) {
                paint.color = Color.rgb(248, 250, 252)
                val r = RectF(x, y, x + 172f, y + 44f)
                canvas.drawRoundRect(r, 6f, 6f, paint)
                paint.color = Color.rgb(203, 213, 225)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.8f
                canvas.drawRoundRect(r, 6f, 6f, paint)
                paint.style = Paint.Style.FILL

                paint.color = Color.rgb(100, 116, 139)
                paint.textSize = 8.2f
                paint.isFakeBoldText = false
                canvas.drawText(label, x + 10f, y + 15f, paint)

                paint.color = valColor
                paint.textSize = 12.5f
                paint.isFakeBoldText = true
                canvas.drawText(value, x + 10f, y + 34f, paint)
            }

            drawSummaryPill("Period Deliveries (${rangeDeliveries.size})", "Rs. ${totalDelivered.toInt()}", 30f, Color.rgb(10, 37, 64))
            drawSummaryPill("Period Payments (${rangePayments.size})", "Rs. ${totalPaid.toInt()}", 211f, Color.rgb(22, 101, 52))
            drawSummaryPill("Period Net Difference", "Rs. ${periodNet.toInt()}", 392f, if (periodNet > 0) Color.rgb(185, 28, 28) else Color.rgb(22, 101, 52))
            y += 58f

            drawLedgerTableHeader()

            if (entries.isEmpty()) {
                paint.color = Color.rgb(100, 116, 139)
                paint.textSize = 10f
                paint.isFakeBoldText = false
                canvas.drawText("No deliveries or payments recorded in this date range.", 40f, y + 8f, paint)
                y += 28f
            } else {
                entries.forEachIndexed { idx, entry ->
                    ensureSpace(22f, redrawTableHeader = true)
                    if (idx % 2 == 1) {
                        paint.color = Color.rgb(248, 250, 252)
                        canvas.drawRect(30f, y - 10f, 565f, y + 10f, paint)
                    }
                    paint.textSize = 8.2f
                    paint.isFakeBoldText = false
                    paint.color = Color.rgb(51, 65, 85)
                    canvas.drawText(entry.date, 36f, y + 2f, paint)
                    canvas.drawText(truncateText(entry.ref, 12), 94f, y + 2f, paint)

                    paint.color = Color.rgb(15, 23, 42)
                    canvas.drawText(truncateText(entry.description, 44), 168f, y + 2f, paint)

                    paint.isFakeBoldText = true
                    if (entry.debit > 0.0) {
                        paint.color = Color.rgb(10, 37, 64)
                        canvas.drawText("Rs. ${entry.debit.toInt()}", 405f, y + 2f, paint)
                    } else {
                        paint.color = Color.rgb(148, 163, 184)
                        canvas.drawText("-", 405f, y + 2f, paint)
                    }

                    if (entry.credit > 0.0) {
                        paint.color = Color.rgb(22, 101, 52)
                        canvas.drawText("Rs. ${entry.credit.toInt()}", 485f, y + 2f, paint)
                    } else {
                        paint.color = Color.rgb(148, 163, 184)
                        canvas.drawText("-", 485f, y + 2f, paint)
                    }

                    paint.color = Color.rgb(226, 232, 240)
                    canvas.drawLine(30f, y + 10f, 565f, y + 10f, paint)
                    y += 20f
                }
            }

            // Totals Footer Row
            ensureSpace(54f)
            y += 6f
            paint.color = Color.rgb(241, 245, 249)
            canvas.drawRoundRect(RectF(30f, y, 565f, y + 36f), 6f, 6f, paint)
            paint.color = Color.rgb(10, 37, 64)
            paint.textSize = 9.5f
            paint.isFakeBoldText = true
            canvas.drawText("PERIOD TOTALS:", 42f, y + 22f, paint)
            canvas.drawText("Rs. ${totalDelivered.toInt()}", 405f, y + 22f, paint)
            paint.color = Color.rgb(22, 101, 52)
            canvas.drawText("Rs. ${totalPaid.toInt()}", 485f, y + 22f, paint)

            drawFooter(canvas, pageNum)
            document.finishPage(page)

            val safeName = customer.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(24)
            val file = File(context.cacheDir, "Ledger_${safeName}_${startDate}_to_${endDate}.pdf")
            FileOutputStream(file).use { fos ->
                document.writeTo(fos)
                fos.flush()
            }
            return file
        } finally {
            try { document.close() } catch (_: Throwable) {}
        }
    }

    /**
     * Shares a PDF file using Android's FileProvider and Intent.ACTION_SEND.
     */
    fun sharePdf(context: Context, file: File, chooserTitle: String) {
        try {
            if (!file.exists() || file.length() <= 0L) {
                Toast.makeText(context, "PDF file is unavailable. Please regenerate.", Toast.LENGTH_SHORT).show()
                return
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, chooserTitle)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, chooserTitle).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (t: Throwable) {
            Toast.makeText(context, "Unable to share PDF: ${t.message ?: "Error"}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Shares a PDF file directly to WhatsApp if installed, otherwise opens Android share chooser.
     */
    fun sharePdfToWhatsApp(context: Context, file: File, caption: String = "", chooserTitle: String) {
        try {
            if (!file.exists() || file.length() <= 0L) {
                Toast.makeText(context, "PDF file is unavailable. Please regenerate.", Toast.LENGTH_SHORT).show()
                return
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val waIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                `package` = "com.whatsapp"
                putExtra(Intent.EXTRA_STREAM, uri)
                if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(waIntent)
            } catch (_: Throwable) {
                val fallback = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, chooserTitle)
                    if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(fallback, chooserTitle).apply {
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
        } catch (t: Throwable) {
            Toast.makeText(context, "Unable to share PDF to WhatsApp: ${t.message ?: "Error"}", Toast.LENGTH_SHORT).show()
        }
    }
}
