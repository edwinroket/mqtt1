package com.example.mqtt1

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class ClientesAdapter(
    private val listaClientes: List<ClienteGas>,
    private val onClienteClick: (ClienteGas) -> Unit // Nuevo parámetro: Acción al hacer click
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
        
        holder.txtEmail.text = cliente.email
        holder.txtNivel.text = "Nivel de Gas: ${cliente.nivelGas}%"

        // LÓGICA DE SEMÁFORO
        when {
            cliente.nivelGas < 10 -> {
                holder.cardView.strokeColor = Color.parseColor("#D32F2F") // Rojo
                holder.txtEstado.text = "CRÍTICO"
                holder.txtEstado.setTextColor(Color.WHITE)
                holder.txtEstado.setBackgroundColor(Color.parseColor("#D32F2F"))
            }
            cliente.nivelGas < 30 -> {
                holder.cardView.strokeColor = Color.parseColor("#FBC02D") // Amarillo
                holder.txtEstado.text = "ALERTA"
                holder.txtEstado.setTextColor(Color.BLACK)
                holder.txtEstado.setBackgroundColor(Color.parseColor("#FFEB3B"))
            }
            else -> {
                holder.cardView.strokeColor = Color.parseColor("#388E3C") // Verde
                holder.txtEstado.text = "ÓPTIMO"
                holder.txtEstado.setTextColor(Color.WHITE)
                holder.txtEstado.setBackgroundColor(Color.parseColor("#4CAF50"))
            }
        }

        // Evento Click en toda la tarjeta
        holder.itemView.setOnClickListener {
            onClienteClick(cliente)
        }
    }

    override fun getItemCount() = listaClientes.size
}