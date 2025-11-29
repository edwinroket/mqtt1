package com.example.mqtt1

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class ClientesAdapter(
    private val listaClientes: List<ClienteGas>,
    private val onClienteClick: (ClienteGas) -> Unit
) : RecyclerView.Adapter<ClientesAdapter.ClienteViewHolder>() {

    class ClienteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtEmail: TextView = view.findViewById(R.id.txtEmailCliente)
        val txtNivel: TextView = view.findViewById(R.id.txtNivelGasCliente)
        val txtEstado: TextView = view.findViewById(R.id.txtEstadoAlerta)
        val cardView: MaterialCardView = view.findViewById(R.id.cardCliente)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClienteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cliente_gas, parent, false)
        return ClienteViewHolder(view)
    }

    override fun onBindViewHolder(holder: ClienteViewHolder, position: Int) {
        val cliente = listaClientes[position]
        val context = holder.itemView.context
        
        holder.txtEmail.text = cliente.email
        holder.txtNivel.text = "Nivel de Gas: ${cliente.nivelGas}%"

        // Colores modernos (tomados de colors.xml)
        val colorCritico = ContextCompat.getColor(context, R.color.danger_red)
        val colorAlerta = ContextCompat.getColor(context, R.color.status_warning)
        val colorOptimo = ContextCompat.getColor(context, R.color.neon_green)
        val colorTextPrimary = ContextCompat.getColor(context, R.color.bg_app_start) // Texto oscuro para contrastar con alerta

        // LÓGICA DE SEMÁFORO (Modernizada)
        when {
            cliente.nivelGas < 10 -> {
                // ROJO - CRÍTICO
                holder.cardView.strokeColor = colorCritico
                holder.cardView.strokeWidth = 4 // Más grueso para destacar
                holder.txtEstado.text = "CRÍTICO"
                holder.txtEstado.setTextColor(colorCritico)
                // Badge sutil
                holder.txtEstado.backgroundTintList = ColorStateList.valueOf(Color.argb(30, 248, 113, 113)) 
            }
            cliente.nivelGas < 30 -> {
                // AMARILLO - ALERTA
                holder.cardView.strokeColor = colorAlerta
                holder.cardView.strokeWidth = 2
                holder.txtEstado.text = "ALERTA"
                holder.txtEstado.setTextColor(colorAlerta)
                holder.txtEstado.backgroundTintList = ColorStateList.valueOf(Color.argb(30, 251, 191, 36))
            }
            else -> {
                // VERDE - NORMAL
                holder.cardView.strokeColor = Color.parseColor("#26FFFFFF") // Borde sutil glass
                holder.cardView.strokeWidth = 1
                holder.txtEstado.text = "NORMAL"
                holder.txtEstado.setTextColor(colorOptimo)
                holder.txtEstado.backgroundTintList = ColorStateList.valueOf(Color.argb(20, 74, 222, 128))
            }
        }

        holder.itemView.setOnClickListener {
            onClienteClick(cliente)
        }
    }

    override fun getItemCount() = listaClientes.size
}