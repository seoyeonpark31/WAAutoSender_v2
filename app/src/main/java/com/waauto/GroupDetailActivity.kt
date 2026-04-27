package com.waauto

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class GroupDetailActivity : AppCompatActivity() {

    private lateinit var etMessage: EditText
    private lateinit var tvContactHeader: TextView
    private lateinit var btnAddContact: Button
    private lateinit var btnSave: Button
    private lateinit var rvContacts: RecyclerView

    private lateinit var groupId: String
    private lateinit var group: Group
    private val contacts = mutableListOf<Contact>()
    private lateinit var adapter: ContactAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_detail)

        groupId = intent.getStringExtra("group_id") ?: run { finish(); return }
        group = DataManager.getGroup(this, groupId) ?: run { finish(); return }

        supportActionBar?.title = group.name
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        etMessage = findViewById(R.id.etMessage)
        tvContactHeader = findViewById(R.id.tvContactHeader)
        btnAddContact = findViewById(R.id.btnAddContact)
        btnSave = findViewById(R.id.btnSave)
        rvContacts = findViewById(R.id.rvContacts)

        contacts.addAll(group.contacts)
        etMessage.setText(group.message)

        adapter = ContactAdapter(contacts) { contact ->
            showDeleteDialog(contact)
        }
        rvContacts.layoutManager = LinearLayoutManager(this)
        rvContacts.adapter = adapter

        updateHeader()

        btnAddContact.setOnClickListener { showAddContactDialog() }

        btnSave.setOnClickListener {
            val updated = group.copy(
                contacts = contacts.toList(),
                message = etMessage.text.toString().trim()
            )
            DataManager.saveGroup(this, updated)
            Toast.makeText(this, "✅ 저장됐어요!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun updateHeader() {
        tvContactHeader.text = "👥 연락처 (${contacts.size}명)"
    }

    private fun showAddContactDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_contact, null)
        val etName = view.findViewById<EditText>(R.id.etName)
        val etPhone = view.findViewById<EditText>(R.id.etPhone)

        // positive button을 null로 해서 자동 닫힘 막기 (유효성 검사 후 직접 닫음)
        val dialog = AlertDialog.Builder(this)
            .setTitle("연락처 추가")
            .setView(view)
            .setPositiveButton("추가", null)
            .setNegativeButton("취소", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = etName.text.toString().trim()
                val phone = etPhone.text.toString().trim()
                    .replace("-", "").replace(" ", "")
                if (name.isBlank()) {
                    etName.error = "이름을 입력하세요"
                    return@setOnClickListener
                }
                if (phone.isBlank()) {
                    etPhone.error = "전화번호를 입력하세요"
                    return@setOnClickListener
                }
                contacts.add(Contact(id = System.currentTimeMillis(), name = name, phone = phone))
                adapter.notifyItemInserted(contacts.size - 1)
                updateHeader()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showDeleteDialog(contact: Contact) {
        AlertDialog.Builder(this)
            .setTitle("삭제")
            .setMessage("${contact.name}을 삭제할까요?")
            .setPositiveButton("삭제") { _, _ ->
                val idx = contacts.indexOf(contact)
                if (idx >= 0) {
                    contacts.removeAt(idx)
                    adapter.notifyItemRemoved(idx)
                    updateHeader()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // 연락처 RecyclerView 어댑터
    inner class ContactAdapter(
        private val list: MutableList<Contact>,
        private val onDelete: (Contact) -> Unit
    ) : RecyclerView.Adapter<ContactAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvName)
            val tvPhone: TextView = view.findViewById(R.id.tvPhone)
            val btnDelete: Button = view.findViewById(R.id.btnDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val c = list[position]
            holder.tvName.text = c.name
            holder.tvPhone.text = c.phone
            holder.btnDelete.setOnClickListener { onDelete(c) }
        }

        override fun getItemCount() = list.size
    }
}
