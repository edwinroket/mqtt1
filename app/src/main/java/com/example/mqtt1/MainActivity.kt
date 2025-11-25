package com.example.mqtt1

import android.content.Intent
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
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import org.eclipse.paho.client.mqttv3.*

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var mqttClient: MqttClient
    private val serverUri = "tcp://test.mosquitto.org:1883"
    private val topicoGas = "balon/gas/peso"

    private lateinit var txtPeso: TextView
    private lateinit var txtEstado: TextView
    private lateinit var progressBarGas: ProgressBar
    private lateinit var txtAdminInfo: TextView
    
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var auth: FirebaseAuth

    // --- CONFIGURACIÓN DE ROLES ---
    // En una app real, esto vendría de Firestore Database
    private val ADMIN_EMAIL = "admin@gas.com" 

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        // Configurar Toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Configurar Drawer
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar, 
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navView.setNavigationItemSelectedListener(this)

        // Vistas del Dashboard
        txtPeso = findViewById(R.id.txtPeso)
        txtEstado = findViewById(R.id.txtEstado)
        progressBarGas = findViewById(R.id.progressBarGas)
        txtAdminInfo = findViewById(R.id.txtAdminInfo)

        // Configurar Header del Menú con datos del usuario
        val headerView = navView.getHeaderView(0)
        val txtUserEmail = headerView.findViewById<TextView>(R.id.txtUserEmail)
        txtUserEmail.text = currentUser?.email ?: "Invitado"

        // --- LÓGICA DE ROLES ---
        verificarPermisosAdmin(currentUser?.email)
        
        // --- MANEJO DE BOTÓN ATRÁS (MODERNO) ---
        // Esto reemplaza al antiguo onBackPressed()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    // Si el menú está abierto, lo cerramos
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    // Si no, dejamos que el sistema maneje el atrás (salir de la app)
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Conexión MQTT
        conectarMQTT()
    }

    private fun verificarPermisosAdmin(email: String?) {
        val menu = navView.menu
        
        // Lógica simple: Si el email coincide, mostramos cosas de admin
        if (email == ADMIN_EMAIL) {
            // ES ADMIN
            txtAdminInfo.visibility = View.VISIBLE
            txtAdminInfo.text = "MODO ADMINISTRADOR\nUsuario: $email"
            
            // Mostrar opciones de menú ocultas
            menu.findItem(R.id.nav_admin_users).isVisible = true
            menu.findItem(R.id.nav_admin_devices).isVisible = true
        } else {
            // ES USUARIO NORMAL
            txtAdminInfo.visibility = View.GONE
            
            // Ocultar opciones de admin
            menu.findItem(R.id.nav_admin_users).isVisible = false
            menu.findItem(R.id.nav_admin_devices).isVisible = false
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                // Ya estamos aquí
            }
            R.id.nav_profile -> {
                Toast.makeText(this, "Perfil de usuario", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_admin_users -> {
                Toast.makeText(this, "Gestión de Usuarios (Admin)", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_admin_devices -> {
                Toast.makeText(this, "Gestión de Balones (Admin)", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_logout -> {
                cerrarSesion()
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun cerrarSesion() {
        try {
            if (::mqttClient.isInitialized && mqttClient.isConnected) {
                mqttClient.disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        auth.signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun conectarMQTT() {
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
        } catch (e: Exception) {
            txtPeso.text = mensaje
        }
    }

    private fun actualizarEstado(estado: String) {
        runOnUiThread { txtEstado.text = estado }
    }
}