package com.waauto

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ContactsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ContactAdapter
    private lateinit var tvCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "연락처 관리"

        recyclerView = findViewById(R.id.recyclerContacts)
        tvCount = findViewById(R.id.tvContactCount)

        adapter = ContactAdapter(
            contacts = DataManager.getContacts(this).toMutableList(),
            onDelete = { contact ->
                DataManager.removeContact(this, contact.id)
                adapter.contacts.remove(contact)
                adapter.notifyDataSetChanged()
                updateCount()
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<Button>(R.id.btnAddContact).setOnClickListener {
            showAddContactDialog()
        }

        updateCount()
    }

    private fun showAddContactDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_contact, null)
        val etName = view.findViewById<EditText>(R.id.etName)
        val etPhone = view.findViewById<EditText>(R.id.etPhone)
        val tvHint = view.findViewById<TextView>(R.id.tvPhoneHint)
        tvHint.text = "국가코드 포함 숫자만 입력\n예) 한국: 821012345678"

        AlertDialog.Builder(this)
            .setTitle("연락처 추가")
            .setView(view)
            .setPositiveButton("추가") { _, _ ->
                val name = etName.text.toString().trim()
                val phone = etPhone.text.toString().trim()
                    .replace("+", "").replace("-", "").replace(" ", "")

                if (name.isEmpty() || phone.isEmpty()) {
                    Toast.makeText(this, "이름과 번호를 입력해주세요", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (adapter.contacts.size >= 100) {
                    Toast.makeText(this, "최대 100명까지 추가 가능합니다", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val contact = Contact(name = name, phone = phone)
                DataManager.addContact(this, contact)
                adapter.contacts.add(contact)
                adapter.notifyDataSetChanged()
                updateCount()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun updateCount() {
        tvCount.text = "총 ${adapter.contacts.size}명 / 최대 100명"
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

class ContactAdapter(
    val contacts: MutableList<Contact>,
    private val onDelete: (Contact) -> Unit
) : RecyclerView.Adapter<ContactAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvPhone: TextView = view.findViewById(R.id.tvPhone)
        val btnDelete: Button = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.tvName.text = contact.name
        holder.tvPhone.text = "+${contact.phone}"
        holder.btnDelete.setOnClickListener { onDelete(contact) }
    }

    override fun getItemCount() = contacts.size
}
