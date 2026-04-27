package com.waauto

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class QuickSendActivity : AppCompatActivity() {

    private lateinit var etLinks: EditText
    private lateinit var etMessage: EditText
    private lateinit var btnExtract: Button
    private lateinit var btnShowNumbers: Button
    private lateinit var btnSend: Button
    private lateinit var layoutExtractResult: LinearLayout
    private lateinit var tvExtractResult: TextView

    private var extractedPhones: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quick_send)

        supportActionBar?.title = "📋 링크 붙여넣기 전송"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        etLinks             = findViewById(R.id.etLinks)
        etMessage           = findViewById(R.id.etMessage)
        btnExtract          = findViewById(R.id.btnExtract)
        btnShowNumbers      = findViewById(R.id.btnShowNumbers)
        btnSend             = findViewById(R.id.btnSend)
        layoutExtractResult = findViewById(R.id.layoutExtractResult)
        tvExtractResult     = findViewById(R.id.tvExtractResult)

        btnExtract.setOnClickListener { extractNumbers() }
        btnShowNumbers.setOnClickListener { showExtractedNumbers() }
        btnSend.setOnClickListener { startSend() }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun extractNumbers() {
        val text = etLinks.text.toString()
        if (text.isBlank()) {
            Toast.makeText(this, "먼저 링크를 붙여넣으세요", Toast.LENGTH_SHORT).show()
            return
        }

        // wa.me//+233... 또는 wa.me/+233... 에서 번호 추출
        val regex = Regex("""wa\.me/+\+?(\d{7,15})""")
        extractedPhones = regex.findAll(text)
            .map { it.groupValues[1] }
            .distinct()
            .toList()

        if (extractedPhones.isEmpty()) {
            Toast.makeText(this, "wa.me 링크를 찾을 수 없어요\n링크 형식: http://wa.me//+233...", Toast.LENGTH_LONG).show()
            return
        }

        tvExtractResult.text = "📞 ${extractedPhones.size}개 번호 추출됨"
        layoutExtractResult.visibility = android.view.View.VISIBLE
    }

    private fun showExtractedNumbers() {
        if (extractedPhones.isEmpty()) return

        val list = extractedPhones.joinToString("\n") { "  +$it" }
        AlertDialog.Builder(this)
            .setTitle("추출된 번호 (${extractedPhones.size}개)")
            .setMessage(list)
            .setPositiveButton("확인", null)
            .show()
    }

    private fun startSend() {
        if (extractedPhones.isEmpty()) {
            Toast.makeText(this, "먼저 '번호 추출' 버튼을 눌러주세요", Toast.LENGTH_SHORT).show()
            return
        }

        val message = etMessage.text.toString().trim()
        if (message.isBlank()) {
            Toast.makeText(this, "메시지를 입력해주세요", Toast.LENGTH_SHORT).show()
            return
        }

        // 추출된 번호를 임시 그룹으로 저장 후 전송
        val tempContacts = extractedPhones.mapIndexed { i, phone ->
            Contact(id = System.currentTimeMillis() + i, name = "연락처${i+1}", phone = phone)
        }

        // 임시 그룹 저장
        val tempGroup = Group(
            id = "quick_send",
            name = "빠른전송",
            contacts = tempContacts,
            message = message
        )
        DataManager.saveGroup(this, tempGroup)

        // 전송 시작
        startForegroundService(
            Intent(this, SenderService::class.java)
                .putExtra(SenderService.EXTRA_GROUP_ID, "quick_send")
        )

        Toast.makeText(this, "📤 ${tempContacts.size}명에게 전송 시작!", Toast.LENGTH_SHORT).show()
        finish()
    }
}
