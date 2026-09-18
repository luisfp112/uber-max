package com.ubermax.app.ui.blacklist

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.ubermax.app.R
import com.ubermax.app.data.db.entity.BlacklistZoneEntity
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
import org.osmdroid.views.overlay.Polyline
import java.util.HashMap

/**
 * Mapa de zonas de lista negra basado en OpenStreetMap (OSMDroid).
 * 100% gratuito: sin API keys, sin cuenta de facturación.
 *
 * Funcionalidad equivalente al anterior mapa (Google Maps):
 *  - Dibujar polígonos tocando el mapa
 *  - Zonas persistidas en Room, dibujadas con fill + marker en el centroide
 *  - La ubicación del usuario se obtiene de FusedLocationProvider (gratis, sin clave)
 *  - Tema claro con los tiles estándar de OpenStreetMap (MAPNIK)
 */
@AndroidEntryPoint
class BlacklistMapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var viewModel: BlacklistMapViewModel
    private lateinit var tvZoneCount: TextView
    private lateinit var tvInstructions: TextView
    private lateinit var btnCloseZone: View
    private lateinit var btnUndo: View
    private lateinit var btnClear: View
    private lateinit var zonesList: LinearLayout
    private lateinit var extractionContainer: View
    private lateinit var tvExtractionProgress: TextView
    private lateinit var progressBar: ProgressBar

    // Ambato, Ecuador (fallback si no hay ubicación disponible)
    private val ambato = GeoPoint(-1.2417, -78.6197)

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    /** Overlay que captura los toques para añadir vértices al polígono en construcción. */
    private lateinit var tapOverlay: MapEventsOverlay

    /** Asocia cada marker de zona con la zona persistida (para el diálogo de borrado). */
    private val zoneByMarker = HashMap<Marker, BlacklistZoneEntity>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blacklist_map)

        // Configuración OSMDroid: storage + User-Agent (exigido por la política
        // de uso de los tiles de OpenStreetMap).
        Configuration.getInstance().load(
            this,
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = packageName

        viewModel = ViewModelProvider(this)[BlacklistMapViewModel::class.java]
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        tvZoneCount = findViewById(R.id.tv_zone_count)
        tvInstructions = findViewById(R.id.tv_instructions)
        btnCloseZone = findViewById(R.id.btn_close_zone)
        btnUndo = findViewById(R.id.btn_undo)
        btnClear = findViewById(R.id.btn_clear)
        zonesList = findViewById(R.id.zones_list)
        extractionContainer = findViewById(R.id.extraction_container)
        tvExtractionProgress = findViewById(R.id.tv_extraction_progress)
        progressBar = findViewById(R.id.progress_bar)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        btnCloseZone.setOnClickListener { showNameDialog() }
        btnUndo.setOnClickListener { viewModel.onUndoLastVertex() }
        btnClear.setOnClickListener { viewModel.onClearCurrentDrawing() }

        setupMap()
        observeState()
    }

    private fun setupMap() {
        mapView = findViewById(R.id.map_fragment)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setTilesScaledToDpi(true)
        mapView.setMultiTouchControls(true)
        mapView.setMinZoomLevel(4.0)
        mapView.setMaxZoomLevel(19.0)
        mapView.setBackgroundColor(Color.WHITE)

        // Captura de toques para construir polígonos
        tapOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                viewModel.onMapTap(p.latitude, p.longitude)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean = false
        })
        mapView.overlays.add(tapOverlay)

        if (hasLocationPermission()) {
            centerOnUserOrFallback()
        } else {
            mapView.controller.setZoom(14.0)
            mapView.controller.setCenter(ambato)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (::mapView.isInitialized) mapView.onPause()
    }

    override fun onDestroy() {
        if (::mapView.isInitialized) mapView.onDetach()
        super.onDestroy()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    /**
     * Centra el mapa en la última ubicación conocida del dispositivo.
     * Si no hay permisos o la ubicación falla, cae a la ciudad base (Ambato).
     */
    private fun centerOnUserOrFallback() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            mapView.controller.setZoom(14.0)
            mapView.controller.setCenter(ambato)
            return
        }
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    val target = if (location != null) {
                        GeoPoint(location.latitude, location.longitude)
                    } else {
                        ambato
                    }
                    mapView.controller.setZoom(14.0)
                    mapView.controller.setCenter(target)
                }
                .addOnFailureListener {
                    mapView.controller.setZoom(14.0)
                    mapView.controller.setCenter(ambato)
                }
        } catch (e: Exception) {
            mapView.controller.setZoom(14.0)
            mapView.controller.setCenter(ambato)
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    tvZoneCount.text = "${state.zones.size} zona${if (state.zones.size != 1) "s" else ""}"

                    // Update instructions
                    tvInstructions.text = when {
                        state.isExtractingAddresses -> {
                            val (done, total) = state.extractionProgress
                            "Extrayendo direcciones... ($done/$total)"
                        }
                        state.currentVertices.isEmpty() -> "Toca el mapa para agregar puntos"
                        state.currentVertices.size < 3 -> "Toca para agregar más puntos (${state.currentVertices.size}/3 mínimo)"
                        else -> "Toca \"Cerrar zona\" para finalizar"
                    }

                    // Button visibility
                    btnCloseZone.visibility = if (state.currentVertices.size >= 3) View.VISIBLE else View.INVISIBLE
                    btnUndo.visibility = if (state.currentVertices.isNotEmpty()) View.VISIBLE else View.INVISIBLE
                    btnClear.visibility = if (state.currentVertices.isNotEmpty()) View.VISIBLE else View.INVISIBLE

                    // Extraction progress
                    if (state.isExtractingAddresses) {
                        extractionContainer.visibility = View.VISIBLE
                        val (done, total) = state.extractionProgress
                        progressBar.max = total
                        progressBar.progress = done
                        tvExtractionProgress.text = "Geocodificando: $done/$total puntos"
                    } else {
                        extractionContainer.visibility = View.GONE
                    }

                    // Extraction summary
                    if (state.showExtractionSummary && state.extractedKeywords.isNotEmpty()) {
                        extractionContainer.visibility = View.VISIBLE
                        progressBar.visibility = View.GONE
                        tvExtractionProgress.text = "Se extrajeron ${state.extractedKeywords.size} keywords de '${state.lastCreatedZoneName}'"
                    }

                    drawMap(state)
                    renderZonesList(state.zones)
                }
            }
        }
    }

    private fun drawMap(state: BlacklistMapViewModel.MapState) {
        mapView.overlays.clear()
        // clear() descarta todos los overlays: re-agregar el de toques para seguir dibujando
        mapView.overlays.add(tapOverlay)

        // Draw existing zones
        for (zone in state.zones) {
            drawZone(zone)
        }

        // Draw current polygon being built
        if (state.currentVertices.isNotEmpty()) {
            val geos = state.currentVertices.map { GeoPoint(it[0], it[1]) }

            // Draw lines between vertices
            if (geos.size >= 2) {
                val line = Polyline(mapView)
                line.setPoints(geos)
                line.color = Color.WHITE
                line.width = 4f
                mapView.overlays.add(line)
            }

            // Draw closing line if >= 3 vertices
            if (geos.size >= 3) {
                val closing = Polyline(mapView)
                closing.setPoints(listOf(geos.last(), geos.first()))
                closing.color = Color.argb(120, 255, 255, 255)
                closing.width = 3f
                mapView.overlays.add(closing)
            }

            // Draw vertices as markers (no son zonas → sin diálogo de borrado)
            for ((index, gp) in geos.withIndex()) {
                val vertexMarker = Marker(mapView)
                vertexMarker.position = gp
                vertexMarker.title = "Vértice ${index + 1}"
                vertexMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                mapView.overlays.add(vertexMarker)
            }
        }
    }

    private fun drawZone(zone: BlacklistZoneEntity) {
        if (zone.isPolygon) {
            val polygon = zone.polygon
            val geos = polygon.map { GeoPoint(it.first, it.second) }

            // Draw filled polygon
            val drawable = Polygon(mapView)
            drawable.setPoints(geos)
            drawable.fillColor = Color.argb(60, 255, 50, 50)
            drawable.strokeColor = Color.argb(180, 255, 50, 50)
            drawable.strokeWidth = 3f
            mapView.overlays.add(drawable)

            // Add marker at centroid
            val centroid = geos.reduce { acc, gp ->
                GeoPoint((acc.latitude + gp.latitude) / 2, (acc.longitude + gp.longitude) / 2)
            }
            addZoneMarker(centroid, zone, "🚫 ${zone.name}", "${polygon.size} vértices · ${zone.extractedKeywords.size} keywords")

        } else if (zone.isLegacyCircle) {
            // Legacy circle
            val center = GeoPoint(zone.latitude, zone.longitude)
            addZoneMarker(center, zone, "🚫 ${zone.name}", "Radio: ${zone.radiusMeters.toInt()}m")

            // Draw approximate circle as polygon
            val circlePoints = generateCirclePoints(zone.latitude, zone.longitude, zone.radiusMeters, 36)
            val drawable = Polygon(mapView)
            drawable.setPoints(circlePoints)
            drawable.fillColor = Color.argb(60, 255, 50, 50)
            drawable.strokeColor = Color.argb(180, 255, 50, 50)
            drawable.strokeWidth = 2f
            mapView.overlays.add(drawable)
        }
    }

    /** Marker de zona: tocar abre el diálogo de eliminación. */
    private fun addZoneMarker(position: GeoPoint, zone: BlacklistZoneEntity, title: String, snippet: String) {
        val marker = Marker(mapView)
        marker.position = position
        marker.title = title
        marker.snippet = snippet
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        zoneByMarker[marker] = zone
        marker.setOnMarkerClickListener { clickedMarker, _ ->
            val z = zoneByMarker[clickedMarker]
            if (z != null) {
                showDeleteDialog(z)
                true
            } else {
                false
            }
        }
        mapView.overlays.add(marker)
    }

    private fun generateCirclePoints(
        centerLat: Double, centerLng: Double, radiusMeters: Double, numPoints: Int
    ): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        for (i in 0 until numPoints) {
            val angle = 2.0 * Math.PI * i / numPoints
            val dLat = radiusMeters * Math.cos(angle) / 111_320.0
            val dLng = radiusMeters * Math.sin(angle) / (111_320.0 * Math.cos(Math.toRadians(centerLat)))
            points.add(GeoPoint(centerLat + dLat, centerLng + dLng))
        }
        return points
    }

    /**
     * Renderiza la lista de zonas activas en el panel inferior.
     * Cada fila muestra el nombre y la abre en contexto: tocar = eliminar.
     */
    private fun renderZonesList(zones: List<BlacklistZoneEntity>) {
        zonesList.removeAllViews()

        for (zone in zones) {
            val row = TextView(this).apply {
                val typeLabel = if (zone.isLegacyCircle) "círculo ${zone.radiusMeters.toInt()}m" else "${zone.polygon.size} vértices"
                text = "🚫 ${zone.name} ($typeLabel) · ${zone.extractedKeywords.size} keywords"
                setTextColor(getColor(R.color.text_secondary))
                textSize = 13f
                setPadding(4, 10, 4, 10)
                // Tocar una zona la elimina
                setOnClickListener { showDeleteDialog(zone) }
            }
            zonesList.addView(row)
        }
    }

    private fun showNameDialog() {
        val input = EditText(this).apply {
            hint = "Nombre de la zona (ej: Ficoa, Huachi...)"
            textSize = 16f
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("🏷️ Nombrar Zona")
            .setView(input)
            .setPositiveButton("Crear zona + extraer direcciones") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Escribe un nombre", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.onClosePolygon(name)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showDeleteDialog(zone: BlacklistZoneEntity) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar zona")
            .setMessage("¿Eliminar '${zone.name}' de la lista negra?")
            .setPositiveButton("Eliminar") { _, _ ->
                viewModel.onDeleteZone(zone)
                Toast.makeText(this, "Zona '${zone.name}' eliminada", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}