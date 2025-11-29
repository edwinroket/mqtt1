package com.example.mqtt1

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import org.eclipse.paho.client.mqttv3.*
import org.json.JSONObject
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private lateinit var mqttClient: MqttClient
    private val serverUri = "tcp://broker.hivemq.com:1883"
    private val topicoGas = "gastracer/gas/peso"

    // UI Usuario
    private lateinit var txtPeso: TextView
    private lateinit var txtEstado: TextView
    private lateinit var progressBarGas: ProgressBar
    private lateinit var switchCompartir: SwitchMaterial
    private lateinit var layoutModoUsuario: View
    private lateinit var layoutAlertaVisual: View

    // UI Admin/Distribuidor
    private lateinit var txtAdminInfo: TextView
    private lateinit var recyclerClientes: RecyclerView
    private lateinit var layoutModoAdmin: View
    private lateinit var switchBuzzerTest: SwitchMaterial

    // Bottom Nav Buttons
    private lateinit var btnHome: CardView
    private lateinit var btnUsers: ImageView
    private lateinit var btnDevices: ImageView
    private lateinit var btnMetrics: ImageView
    private lateinit var btnProfileBottom: ImageView
    
    // Estructura App
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var rtdb: FirebaseDatabase
    
    private var miRolActual = "user"
    private val channelId = "alerta_gas_channel"
    private val umbralCritico = 10 // 10% para activar el Buzzer

    // Configuración de Balón (Valores por defecto: 15kg)
    private var capacidadBalon: Int = 15
    private var pesoTara: Float = 17.0f // Peso aproximado del envase vacío para 15kg

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        crearCanalNotificaciones()

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        rtdb = FirebaseDatabase.getInstance()
        
        val currentUser = auth.currentUser

        // Inicializar Vistas
        txtPeso = findViewById(R.id.txtPeso)
        txtEstado = findViewById(R.id.txtEstado)
        progressBarGas = findViewById(R.id.progressBarGas)
        txtAdminInfo = findViewById(R.id.txtAdminInfo)
        switchCompartir = findViewById(R.id.switchCompartir)
        
        layoutModoUsuario = findViewById(R.id.layoutModoUsuario)
        layoutModoAdmin = findViewById(R.id.layoutModoAdmin)
        recyclerClientes = findViewById(R.id.recyclerClientes)
        layoutAlertaVisual = findViewById(R.id.layoutAlertaVisual)
        
        // Nuevo Switch para el Buzzer
        switchBuzzerTest = findViewById(R.id.switchBuzzerTest)

        // Inicializar Botones Bottom Nav
        btnHome = findViewById(R.id.btnHome)
        btnUsers = findViewById(R.id.btnUsers)
        btnDevices = findViewById(R.id.btnDevices)
        btnMetrics = findViewById(R.id.btnMetrics)
        btnProfileBottom = findViewById(R.id.btnProfileBottom)

        // Configurar Listeners Bottom Nav
        btnHome.setOnClickListener {
            mostrarVistaInicio()
            resaltarBoton(btnHome)
        }

        btnUsers.setOnClickListener {
            if (miRolActual == "admin" || miRolActual == "distribuidor") {
                if (miRolActual == "admin") {
                    // Admin va a la nueva gestión de usuarios
                    startActivity(Intent(this, AdminUsersActivity::class.java))
                } else {
                    // Distribuidor ve la lista de clientes en el home
                    mostrarVistaUsuarios()
                    resaltarBoton(btnUsers)
                }
            } else {
                Toast.makeText(this, getString(R.string.restricted_access), Toast.LENGTH_SHORT).show()
            }
        }

        btnDevices.setOnClickListener {
            if (miRolActual == "admin") {
                mostrarVistaDispositivos()
                resaltarBoton(btnDevices)
            } else {
                Toast.makeText(this, getString(R.string.admin_only), Toast.LENGTH_SHORT).show()
            }
        }

        btnMetrics.setOnClickListener {
            mostrarVistaMetricas()
            resaltarBoton(btnMetrics)
        }

        btnProfileBottom.setOnClickListener {
            mostrarMenuPerfil()
            // No resaltamos perfil ya que abre un menú modal
        }

        // Listener imagen perfil header
        findViewById<View>(R.id.imgProfile).setOnClickListener { mostrarMenuPerfil() }

        // Header Info
        val txtHelloUser = findViewById<TextView>(R.id.txtHelloUser)
        if (txtHelloUser != null) {
            val nombre = currentUser?.email?.split("@")?.get(0) ?: "Usuario"
            txtHelloUser.text = getString(R.string.welcome_user_format, nombre.replaceFirstChar { it.uppercase() })
        }

        switchCompartir.setOnCheckedChangeListener { _, isChecked ->
            actualizarEstadoCompartir(currentUser?.email, isChecked)
        }
        
        // Listener para Switch Buzzer Test (RTDB)
        switchBuzzerTest.setOnCheckedChangeListener { _, isChecked ->
            controlarBuzzerRemoto(isChecked)
        }

        verificarRolYPreferencias(currentUser?.email)
        
        if (miRolActual == "user" || miRolActual == "admin") {
            conectarMQTT()
        }
    }
    
    // Vistas
    private fun mostrarVistaInicio() {
        layoutModoUsuario.visibility = View.VISIBLE
        recyclerClientes.visibility = View.GONE
        layoutModoAdmin.visibility = View.GONE
        txtAdminInfo.visibility = View.GONE
    }

    private fun mostrarVistaUsuarios() {
        layoutModoUsuario.visibility = View.GONE
        recyclerClientes.visibility = View.VISIBLE
        layoutModoAdmin.visibility = View.GONE
        
        txtAdminInfo.visibility = View.VISIBLE
        txtAdminInfo.text = if (miRolActual == "distribuidor") getString(R.string.distribuidor_zone) else getString(R.string.admin_users)
        
        if (recyclerClientes.adapter == null) {
             cargarClientesDisponibles()
        }
    }

    private fun mostrarVistaDispositivos() {
        layoutModoUsuario.visibility = View.GONE
        recyclerClientes.visibility = View.GONE
        layoutModoAdmin.visibility = View.VISIBLE
        
        txtAdminInfo.visibility = View.VISIBLE
        txtAdminInfo.text = getString(R.string.admin_devices)
    }
    
    private fun mostrarVistaMetricas() {
        layoutModoUsuario.visibility = View.GONE
        recyclerClientes.visibility = View.GONE
        layoutModoAdmin.visibility = View.VISIBLE
        
        txtAdminInfo.visibility = View.VISIBLE
        txtAdminInfo.text = getString(R.string.admin_metrics)
    }
    
    private fun resaltarBoton(botonSeleccionado: View) {
        // Resetear colores (blanco por defecto)
        val colorDefault = ContextCompat.getColor(this, android.R.color.white)
        val colorSelected = ContextCompat.getColor(this, R.color.accent_blue) // Asumiendo que existe, o usar color hardcoded

        btnUsers.setColorFilter(colorDefault)
        btnDevices.setColorFilter(colorDefault)
        btnMetrics.setColorFilter(colorDefault)
        btnProfileBottom.setColorFilter(colorDefault)
        
        // btnHome es un CardView, no tiene setColorFilter directo en el view principal
        // Si quisiéramos cambiar el color del icono dentro de btnHome:
        // (btnHome.getChildAt(0) as ImageView).setColorFilter(...)
        
        if (botonSeleccionado is ImageView) {
            botonSeleccionado.setColorFilter(colorSelected)
        }
    }

    private fun mostrarMenuPerfil() {
        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_profile, null)
        
        val btnConfig = view.findViewById<View>(R.id.btnConfigGas)
        val btnDatos = view.findViewById<View>(R.id.btnVerDatos)
        val btnLogout = view.findViewById<View>(R.id.btnLogoutSheet)
        val txtEmailSheet = view.findViewById<TextView>(R.id.txtEmailSheet)
        
        txtEmailSheet.text = auth.currentUser?.email
        
        if (miRolActual == "user") {
            btnConfig.visibility = View.VISIBLE
            btnConfig.setOnClickListener {
                startActivity(Intent(this, GasConfActivity::class.java))
                dialog.dismiss()
            }
        } else {
            btnConfig.visibility = View.GONE
        }
        
        btnDatos.setOnClickListener {
             startActivity(Intent(this, ProfileActivity::class.java))
             dialog.dismiss()
        }
        
        btnLogout.setOnClickListener {
            dialog.dismiss()
            auth.signOut()
            finish()
        }
        
        dialog.setContentView(view)
        dialog.show()
    }

    private fun verificarRolYPreferencias(email: String?) {
        if (email == null) return
        
        db.collection("usuarios")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    for (document in documents) {
                        miRolActual = document.getString("rol") ?: "user"
                        val comparteDatos = document.getBoolean("compartir_datos") ?: false
                        switchCompartir.isChecked = comparteDatos
                        
                        actualizarVisibilidadBotonesNav()
                    }
                } else {
                    actualizarVisibilidadBotonesNav()
                }
            }
            .addOnFailureListener { 
                actualizarVisibilidadBotonesNav()
            }
    }
    
    private fun actualizarVisibilidadBotonesNav() {
        when (miRolActual) {
            "admin" -> {
                btnUsers.visibility = View.VISIBLE
                btnDevices.visibility = View.VISIBLE
            }
            "distribuidor" -> {
                btnUsers.visibility = View.VISIBLE
                btnDevices.visibility = View.GONE
            }
            else -> {
                btnUsers.visibility = View.GONE
                btnDevices.visibility = View.GONE
            }
        }
    }

    private fun actualizarEstadoCompartir(email: String?, compartir: Boolean) {
        if (email == null) return
        db.collection("usuarios")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    db.collection("usuarios").document(document.id)
                        .update("compartir_datos", compartir)
                }
            }
    }
    
    private fun controlarBuzzerRemoto(encender: Boolean) {
        // Escribir en /devices/esp8266-001/actuators/buzzerTest
        // En un sistema real, el deviceId debería ser dinámico o seleccionado de una lista
        val deviceId = "esp8266-001" 
        val ref = rtdb.getReference("devices/$deviceId/actuators/buzzerTest")
        ref.setValue(encender)
            .addOnSuccessListener { 
                val estado = if(encender) "Activado" else "Desactivado"
                Toast.makeText(this, "Buzzer remoto $estado", Toast.LENGTH_SHORT).show() 
            }
            .addOnFailureListener {
                 Toast.makeText(this, "Error conexión RTDB", Toast.LENGTH_SHORT).show()
            }
    }

    private fun cargarClientesDisponibles() {
        recyclerClientes.layoutManager = GridLayoutManager(this, 2)
        
        db.collection("usuarios")
            .whereEqualTo("compartir_datos", true) 
            .whereEqualTo("rol", "user")           
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener

                val lista = ArrayList<ClienteGas>()
                for (doc in snapshots) {
                    val email = doc.getString("email") ?: "Sin email"
                    val nivel = doc.getLong("nivel_gas")?.toInt() ?: 0 
                    lista.add(ClienteGas(email, nivel))
                }

                // ORDENAR LA LISTA: CRÍTICO (0..9) -> ALERTA (10..29) -> NORMAL (30+)
                lista.sortBy { it.nivelGas }
                
                recyclerClientes.adapter = ClientesAdapter(lista) { clienteSeleccionado ->
                    val intent = Intent(this, DetalleClienteActivity::class.java)
                    intent.putExtra("email_cliente", clienteSeleccionado.email)
                    intent.putExtra("es_admin", miRolActual == "admin") 
                    startActivity(intent)
                }
            }
    }

    private fun conectarMQTT() {
        if (::mqttClient.isInitialized && mqttClient.isConnected) return
        
        Thread {
            try {
                mqttClient = MqttClient(serverUri, "android-${System.currentTimeMillis()}", null)
                val options = MqttConnectOptions().apply { isCleanSession = true }
                
                mqttClient.setCallback(object : MqttCallback {
                    override fun connectionLost(cause: Throwable?) { runOnUiThread { txtEstado.text = getString(R.string.estado_desconectado) } }
                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        runOnUiThread { procesarMensaje(message.toString()) }
                    }
                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })

                mqttClient.connect(options)
                if (mqttClient.isConnected) {
                    mqttClient.subscribe(topicoGas, 0)
                    runOnUiThread { txtEstado.text = getString(R.string.estado_conectado) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun procesarMensaje(mensaje: String) {
        try {
            val peso = mensaje.toFloatOrNull() ?: 0f
            txtPeso.text = getString(R.string.weight_format, peso.toString())
            val maxPeso = 15.0f 
            val porcentaje = ((peso / maxPeso) * 100).toInt().coerceIn(0, 100)
            progressBarGas.progress = porcentaje
            
            if (porcentaje <= umbralCritico) {
                layoutAlertaVisual.visibility = View.VISIBLE
                lanzarNotificacionAlerta(porcentaje)
                if (miRolActual == "user" && auth.currentUser != null) {
                    actualizarEstadoAlarma(auth.currentUser!!.email, true)
                }
            } else {
                layoutAlertaVisual.visibility = View.GONE
                if (miRolActual == "user" && auth.currentUser != null) {
                    actualizarEstadoAlarma(auth.currentUser!!.email, false)
                }
            }

            if (miRolActual == "user" && auth.currentUser != null) {
                guardarNivelEnNube(auth.currentUser!!.email, porcentaje)
            }
            
        } catch (_: Exception) {
            // Error parseo
        }
    }
    
    private fun crearCanalNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alerta Gas Crítico"
            val descriptionText = "Notificaciones cuando el gas está por agotarse"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun lanzarNotificacionAlerta(nivelActual: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ ALERTA CRÍTICA DE GAS")
            .setContentText("Nivel bajo ($nivelActual%). Alarma activa.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            notify(1, builder.build())
        }
    }

    private fun guardarNivelEnNube(email: String?, nivel: Int) {
        if (email == null) return
        db.collection("usuarios")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    val userRef = db.collection("usuarios").document(doc.id)
                    userRef.update("nivel_gas", nivel)
                    val metricas = mapOf(
                        "nivel" to nivel,
                        "fecha" to Timestamp.now()
                    )
                    userRef.collection("historial_mediciones").add(metricas)
                }
            }
    }
    
    private fun actualizarEstadoAlarma(email: String?, activar: Boolean) {
        if (email == null) return
        db.collection("usuarios")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    db.collection("usuarios").document(doc.id)
                        .update("alarma_activa", activar)
                }
            }
    }
}