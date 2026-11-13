package com.maxence.offlinebaseltour.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TrackingState {
    STOPPED,
    RECORDING,
    PAUSED
}

// Singleton permettant à l'UI de réagir à l'état réel du service
object TrackingManager {
    private val _trackingState = MutableStateFlow(TrackingState.STOPPED)
    val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()
    
    private val _currentDistance = MutableStateFlow(0f)
    val currentDistance: StateFlow<Float> = _currentDistance.asStateFlow()

    fun updateState(state: TrackingState) {
        _trackingState.value = state
    }
    
    fun updateDistance(distance: Float) {
        _currentDistance.value = distance
    }
}
