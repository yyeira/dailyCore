package com.yyeira.dailycollage

import android.app.Application
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yyeira.dailycollage.data.GalleryRepository
import com.yyeira.dailycollage.domain.CollageLayoutPlanner
import com.yyeira.dailycollage.domain.GridCollageMaker
import com.yyeira.dailycollage.domain.ImageDimensionResolver
import com.yyeira.dailycollage.domain.ImageGrouper
import com.yyeira.dailycollage.model.DayPreview
import com.yyeira.dailycollage.model.LayoutRule
import com.yyeira.dailycollage.model.OutputAspectRatio
import com.yyeira.dailycollage.util.ImageDeleter
import com.yyeira.dailycollage.util.ImageSaver
import com.yyeira.dailycollage.util.LayoutDescriptionFormatter
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
    val layoutRule: LayoutRule = LayoutRule.AUTO,
    val outputAspectRatio: OutputAspectRatio = OutputAspectRatio.NATURAL,
    val deleteOriginalsAfterCollage: Boolean = false,
    val isLoadingPreview: Boolean = false,
    val isProcessing: Boolean = false,
    val previews: List<DayPreview> = emptyList(),
    val rebuildingDateKey: String? = null,
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
    private val dimensionResolver = ImageDimensionResolver(context.contentResolver)
    private val layoutPlanner = CollageLayoutPlanner(canvasWidth = 1080)
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

    fun setLayoutRule(rule: LayoutRule) {
        if (_uiState.value.layoutRule != rule) {
            clearPreviews()
        }
        _uiState.update { it.copy(layoutRule = rule) }
        generatePreview()
    }

    fun setOutputAspectRatio(ratio: OutputAspectRatio) {
        if (_uiState.value.outputAspectRatio != ratio) {
            clearPreviews()
        }
        _uiState.update { it.copy(outputAspectRatio = ratio) }
        generatePreview()
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
                    buildPreviews(startDate, endDate, state.layoutRule, state.outputAspectRatio)
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
                    processCollageFromPreviews(state.previews, state.deleteOriginalsAfterCollage)
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

    fun swapImagesInDay(dateKey: String, indexA: Int, indexB: Int) {
        if (indexA == indexB) return

        val dayIndex = _uiState.value.previews.indexOfFirst { it.dateKey == dateKey }
        if (dayIndex < 0) return

        val day = _uiState.value.previews[dayIndex]
        if (indexA !in day.images.indices || indexB !in day.images.indices) return

        viewModelScope.launch {
            _uiState.update { it.copy(rebuildingDateKey = dateKey, errorMessage = null) }
            try {
                val updatedPreviews = withContext(Dispatchers.IO) {
                    updateDayImageOrder(dayIndex) { images ->
                        val temp = images[indexA]
                        images[indexA] = images[indexB]
                        images[indexB] = temp
                    }
                }
                _uiState.update {
                    it.copy(previews = updatedPreviews, rebuildingDateKey = null)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        rebuildingDateKey = null,
                        errorMessage = e.message ?: e.javaClass.simpleName,
                    )
                }
            }
        }
    }

    fun moveImageInDay(dateKey: String, fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return

        val dayIndex = _uiState.value.previews.indexOfFirst { it.dateKey == dateKey }
        if (dayIndex < 0) return

        val day = _uiState.value.previews[dayIndex]
        if (fromIndex !in day.images.indices || toIndex !in day.images.indices) return

        viewModelScope.launch {
            _uiState.update { it.copy(rebuildingDateKey = dateKey, errorMessage = null) }
            try {
                val updatedPreviews = withContext(Dispatchers.IO) {
                    updateDayImageOrder(dayIndex) { images ->
                        val image = images.removeAt(fromIndex)
                        images.add(toIndex, image)
                    }
                }
                _uiState.update {
                    it.copy(previews = updatedPreviews, rebuildingDateKey = null)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        rebuildingDateKey = null,
                        errorMessage = e.message ?: e.javaClass.simpleName,
                    )
                }
            }
        }
    }

    private fun updateDayImageOrder(
        dayIndex: Int,
        mutate: (MutableList<com.yyeira.dailycollage.model.GalleryImage>) -> Unit,
    ): List<DayPreview> {
        val currentPreviews = _uiState.value.previews
        val day = currentPreviews[dayIndex]
        val images = day.images.toMutableList()
        mutate(images)

        val newBitmap = previewMaker.createCollage(
            images,
            day.layout,
            day.dateKey,
            _uiState.value.outputAspectRatio,
        )
        if (!day.previewBitmap.isRecycled) {
            day.previewBitmap.recycle()
        }

        val newPreviews = currentPreviews.toMutableList()
        newPreviews[dayIndex] = day.copy(images = images, previewBitmap = newBitmap)
        return newPreviews
    }

    private data class ProcessResult(
        val summary: String?,
        val warning: String?,
        val pendingDeleteIntentSender: IntentSender? = null,
    )

    private fun buildPreviews(
        startDate: LocalDate,
        endDate: LocalDate,
        layoutRule: LayoutRule,
        outputAspectRatio: OutputAspectRatio,
    ): List<DayPreview> {
        val images = galleryRepository.queryImages(startDate, endDate)
        if (images.isEmpty()) {
            throw NoImagesException()
        }

        return ImageGrouper.groupByDay(images).map { (dateKey, dayImages) ->
            buildDayPreview(dateKey, dayImages, layoutRule, outputAspectRatio)
        }
    }

    private fun buildDayPreview(
        dateKey: String,
        dayImages: List<com.yyeira.dailycollage.model.GalleryImage>,
        layoutRule: LayoutRule,
        outputAspectRatio: OutputAspectRatio,
    ): DayPreview {
        val dimensions = dimensionResolver.resolve(dayImages).map { it.second }
        val layout = layoutPlanner.plan(dayImages, dimensions, layoutRule)
        val layoutDescription = LayoutDescriptionFormatter.format(context, layout.description)
        val previewBitmap = previewMaker.createCollage(dayImages, layout, dateKey, outputAspectRatio)
        return DayPreview(
            dateKey = dateKey,
            imageCount = dayImages.size,
            layoutDescription = layoutDescription,
            previewBitmap = previewBitmap,
            images = dayImages,
            layout = layout,
        )
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

            val collage = collageMaker.createCollage(
                day.images,
                day.layout,
                day.dateKey,
                _uiState.value.outputAspectRatio,
            )
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
