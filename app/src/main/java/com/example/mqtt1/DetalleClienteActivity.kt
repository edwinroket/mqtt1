package com.example.mqtt1

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.firebase.firestore.FirebaseFirestore

class DetalleClienteActivity : AppCompatActivity() {

    private lateinit var txtNombre: TextView
    private lateinit var txtDireccion: TextView
    private lateinit var txtTelefono: TextView
    private lateinit var txtBalon: TextView
    private lateinit var txtEmpresa: TextView
    private lateinit var txtFecha: TextView
    private lateinit var txtPorcentaje: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var db: FirebaseFirestore
    private var soyAdmin: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalle_cliente)

        // Configurar Toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbarDetalle)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        db = FirebaseFirestore.getInstance()

        // Recuperar datos del Intent
        val emailCliente = intent.getStringExtra("email_cliente")
        soyAdmin = intent.getBooleanExtra("es_admin", false)

        txtNombre = findViewById(R.id.txtDetalleNombre)
        txtDireccion = findViewById(R.id.txtDetalleDireccion)
        txtTelefono = findViewById(R.id.txtDetalleTelefono)
        txtBalon = findViewById(R.id.txtDetalleBalon)
        txtEmpresa = findViewById(R.id.txtDetalleEmpresa)
        txtFecha = findViewById(R.id.txtDetalleFecha)
        txtPorcentaje = findViewById(R.id.txtDetallePorcentaje)
        progressBar = findViewById(R.id.progressDetalleGas)

        // LÓGICA DE PRIVACIDAD
        if (!soyAdmin) {
            // Si NO soy admin (es decir, soy Distribuidor), oculto la empresa
            txtEmpresa.visibility = View.GONE
        }

        if (emailCliente != null) {
            cargarDatosCliente(emailCliente)
        } else {
            Toast.makeText(this, "Error al cargar cliente", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // Manejar click en botón Atrás
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun cargarDatosCliente(email: String) {
        db.collection("usuarios")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    // Datos Personales
                    val nombre = doc.getString("nombre") ?: "No registrado"
                    val direccion = doc.getString("direccion") ?: "No registrada"
                    val referencia = doc.getString("referencia_entrega") ?: ""
                    val telefono = doc.getString("telefono") ?: "No registrado"
                    
                    txtNombre.text = "Nombre: $nombre"
                    txtDireccion.text = "Dirección: $direccion\n(Ref: $referencia)"
                    txtTelefono.text = "Teléfono: $telefono"

                    // Datos Técnicos
                    val kilos = doc.getLong("balon_kilos") ?: 0
                    txtBalon.text = "Balón: $kilos kg"
                    txtEmpresa.text = "Empresa: ${doc.getString("balon_empresa") ?: "--"}"
                    txtFecha.text = "Instalado el: ${doc.getString("balon_fecha_instalacion") ?: "--"}"

                    // Nivel de Gas (Visual)
                    val nivel = doc.getLong("nivel_gas")?.toInt() ?: 0
                    progressBar.progress = nivel
                    txtPorcentaje.text = "$nivel%"
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error de conexión", Toast.LENGTH_SHORT).show()
            }
    }
}