package com.example.meteo

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

enum class DataType {
    TEMPERATURA, HUMIDADE, PARTICULAS
}

class GraphActivity : AppCompatActivity() {

    private lateinit var lineChart: LineChart
    private lateinit var btnDate: Button
    private lateinit var tvTitle: TextView
    private lateinit var statsContainer: LinearLayout

    // Stat card views
    private lateinit var tvMaximaValue: TextView
    private lateinit var tvMinimaValue: TextView
    private lateinit var tvMediaValue: TextView
    private lateinit var tvAmplitudeValue: TextView
    private lateinit var tvTendenciaValue: TextView
    private lateinit var tvRegistosValue: TextView

    // Data type switches
    private lateinit var switchTemperatura: SwitchCompat
    private lateinit var switchHumidade: SwitchCompat
    private lateinit var switchParticulas: SwitchCompat

    private val chartDataList = ArrayList<Entry>()
    private val rawTempList = ArrayList<Float>()
    private val rawHumidadeList = ArrayList<Float>()
    private val rawParticulasList = ArrayList<Float>()

    // Maps to store timestamp and grouped data for each type
    private val tempGroupedData = mutableMapOf<Long, MutableList<Float>>()
    private val humidadeGroupedData = mutableMapOf<Long, MutableList<Float>>()
    private val particulasGroupedData = mutableMapOf<Long, MutableList<Float>>()

