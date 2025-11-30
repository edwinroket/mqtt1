package com.example.mqtt1

import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
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
    
    private var selectedLocation: LatLng? = null
    private var selectedAddress: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_picker)

        txtAddressResult = findViewById(R.id.txtAddressResult)
        btnConfirmLocation = findViewById(R.id.btnConfirmLocation)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragmentPicker) as SupportMapFragment
        mapFragment.getMapAsync(this)

        btnConfirmLocation.setOnClickListener {
            if (selectedAddress.isNotEmpty()) {
                val resultIntent = Intent()
                resultIntent.putExtra("address", selectedAddress)
                // Opcional: Devolver también lat/lng
                // resultIntent.putExtra("lat", selectedLocation?.latitude)
                // resultIntent.putExtra("lng", selectedLocation?.longitude)
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        
        // Configuración inicial (Talca por defecto)
        val startLocation = LatLng(-35.4264, -71.6554)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(startLocation, 15f))

        // Escuchar movimiento de la cámara
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
                        // Construir dirección legible (Calle + Altura)
                        val sb = StringBuilder()
                        if (address.thoroughfare != null) sb.append(address.thoroughfare).append(" ")
                        if (address.subThoroughfare != null) sb.append(address.subThoroughfare)
                        
                        // Si no hay calle exacta, usar nombre del lugar o localidad
                        if (sb.isEmpty()) {
                            if (address.featureName != null) sb.append(address.featureName)
                            else sb.append("Ubicación seleccionada")
                        }

                        selectedAddress = sb.toString()
                        txtAddressResult.text = selectedAddress
                        btnConfirmLocation.isEnabled = true
                    } else {
                        txtAddressResult.text = "Ubicación desconocida"
                        btnConfirmLocation.isEnabled = true // Permitir confirmar aunque sea desconocida (usará coords en el futuro)
                        selectedAddress = "Ubicación Manual"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    txtAddressResult.text = "Error al obtener dirección"
                    btnConfirmLocation.isEnabled = true
                    selectedAddress = "Ubicación Manual"
                }
            }
        }.start()
    }
}