package com.example.mqtt1

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : AppCompatActivity() {

    private lateinit var edtNombre: EditText
    private lateinit var edtTelefono: EditText
    private lateinit var edtDireccion: EditText
    private lateinit var edtEmpresa: EditText
    
    private lateinit var containerAddress: LinearLayout
    private lateinit var containerCompany: LinearLayout
    private lateinit var btnGuardar: Button
    
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    
    private var miRolActual = "user"

    // Lanzador para el selector de mapa
    private val mapPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val address = result.data?.getStringExtra("address")
            if (!address.isNullOrEmpty()) {
                edtDireccion.setText(address)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Vistas
        edtNombre = findViewById(R.id.edtProfileName)
        edtTelefono = findViewById(R.id.edtProfilePhone)
        edtDireccion = findViewById(R.id.edtProfileAddress)
        edtEmpresa = findViewById(R.id.edtProfileCompany)
        
        containerAddress = findViewById(R.id.containerAddress)
        containerCompany = findViewById(R.id.containerCompany)
        btnGuardar = findViewById(R.id.btnSaveProfile)

        // Configurar campo de dirección para abrir el mapa
        // Hacemos que no sea focusable para que el teclado no salte, sino el mapa
        edtDireccion.isFocusable = false
        edtDireccion.setOnClickListener {
            val intent = Intent(this, MapPickerActivity::class.java)
            mapPickerLauncher.launch(intent)
        }

        cargarDatosUsuario()

        btnGuardar.setOnClickListener {
            guardarCambios()
        }
    }
    
    private fun cargarDatosUsuario() {
        val userEmail = auth.currentUser?.email ?: return

        db.collection("usuarios")
            .whereEqualTo("email", userEmail)
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    // Datos Comunes
                    edtNombre.setText(document.getString("nombre") ?: "")
                    edtTelefono.setText(document.getString("telefono") ?: "")
                    
                    // Rol
                    miRolActual = document.getString("rol") ?: "user"
                    
                    // Ajustar Visibilidad según Rol
                    when (miRolActual) {
                        "admin" -> {
                            // Admin NO ve dirección ni empresa
                            containerAddress.visibility = View.GONE
                            containerCompany.visibility = View.GONE
                        }
                        "distribuidor" -> {
                            // Distribuidor ve TODO (Dirección y Empresa)
                            containerAddress.visibility = View.VISIBLE
                            containerCompany.visibility = View.VISIBLE
                            
                            edtDireccion.setText(document.getString("direccion") ?: "")
                            edtEmpresa.setText(document.getString("empresa_nombre") ?: "")
                        }
                        else -> { // user
                            // Usuario ve Dirección pero NO empresa
                            containerAddress.visibility = View.VISIBLE
                            containerCompany.visibility = View.GONE
                            
                            edtDireccion.setText(document.getString("direccion") ?: "")
                        }
                    }
                }
            }
    }

    private fun guardarCambios() {
        val userEmail = auth.currentUser?.email ?: return
        
        // Datos Base
        val datosActualizados = mutableMapOf<String, Any>(
            "nombre" to edtNombre.text.toString(),
            "telefono" to edtTelefono.text.toString()
        )
        
        // Datos condicionales
        if (containerAddress.visibility == View.VISIBLE) {
            datosActualizados["direccion"] = edtDireccion.text.toString()
        }
        
        if (containerCompany.visibility == View.VISIBLE) {
            datosActualizados["empresa_nombre"] = edtEmpresa.text.toString()
        }

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