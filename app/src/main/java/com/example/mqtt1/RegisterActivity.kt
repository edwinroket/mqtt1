package com.example.mqtt1

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class RegisterActivity : AppCompatActivity() {

    private lateinit var edtEmail: EditText
    private lateinit var edtPass: EditText
    private lateinit var edtPassConf: EditText
    private lateinit var btnRegister: Button
    private lateinit var txtBackLogin: TextView
    
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()

        edtEmail = findViewById(R.id.edtEmailReg)
        edtPass = findViewById(R.id.edtPassReg)
        edtPassConf = findViewById(R.id.edtPassConfReg)
        btnRegister = findViewById(R.id.btnRegister)
        txtBackLogin = findViewById(R.id.txtBackLogin)

        btnRegister.setOnClickListener {
            val email = edtEmail.text.toString().trim()
            val pass = edtPass.text.toString().trim()
            val passConf = edtPassConf.text.toString().trim()

            if (email.isEmpty() || pass.isEmpty() || passConf.isEmpty()) {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (pass != passConf) {
                Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (pass.length < 6) {
                Toast.makeText(this, "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            crearUsuario(email, pass)
        }

        txtBackLogin.setOnClickListener {
            finish() // Vuelve a la actividad anterior (Login)
        }
    }

    private fun crearUsuario(email: String, pass: String) {
        btnRegister.isEnabled = false
        btnRegister.text = "Registrando..."

        auth.createUserWithEmailAndPassword(email, pass)
            .addOnCompleteListener(this) { task ->
                btnRegister.isEnabled = true
                btnRegister.text = "REGISTRARME"
                
                if (task.isSuccessful) {
                    Toast.makeText(this, "¡Cuenta creada con éxito!", Toast.LENGTH_SHORT).show()
                    // Al crear cuenta, Firebase hace login automático. Vamos al Dashboard.
                    val intent = Intent(this, MainActivity::class.java)
                    // Limpiamos la pila para que no pueda volver atrás al registro/login
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }
}