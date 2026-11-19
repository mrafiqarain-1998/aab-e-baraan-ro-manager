package com.example.util

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
import androidx.core.content.FileProvider
import com.example.R
import com.example.model.BottleSize
import com.example.model.Customer
import com.example.model.Delivery
import com.example.model.Expense
import com.example.model.Invoice
import com.example.model.Payment
import com.example.model.PaymentSource
import com.example.viewmodel.DashboardStats
import java.io.File
import java.io.FileOutputStream

object PdfGenerator {

    @Volatile
    private var cachedHeaderLogo: Bitmap? = null

    @Volatile
    private var cachedWatermarkLogo: Bitmap? = null

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

    fun findRelatedPaymentsForDelivery(delivery: Delivery, payments: List<Payment>): List<Payment> {
        return payments.filter { p ->
            p.allocations.any { alloc ->
                (delivery.invoiceId.isNotBlank() && alloc.invoiceId == delivery.invoiceId) ||
                    alloc.invoiceId == delivery.id ||
                    alloc.invoiceId == "inv_${delivery.id}"
            } || (delivery.deliveryNumber.isNotBlank() && p.notes.contains(delivery.deliveryNumber))
        }.sortedWith(compareBy<Payment> { it.date }.thenBy { it.createdAt })
    }

    /**
     * Generates a clean, professional Delivery Voucher / Cash Receipt PDF with logo watermark & installment history.
     * Note: Customer Address is explicitly omitted as requested by the user.
     */
    fun generateDeliveryVoucherPdf(
        context: Context,
        delivery: Delivery,
        payments: List<Payment>,
        bottleSizes: List<BottleSize>
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 points
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true }

        // Header Background
        paint.color = Color.rgb(10, 37, 64) // WaterNavyPrimary
        canvas.drawRect(0f, 0f, 595f, 90f, paint)

        // Logo header badge & centered A4 watermark
        drawHeaderLogoAndWatermark(context, canvas)

        // Brand Title
        paint.color = Color.WHITE
        paint.textSize = 18.5f
        paint.isFakeBoldText = true
        canvas.drawText("AAB-E-BARAAN RO DRINKING WATER PLANT", 36f, 40f, paint)

        paint.textSize = 9.5f
        paint.isFakeBoldText = false
        paint.color = Color.rgb(200, 230, 255)
        canvas.drawText("Clean, Pure & Mineral Enriched Drinking Water • Delivery Slip & Receipt", 36f, 58f, paint)
        canvas.drawText("آب باراں منرل واٹر پلانٹ - آفیشل ڈیلیوری و کیش وصولی سلپ", 36f, 75f, paint)

        var y = 118f

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
        canvas.drawText("Customer: ${delivery.customerName}", 54f, y + 22f, paint)

        paint.isFakeBoldText = false
        paint.textSize = 10.5f
        paint.color = Color.rgb(51, 65, 85)
        canvas.drawText("Contact: ${delivery.customerPhone.ifEmpty { "N/A" }}", 54f, y + 42f, paint)
        canvas.drawText("Staff: ${delivery.staffName.ifEmpty { "Staff" }}", 340f, y + 22f, paint)
        canvas.drawText("Payment: ${delivery.paymentMethod}", 340f, y + 42f, paint)

        y += 72f

        // Table Header
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

        // Table Rows
        var returnableEmptiesSummary = ""
        delivery.items.forEach { item ->
            val sizeObj = bottleSizes.find { it.id == item.bottleSizeId }
            val isReturnable = sizeObj?.isReturnableBottle ?: (item.bottleSizeId in listOf("size_19l", "size_12l"))

            paint.color = Color.rgb(15, 23, 42)
            paint.textSize = when {
                item.bottleName.length > 34 -> 8.2f
                item.bottleName.length > 24 -> 9.2f
                else -> 10.5f
            }
            canvas.drawText(item.bottleName, 52f, y, paint)
            paint.textSize = 10.5f
            canvas.drawText("${item.quantity}", 255f, y, paint)
            canvas.drawText(if (isReturnable) "${item.emptyCollected}" else "Locked", 315f, y, paint)
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
            y += 4f
            paint.color = Color.rgb(234, 242, 250)
            val retRect = RectF(40f, y, 555f, y + 24f)
            canvas.drawRoundRect(retRect, 4f, 4f, paint)

            paint.color = Color.rgb(2, 132, 199)
            paint.textSize = 9.5f
            paint.isFakeBoldText = true
            canvas.drawText("Empty Bottles Returned: $returnableEmptiesSummary", 52f, y + 16f, paint)
            y += 34f
        } else {
            y += 6f
        }

        // Payment Calculation Box
        val relatedPayments = findRelatedPaymentsForDelivery(delivery, payments)
        val allocatedPaid = relatedPayments.sumOf { p ->
            val matched = p.allocations.filter {
                (delivery.invoiceId.isNotBlank() && it.invoiceId == delivery.invoiceId) ||
                    it.invoiceId == delivery.id ||
                    it.invoiceId == "inv_${delivery.id}"
            }.sumOf { it.allocatedAmount }
            if (matched > 0.0) matched else p.amount
        }
        val totalPaid = maxOf(delivery.paymentCollected, allocatedPaid).coerceAtMost(delivery.totalAmount)
        val outstandingBal = (delivery.totalAmount - totalPaid).coerceAtLeast(0.0)

        val boxHeight = if (delivery.deliveryCharges > 0.0) 118f else 84f
        paint.color = Color.rgb(248, 250, 252)
        val billBox = RectF(290f, y, 555f, y + boxHeight)
        canvas.drawRoundRect(billBox, 6f, 6f, paint)
        paint.color = Color.rgb(203, 213, 225)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(billBox, 6f, 6f, paint)
        paint.style = Paint.Style.FILL

