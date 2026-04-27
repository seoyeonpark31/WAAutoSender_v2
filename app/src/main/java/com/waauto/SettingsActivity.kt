package com.waauto

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "전송 시간 설정"

        val settings = DataManager.getSettings(this)
        val timePicker = findViewById<TimePicker>(R.id.timePicker)
        val etDelay = findViewById<EditText>(R.id.etDelay)
        val btnSave = findViewById<Button>(R.id.btnSaveTime)

        timePicker.setIs24HourView(true)
        timePicker.hour = settings.sendHour
        timePicker.minute = settings.sendMinute

        val delaySeconds = settings.delayBetweenMessages / 1000
        etDelay.setText(delaySeconds.toString())

        btnSave.setOnClickListener {
            val newHour = timePicker.hour
            val newMinute = timePicker.minute
            val delayMs = ((etDelay.text.toString().toLongOrNull() ?: 4L) * 1000L)
                .coerceIn(2000L, 30000L)  // 최소 2초, 최대 30초

            val updated = settings.copy(
                sendHour = newHour,
                sendMinute = newMinute,
                delayBetweenMessages = delayMs
            )
            DataManager.saveSettings(this, updated)

            // 알람 재설정
            if (updated.isEnabled) {
                AlarmReceiver.scheduleNextAlarm(this)
            }

            val hour = newHour.toString().padStart(2, '0')
            val minute = newMinute.toString().padStart(2, '0')
            Toast.makeText(this, "매일 $hour:$minute 로 설정됨!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
