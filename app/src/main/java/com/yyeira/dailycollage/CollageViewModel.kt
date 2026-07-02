package com.yyeira.dailycollage

import android.app.Application
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yyeira.dailycollage.data.GalleryRepository
import com.yyeira.dailycollage.domain.GridCollageMaker
import com.yyeira.dailycollage.domain.ImageGrouper
import com.yyeira.dailycollage.model.DayPreview
import com.yyeira.dailycollage.util.ImageDeleter
import com.yyeira.dailycollage.util.ImageSaver
import com.yyeira.dailycollage.util.PermissionHelper
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CollageUiState(
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val columns: Int = 2,
    val deleteOriginalsAfterCollage: Boolean = false,
    val isLoadingPreview: Boolean = false,
    val isProcessing: Boolean = false,
    val previews: List<DayPreview> = emptyList(),
    val progressDate: String? = null,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val resultMessage: String? = null,
    val errorMessage: String? = null,
    val warningMessage: String? = null,
    val needsPermission: Boolean = false,
    val pendingDeleteIntentSender: IntentSender? = null,
)

class CollageViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val galleryRepository = GalleryRepository(context.contentResolver)
    private val collageMaker = GridCollageMaker(context.contentResolver)
    private val previewMaker = GridCollageMaker(context.contentResolver, canvasWidth = 360)
    private val imageSaver = ImageSaver(context.contentResolver)
    private val imageDeleter = ImageDeleter(context.contentResolver)

    private val _uiState = MutableStateFlow(CollageUiState())
    val uiState: StateFlow<CollageUiState> = _uiState.asStateFlow()

    init {
        refreshPermissionState()
    }

    fun refreshPermissionState() {
        _uiState.update {
            it.copy(needsPermission = !PermissionHelper.hasAllPermissions(context))
        }
    }

    fun setDateRange(startDate: LocalDate?, endDate: LocalDate?) {
        clearPreviews()
        _uiState.update {
            it.copy(
                startDate = startDate,
                endDate = endDate,
                resultMessage = null,
                errorMessage = null,
                warningMessage = null,
            )
        }
    }

    fun setColumns(columns: Int) {
        if (_uiState.value.columns != columns) {
            clearPreviews()
        }
        _uiState.update { it.copy(columns = columns) }
    }

    fun setDeleteOriginalsAfterCollage(enabled: Boolean) {
        _uiState.update { it.copy(deleteOriginalsAfterCollage = enabled) }
    }

    fun generatePreview() {
        val state = _uiState.value
        val startDate = state.startDate
        val endDate = state.endDate

        if (startDate == null || endDate == null) {
            _uiState.update { it.copy(errorMessage = context.getString(R.string.date_range_not_selected)) }
            return
        }

        if (!PermissionHelper.hasAllPermissions(context)) {
            _uiState.update { it.copy(needsPermission = true) }
            return
        }

        viewModelScope.launch {
            clearPreviews()
            _uiState.update {
                it.copy(
                    isLoadingPreview = true,
                    resultMessage = null,
                    errorMessage = null,
                    warningMessage = null,
                )
            }

            try {
                val previews = withContext(Dispatchers.IO) {
                    buildPreviews(startDate, endDate, state.columns)
                }
                _uiState.update {
                    it.copy(
                        isLoadingPreview = false,
                        previews = previews,
                        warningMessage = buildLargeDayWarning(previews),
                    )
                }
            } catch (_: NoImagesException) {
                _uiState.update {
                    it.copy(
                        isLoadingPreview = false,
                        errorMessage = context.getString(R.string.no_images),
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingPreview = false,
                        errorMessage = e.message ?: e.javaClass.simpleName,
                    )
                }
            }
        }
    }

    fun confirmCollage() {
        val state = _uiState.value
        if (state.previews.isEmpty()) {
            _uiState.update { it.copy(errorMessage = context.getString(R.string.preview_not_ready)) }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    resultMessage = null,
                    errorMessage = null,
                    progressDate = null,
                    progressCurrent = 0,
                    progressTotal = state.previews.size,
                )
            }

            try {
                val result = withContext(Dispatchers.IO) {
                    processCollageFromPreviews(state.previews, state.columns, state.deleteOriginalsAfterCollage)
                }
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        resultMessage = result.summary,
                        warningMessage = result.warning ?: it.warningMessage,
                        pendingDeleteIntentSender = result.pendingDeleteIntentSender,
                    )
                }
                if (result.pendingDeleteIntentSender == null) {
                    clearPreviews()
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = e.message ?: e.javaClass.simpleName,
                    )
                }
            }
        }
    }

    fun onDeleteRequestResult(success: Boolean) {
        _uiState.update { it.copy(pendingDeleteIntentSender = null) }
        if (success) {
            _uiState.update {
                it.copy(
                    resultMessage = context.getString(R.string.delete_confirmed),
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    warningMessage = context.getString(R.string.delete_cancelled),
                )
            }
        }
        clearPreviews()
    }

    fun clearPendingDeleteIntent() {
        _uiState.update { it.copy(pendingDeleteIntentSender = null) }
    }

    private data class ProcessResult(
        val summary: String?,
        val warning: String?,
        val pendingDeleteIntentSender: IntentSender? = null,
    )

    private fun buildPreviews(startDate: LocalDate, endDate: LocalDate, columns: Int): List<DayPreview> {
        val images = galleryRepository.queryImages(startDate, endDate)
        if (images.isEmpty()) {
            throw NoImagesException()
        }

        return ImageGrouper.groupByDay(images).map { (dateKey, dayImages) ->
            val previewBitmap = previewMaker.createCollage(dayImages, columns, dateKey)
            DayPreview(
                dateKey = dateKey,
                imageCount = dayImages.size,
                previewBitmap = previewBitmap,
                images = dayImages,
            )
        }
    }

    private fun buildLargeDayWarning(previews: List<DayPreview>): String? {
        val largeDayCount = previews.count { it.imageCount > 50 }
        return if (largeDayCount > 0) {
            context.getString(R.string.large_day_warning, largeDayCount)
        } else {
            null
        }
    }

    private fun processCollageFromPreviews(
        previews: List<DayPreview>,
        columns: Int,
        deleteOriginals: Boolean,
    ): ProcessResult {
        var savedCount = 0
        val urisToDelete = mutableListOf<Uri>()

        previews.forEachIndexed { index, day ->
            _uiState.update {
                it.copy(
                    progressDate = day.dateKey,
                    progressCurrent = index + 1,
                    progressTotal = previews.size,
                )
            }

            val collage = collageMaker.createCollage(day.images, columns, day.dateKey)
            val saved = imageSaver.saveJpeg(collage, "${day.dateKey}.jpg")
            collage.recycle()
            if (saved) {
                savedCount++
                if (deleteOriginals) {
                    urisToDelete.addAll(day.images.map { it.uri })
                }
            }
        }

        var summary = context.getString(R.string.result_summary, previews.size, savedCount)
        var pendingDeleteIntentSender: IntentSender? = null
        var deleteWarning: String? = null

        if (deleteOriginals && urisToDelete.isNotEmpty()) {
            when (val deleteResult = imageDeleter.delete(urisToDelete)) {
                is ImageDeleter.DeleteResult.NeedsUserConfirmation -> {
                    pendingDeleteIntentSender = deleteResult.intentSender
                    summary += " " + context.getString(R.string.delete_pending_confirmation, urisToDelete.size)
                }
                is ImageDeleter.DeleteResult.Success -> {
                    summary += " " + context.getString(R.string.deleted_originals_summary, deleteResult.deletedCount)
                }
                is ImageDeleter.DeleteResult.Failed -> {
                    deleteWarning = deleteResult.message
                }
            }
        }

        return ProcessResult(
            summary = summary,
            warning = deleteWarning,
            pendingDeleteIntentSender = pendingDeleteIntentSender,
        )
    }

    private fun clearPreviews() {
        _uiState.value.previews.forEach { preview ->
            if (!preview.previewBitmap.isRecycled) {
                preview.previewBitmap.recycle()
            }
        }
        _uiState.update { it.copy(previews = emptyList()) }
    }

    fun openAppSettings(): Intent {
        return Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    override fun onCleared() {
        clearPreviews()
        super.onCleared()
    }

    class NoImagesException : Exception()
}
