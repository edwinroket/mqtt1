package com.example.mqtt1

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.eclipse.paho.client.mqttv3.*
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var mqttClient: MqttClient
    private val serverUri = "tcp://test.mosquitto.org:1883"

    private lateinit var edtTopico: EditText
    private lateinit var btnAgregar: Button
    private lateinit var tablaTopicos: LinearLayout

    // Mapa para actualizar fácilmente la fila de cada tópico
    private val filasTopicos = mutableMapOf<String, TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        edtTopico = findViewById(R.id.edtTopico)
        btnAgregar = findViewById(R.id.btnAgregar)
        tablaTopicos = findViewById(R.id.tablaTopicos)

        conectarMQTT()

        btnAgregar.setOnClickListener {
            val topico = edtTopico.text.toString().trim()
            if (topico.isNotEmpty()) {
                agregarTopico(topico)
            }
        }
    }

    // Conexion al broker MQTT
    private fun conectarMQTT() {
        Thread {
            try {
                mqttClient = MqttClient(serverUri, "android-${System.currentTimeMillis()}", null)

                val options = MqttConnectOptions().apply {
                    isCleanSession = true
                }

                mqttClient.setCallback(object : MqttCallback {

                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        val msg = message.toString()

                        runOnUiThread {
                            actualizarFila(topic!!, msg)
                        }
                    }

                    override fun connectionLost(cause: Throwable?) {}

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })

                mqttClient.connect(options)

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    // AGREGAR TOPICOS DINAMICO
    private fun agregarTopico(topico: String) {

        // Crear fila visual
        val textView = TextView(this).apply {
            text = "$topico → esperando datos…"
            textSize = 16f
            setPadding(8, 8, 8, 8)
        }

        tablaTopicos.addView(textView)
        filasTopicos[topico] = textView

        // Suscribirse al tópico
        Thread {
            try {
                mqttClient.subscribe(topico, 0)
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error al suscribir: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    // ACTUALIZAR FILA AL RECIBIR MENSAJE
    private fun actualizarFila(topico: String, mensaje: String) {
        val fila = filasTopicos[topico]
        fila?.text = "$topico → $mensaje"
    }
}
