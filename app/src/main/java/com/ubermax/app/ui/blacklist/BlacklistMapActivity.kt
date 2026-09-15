package com.ubermax.app.ui.blacklist

import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.widget.EditText
import android.widget.SeekBar
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ubermax.app.R
import com.ubermax.app.data.db.entity.BlacklistZoneEntity
import com.ubermax.app.data.repository.ConfigRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import javax.inject.Inject

/**
 * Mapa interactivo para gestionar zonas de lista negra.
 *
 * El usuario puede:
 * 1. Mantener presionado en el mapa para agregar una zona circular
 * 2. Ajustar el radio con un slider
 * 3. Nombrar la zona (el nombre se usa para comparar con direcciones de destino)
 * 4. Tocar una zona existente para eliminarla
 *
 * Mapa centrado en Ambato, Ecuador (-1.2417, -78.6197)
 */
@AndroidEntryPoint
class BlacklistMapActivity : AppCompatActivity() {

    @Inject lateinit var configRepository: ConfigRepository

    private lateinit var mapView: MapView
    private lateinit var zonesList: LinearLayout
    private lateinit var tvZoneCount: TextView

    // Ambato, Ecuador
    private val ambato = GeoPoint(-1.2417, -78.6197)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configurar OSMDroid
        Configuration.getInstance().userAgentValue = packageName

        setContentView(R.layout.activity_blacklist_map)

        mapView = findViewById(R.id.map_view)
        zonesList = findViewById(R.id.zones_list)
        tvZoneCount = findViewById(R.id.tv_zone_count)

        setupMap()
        setupBackButton()
        loadZones()
    }

    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(14.0)
        mapView.controller.setCenter(ambato)

        // Overlay para capturar long-press en el mapa
        val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
            override fun longPressHelper(p: GeoPoint?): Boolean {
                if (p != null) showAddZoneDialog(p)
                return true
            }
        })
        mapView.overlays.add(0, eventsOverlay)
    }

    private fun setupBackButton() {
        findViewById<android.widget.ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }
    }

    /**
     * Muestra un diálogo para nombrar la zona y seleccionar el radio.
     */
    private fun showAddZoneDialog(point: GeoPoint) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val nameInput = EditText(this).apply {
            hint = "Nombre de la zona (ej: Ficoa, Huachi...)"
            textSize = 16f
        }
        layout.addView(nameInput)

        val radiusLabel = TextView(this).apply {
            text = "Radio: 500m"
            setPadding(0, 24, 0, 8)
            textSize = 14f
        }
        layout.addView(radiusLabel)

        val radiusSeekbar = SeekBar(this).apply {
            max = 2000 // metros máximo
            progress = 500
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val r = progress.coerceAtLeast(100) // mínimo 100m
                    radiusLabel.text = "Radio: ${r}m"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        layout.addView(radiusSeekbar)

        val coordLabel = TextView(this).apply {
            text = "📍 ${String.format("%.4f", point.latitude)}, ${String.format("%.4f", point.longitude)}"
            setPadding(0, 16, 0, 0)
            textSize = 12f
            setTextColor(getColor(R.color.text_tertiary))
        }
        layout.addView(coordLabel)

        AlertDialog.Builder(this)
            .setTitle("🚫 Agregar Zona Excluida")
            .setView(layout)
            .setPositiveButton("Agregar") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Escribe un nombre para la zona", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val radius = radiusSeekbar.progress.coerceAtLeast(100).toDouble()

                lifecycleScope.launch {
                    configRepository.addBlacklistZone(BlacklistZoneEntity(
                        name = name,
                        latitude = point.latitude,
                        longitude = point.longitude,
                        radiusMeters = radius
                    ))
                    loadZones()
                    Toast.makeText(this@BlacklistMapActivity, "Zona '$name' agregada", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Carga todas las zonas de la DB y las dibuja en el mapa.
     */
    private fun loadZones() {
        lifecycleScope.launch {
            val zones = configRepository.getAllBlacklistZones()
            tvZoneCount.text = "${zones.size} zona${if (zones.size != 1) "s" else ""}"

            // Limpiar overlays anteriores (mantener el de eventos en posición 0)
            val eventsOverlay = mapView.overlays.firstOrNull { it is MapEventsOverlay }
            mapView.overlays.clear()
            eventsOverlay?.let { mapView.overlays.add(0, it) }

            // Dibujar cada zona
            for (zone in zones) {
                drawZone(zone)
            }
            mapView.invalidate()

            // Actualizar lista de texto
            zonesList.removeAllViews()
            for (zone in zones) {
                val tv = TextView(this@BlacklistMapActivity).apply {
                    text = "🚫 ${zone.name} (${zone.radiusMeters.toInt()}m)"
                    textSize = 14f
                    setTextColor(getColor(R.color.text_primary))
                    setPadding(0, 6, 0, 6)
                    setOnClickListener {
                        confirmDeleteZone(zone)
                    }
                }
                zonesList.addView(tv)
            }
        }
    }

    /**
     * Dibuja un círculo rojo semitransparente en el mapa.
     */
    private fun drawZone(zone: BlacklistZoneEntity) {
        val center = GeoPoint(zone.latitude, zone.longitude)

        // Círculo
        val circle = Polygon(mapView).apply {
            points = Polygon.pointsAsCircle(center, zone.radiusMeters)
            fillPaint.color = Color.argb(60, 255, 50, 50) // Rojo semitransparente
            outlinePaint.color = Color.argb(180, 255, 50, 50)
            outlinePaint.strokeWidth = 3f
            title = zone.name
        }
        mapView.overlays.add(circle)

        // Marcador en el centro
        val marker = Marker(mapView).apply {
            position = center
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "🚫 ${zone.name}"
            snippet = "Radio: ${zone.radiusMeters.toInt()}m"
        }
        mapView.overlays.add(marker)
    }

    private fun confirmDeleteZone(zone: BlacklistZoneEntity) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar zona")
            .setMessage("¿Eliminar '${zone.name}' de la lista negra?")
            .setPositiveButton("Eliminar") { _, _ ->
                lifecycleScope.launch {
                    configRepository.removeBlacklistZone(zone)
                    loadZones()
                    Toast.makeText(this@BlacklistMapActivity, "Zona '${zone.name}' eliminada", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}
