package com.example.mqtt1

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AdminUsersActivity : AppCompatActivity() {

    private lateinit var recyclerUsers: RecyclerView
    private lateinit var btnAddUser: FloatingActionButton
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_users)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        recyclerUsers = findViewById(R.id.recyclerAdminUsers)
        btnAddUser = findViewById(R.id.fabAddUser)
        recyclerUsers.layoutManager = LinearLayoutManager(this)

        cargarUsuarios()

        btnAddUser.setOnClickListener {
            mostrarDialogoUsuario(null) // null = Crear nuevo
        }
        
        findViewById<View>(R.id.btnBackAdmin).setOnClickListener { finish() }
    }

    private fun cargarUsuarios() {
        db.collection("usuarios")
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener

                val lista = ArrayList<AdminUser>()
                for (doc in snapshots) {
                    val user = AdminUser(
                        id = doc.id,
                        email = doc.getString("email") ?: "",
                        rol = doc.getString("rol") ?: "user"
                    )
                    lista.add(user)
                }
                recyclerUsers.adapter = AdminUsersAdapter(lista, 
                    onEdit = { user -> mostrarDialogoUsuario(user) },
                    onDelete = { user -> eliminarUsuario(user) }
                )
            }
    }

    private fun mostrarDialogoUsuario(user: AdminUser?) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_admin_user)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val edtEmail = dialog.findViewById<EditText>(R.id.edtDialogEmail)
        val spinnerRol = dialog.findViewById<AutoCompleteTextView>(R.id.spinnerDialogRol)
        val btnSave = dialog.findViewById<Button>(R.id.btnDialogSave)
        val txtTitle = dialog.findViewById<TextView>(R.id.txtDialogTitle)

        // Configurar Roles
        val roles = listOf("user", "admin", "distribuidor")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, roles)
        spinnerRol.setAdapter(adapter)

        if (user != null) {
            txtTitle.text = "Editar Usuario"
            edtEmail.setText(user.email)
            edtEmail.isEnabled = false // No se puede cambiar el email (id) fácilmente en Firebase Auth desde aquí
            spinnerRol.setText(user.rol, false)
        } else {
            txtTitle.text = "Nuevo Usuario"
        }

        btnSave.setOnClickListener {
            val email = edtEmail.text.toString()
            val rol = spinnerRol.text.toString()

            if (email.isNotEmpty() && rol.isNotEmpty()) {
                guardarUsuario(user?.id, email, rol)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun guardarUsuario(docId: String?, email: String, rol: String) {
        val data = mapOf(
            "email" to email,
            "rol" to rol
        )

        if (docId != null) {
            // Editar Rol
            db.collection("usuarios").document(docId).update(data)
                .addOnSuccessListener { Toast.makeText(this, "Usuario actualizado", Toast.LENGTH_SHORT).show() }
        } else {
            // Crear Nuevo (Nota: Esto solo crea el registro en DB, no en Auth. 
            // En una app real, se debería usar Cloud Functions o crear la cuenta Auth primero)
            db.collection("usuarios").add(data)
                .addOnSuccessListener { Toast.makeText(this, "Usuario registrado en DB", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun eliminarUsuario(user: AdminUser) {
        // Confirmación simple
        db.collection("usuarios").document(user.id).delete()
            .addOnSuccessListener { Toast.makeText(this, "Usuario eliminado", Toast.LENGTH_SHORT).show() }
    }
}

// Modelo de Datos Simple
data class AdminUser(val id: String, val email: String, val rol: String)