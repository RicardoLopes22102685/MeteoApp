package com.example.meteo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

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

        val spinnerStations = findViewById<Spinner>(R.id.spinnerStationSelect)
        val btnOpenGraph = findViewById<Button>(R.id.btnGoToGraph)

        // Configurar o menu das estações
        val stations = arrayOf("STATION_00", "STATION_01")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, stations)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerStations.adapter = adapter

        btnOpenGraph.setOnClickListener {
            // 1. Ler o que está escrito no Spinner AGORA
            val selectedStation = spinnerStations.selectedItem.toString()

            Log.i("MainActivity", "O utilizador escolheu: $selectedStation") // Log para confirmar

            val intent = Intent(this, GraphActivity::class.java)
            intent.putExtra("STATION_ID", selectedStation)
            startActivity(intent)
        }
    }
}