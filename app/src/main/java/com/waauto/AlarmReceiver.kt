package com.waauto

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Calendar

/**
 * 알람 수신기
 * - 예약 시간이 되면 SenderService를 시작합니다
 * - 기기 재부팅 후 알람을 재등록합니다
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
        const val ACTION_SEND = "com.waauto.SEND_MESSAGES"
        const val REQUEST_CODE = 2001

        fun scheduleNextAlarm(context: Context) {
            val settings = DataManager.getSettings(context)
            if (!settings.isEnabled) {
                cancelAlarm(context)
                return
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_SEND
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 오늘 설정 시간 계산
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, settings.sendHour)
                set(Calendar.MINUTE, settings.sendMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // 이미 지났으면 내일로
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }

            try {
                // Galaxy S22는 정확한 알람 허용 필요
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
                Log.d(TAG, "알람 설정: ${calendar.time}")
            } catch (e: SecurityException) {
                // 권한 없을 경우 setAndAllowWhileIdle로 대체
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        }

        fun cancelAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_SEND
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let { alarmManager.cancel(it) }
            Log.d(TAG, "알람 취소됨")
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                // 재부팅 후 알람 재등록
                Log.d(TAG, "기기 재부팅 - 알람 재등록")
                scheduleNextAlarm(context)
            }
            ACTION_SEND -> {
                Log.d(TAG, "알람 수신 - 메시지 전송 시작")
                // 오늘이 활성 요일인지 확인
                val settings = DataManager.getSettings(context)
                val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
                if (today in settings.activeDays) {
                    // SenderService 시작
                    val serviceIntent = Intent(context, SenderService::class.java)
                    context.startForegroundService(serviceIntent)
                } else {
                    Log.d(TAG, "오늘($today)은 전송 안 하는 날")
                }
                // 다음 날 알람 재등록
                scheduleNextAlarm(context)
            }
        }
    }
}
