package com.example.mqtt1

import android.app.Activity
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.Locale

class ProfileActivity : AppCompatActivity() {

    private lateinit var edtNombre: EditText
    private lateinit var edtTelefono: EditText
    private lateinit var edtDireccion: EditText
    private lateinit var edtEmpresa: EditText
    private lateinit var txtEmail: TextView
    private lateinit var imgProfile: ImageView
    private lateinit var btnCamera: View
    
    private lateinit var containerAddress: LinearLayout
    private lateinit var containerCompany: LinearLayout
    private lateinit var btnGuardar: Button
    private lateinit var btnChangePassword: Button // NUEVO
    
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    
    private var miRolActual = "user"
    
    // Variables para guardar coordenadas
    private var latitud: Double? = null
    private var longitud: Double? = null
    
    // Variable para la nueva foto
    private var selectedImageUri: Uri? = null

    // Lanzador para el selector de mapa
    private val mapPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val address = result.data?.getStringExtra("address")
            
            // Recibir coordenadas
            val lat = result.data?.getDoubleExtra("lat", 0.0)
            val lng = result.data?.getDoubleExtra("lng", 0.0)
            
            if (lat != 0.0 && lng != 0.0) {
                latitud = lat
                longitud = lng
            }

            if (!address.isNullOrEmpty()) {
                edtDireccion.setText(address)
            }
        }
    }
    
    // Lanzador para galería de imágenes
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            imgProfile.setImageURI(uri) // Mostrar localmente antes de subir
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        // Vistas
        edtNombre = findViewById(R.id.edtProfileName)
        edtTelefono = findViewById(R.id.edtProfilePhone)
        edtDireccion = findViewById(R.id.edtProfileAddress)
        edtEmpresa = findViewById(R.id.edtProfileCompany)
        txtEmail = findViewById(R.id.txtProfileEmail)
        
        imgProfile = findViewById(R.id.imgProfileEdit)
        btnCamera = findViewById(R.id.cardProfilePic) 
        
        containerAddress = findViewById(R.id.containerAddress)
        containerCompany = findViewById(R.id.containerCompany)
        btnGuardar = findViewById(R.id.btnSaveProfile)
        btnChangePassword = findViewById(R.id.btnChangePassword) // NUEVO

        // Configurar campo de dirección
        edtDireccion.isFocusable = false
        edtDireccion.setOnClickListener {
            val intent = Intent(this, MapPickerActivity::class.java)
            mapPickerLauncher.launch(intent)
        }
        
        // Configurar botón de foto
        btnCamera.setOnClickListener {
            galleryLauncher.launch("image/*")
        }
        
        // Configurar botón de contraseña
        btnChangePassword.setOnClickListener {
            enviarCorreoRecuperacion()
        }

        cargarDatosUsuario()

        btnGuardar.setOnClickListener {
            guardarCambios()
        }
    }
    
    private fun enviarCorreoRecuperacion() {
        val email = auth.currentUser?.email
        if (email != null) {
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    Toast.makeText(this, "Correo de cambio de contraseña enviado a $email", Toast.LENGTH_LONG).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error al enviar correo: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
    
    private fun cargarDatosUsuario() {
        val userEmail = auth.currentUser?.email ?: return
        txtEmail.text = userEmail

        db.collection("usuarios")
            .whereEqualTo("email", userEmail)
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    // Datos Comunes
                    edtNombre.setText(document.getString("nombre") ?: "")
                    edtTelefono.setText(document.getString("telefono") ?: "")
                    
                    // Cargar Foto con Glide
                    val fotoUrl = document.getString("foto_url")
                    if (!fotoUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(fotoUrl)
                            .circleCrop()
                            .into(imgProfile)
                    }
                    
                    // Cargar coordenadas existentes
                    latitud = document.getDouble("lat")
                    longitud = document.getDouble("lng")
                    
                    // Lógica para Dirección
                    val direccionGuardada = document.getString("direccion")
                    if (!direccionGuardada.isNullOrEmpty()) {
                        edtDireccion.setText(direccionGuardada)
                    } else {
                        // Si no hay texto pero si coordenadas, buscar dirección
                        if (latitud != null && longitud != null && latitud != 0.0) {
                            obtenerDireccionDesdeCoordenadas(latitud!!, longitud!!)
                        }
                    }
                    
                    // Rol
                    miRolActual = document.getString("rol") ?: "user"
                    
                    // Ajustar Visibilidad según Rol
                    when (miRolActual) {
                        "admin" -> {
                            containerAddress.visibility = View.GONE
                            containerCompany.visibility = View.GONE
                        }
                        "distribuidor" -> {
                            containerAddress.visibility = View.VISIBLE
                            containerCompany.visibility = View.VISIBLE
                            edtEmpresa.setText(document.getString("empresa_nombre") ?: "")
                        }
                        else -> { // user
                            containerAddress.visibility = View.VISIBLE
                            containerCompany.visibility = View.GONE
                        }
                    }
                }
            }
    }
    
    private fun obtenerDireccionDesdeCoordenadas(lat: Double, lng: Double) {
        edtDireccion.setText("Cargando dirección...")
        Thread {
            try {
                val geocoder = Geocoder(this, Locale.getDefault())
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                
                runOnUiThread {
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        val sb = StringBuilder()
                        if (address.thoroughfare != null) sb.append(address.thoroughfare).append(" ")
                        if (address.subThoroughfare != null) sb.append(address.subThoroughfare)
                        
                        if (sb.isEmpty()) {
                             // Si no hay calle exacta, probar con nombre del lugar
                             if (address.featureName != null) sb.append(address.featureName)
                             else sb.append("Ubicación GPS Detectada")
                        }
                        
                        edtDireccion.setText(sb.toString())
                    } else {
                        edtDireccion.setText("Ubicación GPS (${String.format("%.4f", lat)}, ${String.format("%.4f", lng)})")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                     // Si falla geocoder, mostrar coords
                     edtDireccion.setText("${String.format("%.4f", lat)}, ${String.format("%.4f", lng)}")
                }
            }
        }.start()
    }

    private fun guardarCambios() {
        btnGuardar.isEnabled = false
        btnGuardar.text = "Guardando..."
        
        if (selectedImageUri != null) {
            subirFotoYGuardarDatos()
        } else {
            actualizarFirestore(null)
        }
    }
    
    private fun subirFotoYGuardarDatos() {
        val userEmail = auth.currentUser?.email ?: return
        val filename = "profile_${System.currentTimeMillis()}.jpg"
        val ref = storage.reference.child("profiles/$userEmail/$filename")
        
        ref.putFile(selectedImageUri!!)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { uri ->
                    actualizarFirestore(uri.toString())
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al subir imagen: ${it.message}", Toast.LENGTH_SHORT).show()
                actualizarFirestore(null) 
            }
    }
    
    private fun actualizarFirestore(nuevaFotoUrl: String?) {
        val userEmail = auth.currentUser?.email ?: return
        
        val datosActualizados = mutableMapOf<String, Any>(
            "nombre" to edtNombre.text.toString(),
            "telefono" to edtTelefono.text.toString()
        )
        
        if (nuevaFotoUrl != null) {
            datosActualizados["foto_url"] = nuevaFotoUrl
        }
        
        if (containerAddress.visibility == View.VISIBLE) {
            datosActualizados["direccion"] = edtDireccion.text.toString()
            latitud?.let { datosActualizados["lat"] = it }
            longitud?.let { datosActualizados["lng"] = it }
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
                            btnGuardar.isEnabled = true
                            btnGuardar.text = "Actualizar Perfil"
                            finish()
                        }
                        .addOnFailureListener {
                            btnGuardar.isEnabled = true
                            btnGuardar.text = "Actualizar Perfil"
                        }
                }
            }
    }
}