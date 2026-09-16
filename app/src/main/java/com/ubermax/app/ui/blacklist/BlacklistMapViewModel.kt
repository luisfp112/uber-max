package com.ubermax.app.ui.blacklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ubermax.app.data.db.entity.BlacklistEntryEntity
import com.ubermax.app.data.db.entity.BlacklistZoneEntity
import com.ubermax.app.data.geocoding.NominatimGeocoder
import com.ubermax.app.data.repository.ConfigRepository
import com.ubermax.app.domain.geometry.PolygonGeometry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BlacklistMapViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    private val geocoder: NominatimGeocoder
) : ViewModel() {

    data class MapState(
        val zones: List<BlacklistZoneEntity> = emptyList(),
        val currentVertices: List<DoubleArray> = emptyList(),
        val isDrawingMode: Boolean = true,
        val isExtractingAddresses: Boolean = false,
        val extractionProgress: Pair<Int, Int> = (0 to 0),
        val extractedKeywords: Set<String> = emptySet(),
        val showExtractionSummary: Boolean = false,
        val lastCreatedZoneName: String = ""
    )

    private val _state = MutableStateFlow(MapState())
    val state: StateFlow<MapState> = _state.asStateFlow()

    init {
        loadZones()
    }

    fun loadZones() {
        viewModelScope.launch {
            val zones = configRepository.getAllBlacklistZones()
            _state.update { it.copy(zones = zones) }
        }
    }

    fun onMapTap(lat: Double, lng: Double) {
        if (!_state.value.isDrawingMode) return
        _state.update {
            it.copy(currentVertices = it.currentVertices + doubleArrayOf(lat, lng))
        }
    }

    fun onUndoLastVertex() {
        val verts = _state.value.currentVertices
        if (verts.isEmpty()) return
        _state.update {
            it.copy(currentVertices = verts.dropLast(1))
        }
    }

    fun onClearCurrentDrawing() {
        _state.update {
            it.copy(
                currentVertices = emptyList(),
                extractedKeywords = emptySet(),
                showExtractionSummary = false
            )
        }
    }

    fun onSetDrawingMode(enabled: Boolean) {
        _state.update {
            it.copy(isDrawingMode = enabled, currentVertices = emptyList())
        }
    }

    fun onClosePolygon(name: String) {
        val vertices = _state.value.currentVertices
        if (vertices.size < 3 || name.isBlank()) return

        val polygonJson = PolygonGeometry.serializePolygon(
            vertices.map { it[0] to it[1] }
        )

        val zone = BlacklistZoneEntity(
            name = name.trim(),
            polygonJson = polygonJson
        )

        viewModelScope.launch {
            val newId = configRepository.addBlacklistZone(zone)
            val savedZone = zone.copy(id = newId.toInt())

            _state.update {
                it.copy(
                    currentVertices = emptyList(),
                    isDrawingMode = false
                )
            }
            loadZones()
            startAddressExtraction(savedZone)
        }
    }

    fun startAddressExtraction(zone: BlacklistZoneEntity) {
        val polygon = zone.polygon
        if (polygon.size < 3 || zone.id == 0) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isExtractingAddresses = true,
                    extractionProgress = (0 to 0),
                    lastCreatedZoneName = zone.name
                )
            }

            val keywords = geocoder.extractKeywordsFromPolygon(polygon) { done, total ->
                _state.update { it.copy(extractionProgress = (done to total)) }
            }

            // Actualizar los keywords extraídos en la misma zona (mismo id)
            if (keywords.isNotEmpty()) {
                val updatedZone = zone.copy(
                    extractedKeywordsJson = org.json.JSONArray(keywords.toList()).toString()
                )
                configRepository.updateBlacklistZone(updatedZone)

                // Añadir keywords individuales para matching por texto
                for (keyword in keywords) {
                    configRepository.addBlacklistEntry(
                        BlacklistEntryEntity(
                            keyword = keyword,
                            type = "ZONE",
                            reason = "Auto-extracted from zone '${zone.name}'"
                        )
                    )
                }
            }

            _state.update {
                it.copy(
                    isExtractingAddresses = false,
                    extractedKeywords = keywords,
                    showExtractionSummary = true
                )
            }

            loadZones()
        }
    }

    fun onDeleteZone(zone: BlacklistZoneEntity) {
        viewModelScope.launch {
            configRepository.removeBlacklistZone(zone)
            // Limpiar keywords auto-extraídas de esa zona
            configRepository.removeBlacklistEntriesForZone(zone.name)
            loadZones()
        }
    }

    fun onDismissExtractionSummary() {
        _state.update { it.copy(showExtractionSummary = false) }
    }
}
