package com.example.mqtt1

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class ClientesAdapter(
    private val listaClientes: List<ClienteGas>,
    private val onClienteClick: (ClienteGas) -> Unit
) : RecyclerView.Adapter<ClientesAdapter.ClienteViewHolder>() {

    class ClienteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Elementos del nuevo layout item_cliente_gas.xml
        val txtEmail: TextView = view.findViewById(R.id.txtClienteEmail)
        val txtNivel: TextView = view.findViewById(R.id.txtNivelGas)
        val txtStatus: TextView = view.findViewById(R.id.statusChip)
        val txtInitial: TextView = view.findViewById(R.id.txtInitial)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClienteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cliente_gas, parent, false)
        return ClienteViewHolder(view)
    }

    override fun onBindViewHolder(holder: ClienteViewHolder, position: Int) {
        val cliente = listaClientes[position]
        val context = holder.itemView.context
        
        // 1. Email y Avatar
        holder.txtEmail.text = cliente.email
        holder.txtInitial.text = cliente.email.firstOrNull()?.toString()?.uppercase() ?: "U"
        
        // 2. Nivel
        holder.txtNivel.text = "${cliente.nivelGas}%"

        // 3. Lógica de Estado y Colores
        when {
            cliente.nivelGas < 10 -> {
                // CRÍTICO
                holder.txtStatus.text = "CRÍTICO"
                holder.txtStatus.setTextColor(Color.WHITE)
                holder.txtStatus.background = ContextCompat.getDrawable(context, R.drawable.bg_status_chip_red) // Necesitas crear este drawable o usar color directo
                holder.txtNivel.setTextColor(Color.parseColor("#FF5252")) // Rojo Neón
            }
            cliente.nivelGas < 30 -> {
                // ALERTA
                holder.txtStatus.text = "ALERTA"
                holder.txtStatus.setTextColor(Color.BLACK)
                holder.txtStatus.background = ContextCompat.getDrawable(context, R.drawable.bg_status_chip_yellow)
                holder.txtNivel.setTextColor(Color.parseColor("#FFD740")) // Amarillo Neón
            }
            else -> {
                // NORMAL
                holder.txtStatus.text = "NORMAL"
                holder.txtStatus.setTextColor(Color.BLACK)
                holder.txtStatus.background = ContextCompat.getDrawable(context, R.drawable.bg_status_chip_green)
                holder.txtNivel.setTextColor(Color.parseColor("#69F0AE")) // Verde Neón
            }
        }

        holder.itemView.setOnClickListener {
            onClienteClick(cliente)
        }
    }

    override fun getItemCount() = listaClientes.size
}