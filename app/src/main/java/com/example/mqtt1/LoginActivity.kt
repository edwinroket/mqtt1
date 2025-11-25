package com.example.mqtt1

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var edtEmail: EditText
    private lateinit var edtPass: EditText
    private lateinit var btnLogin: Button
    private lateinit var txtRegister: TextView
    
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        // Verificar si ya hay usuario logueado
        if (auth.currentUser != null) {
            navegarAlDashboard()
        }

        edtEmail = findViewById(R.id.edtEmail)
        edtPass = findViewById(R.id.edtPassword)
        btnLogin = findViewById(R.id.btnLogin)
        txtRegister = findViewById(R.id.txtRegister)

        // Botón de Login
        btnLogin.setOnClickListener {
            val email = edtEmail.text.toString().trim()
            val password = edtPass.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                realizarLogin(email, password)
            } else {
                Toast.makeText(this, "Por favor completa los campos", Toast.LENGTH_SHORT).show()
            }
        }

        // Botón para ir al Registro
        txtRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    private fun realizarLogin(email: String, pass: String) {
        btnLogin.isEnabled = false
        btnLogin.text = "Verificando..."

        auth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener(this) { task ->
                btnLogin.isEnabled = true
                btnLogin.text = "INGRESAR"
                
                if (task.isSuccessful) {
                    navegarAlDashboard()
                } else {
                    Toast.makeText(baseContext, "Error: ${task.exception?.message}", 
                        Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun navegarAlDashboard() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}