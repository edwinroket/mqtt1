package com.example.mqtt1

import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import java.util.Locale

class MapPickerActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private lateinit var txtAddressResult: TextView
    private lateinit var btnConfirmLocation: Button
    private lateinit var edtSearchMap: EditText
    private lateinit var btnSearchMap: ImageView
    
    private var selectedLocation: LatLng? = null
    private var selectedAddress: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_picker)

        txtAddressResult = findViewById(R.id.txtAddressResult)
        btnConfirmLocation = findViewById(R.id.btnConfirmLocation)
        edtSearchMap = findViewById(R.id.edtSearchMap)
        btnSearchMap = findViewById(R.id.btnSearchMap)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragmentPicker) as SupportMapFragment
        mapFragment.getMapAsync(this)

        btnConfirmLocation.setOnClickListener {
            if (selectedAddress.isNotEmpty()) {
                val resultIntent = Intent()
                resultIntent.putExtra("address", selectedAddress)
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        }
        
        // Listener para el botón de lupa
        btnSearchMap.setOnClickListener {
            buscarUbicacion()
        }
        
        // Listener para tecla "Enter/Buscar" en el teclado
        edtSearchMap.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                buscarUbicacion()
                true
            } else {
                false
            }
        }
    }

    private fun buscarUbicacion() {
        val query = edtSearchMap.text.toString()
        if (query.isBlank()) return
        
        // Añadir contexto local para mejorar búsqueda (ej: ", Talca, Chile")
        // O dejar abierto para búsqueda global
        val searchQuery = "$query, Chile" 
        
        Thread {
            try {
                val geocoder = Geocoder(this, Locale.getDefault())
                val addresses = geocoder.getFromLocationName(searchQuery, 1)
                
                runOnUiThread {
                    if (!addresses.isNullOrEmpty()) {
                        val location = addresses[0]
                        val latLng = LatLng(location.latitude, location.longitude)
                        
                        // Mover la cámara al resultado
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                        
                        // Cerrar teclado (opcional pero recomendado)
                        // val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        // imm.hideSoftInputFromWindow(edtSearchMap.windowToken, 0)
                        
                    } else {
                        Toast.makeText(this, "Ubicación no encontrada", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Error en búsqueda", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        
        // Configuración inicial (Talca por defecto)
        val startLocation = LatLng(-35.4264, -71.6554)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(startLocation, 15f))

        // Escuchar movimiento de la cámara (PIN FIJO)
        googleMap.setOnCameraIdleListener {
            val center = googleMap.cameraPosition.target
            selectedLocation = center
            getAddressFromLatLng(center)
        }
    }

    private fun getAddressFromLatLng(latLng: LatLng) {
        txtAddressResult.text = "Buscando dirección..."
        btnConfirmLocation.isEnabled = false
        
        Thread {
            try {
                val geocoder = Geocoder(this, Locale.getDefault())
                val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                
                runOnUiThread {
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        // Priorizar calle y número
                        val sb = StringBuilder()
                        if (address.thoroughfare != null) sb.append(address.thoroughfare).append(" ")
                        if (address.subThoroughfare != null) sb.append(address.subThoroughfare)
                        
                        if (sb.isEmpty()) {
                            if (address.featureName != null) sb.append(address.featureName)
                            else sb.append("Ubicación Manual")
                        }

                        selectedAddress = sb.toString()
                        txtAddressResult.text = selectedAddress
                        btnConfirmLocation.isEnabled = true
                    } else {
                        txtAddressResult.text = "Ubicación desconocida"
                        btnConfirmLocation.isEnabled = true 
                        selectedAddress = "Ubicación Manual (${String.format("%.4f", latLng.latitude)}, ${String.format("%.4f", latLng.longitude)})"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    txtAddressResult.text = "Dirección Manual"
                    btnConfirmLocation.isEnabled = true
                    selectedAddress = "Manual"
                }
            }
        }.start()
    }
}