    private var currentStationId: String = "STATION_00"
    private val selectedCalendar = Calendar.getInstance()
    private var selectedDataType: DataType = DataType.TEMPERATURA

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_graph)

        currentStationId = intent.getStringExtra("STATION_ID") ?: "STATION_00"

        lineChart = findViewById(R.id.lineChart)
        btnDate = findViewById(R.id.btnSelectDate)
        tvTitle = findViewById(R.id.tvStationTitle)
        statsContainer = findViewById(R.id.statsContainer)
        tvMaximaValue = findViewById(R.id.tvMaximaValue)
        tvMinimaValue = findViewById(R.id.tvMinimaValue)
        tvMediaValue = findViewById(R.id.tvMediaValue)
        tvAmplitudeValue = findViewById(R.id.tvAmplitudeValue)
        tvTendenciaValue = findViewById(R.id.tvTendenciaValue)
        tvRegistosValue = findViewById(R.id.tvRegistosValue)

        // Initialize switches
        switchTemperatura = findViewById(R.id.switchTemperatura)
        switchHumidade = findViewById(R.id.switchHumidade)
        switchParticulas = findViewById(R.id.switchParticulas)

        tvTitle.text = currentStationId
        setupChartProperties()
        setupDatePicker()
        setupDataTypeSwitches()
        setupBackButton()
        loadLatestDataDate() //Verifica qual é a última data disponível
    }

    // Descobre o último dia com dados
    private fun loadLatestDataDate() {
        val db = FirebaseFirestore.getInstance()
        lineChart.setNoDataText("A procurar registos...")

        // Pede apenas o registo mais recente (Ordenado por timestamp DESC, limite 1)
        db.collection(currentStationId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    // Encontrou dados!
                    val lastDoc = result.documents[0]
                    val lastTs = lastDoc.getLong("timestamp")

                    if (lastTs != null) {
                        // Atualiza o calendário para o dia desse registo
                        selectedCalendar.timeInMillis = lastTs * 1000
                        Log.i("GraphActivity", "Último dado encontrado em: ${selectedCalendar.time}")
                    }
                } else {
                    Log.w("GraphActivity", "Estação vazia. A usar data de hoje.")
                    Toast.makeText(this, "Esta estação ainda não tem dados.", Toast.LENGTH_LONG).show()
                }

                // Quer tenha encontrado ou não, carrega o gráfico para a data definida
                updateDateButtonText()
                fetchDataForSelectedDate()
            }
            .addOnFailureListener { e ->
                Log.e("GraphActivity", "Erro ao procurar data", e)
                // Se falhar (ex: sem internet), tenta carregar com a data de hoje
                updateDateButtonText()
                fetchDataForSelectedDate()
            }
    }

    private fun setupDatePicker() {
        btnDate.setOnClickListener {
            val year = selectedCalendar.get(Calendar.YEAR)
            val month = selectedCalendar.get(Calendar.MONTH)
            val day = selectedCalendar.get(Calendar.DAY_OF_MONTH)

            val dpd = DatePickerDialog(this, { _, y, m, d ->
                selectedCalendar.set(y, m, d)
                updateDateButtonText()
                fetchDataForSelectedDate()
            }, year, month, day)
            dpd.show()
        }
    }

    private fun setupDataTypeSwitches() {
        // Set initial state - only temperatura is checked
        switchTemperatura.isChecked = true
        switchHumidade.isChecked = false
        switchParticulas.isChecked = false

        switchTemperatura.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                switchHumidade.isChecked = false
                switchParticulas.isChecked = false
                selectedDataType = DataType.TEMPERATURA
                repopulateChartDataList()
                updateChartDisplay()
                calculateAndShowStats()
            }
        }

        switchHumidade.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                switchTemperatura.isChecked = false
                switchParticulas.isChecked = false
                selectedDataType = DataType.HUMIDADE
                repopulateChartDataList()
                updateChartDisplay()
                calculateAndShowStats()
            }
        }

        switchParticulas.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                switchTemperatura.isChecked = false
                switchHumidade.isChecked = false
                selectedDataType = DataType.PARTICULAS
                repopulateChartDataList()
                updateChartDisplay()
                calculateAndShowStats()
            }
        }
    }

    private fun setupBackButton() {
        val btnBack = findViewById<Button>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun repopulateChartDataList() {
        chartDataList.clear()

        when (selectedDataType) {
            DataType.TEMPERATURA -> {
                for ((blockTs, temps) in tempGroupedData) {
                    chartDataList.add(Entry(blockTs.toFloat(), temps.average().toFloat()))
                }
            }
            DataType.HUMIDADE -> {
                for ((blockTs, humidades) in humidadeGroupedData) {
                    chartDataList.add(Entry(blockTs.toFloat(), humidades.average().toFloat()))
                }
            }
            DataType.PARTICULAS -> {
                for ((blockTs, particulas) in particulasGroupedData) {
                    chartDataList.add(Entry(blockTs.toFloat(), particulas.average().toFloat()))
                }
            }
        }

        chartDataList.sortBy { it.x }
    }

    private fun updateDateButtonText() {
        val format = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        btnDate.text = format.format(selectedCalendar.time)
    }

    private fun fetchDataForSelectedDate() {
        val year = selectedCalendar.get(Calendar.YEAR)
        val month = selectedCalendar.get(Calendar.MONTH)
        val day = selectedCalendar.get(Calendar.DAY_OF_MONTH)

        val calStart = Calendar.getInstance()
        calStart.set(year, month, day, 0, 0, 0)
        val startTs = calStart.timeInMillis / 1000

        val calEnd = Calendar.getInstance()
        calEnd.set(year, month, day, 23, 59, 59)
        val endTs = calEnd.timeInMillis / 1000

        lineChart.clear()
        lineChart.setNoDataText("A carregar...")

        val db = FirebaseFirestore.getInstance()

        db.collection(currentStationId)
            .whereGreaterThanOrEqualTo("timestamp", startTs)
            .whereLessThanOrEqualTo("timestamp", endTs)
            .get()
            .addOnSuccessListener { result ->
                chartDataList.clear()
                rawTempList.clear()
                rawHumidadeList.clear()
                rawParticulasList.clear()
                tempGroupedData.clear()
                humidadeGroupedData.clear()
                particulasGroupedData.clear()

                if (result.isEmpty) {
                    lineChart.setNoDataText("Sem dados neste dia.")
                    lineChart.invalidate()
                    return@addOnSuccessListener
                }

                val groupedData = mutableMapOf<Long, MutableList<Float>>()
                val groupedDataHumidade = mutableMapOf<Long, MutableList<Float>>()
                val groupedDataParticulas = mutableMapOf<Long, MutableList<Float>>()

                for (document in result) {
                    val temp = document.getDouble("temperatura")
                    val humidade = document.getDouble("humidade")
                    val particulas = document.getDouble("particulas")
                    val ts = document.getLong("timestamp")

                    if (ts != null) {
                        // Process temperatura
                        if (temp != null) {
                            rawTempList.add(temp.toFloat())
                            // Média de 5 minutos
                            val fiveMinBlockTs = (ts / 300) * 300
                            if (!groupedData.containsKey(fiveMinBlockTs)) {
                                groupedData[fiveMinBlockTs] = mutableListOf()
                            }
                            groupedData[fiveMinBlockTs]?.add(temp.toFloat())
                        }

                        // Process humidade
                        if (humidade != null) {
                            rawHumidadeList.add(humidade.toFloat())
                            val fiveMinBlockTs = (ts / 300) * 300
                            if (!groupedDataHumidade.containsKey(fiveMinBlockTs)) {
                                groupedDataHumidade[fiveMinBlockTs] = mutableListOf()
                            }
                            groupedDataHumidade[fiveMinBlockTs]?.add(humidade.toFloat())
                        }

                        // Process particulas
                        if (particulas != null) {
                            rawParticulasList.add(particulas.toFloat())
                            val fiveMinBlockTs = (ts / 300) * 300
                            if (!groupedDataParticulas.containsKey(fiveMinBlockTs)) {
                                groupedDataParticulas[fiveMinBlockTs] = mutableListOf()
                            }
                            groupedDataParticulas[fiveMinBlockTs]?.add(particulas.toFloat())
                        }
                    }
                }

                // Store grouped data in member variables for switching data types later
                tempGroupedData.putAll(groupedData)
                humidadeGroupedData.putAll(groupedDataHumidade)
                particulasGroupedData.putAll(groupedDataParticulas)

                // Populate chartDataList based on selected data type
                when (selectedDataType) {
                    DataType.TEMPERATURA -> {
                        for ((blockTs, temps) in groupedData) {
                            chartDataList.add(Entry(blockTs.toFloat(), temps.average().toFloat()))
                        }
                    }
                    DataType.HUMIDADE -> {
                        for ((blockTs, humidades) in groupedDataHumidade) {
                            chartDataList.add(Entry(blockTs.toFloat(), humidades.average().toFloat()))
                        }
                    }
                    DataType.PARTICULAS -> {
                        for ((blockTs, particulas) in groupedDataParticulas) {
                            chartDataList.add(Entry(blockTs.toFloat(), particulas.average().toFloat()))
                        }
                    }
                }

                chartDataList.sortBy { it.x }

                updateChartDisplay()
                calculateAndShowStats()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Erro de ligação", Toast.LENGTH_SHORT).show()
            }
    }

    private fun calculateAndShowStats() {
        // Select the appropriate data list based on selected data type
        val dataList = when (selectedDataType) {
            DataType.TEMPERATURA -> rawTempList
            DataType.HUMIDADE -> rawHumidadeList
            DataType.PARTICULAS -> rawParticulasList
        }

        if (dataList.isEmpty()) return

        val max = dataList.maxOrNull() ?: 0f
        val min = dataList.minOrNull() ?: 0f
        val avg = dataList.average()
        val amplitude = max - min

        // Calculate trend based on chronologically ordered data
        val groupedData = when (selectedDataType) {
            DataType.TEMPERATURA -> tempGroupedData
            DataType.HUMIDADE -> humidadeGroupedData
            DataType.PARTICULAS -> particulasGroupedData
        }

        val sortedTimestamps = groupedData.keys.sorted()
        val trend = if (sortedTimestamps.isNotEmpty()) {
            val firstBlockTs = sortedTimestamps.first()
            val lastBlockTs = sortedTimestamps.last()
            val firstValue = groupedData[firstBlockTs]?.average() ?: 0.0
            val lastValue = groupedData[lastBlockTs]?.average() ?: 0.0
            val difference = lastValue - firstValue

            if (lastValue > firstValue) "⬆ Subida (+${"%.1f".format(difference)})"
            else if (lastValue < firstValue) "⬇ Descida (${"%.1f".format(difference)})"
            else "➡ Estável"
        } else {
            "➡ Estável"
        }

        // Get the unit suffix based on data type
        val unitSuffix = when (selectedDataType) {
            DataType.TEMPERATURA -> " °C"
            DataType.HUMIDADE -> " %"
            DataType.PARTICULAS -> " µg/m³"
        }

        // Update stat card values
        tvMaximaValue.text = "${"%.1f".format(max)}$unitSuffix"
        tvMinimaValue.text = "${"%.1f".format(min)}$unitSuffix"
        tvMediaValue.text = "${"%.2f".format(avg)}$unitSuffix"
        tvAmplitudeValue.text = "${"%.1f".format(amplitude)}$unitSuffix"
        tvTendenciaValue.text = trend
        tvRegistosValue.text = "${dataList.size}"
    }

    private fun setupChartProperties() {
        lineChart.description.isEnabled = false
        lineChart.setTouchEnabled(true)
        lineChart.isDragEnabled = true
        lineChart.setScaleEnabled(true)
        lineChart.setPinchZoom(true)
        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.valueFormatter = object : ValueFormatter() {
            private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            override fun getFormattedValue(value: Float): String {
                return dateFormat.format(Date(value.toLong() * 1000))
            }
        }
        lineChart.axisLeft.setDrawGridLines(true)
        lineChart.axisRight.isEnabled = false
    }

    private fun updateChartDisplay() {
        if (chartDataList.isEmpty()) return

        // Determine label and colors based on data type
        val (label, lineColor, fillColor) = when (selectedDataType) {
            DataType.TEMPERATURA -> Triple("Temperatura", Color.rgb(255, 87, 34), Color.rgb(255, 204, 188))
            DataType.HUMIDADE -> Triple("Humidade", Color.rgb(33, 150, 243), Color.rgb(187, 222, 251))
            DataType.PARTICULAS -> Triple("Partículas", Color.rgb(156, 39, 176), Color.rgb(225, 190, 231))
        }

        val dataSet = LineDataSet(chartDataList, "$currentStationId - $label")
        dataSet.mode = LineDataSet.Mode.LINEAR
        dataSet.setDrawCircles(false)
        dataSet.setDrawValues(false)
        dataSet.color = lineColor
        dataSet.lineWidth = 2f
        dataSet.setDrawFilled(true)
        dataSet.fillColor = fillColor
        dataSet.fillAlpha = 150
        val lineData = LineData(dataSet)
        lineChart.data = lineData
        lineChart.invalidate()
        lineChart.animateY(800)
    }
}