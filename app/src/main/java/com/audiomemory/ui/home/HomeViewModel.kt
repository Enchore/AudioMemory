package com.audiomemory.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiomemory.data.entity.MemoryWithTags
import com.audiomemory.data.entity.SpeakerEntity
import com.audiomemory.data.repository.MemoryRepository
import com.audiomemory.service.recording.AudioRecordingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MemoryRepository,
) : ViewModel() {

    val isRecording = AudioRecordingService.isRecording
    val isPaused = AudioRecordingService.isPaused
    val elapsedSeconds = AudioRecordingService.elapsedSeconds

    val recentMemories: StateFlow<List<MemoryWithTags>> =
        repository.getRecentMemories(10)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalMemoryCount: StateFlow<Int> =
        repository.getTotalMemoryCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val speakers: StateFlow<List<SpeakerEntity>> =
        repository.getAllSpeakers()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingChunks: StateFlow<Int> =
        repository.getPendingChunkCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}
