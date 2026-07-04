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
import com.yyeira.dailycollage.model.Album
import com.yyeira.dailycollage.model.CropOffset
import com.yyeira.dailycollage.model.DayPreview
import com.yyeira.dailycollage.model.GalleryImage
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
    val availableAlbums: List<Album> = emptyList(),
    val selectedAlbumIds: Set<Long> = emptySet(),
    val isLoadingAlbums: Boolean = false,
    val layoutRule: LayoutRule = LayoutRule.AUTO,
    val outputAspectRatio: OutputAspectRatio = OutputAspectRatio.NATURAL,
    val deleteOriginalsAfterCollage: Boolean = false,
    val isLoadingPreview: Boolean = false,
    val isProcessing: Boolean = false,
    val previews: List<DayPreview> = emptyList(),
    val rebuildingDateKey: String? = null,
    val pendingAddDateKey: String? = null,
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
                availableAlbums = emptyList(),
                selectedAlbumIds = emptySet(),
                resultMessage = null,
                errorMessage = null,
                warningMessage = null,
            )
        }
        if (startDate != null && endDate != null) {
            loadAlbums(startDate, endDate)
        }
    }

    private fun loadAlbums(startDate: LocalDate, endDate: LocalDate) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAlbums = true) }
            try {
                val albums = withContext(Dispatchers.IO) {
                    galleryRepository.queryAlbums(startDate, endDate)
                }
                _uiState.update { it.copy(availableAlbums = albums, isLoadingAlbums = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoadingAlbums = false) }
            }
        }
    }

    fun toggleAlbum(bucketId: Long) {
        clearPreviews()
        _uiState.update {
            val newIds = it.selectedAlbumIds.toMutableSet()
            if (bucketId in newIds) newIds.remove(bucketId) else newIds.add(bucketId)
            it.copy(selectedAlbumIds = newIds)
        }
    }

    fun selectAllAlbums() {
        clearPreviews()
        _uiState.update { it.copy(selectedAlbumIds = emptySet()) }
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
                val albumFilter = state.selectedAlbumIds.ifEmpty { null }
                val previews = withContext(Dispatchers.IO) {
                    buildPreviews(startDate, endDate, state.layoutRule, state.outputAspectRatio, albumFilter)
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

    fun updateCropOffset(dateKey: String, imageIndex: Int, offset: CropOffset) {
        val dayIndex = _uiState.value.previews.indexOfFirst { it.dateKey == dateKey }
        if (dayIndex < 0) return

        val day = _uiState.value.previews[dayIndex]
        if (imageIndex !in day.images.indices) return

        val newOffsets = day.cropOffsets.toMutableMap()
        newOffsets[imageIndex] = offset.coerced()

        viewModelScope.launch {
            _uiState.update { it.copy(rebuildingDateKey = dateKey, errorMessage = null) }
            try {
                val newBitmap = withContext(Dispatchers.IO) {
                    previewMaker.createCollage(
                        day.images,
                        day.layout,
                        day.dateKey,
                        _uiState.value.outputAspectRatio,
                        newOffsets,
                    )
                }
                if (!day.previewBitmap.isRecycled) {
                    day.previewBitmap.recycle()
                }
                val newPreviews = _uiState.value.previews.toMutableList()
                newPreviews[dayIndex] = day.copy(
                    previewBitmap = newBitmap,
                    cropOffsets = newOffsets,
                )
                _uiState.update {
                    it.copy(previews = newPreviews, rebuildingDateKey = null)
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

    fun removeImageFromDay(dateKey: String, imageIndex: Int) {
        val dayIndex = _uiState.value.previews.indexOfFirst { it.dateKey == dateKey }
        if (dayIndex < 0) return

        val day = _uiState.value.previews[dayIndex]
        if (imageIndex !in day.images.indices) return

        val newImages = day.images.toMutableList().apply { removeAt(imageIndex) }

        if (newImages.isEmpty()) {
            val newPreviews = _uiState.value.previews.toMutableList()
            if (!day.previewBitmap.isRecycled) day.previewBitmap.recycle()
            newPreviews.removeAt(dayIndex)
            _uiState.update { it.copy(previews = newPreviews) }
            return
        }

        rebuildDay(dayIndex, day.dateKey, newImages)
    }

    fun requestAddImages(dateKey: String) {
        _uiState.update { it.copy(pendingAddDateKey = dateKey) }
    }

    fun addImagesToDay(uris: List<Uri>) {
        val dateKey = _uiState.value.pendingAddDateKey
        _uiState.update { it.copy(pendingAddDateKey = null) }
        if (dateKey == null || uris.isEmpty()) return

        val dayIndex = _uiState.value.previews.indexOfFirst { it.dateKey == dateKey }
        if (dayIndex < 0) return

        val day = _uiState.value.previews[dayIndex]
        val addedImages = uris.map { GalleryImage(uri = it, takenAtMillis = 0L) }
        val newImages = day.images + addedImages

        rebuildDay(dayIndex, dateKey, newImages)
    }

    fun resetDay(dateKey: String) {
        val dayIndex = _uiState.value.previews.indexOfFirst { it.dateKey == dateKey }
        if (dayIndex < 0) return

        val day = _uiState.value.previews[dayIndex]
        if (!day.isModified) return

        rebuildDay(dayIndex, dateKey, day.originalImages, originalImages = day.originalImages)
    }

    private fun rebuildDay(
        dayIndex: Int,
        dateKey: String,
        newImages: List<GalleryImage>,
        originalImages: List<GalleryImage>? = null,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(rebuildingDateKey = dateKey, errorMessage = null) }
            try {
                val preserveOriginal = originalImages
                    ?: _uiState.value.previews.getOrNull(dayIndex)?.originalImages
                    ?: newImages
                val newDay = withContext(Dispatchers.IO) {
                    buildDayPreview(
                        dateKey,
                        newImages,
                        _uiState.value.layoutRule,
                        _uiState.value.outputAspectRatio,
                    ).copy(originalImages = preserveOriginal)
                }
                val currentPreviews = _uiState.value.previews
                if (dayIndex in currentPreviews.indices) {
                    val oldDay = currentPreviews[dayIndex]
                    if (!oldDay.previewBitmap.isRecycled) oldDay.previewBitmap.recycle()
                }
                val newPreviews = currentPreviews.toMutableList()
                if (dayIndex in newPreviews.indices) {
                    newPreviews[dayIndex] = newDay
                }
                _uiState.update {
                    it.copy(previews = newPreviews, rebuildingDateKey = null)
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
            day.cropOffsets,
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
        bucketIds: Set<Long>? = null,
    ): List<DayPreview> {
        val images = galleryRepository.queryImages(startDate, endDate, bucketIds)
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
                day.cropOffsets,
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
