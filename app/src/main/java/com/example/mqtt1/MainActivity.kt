package com.example.mqtt1

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.eclipse.paho.client.mqttv3.*

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var mqttClient: MqttClient
    private val serverUri = "tcp://broker.hivemq.com:1883"
    private val topicoGas = "gastracer/gas/peso"

    // UI Usuario
    private lateinit var txtPeso: TextView
    private lateinit var txtEstado: TextView
    private lateinit var progressBarGas: ProgressBar
    private lateinit var switchCompartir: SwitchMaterial
    private lateinit var cardCompartir: View
    private lateinit var layoutModoUsuario: View

    // UI Admin/Distribuidor
    private lateinit var txtAdminInfo: TextView
    private lateinit var recyclerClientes: RecyclerView
    
    // Estructura App
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    
    private var miRolActual = "user"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        
        val currentUser = auth.currentUser

        // Toolbar & Drawer
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar, 
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        navView.setNavigationItemSelectedListener(this)

        // Inicializar Vistas
        txtPeso = findViewById(R.id.txtPeso)
        txtEstado = findViewById(R.id.txtEstado)
        progressBarGas = findViewById(R.id.progressBarGas)
        txtAdminInfo = findViewById(R.id.txtAdminInfo)
        switchCompartir = findViewById(R.id.switchCompartir)
        cardCompartir = findViewById(R.id.cardCompartir)
        layoutModoUsuario = findViewById(R.id.layoutModoUsuario)
        recyclerClientes = findViewById(R.id.recyclerClientes)

        // Configurar Lista Clientes
        recyclerClientes.layoutManager = LinearLayoutManager(this)

        // Header Menú
        val headerView = navView.getHeaderView(0)
        val txtUserEmail = headerView.findViewById<TextView>(R.id.txtUserEmail)
        txtUserEmail.text = currentUser?.email ?: "Invitado"

        // Eventos
        switchCompartir.setOnCheckedChangeListener { _, isChecked ->
            actualizarEstadoCompartir(currentUser?.email, isChecked)
        }

        // Cargar Rol
        verificarRolYPreferencias(currentUser?.email)
        
        // Back Button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // MQTT solo si eres Usuario
        if (miRolActual == "user") {
            conectarMQTT()
        }
    }

    private fun verificarRolYPreferencias(email: String?) {
        if (email == null) return
        val menu = navView.menu
        
        db.collection("usuarios")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    for (document in documents) {
                        miRolActual = document.getString("rol") ?: "user"
                        val comparteDatos = document.getBoolean("compartir_datos") ?: false
                        switchCompartir.isChecked = comparteDatos
                        
                        when (miRolActual) {
                            "admin" -> activarModoAdmin(email, menu)
                            "distribuidor" -> activarModoDistribuidor(email, menu)
                            else -> activarModoUsuario(menu)
                        }
                    }
                } else {
                    activarModoUsuario(menu)
                }
            }
            .addOnFailureListener { 
                activarModoUsuario(menu)
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

    // --- MODOS DE VISTA ---

    private fun activarModoAdmin(email: String, menu: android.view.Menu) {
        layoutModoUsuario.visibility = View.VISIBLE
        recyclerClientes.visibility = View.GONE
        
        txtAdminInfo.visibility = View.VISIBLE
        txtAdminInfo.text = "ADMINISTRADOR: $email"
        txtAdminInfo.setBackgroundColor(Color.parseColor("#FFEB3B")) 
        txtAdminInfo.setTextColor(Color.parseColor("#D32F2F"))
        
        cardCompartir.visibility = View.GONE
        
        // Admin NO VE "Mi Gas" (porque no consume, gestiona)
        menu.findItem(R.id.nav_gas_config).isVisible = false 
        
        menu.findItem(R.id.nav_admin_users).isVisible = true
        menu.findItem(R.id.nav_admin_devices).isVisible = true
        
        conectarMQTT() 
    }

    private fun activarModoDistribuidor(email: String, menu: android.view.Menu) {
        layoutModoUsuario.visibility = View.GONE
        recyclerClientes.visibility = View.VISIBLE

        txtAdminInfo.visibility = View.VISIBLE
        txtAdminInfo.text = "MODO DISTRIBUIDOR"
        txtAdminInfo.setBackgroundColor(Color.parseColor("#E3F2FD"))
        txtAdminInfo.setTextColor(Color.parseColor("#1565C0"))

        // Distribuidor NO VE "Mi Gas"
        menu.findItem(R.id.nav_gas_config).isVisible = false
        
        menu.findItem(R.id.nav_admin_users).isVisible = false 
        menu.findItem(R.id.nav_admin_devices).isVisible = false
        
        cargarClientesDisponibles() 
    }

    private fun activarModoUsuario(menu: android.view.Menu) {
        miRolActual = "user"
        layoutModoUsuario.visibility = View.VISIBLE
        recyclerClientes.visibility = View.GONE
        txtAdminInfo.visibility = View.GONE
        cardCompartir.visibility = View.VISIBLE 
        
        // Usuario SÍ VE "Mi Gas"
        menu.findItem(R.id.nav_gas_config).isVisible = true 
        
        menu.findItem(R.id.nav_admin_users).isVisible = false
        menu.findItem(R.id.nav_admin_devices).isVisible = false
        
        conectarMQTT()
    }

    private fun cargarClientesDisponibles() {
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
                
                recyclerClientes.adapter = ClientesAdapter(lista) { clienteSeleccionado ->
                    val intent = Intent(this, DetalleClienteActivity::class.java)
                    intent.putExtra("email_cliente", clienteSeleccionado.email)
                    intent.putExtra("es_admin", miRolActual == "admin") // Le pasamos si somos admin
                    startActivity(intent)
                }
            }
    }

    private fun conectarMQTT() {
        if (::mqttClient.isInitialized && mqttClient.isConnected) return
        
        actualizarEstado("Conectando...")
        Thread {
            try {
                mqttClient = MqttClient(serverUri, "android-${System.currentTimeMillis()}", null)
                val options = MqttConnectOptions().apply { isCleanSession = true }
                
                mqttClient.setCallback(object : MqttCallback {
                    override fun connectionLost(cause: Throwable?) { actualizarEstado("Desconectado") }
                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        runOnUiThread { procesarMensaje(message.toString()) }
                    }
                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })

                mqttClient.connect(options)
                if (mqttClient.isConnected) {
                    mqttClient.subscribe(topicoGas, 0)
                    actualizarEstado("En línea")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                actualizarEstado("Error Conexión")
            }
        }.start()
    }

    private fun procesarMensaje(mensaje: String) {
        try {
            val peso = mensaje.toFloatOrNull() ?: 0f
            txtPeso.text = "$peso kg"
            val maxPeso = 15.0f 
            val porcentaje = ((peso / maxPeso) * 100).toInt().coerceIn(0, 100)
            progressBarGas.progress = porcentaje
            
            if (miRolActual == "user" && auth.currentUser != null) {
                guardarNivelEnNube(auth.currentUser!!.email, porcentaje)
            }
            
        } catch (e: Exception) {
            txtPeso.text = mensaje
        }
    }

    private fun guardarNivelEnNube(email: String?, nivel: Int) {
        if (email == null) return
        db.collection("usuarios")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    db.collection("usuarios").document(doc.id)
                        .update("nivel_gas", nivel)
                }
            }
    }
    
    private fun actualizarEstado(estado: String) {
        runOnUiThread { txtEstado.text = estado }
    }
    
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                // Si es Admin, "Home" vuelve al Dashboard normal
                if (miRolActual == "admin") {
                    layoutModoUsuario.visibility = View.VISIBLE
                    recyclerClientes.visibility = View.GONE
                }
            }
            R.id.nav_profile -> {
                startActivity(Intent(this, ProfileActivity::class.java))
            }
            R.id.nav_gas_config -> {
                startActivity(Intent(this, GasConfActivity::class.java))
            }
            R.id.nav_admin_users -> {
                // Admin queriendo ver la lista de clientes
                layoutModoUsuario.visibility = View.GONE
                recyclerClientes.visibility = View.VISIBLE
                cargarClientesDisponibles()
                Toast.makeText(this, "Vista de Clientes Activa", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_admin_devices -> Toast.makeText(this, "Gestión de Balones", Toast.LENGTH_SHORT).show()
            R.id.nav_logout -> cerrarSesion()
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun cerrarSesion() {
        try {
            if (::mqttClient.isInitialized && mqttClient.isConnected) mqttClient.disconnect()
        } catch (e: Exception) { }
        auth.signOut()
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}