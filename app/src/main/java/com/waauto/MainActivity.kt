package com.waauto

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var btnAccessibility: Button
    private lateinit var btnOverlay: Button
    private lateinit var btnSync: Button
    private lateinit var btnSheetUrl: Button
    private lateinit var groupContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnAccessibility = findViewById(R.id.btnAccessibility)
        btnOverlay       = findViewById(R.id.btnOverlay)
        btnSync          = findViewById(R.id.btnSync)
        btnSheetUrl      = findViewById(R.id.btnSheetUrl)
        groupContainer   = findViewById(R.id.groupContainer)

        // 빠른 전송 버튼
        findViewById<Button>(R.id.btnQuickSend).setOnClickListener {
            startActivity(Intent(this, QuickSendActivity::class.java))
        }

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "'WA 자동전송' 앱을 찾아 켜주세요", Toast.LENGTH_LONG).show()
        }

        btnOverlay.setOnClickListener {
            startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ))
        }

        // 시트 URL 설정 다이얼로그
        btnSheetUrl.setOnClickListener {
            showSheetUrlDialog()
        }

        // 구글 시트 동기화
        btnSync.setOnClickListener {
            val url = DataManager.getSheetUrl(this)
            if (url.isBlank()) {
                Toast.makeText(this, "먼저 🔗 시트 URL을 설정해주세요!", Toast.LENGTH_LONG).show()
                showSheetUrlDialog()
                return@setOnClickListener
            }
            startSync()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionButtons()
        updateSheetButtons()
        buildGroupCards()
    }

    private fun startSync() {
        btnSync.isEnabled = false
        btnSync.text = "⏳ 동기화 중..."

        lifecycleScope.launch {
            val result = DataManager.syncFromSheet(this@MainActivity)
            btnSync.isEnabled = true
            btnSync.text = "🔄 구글 시트 동기화"

            if (result.success) {
                Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_LONG).show()
                buildGroupCards()  // 카드 새로고침
            } else {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("동기화 실패")
                    .setMessage(result.message)
                    .setPositiveButton("확인", null)
                    .show()
            }
        }
    }

    private fun showSheetUrlDialog() {
    val et = EditText(this)
    et.hint = "https://script.google.com/macros/s/..."
    et.setText(DataManager.getSheetUrl(this))
    et.setSingleLine(false)
    et.minLines = 2
    val pad = (16 * resources.displayMetrics.density).toInt()
    et.setPadding(pad, pad, pad, pad)

    AlertDialog.Builder(this)
        .setTitle("🔗 구글 시트 URL 설정")
        .setMessage("Apps Script 웹앱 URL을 입력하세요")
        .setView(et)
        .setPositiveButton("저장") { _, _ ->
            val url = et.text.toString().trim()
            DataManager.saveSheetUrl(this, url)
            updateSheetButtons()
            Toast.makeText(this, "URL 저장됨!", Toast.LENGTH_SHORT).show()
        }
        .setNegativeButton("취소", null)
        .show()
}

    private fun updatePermissionButtons() {
        val accessOk = isAccessibilityEnabled()
        val overlayOk = Settings.canDrawOverlays(this)

        btnAccessibility.text = if (accessOk) "✅ 접근성 권한 허용됨" else "⚠️ 접근성 권한 허용하기 (필수)"
        btnAccessibility.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (accessOk) Color.parseColor("#388E3C") else Color.parseColor("#FF5722")
        )
        btnOverlay.text = if (overlayOk) "✅ 다른 앱 위에 표시 허용됨" else "⚠️ 다른 앱 위에 표시 허용하기"
        btnOverlay.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (overlayOk) Color.parseColor("#388E3C") else Color.parseColor("#E91E63")
        )
    }

    private fun updateSheetButtons() {
        val hasUrl = DataManager.getSheetUrl(this).isNotBlank()
        btnSheetUrl.text = if (hasUrl) "🔗 시트 URL 설정됨 (변경)" else "🔗 구글 시트 URL 설정"
        btnSheetUrl.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (hasUrl) Color.parseColor("#37474F") else Color.parseColor("#607D8B")
        )
    }

    private fun buildGroupCards() {
        groupContainer.removeAllViews()
        val groups = DataManager.getGroups(this)
        val colors = DataManager.PREDEFINED.map { it.third }

        groups.forEachIndexed { index, group ->
            val card = LayoutInflater.from(this).inflate(R.layout.item_group, groupContainer, false)

            val colorBar = card.findViewById<android.view.View>(R.id.groupColorBar)
            val tvName   = card.findViewById<TextView>(R.id.tvGroupName)
            val tvCount  = card.findViewById<TextView>(R.id.tvContactCount)
            val tvMsg    = card.findViewById<TextView>(R.id.tvMessagePreview)
            val btnEdit  = card.findViewById<Button>(R.id.btnEdit)
            val btnSend  = card.findViewById<Button>(R.id.btnSend)

            val colorHex = colors.getOrElse(index) { "#2196F3" }
            colorBar.setBackgroundColor(Color.parseColor(colorHex))
            btnSend.backgroundTintList = android.content.res.ColorStateList.valueOf(
                Color.parseColor(colorHex)
            )

            tvName.text  = group.name
            tvCount.text = "${group.contacts.size}명"
            tvMsg.text   = if (group.message.isBlank()) "메시지 없음 - 편집에서 입력하세요"
                           else group.message

            btnEdit.setOnClickListener {
                startActivity(Intent(this, GroupDetailActivity::class.java)
                    .putExtra("group_id", group.id))
            }

            btnSend.setOnClickListener {
                if (!isAccessibilityEnabled()) {
                    Toast.makeText(this, "먼저 접근성 권한을 허용해주세요!", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "먼저 '다른 앱 위에 표시' 권한을 허용해주세요!", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (group.contacts.isEmpty()) {
                    Toast.makeText(this, "연락처가 없어요!\n시트 동기화 또는 편집에서 추가해주세요", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (group.message.isBlank()) {
                    Toast.makeText(this, "메시지가 없어요!\n편집에서 메시지를 입력해주세요", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                startForegroundService(Intent(this, SenderService::class.java)
                    .putExtra(SenderService.EXTRA_GROUP_ID, group.id))
            }

            groupContainer.addView(card)
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "$packageName/${WAAccessibilityService::class.java.canonicalName}"
        return try {
            val enabled = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
            if (enabled != 1) return false
            val services = Settings.Secure.getString(contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(services)
            splitter.any { it.equals(service, ignoreCase = true) }
        } catch (e: Exception) { false }
    }
}