        var boxY = y + 22f
        if (delivery.deliveryCharges > 0.0) {
            paint.textSize = 10.5f
            paint.isFakeBoldText = false
            paint.color = Color.rgb(71, 85, 105)
            canvas.drawText("Bottles Subtotal:", 306f, boxY, paint)
            canvas.drawText("Rs. ${delivery.subtotal.toInt()}", 468f, boxY, paint)
            boxY += 18f

            canvas.drawText("Delivery Charges:", 306f, boxY, paint)
            canvas.drawText("Rs. ${delivery.deliveryCharges.toInt()}", 468f, boxY, paint)
            boxY += 18f
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
            paint.isFakeBoldText = false
            paint.textSize = 10f
            paint.color = Color.rgb(71, 85, 105)
            canvas.drawText("Note: ${delivery.notes}", 40f, y, paint)
            y += 18f
        }

        // Payment Installments Log (if multiple installments received)
        if (relatedPayments.isNotEmpty()) {
            paint.isFakeBoldText = true
            paint.textSize = 10.5f
            paint.color = Color.rgb(10, 37, 64)
            canvas.drawText("Payment Installments Record (وصولی تاریخ وار تفصیل):", 40f, y, paint)
            y += 16f

            paint.isFakeBoldText = false
            paint.textSize = 9.5f
            relatedPayments.forEachIndexed { idx, p ->
                val matched = p.allocations.find {
                    (delivery.invoiceId.isNotBlank() && it.invoiceId == delivery.invoiceId) ||
                        it.invoiceId == delivery.id ||
                        it.invoiceId == "inv_${delivery.id}"
                }?.allocatedAmount
                val allocAmt = matched ?: p.amount
                paint.color = Color.rgb(71, 85, 105)
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
        paint.color = Color.rgb(226, 232, 240)
        canvas.drawLine(40f, y, 555f, y, paint)
        y += 18f

        paint.textSize = 9f
        paint.color = Color.rgb(148, 163, 184)
        paint.isFakeBoldText = false
        canvas.drawText("This is a computer generated delivery slip from Aab-e-Baraan RO Plant Management System.", 40f, y, paint)
        canvas.drawText("For plant inquiries or deliveries contact helpline: +92 300 1234567", 40f, y + 14f, paint)

        document.finishPage(page)

        val outputDir = context.cacheDir
        val safeNum = delivery.deliveryNumber.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val file = File(outputDir, "Delivery_Slip_$safeNum.pdf")
        val fos = FileOutputStream(file)
        document.writeTo(fos)
        fos.close()
        document.close()

        return file
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
        canvas.drawText("Thank you for your payment • Aab-e-Baraan RO Drinking Water Plant.", 40f, y, paint)

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
        canvas.drawText("Generated automatically by Aab-e-Baraan Enterprise Management System.", 40f, y, paint)

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
        canvas.drawText("Thank you for choosing Aab-e-Baraan RO Drinking Water Plant.", 40f, y, paint)

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
        paint.color = Color.rgb(224, 242, 254)
        val tableHeaderRect = RectF(40f, y, 555f, y + 26f)
        canvas.drawRoundRect(tableHeaderRect, 4f, 4f, paint)

        paint.color = Color.rgb(10, 37, 64)
        paint.textSize = 11f
        paint.isFakeBoldText = true
        canvas.drawText("Expense Description", 52f, y + 17f, paint)
        canvas.drawText("Amount (PKR)", 440f, y + 17f, paint)

        y += 40f

        paint.color = Color.rgb(30, 41, 59)
        paint.textSize = 11.5f
        paint.isFakeBoldText = false
        canvas.drawText(expense.description.ifBlank { expense.categoryName }, 52f, y, paint)

        paint.isFakeBoldText = true
        paint.color = Color.rgb(185, 28, 28)
        canvas.drawText("Rs. ${expense.amount.toInt()}", 440f, y, paint)

        y += 22f
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

        y += 78f
        paint.color = Color.rgb(71, 85, 105)
        paint.textSize = 10f
        paint.isFakeBoldText = false
        val statusText = if (expense.isAcknowledged) {
            "Verification Status: Verified${if (expense.acknowledgedBy.isNotBlank()) " by ${expense.acknowledgedBy}" else ""}"
        } else {
            "Verification Status: Pending Admin Verification"
        }
        canvas.drawText(statusText, 40f, y, paint)

        y += 24f
        paint.color = Color.rgb(148, 163, 184)
        paint.textSize = 9f
        canvas.drawText("Generated by Aab-e-Baraan RO Plant Management System.", 40f, y, paint)

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
        return """
            *AAB-E-BARAAN RO PLANT - EXPENSE VOUCHER*
            Voucher #: ${expense.voucherNumber}
            Date: ${expense.date}
            Type: ${expense.expenseType.name}
            Category: ${expense.categoryName}
            Description: ${expense.description}
            Amount: Rs. ${expense.amount.toInt()}
            Vendor: ${expense.payee.ifBlank { "General" }}
            Payment Source: $sourceTxt
            Payment Mode: $modeTxt
            Note: ${expense.notes.ifBlank { "-" }}
        """.trimIndent()
    }

    /**
     * Shares a PDF file using Android's FileProvider and Intent.ACTION_SEND.
     */
    fun sharePdf(context: Context, file: File, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, chooserTitle)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    /**
     * Shares a PDF file directly to WhatsApp if installed, otherwise opens Android share chooser.
     */
    fun sharePdfToWhatsApp(context: Context, file: File, caption: String = "", chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val waIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            `package` = "com.whatsapp"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(waIntent)
        } catch (_: Exception) {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, chooserTitle)
                if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(fallback, chooserTitle))
        }
    }
}
