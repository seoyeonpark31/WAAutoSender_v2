package com.waauto

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

class MessagesActivity : AppCompatActivity() {

    private val dayOrder = listOf(
        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
        Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
    )

    private val messageFields = mutableMapOf<Int, EditText>()
    private val dayToggles = mutableMapOf<Int, CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_messages)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "요일별 메시지 설정"

        val settings = DataManager.getSettings(this)
        val container = findViewById<LinearLayout>(R.id.messageContainer)

        // 요일별 메시지 입력칸 동적 생성
        for (day in dayOrder) {
            val dayName = DataManager.getDayName(day)
            val isActive = day in settings.activeDays
            val currentMessage = settings.dayMessages[day] ?: ""

            // 요일 헤더 (체크박스 + 요일명)
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 24, 0, 8)
            }

            val checkbox = CheckBox(this).apply {
                text = dayName
                isChecked = isActive
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            dayToggles[day] = checkbox
            header.addView(checkbox)
            container.addView(header)

            // 메시지 입력창
            val editText = EditText(this).apply {
                hint = "$dayName 메시지를 입력하세요"
                setText(currentMessage)
                minLines = 2
                maxLines = 5
                isEnabled = isActive
                alpha = if (isActive) 1f else 0.4f
                setPadding(16, 12, 16, 12)
                setBackgroundResource(android.R.drawable.edit_text)
            }
            messageFields[day] = editText
            container.addView(editText)

            // 체크박스 상태에 따라 입력창 활성/비활성
            checkbox.setOnCheckedChangeListener { _, checked ->
                editText.isEnabled = checked
                editText.alpha = if (checked) 1f else 0.4f
            }
        }

        findViewById<Button>(R.id.btnSaveMessages).setOnClickListener {
            saveMessages()
        }
    }

    private fun saveMessages() {
        val currentSettings = DataManager.getSettings(this)
        val newMessages = mutableMapOf<Int, String>()
        val newActiveDays = mutableSetOf<Int>()

        for (day in dayOrder) {
            val message = messageFields[day]?.text?.toString()?.trim() ?: ""
            val isActive = dayToggles[day]?.isChecked == true

            newMessages[day] = message
            if (isActive) newActiveDays.add(day)
        }

        val updated = currentSettings.copy(
            dayMessages = newMessages,
            activeDays = newActiveDays
        )
        DataManager.saveSettings(this, updated)

        // 알람 재설정 (요일 변경사항 반영)
        if (updated.isEnabled) {
            AlarmReceiver.scheduleNextAlarm(this)
        }

        Toast.makeText(this, "저장되었습니다!", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
