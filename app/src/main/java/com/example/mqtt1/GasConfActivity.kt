package com.example.mqtt1

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GasConfActivity : AppCompatActivity() {

    private lateinit var radioGroup: RadioGroup
    private lateinit var spinnerEmpresa: AutoCompleteTextView
    private lateinit var txtFecha: TextView
    private lateinit var btnGuardar: Button

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gas_conf)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        radioGroup = findViewById(R.id.radioGroupKilos)
        spinnerEmpresa = findViewById(R.id.spinnerEmpresa)
        txtFecha = findViewById(R.id.txtFechaHoy)
        btnGuardar = findViewById(R.id.btnGuardarConfig)

        // Configurar Spinner de Empresas
        val empresas = listOf("Gasco", "Abastible", "Lipigas", "HlaGas")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, empresas)
        spinnerEmpresa.setAdapter(adapter)

        // Poner fecha de hoy
        val fechaHoy = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        txtFecha.text = fechaHoy

        btnGuardar.setOnClickListener {
            guardarPreferencias()
        }
        
        cargarPreferenciasExistentes()
    }

    private fun cargarPreferenciasExistentes() {
        val userEmail = auth.currentUser?.email ?: return
        db.collection("usuarios").whereEqualTo("email", userEmail).get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    // Cargar Empresa
                    val empresa = doc.getString("balon_empresa")
                    if (empresa != null) spinnerEmpresa.setText(empresa, false)

                    // Cargar Kilos (Seleccionar RadioButton correcto)
                    val kilos = doc.getLong("balon_kilos")?.toInt()
                    when (kilos) {
                        5 -> findViewById<RadioButton>(R.id.rb5kg).isChecked = true
                        11 -> findViewById<RadioButton>(R.id.rb11kg).isChecked = true
                        15 -> findViewById<RadioButton>(R.id.rb15kg).isChecked = true
                        45 -> findViewById<RadioButton>(R.id.rb45kg).isChecked = true
                    }
                }
            }
    }

    private fun guardarPreferencias() {
        val userEmail = auth.currentUser?.email ?: return

        // Obtener Kilos seleccionados
        val selectedId = radioGroup.checkedRadioButtonId
        if (selectedId == -1) {
            Toast.makeText(this, "Selecciona el tamaño del balón", Toast.LENGTH_SHORT).show()
            return
        }
        
        val kilos = when (selectedId) {
            R.id.rb5kg -> 5
            R.id.rb11kg -> 11
            R.id.rb15kg -> 15
            R.id.rb45kg -> 45
            else -> 15
        }

        val empresa = spinnerEmpresa.text.toString()
        val fecha = txtFecha.text.toString()

        val datosBalon = mapOf(
            "balon_kilos" to kilos,
            "balon_empresa" to empresa,
            "balon_fecha_instalacion" to fecha
        )

        // Guardar en Firestore
        db.collection("usuarios")
            .whereEqualTo("email", userEmail)
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    db.collection("usuarios").document(document.id)
                        .update(datosBalon)
                        .addOnSuccessListener {
                            Toast.makeText(this, "¡Balón configurado!", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                }
            }
    }
}