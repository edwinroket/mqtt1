package com.example.mqtt1

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Date

class ChatActivity : AppCompatActivity() {

    private lateinit var recyclerChat: RecyclerView
    private lateinit var edtMessage: EditText
    private lateinit var btnSend: ImageView
    private lateinit var btnBack: ImageView
    private lateinit var txtTitle: TextView

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var emailDestino: String = ""
    private var chatId: String = ""
    private var miEmail: String = ""
    private val listaMensajes = ArrayList<MensajeChat>()
    private lateinit var adapter: MensajeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        miEmail = auth.currentUser?.email ?: ""

        // Recibir datos
        emailDestino = intent.getStringExtra("email_destino") ?: ""
        
        if (emailDestino.isEmpty()) {
            Toast.makeText(this, "Error: Usuario no válido", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Generar ID único para el chat (orden alfabético para que sea el mismo para ambos usuarios)
        val usuarios = listOf(miEmail, emailDestino).sorted()
        chatId = "${usuarios[0]}_${usuarios[1]}".replace(".", "_").replace("@", "_")

        // UI
        recyclerChat = findViewById(R.id.recyclerChat)
        edtMessage = findViewById(R.id.edtMessage)
        btnSend = findViewById(R.id.btnSend)
        btnBack = findViewById(R.id.btnBackChat)
        txtTitle = findViewById(R.id.txtChatTitle)

        txtTitle.text = emailDestino

        // Configurar Recycler
        adapter = MensajeAdapter(listaMensajes, miEmail)
        recyclerChat.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recyclerChat.adapter = adapter

        btnBack.setOnClickListener { finish() }

        btnSend.setOnClickListener {
            enviarMensaje()
        }

        escucharMensajes()
    }

    private fun enviarMensaje() {
        val texto = edtMessage.text.toString().trim()
        if (texto.isEmpty()) return

        val mensaje = hashMapOf(
            "remitente" to miEmail,
            "texto" to texto,
            "fecha" to Date()
        )

        db.collection("chats").document(chatId)
            .collection("mensajes")
            .add(mensaje)
            .addOnSuccessListener {
                edtMessage.setText("")
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al enviar", Toast.LENGTH_SHORT).show()
            }
    }

    private fun escucharMensajes() {
        db.collection("chats").document(chatId)
            .collection("mensajes")
            .orderBy("fecha", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener

                if (snapshots != null) {
                    listaMensajes.clear()
                    for (doc in snapshots) {
                        val remitente = doc.getString("remitente") ?: ""
                        val texto = doc.getString("texto") ?: ""
                        listaMensajes.add(MensajeChat(remitente, texto))
                    }
                    adapter.notifyDataSetChanged()
                    recyclerChat.scrollToPosition(listaMensajes.size - 1)
                }
            }
    }
}

// Clases auxiliares para el Chat
data class MensajeChat(val remitente: String, val texto: String)

class MensajeAdapter(private val mensajes: List<MensajeChat>, private val miEmail: String) : 
    RecyclerView.Adapter<MensajeAdapter.MensajeViewHolder>() {

    class MensajeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtMsg: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MensajeViewHolder {
        // Usamos layouts simples de Android por ahora para simplificar
        val layout = if (viewType == 1) android.R.layout.simple_list_item_1 else android.R.layout.simple_list_item_1
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return MensajeViewHolder(view)
    }

    override fun onBindViewHolder(holder: MensajeViewHolder, position: Int) {
        val msg = mensajes[position]
        holder.txtMsg.text = msg.texto // No mostrar el email en cada mensaje
        
        // Estilo básico: alinear a la derecha si es mío, a la izquierda si es del otro
        if (msg.remitente == miEmail) {
            holder.txtMsg.setTextColor(Color.CYAN)
            holder.txtMsg.textAlignment = View.TEXT_ALIGNMENT_TEXT_END
        } else {
            holder.txtMsg.setTextColor(Color.WHITE)
            holder.txtMsg.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
        }
    }
    
    override fun getItemViewType(position: Int): Int {
        return if (mensajes[position].remitente == miEmail) 1 else 0 // 1 = Enviado, 0 = Recibido
    }

    override fun getItemCount() = mensajes.size
}