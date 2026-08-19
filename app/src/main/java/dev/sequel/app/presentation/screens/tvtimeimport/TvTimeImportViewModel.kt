package dev.sequel.app.presentation.screens.tvtimeimport

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sequel.app.domain.usecase.ImportProgress
import dev.sequel.app.domain.usecase.TvTimeImporterUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TvTimeImportViewModel @Inject constructor(
    private val importerUseCase: TvTimeImporterUseCase
) : ViewModel() {

    private val _importState = MutableStateFlow<ImportProgress>(ImportProgress.Idle)
    val importState: StateFlow<ImportProgress> = _importState.asStateFlow()

    fun startImport(uri: Uri) {
        viewModelScope.launch {
            importerUseCase.importCsv(uri).collect { progress ->
                _importState.value = progress
            }
        }
    }

    fun resetState() {
        _importState.value = ImportProgress.Idle
    }
}
