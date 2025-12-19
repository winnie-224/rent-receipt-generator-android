package com.winn.rentreceiptgenerator
import android.app.DatePickerDialog
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.util.*

class MainActivity : AppCompatActivity() {
    private var stampBitmap: Bitmap? = null

    private var receiptDate: String = ""
    private var fromDate: String = ""
    private var toDate: String = ""


    private val stampPickerLauncher =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) {
            uri->
            uri?.let{
                val inputStream = contentResolver.openInputStream(it)
                stampBitmap = BitmapFactory.decodeStream(inputStream)
                stampPreview.setImageBitmap(stampBitmap)
                stampPreview.scaleX = stampScale
                stampPreview.scaleY = stampScale
                Toast.makeText(this, "Stamp Selected", Toast.LENGTH_SHORT).show()
            }
        }
    private var stampScale = 1.0f
    private  lateinit var stampPreview: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //Receipt date picker
        findViewById<Button>(R.id.btnReceiptDate).setOnClickListener {
            showDatePicker { selected ->
                receiptDate = selected
                findViewById<TextView>(R.id.tvReceiptDate).text =
                    "Date: $receiptDate"
            }
        }
        //Stamp preview
        stampPreview = findViewById(R.id.ivStampPreview)

        //Stamp picker
        findViewById<Button>(R.id.btnPickStamp).setOnClickListener {
            pickStampImage()
        }
        //Stamp resize
        findViewById<Button>(R.id.btnStampPlus).setOnClickListener {
            stampScale = (stampScale + 0.1f).coerceAtMost(1.5f)
            updateStampPreview()
        }
        findViewById<Button>(R.id.btnStampMinus).setOnClickListener {
            stampScale = (stampScale - 0.1f).coerceAtLeast(0.5f)
            updateStampPreview()
        }
        //Generate pdf
        findViewById<Button>(R.id.btnGenerate).setOnClickListener {
            generatePdf()
        }
        //From data picker
        findViewById<Button>(R.id.btnFromDate).setOnClickListener {
            showDatePicker {selected ->
                fromDate = selected
                findViewById<TextView>(R.id.tvFromDate).text = "From Date: $fromDate"

            }
        }
        //To data picker
        findViewById<Button>(R.id.btnToDate).setOnClickListener {
            showDatePicker { selected ->
                toDate = selected
                findViewById<TextView>(R.id.tvToDate).text = "To Date: $toDate"

            }
        }

        // Form reset
        findViewById<Button>(R.id.btnReset).setOnClickListener {
            resetForm()
        }

    }

    //Form setter
    private fun resetForm() {
        //clear all text fields
        findViewById<EditText>(R.id.etOwner).text.clear()
        findViewById<EditText>(R.id.etTenant).text.clear()
        findViewById<EditText>(R.id.etAddress).text.clear()
        findViewById<EditText>(R.id.etRent).text.clear()
        findViewById<EditText>(R.id.etAdvance).text.clear()
        //clear all date fields
        receiptDate = ""
        fromDate = ""
        toDate = ""
        findViewById<TextView>(R.id.tvReceiptDate).text = "Receipt Date: Not Selected"
        findViewById<TextView>(R.id.tvFromDate).text = "From Date: Not Selected"
        findViewById<TextView>(R.id.tvToDate).text = "To Date: Not Selected"
        //clear stamp image
        stampBitmap = null
        toast("Form Reset")
    }
    //Stamp Image Picker
    private fun pickStampImage() {
        stampPickerLauncher.launch("image/*")
    }

    private fun updateStampPreview() {
        stampBitmap?.let {
            stampPreview.scaleX = stampScale
            stampPreview.scaleY = stampScale
        }
    }

    private fun showDatePicker(onDateSelected: (String) -> Unit) {
        val cal = Calendar.getInstance()

        val dialog = DatePickerDialog(
            this,
            {_, y, m, d ->
                val date = String.format("%02d/%02d/%04d", d, m + 1, y)
                onDateSelected(date)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        dialog.show()
    }
    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun validateInputs(): Boolean{
        val owner = findViewById<EditText>(R.id.etOwner).text.toString().trim()
        val tenant = findViewById<EditText>(R.id.etTenant).text.toString().trim()
        val address = findViewById<EditText>(R.id.etAddress).text.toString().trim()
        val rentStr = findViewById<EditText>(R.id.etRent).text.toString().trim()
        val advanceStr = findViewById<EditText>(R.id.etAdvance).text.toString().trim()

        if (owner.isEmpty()){
            toast("Owner name is required!")
            return false
        }
        if (tenant.isEmpty()){
            toast("Tenant name is required!")
            return false
        }
        if (address.isEmpty()){
            toast("Address is required!")
            return false
        }
        //Date validations
        if (receiptDate.isEmpty()){
            toast("Receipt date is required!")
            return false
        }
        if (fromDate.isEmpty()){
            toast("From date is required!")
            return false
        }
        if (toDate.isEmpty()){
            toast("To date is required!")
            return false
        }
        val rent = rentStr.toIntOrNull()
        if (rent==null || rent <= 0){
            toast("Rent is required!")
            return false
        }
        //Date logic: To>=From
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val fromDateObj = sdf.parse(fromDate)
        val toDateObj = sdf.parse(toDate)
        if (fromDateObj!=null && toDateObj!=null && fromDateObj.after(toDateObj)){
            toast("To date cannot be before from date!")
            return false
        }

        //Validating amount-to-words limit
        val totalAmount = rent - (advanceStr.toIntOrNull() ?: 0)
        if (totalAmount >= 1000000) {
            toast("Total amount cannot be more than 1,00,00,000")
            return false
        }
        if (totalAmount < 0) {
            toast("Advance cannot be more than rent")
            return false
        }
        return true
    }
    //PDF Generation
    private fun generatePdf() {
        if(!validateInputs()) return
        //Read Form Data
        val owner = findViewById<EditText>(R.id.etOwner).text.toString()
        val tenant = findViewById<EditText>(R.id.etTenant).text.toString()
        val address = findViewById<EditText>(R.id.etAddress).text.toString()
        val rentStr = findViewById<EditText>(R.id.etRent).text.toString()
        val advanceStr = findViewById<EditText>(R.id.etAdvance).text.toString()

        val rent = rentStr.toIntOrNull() ?: 0
        val advance = advanceStr.toIntOrNull() ?: 0
        val total = rent - advance
        val balance = rent - total


        //Create PDF
        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(600, 440, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint()
        paint.textSize = 12f

        val underlinePaint = Paint(paint)
        underlinePaint.isUnderlineText = true

        //Outer border
        paint.style = Paint.Style.STROKE
        canvas.drawRect(20f, 20f, 580f, 420f, paint)

        //Title
        underlinePaint.style = Paint.Style.FILL
        underlinePaint.textSize = 18f
        underlinePaint.isFakeBoldText = true
        underlinePaint.typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.SANS_SERIF,
            android.graphics.Typeface.BOLD
        )
        canvas.drawText("RENT RECEIPT", 270f, 60f, underlinePaint)

        paint.typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.SANS_SERIF,
            android.graphics.Typeface.NORMAL
        )
        paint.style = Paint.Style.FILL
        paint.textSize = 12f
        paint.isFakeBoldText = false

        // Owner and Date
        if(receiptDate.isEmpty()) {
            Toast.makeText(this, "Please select receipt date", Toast.LENGTH_SHORT).show()
            return
        }
        canvas.drawText("Date: ", 420f, 90f, paint)
        canvas.drawText(receiptDate, 450f, 90f, paint)
        canvas.drawText("Owner: ", 40f, 120f, paint)
        drawTextWithUnderline(canvas, owner, 85f, 120f, paint)

        //Stamp Box
        paint.style = Paint.Style.STROKE
        val left = 420f
        val top = 120f
        val right = 500f
        val bottom = 200f
        val boxWidth = right - left
        val boxHeight = bottom - top
        val scaledWidth = boxWidth * stampScale
        val scaledHeight = boxHeight * stampScale
        val centerX = (left + right)/ 2
        val centerY = (top + bottom) / 2
        val scaledRect = Rect(
            (centerX-scaledWidth/2).toInt(),
            (centerY-scaledHeight/2).toInt(),
            (centerX+scaledWidth/2).toInt(),
            (centerY+scaledHeight/2).toInt()
        )
        stampBitmap?.let { bitmap ->
            canvas.drawBitmap(bitmap, null, scaledRect, null)
        }




        paint.style = Paint.Style.FILL

        //Body Text
        canvas.drawText("Received with thanks from:", 40f, 150f, paint)
        drawTextWithUnderline(canvas, "$tenant (Tenant)", 190f, 150f, paint)

        canvas.drawText("Address:", 40f, 180f, paint)
        drawTextWithUnderline(canvas, address, 90f, 180f, paint)


        canvas.drawText("The Sum of Rupees:", 40f, 210f, paint)
        drawTextWithUnderline(
            canvas,
            "${numberToWords(total)} only",
            155f,
            210f,
            paint
        )


        canvas.drawText("From:", 40f, 240f, paint)
        drawTextWithUnderline(canvas, fromDate, 80f, 240f, paint)

        canvas.drawText("To:", 180f, 240f, paint)
        drawTextWithUnderline(canvas, toDate, 210f, 240f, paint)

        // Amounts

        val yAmount = 270f

        // Total
        canvas.drawText("Total Rs.", 40f, yAmount, paint)
        drawTextWithUnderline(canvas, total.toString(), 100f, yAmount, paint)

        // Advance
        canvas.drawText("Advance Rs.", 200f, yAmount, paint)
        drawTextWithUnderline(canvas, advance.toString(), 285f, yAmount, paint)

        // Balance
        canvas.drawText("Balance Rs.", 40f, 300f, paint)
        drawTextWithUnderline(canvas, balance.toString(), 120f, 300f, paint)

        //Total box
        paint.style = Paint.Style.STROKE
        canvas.drawRect(50f,350f,150f, 380f,paint)
        paint.style = Paint.Style.FILL
        canvas.drawText("Rs. $total /-", 60f, 370f, paint)

        //Signature Box
        paint.style = Paint.Style.STROKE
        canvas.drawRect(390f, 270f, 560f, 340f, paint)
        paint.style = Paint.Style.FILL
        canvas.drawText("Signature of Property Owner", 400f, 355f, paint)

        pdf.finishPage(page)
        val fileName = "Rent_Receipt_${System.currentTimeMillis()}.pdf"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download")

        }
        val resolver = contentResolver
        val uri = resolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            contentValues
        )
        if(uri != null) {
            resolver.openOutputStream(uri)?.use { outputStream ->
                pdf.writeTo(outputStream)
            }
            Toast.makeText(
                this,
                "PDF saved to Download folder",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(
                this,
                "Failed to save PDF",
                Toast.LENGTH_LONG
            ).show()
        }


        pdf.close()

    }

    //Display utility
    private fun drawTextWithUnderline(
        canvas: android.graphics.Canvas,
        text: String,
        x: Float,
        y: Float,
        paint: Paint,
        linePadding: Float = 5f
    ) {
        // draw text
        canvas.drawText(text, x, y, paint)

        // measure text width
        val textWidth = paint.measureText(text)

        // draw underline
        canvas.drawLine(
            x,
            y + linePadding,
            x + textWidth,
            y + linePadding,
            paint
        )
    }


    //------number to words------------------
    private fun numberToWords(number: Int): String{
        if(number == 0) return "Zero"
        val units = arrayOf(
            "", "One", "Two", "Three", "Four", "Five",
            "Six", "Seven", "Eight", "Nine", "Ten",
            "Eleven", "Twelve", "Thirteen", "Fourteen",
            "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"
        )
        val tens = arrayOf(
            "", "", "Twenty", "Thirty", "Forty", "Fifty",
            "Sixty", "Seventy", "Eighty", "Ninety"
        )
        fun convert(n:Int):String =
            when{
                n<20 -> units[n]
                n<100 -> tens[n/10]+" "+units[n%10]
                n<1000 -> units[n/100]+" Hundred "+convert(n%100)
                else -> convert(n/1000)+" Thousand "+convert(n%1000)
            }
        return convert(number).trim()

    }
}

