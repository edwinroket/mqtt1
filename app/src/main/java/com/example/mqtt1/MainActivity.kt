package com.example.mqtt1

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.json.JSONObject
import java.util.Locale
import kotlin.math.max

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    // --- MQTT ---
    private lateinit var mqttClient: MqttClient
    private val serverUri = "tcp://broker.hivemq.com:1883"
    private val topicoGas = "gastracer/gas/peso"

    // --- VISTAS PRINCIPALES ---
    private lateinit var layoutUsuario: View
    private lateinit var layoutAdmin: View
    private lateinit var layoutDistribuidor: View
    private lateinit var layoutMapaGlobal: View 

    // --- UI MODO USUARIO ---
    private lateinit var tvPeso: TextView
    private lateinit var tvEstado: TextView
    private lateinit var tvPorcentaje: TextView
    private lateinit var progressGas: ProgressBar
    private lateinit var swCompartir: SwitchMaterial
    private lateinit var layoutAlerta: View
    private lateinit var tvDiasRestantes: TextView
    private lateinit var chartUser: LineChart

    // --- UI MODO ADMIN ---
    private lateinit var tvTotalClientes: TextView
    private lateinit var tvTotalAlertas: TextView
    private lateinit var pieChartAdmin: PieChart

    // --- UI MODO DISTRIBUIDOR ---
    private lateinit var rvDistribuidor: RecyclerView

    // --- MAPA GLOBAL UI ---
    private lateinit var lblMapTitle: TextView
    private lateinit var recyclerMapList: RecyclerView
    private var googleMap: GoogleMap? = null

    // --- UI COMÚN ---
    private lateinit var rvClientes: RecyclerView 
    private lateinit var swBuzzer: SwitchMaterial
    private lateinit var btnLogoutTop: ImageButton

    // --- BOTTOM NAV ---
    private lateinit var navHome: ImageView
    private lateinit var navUsers: ImageView
    private lateinit var navDevices: ImageView
    private lateinit var navMetrics: ImageView
    private lateinit var navProfile: ImageView
    
    // --- FIREBASE & UTILS ---
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var rtdb: FirebaseDatabase
    
    private var miRolActual = "user"
    private val channelId = "alerta_gas_channel"
    private val umbralCritico = 10 
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    private var ultimoTiempoAlarma: Long = 0
    private val INTERVALO_ALARMA = 600000L 

    private var capacidadBalon: Int = 15
    private var pesoTara: Float = 0.0f 

    // COORDENADAS TALCA (Fallback si falla GPS)
    private val LAT_TALCA = -35.4264
    private val LNG_TALCA = -71.6554

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        crearCanalNotificaciones()

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        rtdb = FirebaseDatabase.getInstance()
        
        inicializarVistas()
        configurarListeners()
        
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
        
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val txtHelloUser = findViewById<TextView>(R.id.txtHelloUser)
            val nombre = currentUser.email?.split("@")?.get(0) ?: "Usuario"
            txtHelloUser.text = getString(R.string.welcome_user_format, nombre.replaceFirstChar { it.uppercase() })
            
            verificarRolYPreferencias(currentUser.email)
        } else {
            mostrarVistaUsuario()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        
        // --- AQUÍ APLICAMOS EL ESTILO NOCTURNO ---
        try {
            val success = googleMap?.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark)
            )
            if (success == false) {
                // Error parsing style
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        enableMyLocation()
        
        // Centrar en Talca por defecto
        val talca = LatLng(LAT_TALCA, LNG_TALCA)
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(talca, 13f))
    }
    
    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap?.isMyLocationEnabled = true
            googleMap?.uiSettings?.isMyLocationButtonEnabled = true
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation()
            }
        }
    }

    private fun inicializarVistas() {
        layoutUsuario = findViewById(R.id.layoutModoUsuario)
        layoutAdmin = findViewById(R.id.layoutModoAdmin)
        layoutDistribuidor = findViewById(R.id.layoutModoDistribuidor)
        layoutMapaGlobal = findViewById(R.id.layoutMapaGlobal) 

        tvPeso = findViewById(R.id.txtPeso)
        tvEstado = findViewById(R.id.txtEstado)
        tvPorcentaje = findViewById(R.id.txtPorcentajeCentro)
        progressGas = findViewById(R.id.progressBarGas)
        swCompartir = findViewById(R.id.switchCompartir)
        layoutAlerta = findViewById(R.id.layoutAlertaVisual)
        tvDiasRestantes = findViewById(R.id.txtDiasRestantes)
        chartUser = findViewById(R.id.chartUsuario)
        swBuzzer = findViewById(R.id.switchBuzzerTest)

        tvTotalClientes = findViewById(R.id.txtTotalClientes)
        tvTotalAlertas = findViewById(R.id.txtTotalAlertas)
        pieChartAdmin = findViewById(R.id.chartAdminEstado)
        rvClientes = findViewById(R.id.recyclerClientes)

        rvDistribuidor = findViewById(R.id.recyclerDistribuidor)
        
        lblMapTitle = findViewById(R.id.lblMapTitle)
        recyclerMapList = findViewById(R.id.recyclerMapList)
        
        btnLogoutTop = findViewById(R.id.btnNotif)

        navHome = findViewById(R.id.btnHome)
        navUsers = findViewById(R.id.btnUsers)
        navDevices = findViewById(R.id.btnDevices)
        navMetrics = findViewById(R.id.btnMetrics)
        navProfile = findViewById(R.id.btnProfileBottom)
        
        // --- Aplicar efecto Bounce a botones ---
        aplicarEfectoBounce(btnLogoutTop)
        aplicarEfectoBounce(navHome)
        aplicarEfectoBounce(navUsers)
        aplicarEfectoBounce(navDevices)
        aplicarEfectoBounce(navMetrics)
        aplicarEfectoBounce(navProfile)

        configurarGraficoUsuario()
        configurarGraficoAdmin()
    }
    
    // Función para dar feedback táctil "Bounce"
    @SuppressLint("ClickableViewAccessibility")
    private fun aplicarEfectoBounce(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val scaleDownX = ObjectAnimator.ofFloat(v, "scaleX", 0.9f)
                    val scaleDownY = ObjectAnimator.ofFloat(v, "scaleY", 0.9f)
                    scaleDownX.duration = 100
                    scaleDownY.duration = 100
                    scaleDownX.start()
                    scaleDownY.start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val scaleUpX = ObjectAnimator.ofFloat(v, "scaleX", 1f)
                    val scaleUpY = ObjectAnimator.ofFloat(v, "scaleY", 1f)
                    scaleUpX.duration = 100
                    scaleUpY.duration = 100
                    scaleUpX.start()
                    scaleUpY.start()
                }
            }
            false // No consumir el evento para que onClick siga funcionando
        }
    }

    private fun configurarListeners() {
        btnLogoutTop.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        navHome.setOnClickListener {
            when (miRolActual) {
                "admin" -> mostrarVistaAdmin()
                "distribuidor" -> mostrarVistaDistribuidor()
                else -> mostrarVistaUsuario()
            }
            resaltarBoton(navHome)
        }

        navUsers.setOnClickListener {
            if (miRolActual == "admin") {
                startActivity(Intent(this, AdminUsersActivity::class.java))
            } else if (miRolActual == "distribuidor") {
                mostrarVistaDistribuidor()
            } else {
                Toast.makeText(this, "Opción no disponible", Toast.LENGTH_SHORT).show()
            }
            resaltarBoton(navUsers)
        }

        navMetrics.setOnClickListener {
            if (miRolActual == "admin") {
                mostrarVistaAdmin()
            } else {
                Toast.makeText(this, "Opción para Administradores", Toast.LENGTH_SHORT).show()
            }
            resaltarBoton(navMetrics)
        }

        navDevices.setOnClickListener {
            mostrarVistaMapa()
            resaltarBoton(navDevices)
        }

        navProfile.setOnClickListener { 
            mostrarMenuPerfil()
            resaltarBoton(navProfile)
        }
        
        findViewById<View>(R.id.imgProfile).setOnClickListener { mostrarMenuPerfil() }

        swCompartir.setOnCheckedChangeListener { _, isChecked ->
            actualizarEstadoCompartir(auth.currentUser?.email, isChecked)
        }
        
        swBuzzer.setOnCheckedChangeListener { _, isChecked ->
            controlarBuzzerRemoto(isChecked)
        }
    }
    
    private fun resaltarBoton(boton: ImageView) {
        val colorDefault = ContextCompat.getColor(this, android.R.color.white)
        val colorSelected = Color.parseColor("#00F2FF") 

        navHome.setColorFilter(colorDefault)
        navUsers.setColorFilter(colorDefault)
        navDevices.setColorFilter(colorDefault)
        navMetrics.setColorFilter(colorDefault)
        navProfile.setColorFilter(colorDefault)
        
        boton.setColorFilter(colorSelected)
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
                        
                        if (miRolActual == "user") {
                            val comparteDatos = document.getBoolean("compartir_datos") ?: false
                            swCompartir.isChecked = comparteDatos
                            val kilosConfig = document.getLong("balon_kilos")?.toInt() ?: 15
                            actualizarConfiguracionBalon(kilosConfig)
                        }

                        when (miRolActual) {
                            "admin" -> {
                                mostrarVistaAdmin()
                                cargarDatosAdmin()
                            }
                            "distribuidor" -> {
                                mostrarVistaDistribuidor()
                                cargarDatosDistribuidor()
                            }
                            else -> {
                                mostrarVistaUsuario()
                                conectarMQTT()
                                cargarDatosGraficoUsuario(email)
                            }
                        }
                    }
                }
            }
    }

    private fun mostrarVistaUsuario() {
        layoutUsuario.visibility = View.VISIBLE
        layoutAdmin.visibility = View.GONE
        layoutDistribuidor.visibility = View.GONE
        layoutMapaGlobal.visibility = View.GONE
        resaltarBoton(navHome)
    }

    private fun mostrarVistaAdmin() {
        layoutUsuario.visibility = View.GONE
        layoutAdmin.visibility = View.VISIBLE
        layoutDistribuidor.visibility = View.GONE
        layoutMapaGlobal.visibility = View.GONE
        resaltarBoton(navHome)
    }

    private fun mostrarVistaDistribuidor() {
        layoutUsuario.visibility = View.GONE
        layoutAdmin.visibility = View.GONE
        layoutDistribuidor.visibility = View.VISIBLE
        layoutMapaGlobal.visibility = View.GONE
        resaltarBoton(navHome)
    }
    
    private fun mostrarVistaMapa() {
        layoutUsuario.visibility = View.GONE
        layoutAdmin.visibility = View.GONE
        layoutDistribuidor.visibility = View.GONE
        layoutMapaGlobal.visibility = View.VISIBLE
        
        when (miRolActual) {
            "admin" -> {
                lblMapTitle.text = "UBICACIÓN DE CLIENTES (ADMIN)"
                cargarMapaAdmin()
            }
            "user" -> {
                lblMapTitle.text = "DISTRIBUIDORES CERCANOS"
                cargarMapaUsuario()
            }
            "distribuidor" -> {
                lblMapTitle.text = "MI RUTA DE ENTREGA"
                cargarDatosDistribuidor(esMapaGlobal = true) 
            }
        }
    }
    
    private fun geocodificarDireccion(direccion: String, onResult: (LatLng?) -> Unit) {
        Thread {
            try {
                val geocoder = Geocoder(this, Locale.getDefault())
                val addresses = geocoder.getFromLocationName("$direccion, Talca, Chile", 1)
                if (!addresses.isNullOrEmpty()) {
                    val lat = addresses[0].latitude
                    val lng = addresses[0].longitude
                    runOnUiThread { onResult(LatLng(lat, lng)) }
                } else {
                    runOnUiThread { onResult(null) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { onResult(null) }
            }
        }.start()
    }
    
    private fun cargarMapaAdmin() {
        recyclerMapList.layoutManager = LinearLayoutManager(this)
        googleMap?.clear()
        
        db.collection("usuarios")
            .whereEqualTo("rol", "user")
            .get()
            .addOnSuccessListener { documents ->
                val lista = ArrayList<ClienteGas>()
                
                for (doc in documents) {
                    val email = doc.getString("email") ?: ""
                    val direccion = doc.getString("direccion") ?: ""
                    val nivel = doc.getLong("nivel_gas")?.toInt() ?: 0
                    val lat = doc.getDouble("lat")
                    val lng = doc.getDouble("lng")
                    
                    lista.add(ClienteGas(email, nivel, "user", "", direccion))
                    
                    val colorMarker = when {
                         nivel < 10 -> BitmapDescriptorFactory.HUE_RED
                         nivel < 30 -> BitmapDescriptorFactory.HUE_YELLOW
                         else -> BitmapDescriptorFactory.HUE_GREEN
                    }

                    // PRIORIDAD: Coordenadas numéricas
                    if (lat != null && lng != null && lat != 0.0 && lng != 0.0) {
                        googleMap?.addMarker(MarkerOptions()
                            .position(LatLng(lat, lng))
                            .title("$email ($nivel%)")
                            .snippet(direccion) // La dirección queda como texto informativo
                            .icon(BitmapDescriptorFactory.defaultMarker(colorMarker)))
                    } 
                    // FALLBACK: Texto de dirección
                    else if (direccion.isNotEmpty() && direccion.length > 5) {
                        geocodificarDireccion(direccion) { latLng ->
                            if (latLng != null) {
                                googleMap?.addMarker(MarkerOptions()
                                    .position(latLng)
                                    .title("$email ($nivel%)")
                                    .snippet(direccion)
                                    .icon(BitmapDescriptorFactory.defaultMarker(colorMarker)))
                            }
                        }
                    }
                }
                
                recyclerMapList.adapter = ClientesAdapter(lista) { cliente ->
                    // Admin: Abrir detalle de cliente
                    val intent = Intent(this, DetalleClienteActivity::class.java)
                    intent.putExtra("email_cliente", cliente.email)
                    intent.putExtra("es_admin", true)
                    startActivity(intent)
                }
            }
    }
    
    private fun cargarMapaUsuario() {
        recyclerMapList.layoutManager = LinearLayoutManager(this)
        googleMap?.clear()
        
        db.collection("usuarios")
            .whereEqualTo("rol", "distribuidor")
            .get()
            .addOnSuccessListener { documents ->
                val listaDistribuidores = ArrayList<ClienteGas>()
                
                for (doc in documents) {
                    val empresa = doc.getString("empresa_nombre") ?: "Distribuidor"
                    val direccion = doc.getString("direccion") ?: ""
                    val email = doc.getString("email") ?: ""
                    val lat = doc.getDouble("lat")
                    val lng = doc.getDouble("lng")
                    
                    // Aquí marcamos explícitamente el rol como distribuidor
                    listaDistribuidores.add(ClienteGas(email, 0, "distribuidor", empresa, direccion)) 
                    
                    // PRIORIDAD: Coordenadas
                    if (lat != null && lng != null && lat != 0.0 && lng != 0.0) {
                        googleMap?.addMarker(MarkerOptions()
                            .position(LatLng(lat, lng))
                            .title(empresa)
                            .snippet(direccion)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))) 
                    }
                    // FALLBACK: Texto
                    else if (direccion.isNotEmpty() && direccion.length > 5) {
                        geocodificarDireccion(direccion) { latLng ->
                            if (latLng != null) {
                                googleMap?.addMarker(MarkerOptions()
                                    .position(latLng)
                                    .title(empresa)
                                    .snippet(direccion)
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))) 
                            }
                        }
                    }
                }
                
                recyclerMapList.adapter = ClientesAdapter(listaDistribuidores) { cliente ->
                    // Usuario hace clic en Distribuidor -> ABRIR CHAT
                    val intent = Intent(this, ChatActivity::class.java)
                    intent.putExtra("email_destino", cliente.email)
                    startActivity(intent)
                }
            }
    }

    private fun cargarDatosAdmin() {
        db.collection("usuarios")
            .whereEqualTo("rol", "user")
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener

                var totalClientes = 0
                var alertasCriticas = 0
                var alertasWarning = 0
                var normal = 0

                for (doc in snapshots) {
                    totalClientes++
                    val nivel = doc.getLong("nivel_gas")?.toInt() ?: 0
                    when {
                        nivel < 10 -> alertasCriticas++
                        nivel < 30 -> alertasWarning++
                        else -> normal++
                    }
                }

                tvTotalClientes.text = totalClientes.toString()
                tvTotalAlertas.text = alertasCriticas.toString()

                actualizarGraficoPastel(normal, alertasWarning, alertasCriticas)
            }
    }

    private fun configurarGraficoAdmin() {
        pieChartAdmin.description.isEnabled = false
        pieChartAdmin.legend.isEnabled = false
        pieChartAdmin.setHoleColor(Color.TRANSPARENT)
        pieChartAdmin.setEntryLabelColor(Color.WHITE)
    }

    private fun actualizarGraficoPastel(normal: Int, warning: Int, critico: Int) {
        val entries = ArrayList<PieEntry>()
        if (normal > 0) entries.add(PieEntry(normal.toFloat(), "Bien"))
        if (warning > 0) entries.add(PieEntry(warning.toFloat(), "Bajo"))
        if (critico > 0) entries.add(PieEntry(critico.toFloat(), "Crítico"))

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = listOf(
            Color.parseColor("#00E676"), 
            Color.parseColor("#FFEA00"), 
            Color.parseColor("#FF1744")
        )
        dataSet.valueTextColor = Color.BLACK
        dataSet.valueTextSize = 14f

        val data = PieData(dataSet)
        pieChartAdmin.data = data
        pieChartAdmin.invalidate()
    }

    private fun cargarDatosDistribuidor(esMapaGlobal: Boolean = false) {
        val targetRecycler = if (esMapaGlobal) recyclerMapList else rvDistribuidor
        targetRecycler.layoutManager = LinearLayoutManager(this)
        
        db.collection("usuarios")
            .whereEqualTo("rol", "user")
            .whereLessThan("nivel_gas", 30)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener
                
                if (esMapaGlobal) googleMap?.clear() 

                val listaPrioridad = ArrayList<ClienteGas>()
                
                for (doc in snapshots) {
                    val email = doc.getString("email") ?: "Sin email"
                    val nivel = doc.getLong("nivel_gas")?.toInt() ?: 0 
                    val direccion = doc.getString("direccion") ?: ""
                    val lat = doc.getDouble("lat")
                    val lng = doc.getDouble("lng")
                    
                    listaPrioridad.add(ClienteGas(email, nivel, "user", "", direccion))
                    
                    if (esMapaGlobal) {
                         val colorMarker = if (nivel < 10) BitmapDescriptorFactory.HUE_RED else BitmapDescriptorFactory.HUE_YELLOW
                         
                         // PRIORIDAD: Coordenadas
                         if (lat != null && lng != null && lat != 0.0 && lng != 0.0) {
                             googleMap?.addMarker(MarkerOptions()
                                .position(LatLng(lat, lng))
                                .title("$email ($nivel%)")
                                .snippet(direccion)
                                .icon(BitmapDescriptorFactory.defaultMarker(colorMarker)))
                         }
                         // FALLBACK: Texto
                         else if (direccion.isNotEmpty() && direccion.length > 5) {
                             geocodificarDireccion(direccion) { latLng ->
                                if (latLng != null) {
                                    googleMap?.addMarker(MarkerOptions()
                                        .position(latLng)
                                        .title("$email ($nivel%)")
                                        .snippet(direccion)
                                        .icon(BitmapDescriptorFactory.defaultMarker(colorMarker)))
                                }
                            }
                         }
                    }
                }
                listaPrioridad.sortBy { it.nivelGas }

                targetRecycler.adapter = ClientesAdapter(listaPrioridad) { cliente ->
                    val intent = Intent(this, DetalleClienteActivity::class.java)
                    intent.putExtra("email_cliente", cliente.email)
                    intent.putExtra("es_admin", true)
                    startActivity(intent)
                }
            }
    }

    private fun configurarGraficoUsuario() {
        chartUser.description.isEnabled = false
        chartUser.setTouchEnabled(true)
        chartUser.setDrawGridBackground(false)
        chartUser.axisRight.isEnabled = false
        chartUser.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chartUser.xAxis.textColor = Color.WHITE
        chartUser.axisLeft.textColor = Color.WHITE
        chartUser.legend.isEnabled = false
    }

    private fun cargarDatosGraficoUsuario(email: String?) {
        if (email == null) return
        
        db.collection("usuarios")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                     doc.reference.collection("historial_mediciones")
                        .orderBy("fecha", Query.Direction.DESCENDING)
                        .limit(10)
                        .get()
                        .addOnSuccessListener { snapshots ->
                            val entries = ArrayList<Entry>()
                            var index = 0f
                            for (shot in snapshots.reversed()) {
                                val nivel = shot.getDouble("nivel")?.toFloat() ?: 0f
                                entries.add(Entry(index, nivel))
                                index++
                            }
                            
                            if (entries.isNotEmpty()) {
                                val dataSet = LineDataSet(entries, "Consumo")
                                dataSet.color = Color.CYAN
                                dataSet.setDrawFilled(true)
                                dataSet.fillColor = Color.CYAN
                                dataSet.fillAlpha = 50
                                dataSet.valueTextColor = Color.WHITE
                                
                                chartUser.data = LineData(dataSet)
                                chartUser.invalidate()
                                calcularEstimacionDias(entries)
                            }
                        }
                }
            }
    }

    private fun calcularEstimacionDias(entries: List<Entry>) {
        if (entries.size < 2) {
            tvDiasRestantes.text = "-- Días"
            return
        }
        val primerNivel = entries.first().y
        val ultimoNivel = entries.last().y
        val perdida = primerNivel - ultimoNivel
        
        if (perdida > 0) {
            val consumoPorPunto = perdida / (entries.size - 1)
            val diasRestantes = (ultimoNivel / consumoPorPunto).toInt()
            tvDiasRestantes.text = "~$diasRestantes Días Estimados"
        } else {
            tvDiasRestantes.text = "> 30 Días"
        }
    }

    private fun conectarMQTT() {
        if (::mqttClient.isInitialized && mqttClient.isConnected) return
        Thread {
            try {
                mqttClient = MqttClient(serverUri, "android-${System.currentTimeMillis()}", null)
                val options = MqttConnectOptions().apply { isCleanSession = true }
                mqttClient.setCallback(object : MqttCallback {
                    override fun connectionLost(cause: Throwable?) { runOnUiThread { tvEstado.text = getString(R.string.estado_desconectado) } }
                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        runOnUiThread { procesarMensaje(message.toString()) }
                    }
                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })
                mqttClient.connect(options)
                if (mqttClient.isConnected) {
                    mqttClient.subscribe(topicoGas, 0)
                    runOnUiThread { tvEstado.text = getString(R.string.estado_conectado) }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }

    private fun procesarMensaje(mensaje: String) {
        try {
            var pesoBruto = 0f
            if (mensaje.trim().startsWith("{")) {
                val json = JSONObject(mensaje)
                pesoBruto = json.optDouble("pesoBrutoKg", 0.0).toFloat()
            } else {
                pesoBruto = mensaje.toFloatOrNull() ?: 0f
            }

            val pesoNeto = max(0f, pesoBruto - pesoTara)
            val pesoMostrar = String.format("%.1f", pesoNeto)
            tvPeso.text = getString(R.string.weight_format, pesoMostrar)
            
            val porcentaje = ((pesoNeto / capacidadBalon) * 100).toInt().coerceIn(0, 100)
            
            // --- ANIMACIÓN DE BARRA DE PROGRESO Y PORCENTAJE ---
            val currentProgress = progressGas.progress
            if (currentProgress != porcentaje) {
                // Animar Barra
                val animator = ObjectAnimator.ofInt(progressGas, "progress", currentProgress, porcentaje)
                animator.duration = 800 // 800ms de animación
                animator.interpolator = DecelerateInterpolator()
                animator.start()
                
                // Animar Texto Numérico (Rolling Number)
                val textAnimator = ValueAnimator.ofInt(currentProgress, porcentaje)
                textAnimator.duration = 800
                textAnimator.addUpdateListener { animation ->
                    tvPorcentaje.text = "${animation.animatedValue}%"
                }
                textAnimator.start()
            }

            val colorState: Int
            val textState: String

            when {
                porcentaje > 30 -> {
                    colorState = Color.parseColor("#00E676") 
                    textState = "Estado: Normal"
                    layoutAlerta.visibility = View.GONE
                    actualizarEstadoAlarma(auth.currentUser?.email, false)
                }
                porcentaje > 10 -> {
                    colorState = Color.parseColor("#FFEA00") 
                    textState = "Estado: Advertencia"
                    layoutAlerta.visibility = View.GONE
                    actualizarEstadoAlarma(auth.currentUser?.email, false)
                }
                else -> {
                    colorState = Color.parseColor("#FF1744") 
                    textState = "Estado: CRÍTICO"
                    layoutAlerta.visibility = View.VISIBLE
                    
                    val ahora = System.currentTimeMillis()
                    if (ahora - ultimoTiempoAlarma > INTERVALO_ALARMA) {
                        activarAlarmaAutomatica()
                        ultimoTiempoAlarma = ahora
                    }
                    lanzarNotificacionAlerta(porcentaje)
                    actualizarEstadoAlarma(auth.currentUser?.email, true)
                }
            }

            tvEstado.text = textState
            tvEstado.setTextColor(colorState)
            progressGas.progressDrawable.setTint(colorState)

            guardarNivelEnNube(auth.currentUser?.email, porcentaje)
            
        } catch (e: Exception) { e.printStackTrace() }
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

    private fun actualizarConfiguracionBalon(kilos: Int) {
        capacidadBalon = kilos
        pesoTara = 0.0f 
    }
    
    private fun actualizarEstadoCompartir(email: String?, compartir: Boolean) {
        if (email == null) return
        db.collection("usuarios").whereEqualTo("email", email).get()
            .addOnSuccessListener { for (doc in it) doc.reference.update("compartir_datos", compartir) }
    }
    
    private fun controlarBuzzerRemoto(encender: Boolean) {
        rtdb.getReference("devices/esp8266-001/actuators/buzzerTest").setValue(encender)
    }
    
    private fun activarAlarmaAutomatica() {
        controlarBuzzerRemoto(true)
        Toast.makeText(this, "⚠️ Alarma Automática", Toast.LENGTH_LONG).show()
        Handler(Looper.getMainLooper()).postDelayed({ controlarBuzzerRemoto(false) }, 8000) 
    }

    private fun crearCanalNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Alerta Gas", NotificationManager.IMPORTANCE_HIGH)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun lanzarNotificacionAlerta(nivelActual: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ ALERTA GAS")
            .setContentText("Nivel crítico: $nivelActual%")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        NotificationManagerCompat.from(this).notify(1, builder.build())
    }

    private fun guardarNivelEnNube(email: String?, nivel: Int) {
        if (email == null) return
        db.collection("usuarios").whereEqualTo("email", email).get()
            .addOnSuccessListener { 
                for (doc in it) {
                    doc.reference.update("nivel_gas", nivel)
                    doc.reference.collection("historial_mediciones").add(mapOf("nivel" to nivel, "fecha" to Timestamp.now()))
                }
            }
    }
    
    private fun actualizarEstadoAlarma(email: String?, activar: Boolean) {
        if (email == null) return
        db.collection("usuarios").whereEqualTo("email", email).get()
            .addOnSuccessListener { for (doc in it) doc.reference.update("alarma_activa", activar) }
    }
}