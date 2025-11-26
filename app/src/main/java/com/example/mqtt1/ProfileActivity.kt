package com.example.mqtt1

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : AppCompatActivity() {

    private lateinit var edtNombre: TextInputEditText
    private lateinit var edtDireccion: TextInputEditText
    private lateinit var edtReferencia: TextInputEditText
    private lateinit var edtTelefono: TextInputEditText
    private lateinit var btnGuardar: Button
    
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // Configurar Toolbar con botón Atrás
        val toolbar: Toolbar = findViewById(R.id.toolbarProfile)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        edtNombre = findViewById(R.id.edtNombre)
        edtDireccion = findViewById(R.id.edtDireccion)
        edtReferencia = findViewById(R.id.edtReferencia)
        edtTelefono = findViewById(R.id.edtTelefono)
        btnGuardar = findViewById(R.id.btnGuardarPerfil)

        cargarDatosUsuario()

        btnGuardar.setOnClickListener {
            guardarCambios()
        }
    }
    
    // Manejar click en botón Atrás de la toolbar
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun cargarDatosUsuario() {
        val userEmail = auth.currentUser?.email ?: return

        db.collection("usuarios")
            .whereEqualTo("email", userEmail)
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    edtNombre.setText(document.getString("nombre") ?: "")
                    edtDireccion.setText(document.getString("direccion") ?: "")
                    edtReferencia.setText(document.getString("referencia_entrega") ?: "")
                    edtTelefono.setText(document.getString("telefono") ?: "")
                    
                    // Verificar rol para personalizar la UI si es necesario
                    // (El distribuidor usa este mismo perfil pero rellena "Nombre Empresa")
                    val rol = document.getString("rol")
                    if (rol == "distribuidor") {
                        supportActionBar?.title = "Perfil Distribuidor"
                        // Podríamos cambiar hints aquí si quisiéramos
                    }
                }
            }
    }

    private fun guardarCambios() {
        val userEmail = auth.currentUser?.email ?: return
        
        val datosActualizados = mapOf(
            "nombre" to edtNombre.text.toString(),
            "direccion" to edtDireccion.text.toString(),
            "referencia_entrega" to edtReferencia.text.toString(),
            "telefono" to edtTelefono.text.toString()
        )

        db.collection("usuarios")
            .whereEqualTo("email", userEmail)
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    db.collection("usuarios").document(document.id)
                        .update(datosActualizados)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Perfil actualizado correctamente", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                }
            }
    }
}