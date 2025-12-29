package com.example.meteo

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import androidx.core.graphics.toColorInt

class GraphActivity : AppCompatActivity() {

    private lateinit var lineChart: LineChart
    private lateinit var btnDate: Button
    private lateinit var tvTitle: TextView
    private lateinit var statsGrid: GridLayout
    private lateinit var tvStatsInfo: TextView

    private val chartDataList = ArrayList<Entry>()
    private val rawTempList = ArrayList<Float>()

    private var currentStationId: String = "STATION_00"
    private val selectedCalendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_graph)

        currentStationId = intent.getStringExtra("STATION_ID") ?: "STATION_00"

        lineChart = findViewById(R.id.lineChart)
        btnDate = findViewById(R.id.btnSelectDate)
        tvTitle = findViewById(R.id.tvStationTitle)
        statsGrid = findViewById(R.id.statsGrid)
        tvStatsInfo = findViewById(R.id.tvStatsInfo)
        tvTitle.text = currentStationId
        setupChartProperties()
        setupDatePicker()
        loadLatestDataDate() //Verifica qual é a última data disponível
    }

    // Descobre o último dia com dados
    private fun loadLatestDataDate() {
        val db = FirebaseFirestore.getInstance()
        lineChart.setNoDataText("A procurar registos...")
        tvStatsInfo.text = "A procurar dados..."

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
        tvStatsInfo.text = "A calcular estatísticas..."
        statsGrid.removeAllViews()
        statsGrid.addView(tvStatsInfo)

        val db = FirebaseFirestore.getInstance()

        db.collection(currentStationId)
            .whereGreaterThanOrEqualTo("timestamp", startTs)
            .whereLessThanOrEqualTo("timestamp", endTs)
            .get()
            .addOnSuccessListener { result ->
                chartDataList.clear()
                rawTempList.clear()

                if (result.isEmpty) {
                    lineChart.setNoDataText("Sem dados neste dia.")
                    tvStatsInfo.text = "Sem dados para estatísticas."
                    lineChart.invalidate()
                    return@addOnSuccessListener
                }

                val groupedData = mutableMapOf<Long, MutableList<Float>>()

                for (document in result) {
                    val temp = document.getDouble("temperatura")
                    val ts = document.getLong("timestamp")

                    if (temp != null && ts != null) {
                        rawTempList.add(temp.toFloat())

                        // Média de 5 minutos
                        val fiveMinBlockTs = (ts / 300) * 300
                        if (!groupedData.containsKey(fiveMinBlockTs)) {
                            groupedData[fiveMinBlockTs] = mutableListOf()
                        }
                        groupedData[fiveMinBlockTs]?.add(temp.toFloat())
                    }
                }

                for ((blockTs, temps) in groupedData) {
                    chartDataList.add(Entry(blockTs.toFloat(), temps.average().toFloat()))
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
        if (rawTempList.isEmpty()) return

        statsGrid.removeAllViews()

        val max = rawTempList.maxOrNull() ?: 0f
        val min = rawTempList.minOrNull() ?: 0f
        val avg = rawTempList.average()
        val amplitude = max - min


        val firstTemp = rawTempList.first()
        val lastTemp = rawTempList.last()
        val trend = if (lastTemp > firstTemp) "⬆ Subida (+${"%.1f".format(lastTemp - firstTemp)})"
        else if (lastTemp < firstTemp) "⬇ Descida (${"%.1f".format(lastTemp - firstTemp)})"
        else "➡ Estável"

        addStatCard("Máxima", "%.1f °C".format(max), "#FFCDD2".toColorInt())
        addStatCard("Mínima", "%.1f °C".format(min), "#BBDEFB".toColorInt())
        addStatCard("Média", "%.2f °C".format(avg), "#E1BEE7".toColorInt())
        addStatCard("Amplitude", "%.1f °C".format(amplitude), Color.WHITE)
        addStatCard("Tendência", trend, "#DCEDC8".toColorInt())
        addStatCard("Registos", "${rawTempList.size}", Color.WHITE)
    }

    private fun addStatCard(title: String, value: String, bgColor: Int) {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(30, 30, 30, 30)
        container.setBackgroundColor(bgColor)

        val params = GridLayout.LayoutParams()
        params.width = 0
        params.height = GridLayout.LayoutParams.WRAP_CONTENT
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        params.setMargins(10, 10, 10, 10)
        container.layoutParams = params

        val tvTitle = TextView(this)
        tvTitle.text = title.uppercase()
        tvTitle.textSize = 12f
        tvTitle.setTextColor(Color.GRAY)
        tvTitle.gravity = Gravity.CENTER

        val tvValue = TextView(this)
        tvValue.text = value
        tvValue.textSize = 20f
        tvValue.setTypeface(null, Typeface.BOLD)
        tvValue.setTextColor(Color.BLACK)
        tvValue.gravity = Gravity.CENTER

        container.addView(tvTitle)
        container.addView(tvValue)
        statsGrid.addView(container)
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
        val dayFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
        val dateString = dayFormat.format(selectedCalendar.time)
        val dataSet = LineDataSet(chartDataList, "$currentStationId")
        dataSet.mode = LineDataSet.Mode.LINEAR
        dataSet.setDrawCircles(false)
        dataSet.setDrawValues(false)
        dataSet.color = Color.rgb(41, 121, 255)
        dataSet.lineWidth = 2f
        dataSet.setDrawFilled(true)
        dataSet.fillColor = Color.rgb(187, 222, 251)
        dataSet.fillAlpha = 150
        val lineData = LineData(dataSet)
        lineChart.data = lineData
        lineChart.invalidate()
        lineChart.animateY(800)
    }
}