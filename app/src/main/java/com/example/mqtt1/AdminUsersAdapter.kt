package com.example.mqtt1

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AdminUsersAdapter(
    private val userList: List<AdminUser>,
    private val onEdit: (AdminUser) -> Unit,
    private val onDelete: (AdminUser) -> Unit
) : RecyclerView.Adapter<AdminUsersAdapter.AdminUserViewHolder>() {

    class AdminUserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtEmail: TextView = view.findViewById(R.id.txtUserEmail)
        val txtRole: TextView = view.findViewById(R.id.txtUserRole)
        val btnEdit: ImageView = view.findViewById(R.id.btnEditUser)
        val btnDelete: ImageView = view.findViewById(R.id.btnDeleteUser)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdminUserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_user, parent, false)
        return AdminUserViewHolder(view)
    }

    override fun onBindViewHolder(holder: AdminUserViewHolder, position: Int) {
        val user = userList[position]
        holder.txtEmail.text = user.email
        holder.txtRole.text = user.rol.uppercase()

        holder.btnEdit.setOnClickListener { onEdit(user) }
        holder.btnDelete.setOnClickListener { onDelete(user) }
    }

    override fun getItemCount() = userList.size
}