package com.example.meteo

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val db = FirebaseFirestore.getInstance()
        db.collection("STATION_01")
            .get()
            .addOnSuccessListener { result ->
                for (document in result) {

                    val idTimestamp = document.id      // Nome do documento
                    val temperature = document.getDouble("temperatura")
                    val humidity = document.getDouble("humidade")
                    val timestamp = document.getLong("timestamp")

                    Log.d("Firestore", "Doc: $idTimestamp -> temp=$temperature hum=$humidity ts=$timestamp")
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Erro ao ler dados", e)
            }

    }
}