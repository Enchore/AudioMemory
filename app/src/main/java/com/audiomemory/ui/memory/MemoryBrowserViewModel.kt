package com.audiomemory.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiomemory.data.entity.MemoryType
import com.audiomemory.data.entity.MemoryWithTags
import com.audiomemory.data.repository.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MemoryBrowserViewModel @Inject constructor(
    private val repository: MemoryRepository,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow<MemoryType?>(null)
    val selectedFilter = _selectedFilter.asStateFlow()

    val memories: StateFlow<List<MemoryWithTags>> =
        combine(_searchQuery, _selectedFilter) { query, filter -> Pair(query, filter) }
            .flatMapLatest { (query, filter) ->
                when {
                    query.isNotBlank() -> repository.searchMemories(query).map { entities ->
                        entities.map { MemoryWithTags(it, emptyList()) } // simplified for search
                    }
                    filter != null -> repository.getMemoriesByType(filter)
                    else -> repository.getRecentMemories(100)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onFilterChanged(type: MemoryType?) {
        _selectedFilter.value = type
    }
}
