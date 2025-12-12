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
        
        // 1. Datos Básicos
        // Si es distribuidor y tiene nombre de empresa, mostrar eso en vez del email
        if (cliente.rol == "distribuidor" && cliente.empresa.isNotEmpty()) {
            holder.txtEmail.text = cliente.empresa
            holder.txtInitial.text = cliente.empresa.firstOrNull()?.toString()?.uppercase() ?: "D"
        } else {
            holder.txtEmail.text = cliente.email
            holder.txtInitial.text = cliente.email.firstOrNull()?.toString()?.uppercase() ?: "U"
        }
        
        // 2. Lógica Diferenciada por Rol
        if (cliente.rol == "distribuidor") {
            // Ocultar datos de gas
            holder.txtNivel.visibility = View.GONE
            holder.txtStatus.visibility = View.GONE
        } else {
            // Mostrar datos de gas (Lógica original)
            holder.txtNivel.visibility = View.VISIBLE
            holder.txtStatus.visibility = View.VISIBLE
            holder.txtNivel.text = "${cliente.nivelGas}%"

            when {
                cliente.nivelGas < 10 -> {
                    holder.txtStatus.text = "CRÍTICO"
                    holder.txtStatus.setTextColor(Color.WHITE)
                    holder.txtStatus.background = ContextCompat.getDrawable(context, R.drawable.bg_status_chip_red)
                    holder.txtNivel.setTextColor(Color.parseColor("#FF5252"))
                }
                cliente.nivelGas < 30 -> {
                    holder.txtStatus.text = "ALERTA"
                    holder.txtStatus.setTextColor(Color.BLACK)
                    holder.txtStatus.background = ContextCompat.getDrawable(context, R.drawable.bg_status_chip_yellow)
                    holder.txtNivel.setTextColor(Color.parseColor("#FFD740"))
                }
                else -> {
                    holder.txtStatus.text = "NORMAL"
                    holder.txtStatus.setTextColor(Color.BLACK)
                    holder.txtStatus.background = ContextCompat.getDrawable(context, R.drawable.bg_status_chip_green)
                    holder.txtNivel.setTextColor(Color.parseColor("#69F0AE"))
                }
            }
        }

        holder.itemView.setOnClickListener {
            onClienteClick(cliente)
        }
    }

    override fun getItemCount() = listaClientes.size
}