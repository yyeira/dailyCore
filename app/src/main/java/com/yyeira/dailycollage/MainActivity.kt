package com.yyeira.dailycollage

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yyeira.dailycollage.model.Album
import com.yyeira.dailycollage.model.CollageLayout
import com.yyeira.dailycollage.model.CropOffset
import com.yyeira.dailycollage.model.DayPreview
import com.yyeira.dailycollage.model.GalleryImage
import com.yyeira.dailycollage.model.LayoutRule
import com.yyeira.dailycollage.model.OutputAspectRatio
import com.yyeira.dailycollage.util.PermissionHelper
import com.yyeira.dailycollage.util.ThumbnailDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val viewModel: CollageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CollageScreen(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissionState()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageScreen(viewModel: CollageViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.refreshPermissionState()
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onDeleteRequestResult(result.resultCode == Activity.RESULT_OK)
    }

    val addImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        viewModel.addImagesToDay(uris)
    }

    LaunchedEffect(uiState.pendingAddDateKey) {
        if (uiState.pendingAddDateKey != null) {
            addImageLauncher.launch("image/*")
        }
    }

    LaunchedEffect(uiState.needsPermission) {
        if (uiState.needsPermission) {
            permissionLauncher.launch(PermissionHelper.requiredPermissions())
        }
    }

    LaunchedEffect(uiState.pendingDeleteIntentSender) {
        val intentSender = uiState.pendingDeleteIntentSender ?: return@LaunchedEffect
        deleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        viewModel.clearPendingDeleteIntent()
    }

    val hasPreviews = uiState.previews.isNotEmpty()
    var settingsExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(hasPreviews) {
        if (hasPreviews) settingsExpanded = false
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(0.dp))
            }

            if (hasPreviews && !settingsExpanded) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { settingsExpanded = true },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val datePart = if (uiState.startDate != null && uiState.endDate != null) {
                                "${uiState.startDate!!.format(dateFormatter)} ~ ${uiState.endDate!!.format(dateFormatter)}"
                            } else ""
                            val albumPart = if (uiState.selectedAlbumIds.isEmpty()) {
                                stringResource(R.string.album_all)
                            } else {
                                uiState.availableAlbums
                                    .filter { it.bucketId in uiState.selectedAlbumIds }
                                    .joinToString("、") { it.displayName }
                            }
                            Text(
                                text = "$datePart · $albumPart",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = stringResource(R.string.settings_expand),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            if (!hasPreviews || settingsExpanded) {
                item {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isProcessing && !uiState.isLoadingPreview,
                    ) {
                        val label = when {
                            uiState.startDate != null && uiState.endDate != null -> {
                                "${uiState.startDate!!.format(dateFormatter)} ~ ${uiState.endDate!!.format(dateFormatter)}"
                            }
                            else -> stringResource(R.string.select_date_range)
                        }
                        Text(label)
                    }
                }

                if (uiState.startDate != null && uiState.endDate != null) {
                    item {
                        AlbumSelector(
                            albums = uiState.availableAlbums,
                            selectedIds = uiState.selectedAlbumIds,
                            isLoading = uiState.isLoadingAlbums,
                            enabled = !uiState.isProcessing && !uiState.isLoadingPreview,
                            onToggle = viewModel::toggleAlbum,
                            onSelectAll = viewModel::selectAllAlbums,
                        )
                    }
                }

                item {
                    LayoutRuleSelector(
                        selectedRule = uiState.layoutRule,
                        onRuleSelected = viewModel::setLayoutRule,
                        enabled = !uiState.isProcessing && !uiState.isLoadingPreview,
                    )
                }

                item {
                    OutputAspectRatioSelector(
                        selectedRatio = uiState.outputAspectRatio,
                        onRatioSelected = viewModel::setOutputAspectRatio,
                        enabled = !uiState.isProcessing && !uiState.isLoadingPreview,
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.delete_originals),
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = uiState.deleteOriginalsAfterCollage,
                            onCheckedChange = viewModel::setDeleteOriginalsAfterCollage,
                            enabled = !uiState.isProcessing && !uiState.isLoadingPreview,
                        )
                    }
                }

                item {
                    OutlinedButton(
                        onClick = { viewModel.generatePreview() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isProcessing && !uiState.isLoadingPreview,
                    ) {
                        Text(stringResource(R.string.generate_preview))
                    }
                }

                if (hasPreviews) {
                    item {
                        TextButton(
                            onClick = { settingsExpanded = false },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.settings_collapse))
                        }
                    }
                }
            }

            if (uiState.isLoadingPreview) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.preview_loading))
                    }
                }
            }

            if (uiState.previews.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.preview_title, uiState.previews.size),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.edit_preview_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                items(uiState.previews, key = { it.dateKey }) { preview ->
                    PreviewDayCard(
                        preview = preview,
                        isRebuilding = uiState.rebuildingDateKey == preview.dateKey,
                        onSwapImages = { from, to ->
                            viewModel.swapImagesInDay(preview.dateKey, from, to)
                        },
                        onMoveImage = { from, to ->
                            viewModel.moveImageInDay(preview.dateKey, from, to)
                        },
                        onCropOffsetChanged = { index, offset ->
                            viewModel.updateCropOffset(preview.dateKey, index, offset)
                        },
                        onRemoveImage = { index ->
                            viewModel.removeImageFromDay(preview.dateKey, index)
                        },
                        onAddImages = {
                            viewModel.requestAddImages(preview.dateKey)
                        },
                        onResetDay = {
                            viewModel.resetDay(preview.dateKey)
                        },
                    )
                }

                item {
                    Button(
                        onClick = { viewModel.confirmCollage() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isProcessing && !uiState.isLoadingPreview,
                    ) {
                        Text(stringResource(R.string.confirm_collage))
                    }
                }
            }

            if (uiState.needsPermission) {
                item {
                    Text(
                        text = stringResource(R.string.permission_required),
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(
                        onClick = { context.startActivity(viewModel.openAppSettings()) },
                    ) {
                        Text(stringResource(R.string.open_settings))
                    }
                }
            }

            if (uiState.isProcessing) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(
                                R.string.processing,
                                uiState.progressDate.orEmpty(),
                                uiState.progressCurrent,
                                uiState.progressTotal,
                            ),
                        )
                    }
                }
            }

            uiState.warningMessage?.let { message ->
                item {
                    Text(text = message, color = MaterialTheme.colorScheme.tertiary)
                }
            }

            uiState.resultMessage?.let { message ->
                item {
                    Text(text = message, color = MaterialTheme.colorScheme.primary)
                }
            }

            uiState.errorMessage?.let { message ->
                item {
                    Text(text = message, color = MaterialTheme.colorScheme.error)
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = uiState.startDate?.atStartOfDay(ZoneId.systemDefault())
                ?.toInstant()?.toEpochMilli(),
            initialSelectedEndDateMillis = uiState.endDate?.atStartOfDay(ZoneId.systemDefault())
                ?.toInstant()?.toEpochMilli(),
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = pickerState.selectedStartDateMillis?.toLocalDate()
                        val end = pickerState.selectedEndDateMillis?.toLocalDate()
                        viewModel.setDateRange(start, end)
                        showDatePicker = false
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            },
        ) {
            DateRangePicker(state = pickerState, modifier = Modifier.fillMaxWidth())
        }
    }

}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlbumSelector(
    albums: List<Album>,
    selectedIds: Set<Long>,
    isLoading: Boolean,
    enabled: Boolean,
    onToggle: (Long) -> Unit,
    onSelectAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.album_selector_title),
            style = MaterialTheme.typography.titleSmall,
        )
        when {
            isLoading -> Text(
                text = stringResource(R.string.album_loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            albums.isEmpty() -> Text(
                text = stringResource(R.string.album_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedIds.isEmpty(),
                    onClick = onSelectAll,
                    enabled = enabled,
                    label = { Text(stringResource(R.string.album_all)) },
                )
                albums.forEach { album ->
                    FilterChip(
                        selected = album.bucketId in selectedIds,
                        onClick = { onToggle(album.bucketId) },
                        enabled = enabled,
                        label = {
                            Text(stringResource(R.string.album_item, album.displayName, album.imageCount))
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayoutRuleSelector(
    selectedRule: LayoutRule,
    onRuleSelected: (LayoutRule) -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.layout_rule_title),
            style = MaterialTheme.typography.titleSmall,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LayoutRule.entries.forEach { rule ->
                FilterChip(
                    selected = selectedRule == rule,
                    onClick = { onRuleSelected(rule) },
                    enabled = enabled,
                    label = { Text(stringResource(layoutRuleLabelRes(rule))) },
                )
            }
        }
    }
}

@Composable
private fun layoutRuleLabelRes(rule: LayoutRule): Int {
    return when (rule) {
        LayoutRule.AUTO -> R.string.layout_rule_auto
        LayoutRule.VERTICAL -> R.string.layout_rule_vertical
        LayoutRule.HORIZONTAL -> R.string.layout_rule_horizontal
        LayoutRule.HERO_TOP -> R.string.layout_rule_hero_top
        LayoutRule.HERO_LEFT -> R.string.layout_rule_hero_left
        LayoutRule.HERO_RIGHT -> R.string.layout_rule_hero_right
        LayoutRule.GRID_2 -> R.string.layout_rule_grid_2
        LayoutRule.GRID_3 -> R.string.layout_rule_grid_3
        LayoutRule.GRID_4 -> R.string.layout_rule_grid_4
        LayoutRule.GRID_SQUARE -> R.string.layout_rule_grid_square
        LayoutRule.GRID_9 -> R.string.layout_rule_grid_9
        LayoutRule.FIT_2 -> R.string.layout_rule_fit_2
        LayoutRule.FIT_3 -> R.string.layout_rule_fit_3
        LayoutRule.FIT_4 -> R.string.layout_rule_fit_4
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OutputAspectRatioSelector(
    selectedRatio: OutputAspectRatio,
    onRatioSelected: (OutputAspectRatio) -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.output_ratio_title),
            style = MaterialTheme.typography.titleSmall,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutputAspectRatio.entries.forEach { ratio ->
                FilterChip(
                    selected = selectedRatio == ratio,
                    onClick = { onRatioSelected(ratio) },
                    enabled = enabled,
                    label = { Text(stringResource(outputAspectRatioLabelRes(ratio))) },
                )
            }
        }
    }
}

@Composable
private fun outputAspectRatioLabelRes(ratio: OutputAspectRatio): Int {
    return when (ratio) {
        OutputAspectRatio.NATURAL -> R.string.output_ratio_natural
        OutputAspectRatio.RATIO_1_1 -> R.string.output_ratio_1_1
        OutputAspectRatio.RATIO_4_3 -> R.string.output_ratio_4_3
        OutputAspectRatio.RATIO_3_4 -> R.string.output_ratio_3_4
        OutputAspectRatio.RATIO_16_9 -> R.string.output_ratio_16_9
        OutputAspectRatio.RATIO_9_16 -> R.string.output_ratio_9_16
    }
}

@Composable
private fun PreviewDayCard(
    preview: DayPreview,
    isRebuilding: Boolean,
    onSwapImages: (Int, Int) -> Unit,
    onMoveImage: (Int, Int) -> Unit,
    onCropOffsetChanged: (Int, CropOffset) -> Unit,
    onRemoveImage: (Int) -> Unit,
    onAddImages: () -> Unit,
    onResetDay: () -> Unit,
) {
    var selectedIndex by remember(preview.dateKey) { mutableStateOf<Int?>(null) }
    var showZoomDialog by remember { mutableStateOf(false) }
    var editingIndex by remember(preview.dateKey) { mutableStateOf(-1) }

    val cellRects = remember(preview.layout, preview.previewBitmap.width, preview.previewBitmap.height) {
        computeNormalizedCellRects(preview.layout, preview.previewBitmap.width, preview.previewBitmap.height)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        R.string.preview_day_label,
                        preview.dateKey,
                        preview.imageCount,
                        preview.layoutDescription,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (preview.isModified) {
                    TextButton(
                        onClick = onResetDay,
                        enabled = !isRebuilding,
                    ) {
                        Text(stringResource(R.string.reset_day))
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = preview.previewBitmap.asImageBitmap(),
                    contentDescription = preview.dateKey,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(isRebuilding) {
                            if (isRebuilding) return@pointerInput
                            detectTapGestures(
                                onDoubleTap = { showZoomDialog = true },
                            )
                        },
                    contentScale = ContentScale.Fit,
                )
                if (isRebuilding) {
                    CircularProgressIndicator()
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(preview.images, key = { _, image -> image.uri }) { index, image ->
                    ImageOrderEditorItem(
                        image = image,
                        index = index,
                        total = preview.images.size,
                        isSelected = selectedIndex == index,
                        enabled = !isRebuilding,
                        onClick = {
                            if (preview.images.size > 1) {
                                when (val selected = selectedIndex) {
                                    null -> selectedIndex = index
                                    index -> selectedIndex = null
                                    else -> {
                                        onSwapImages(selected, index)
                                        selectedIndex = null
                                    }
                                }
                            }
                        },
                        onMoveLeft = {
                            if (index > 0) {
                                onMoveImage(index, index - 1)
                                selectedIndex = index - 1
                            }
                        },
                        onMoveRight = {
                            if (index < preview.images.lastIndex) {
                                onMoveImage(index, index + 1)
                                selectedIndex = index + 1
                            }
                        },
                        onOpenCropEditor = {
                            editingIndex = index
                        },
                        onRemove = {
                            onRemoveImage(index)
                            if (selectedIndex == index) selectedIndex = null
                        },
                    )
                }

                item(key = "add_button") {
                    AddImageButton(
                        enabled = !isRebuilding,
                        onClick = onAddImages,
                    )
                }
            }
        }
    }

    if (showZoomDialog) {
        ZoomablePreviewDialog(
            preview = preview,
            cellRects = cellRects,
            onSwapImages = onSwapImages,
            onCropOffsetChanged = onCropOffsetChanged,
            onDismiss = { showZoomDialog = false },
        )
    }

    if (editingIndex >= 0 && editingIndex in preview.images.indices) {
        val cell = preview.layout.cells.firstOrNull { it.imageIndex == editingIndex }
        if (cell != null) {
            CropEditorDialog(
                imageUri = preview.images[editingIndex].uri,
                cellAspectRatio = cell.width.toFloat() / cell.height.coerceAtLeast(1),
                currentOffset = preview.cropOffsets[editingIndex] ?: CropOffset.CENTER,
                onConfirm = { offset ->
                    onCropOffsetChanged(editingIndex, offset)
                    editingIndex = -1
                },
                onDismiss = { editingIndex = -1 },
            )
        }
    }
}

@Composable
private fun CropEditorDialog(
    imageUri: Uri,
    cellAspectRatio: Float,
    currentOffset: CropOffset,
    onConfirm: (CropOffset) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    var baseZoom by remember { mutableFloatStateOf(1f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(imageUri) {
        bitmap = withContext(Dispatchers.IO) {
            ThumbnailDecoder.decodeLarge(context.contentResolver, imageUri, 1200)
        }
    }

    val frameLeft: Float
    val frameTop: Float
    val frameW: Float
    val frameH: Float
    if (containerSize.width > 0 && containerSize.height > 0) {
        val maxW = containerSize.width * 0.85f
        val maxH = containerSize.height * 0.6f
        if (maxW / cellAspectRatio <= maxH) {
            frameW = maxW
            frameH = maxW / cellAspectRatio
        } else {
            frameH = maxH
            frameW = maxH * cellAspectRatio
        }
        frameLeft = (containerSize.width - frameW) / 2f
        frameTop = (containerSize.height - frameH) / 2f
    } else {
        frameLeft = 0f; frameTop = 0f; frameW = 0f; frameH = 0f
    }

    LaunchedEffect(bitmap, containerSize) {
        val bmp = bitmap ?: return@LaunchedEffect
        if (containerSize.width <= 0 || frameW <= 0f) return@LaunchedEffect
        if (initialized) return@LaunchedEffect
        val imgW = bmp.width.toFloat()
        val imgH = bmp.height.toFloat()
        val fitScale = minOf(containerSize.width / imgW, containerSize.height / imgH)
        val imageFitW = imgW * fitScale
        val imageFitH = imgH * fitScale
        baseZoom = maxOf(frameW / imageFitW, frameH / imageFitH)

        val initZoom = baseZoom * currentOffset.scale.coerceAtLeast(1f)
        zoom = initZoom
        val panMaxX = ((imageFitW * initZoom - frameW) / 2f).coerceAtLeast(0f)
        val panMaxY = ((imageFitH * initZoom - frameH) / 2f).coerceAtLeast(0f)
        panX = ((0.5f - currentOffset.x) * 2f * panMaxX).coerceIn(-panMaxX, panMaxX)
        panY = ((0.5f - currentOffset.y) * 2f * panMaxY).coerceIn(-panMaxY, panMaxY)
        initialized = true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { containerSize = it }
                .pointerInput(baseZoom) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        val bmp = bitmap ?: return@detectTransformGestures
                        if (containerSize.width <= 0 || frameW <= 0f) return@detectTransformGestures
                        val newZoom = (zoom * gestureZoom).coerceIn(baseZoom, baseZoom * 5f)
                        zoom = newZoom
                        val imgW = bmp.width.toFloat()
                        val imgH = bmp.height.toFloat()
                        val fitScale = minOf(containerSize.width / imgW, containerSize.height / imgH)
                        val pmX = ((imgW * fitScale * newZoom - frameW) / 2f).coerceAtLeast(0f)
                        val pmY = ((imgH * fitScale * newZoom - frameH) / 2f).coerceAtLeast(0f)
                        panX = (panX + pan.x).coerceIn(-pmX, pmX)
                        panY = (panY + pan.y).coerceIn(-pmY, pmY)
                    }
                },
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = zoom,
                            scaleY = zoom,
                            translationX = panX,
                            translationY = panY,
                        ),
                    contentScale = ContentScale.Fit,
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                )
            }

            if (frameW > 0f) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val dim = Color.Black.copy(alpha = 0.55f)
                    drawRect(dim, Offset.Zero, Size(size.width, frameTop))
                    drawRect(dim, Offset(0f, frameTop + frameH), Size(size.width, size.height - frameTop - frameH))
                    drawRect(dim, Offset(0f, frameTop), Size(frameLeft, frameH))
                    drawRect(dim, Offset(frameLeft + frameW, frameTop), Size(size.width - frameLeft - frameW, frameH))
                    drawRect(Color.White, Offset(frameLeft, frameTop), Size(frameW, frameH), style = Stroke(2.dp.toPx()))
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 16.dp)
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.25f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel), color = Color.White)
                }
                TextButton(onClick = {
                    zoom = baseZoom
                    panX = 0f
                    panY = 0f
                }) {
                    Text(stringResource(R.string.crop_editor_reset), color = Color.White)
                }
                Button(onClick = {
                    val bmp = bitmap ?: return@Button
                    if (containerSize.width <= 0 || frameW <= 0f) return@Button
                    val imgW = bmp.width.toFloat()
                    val imgH = bmp.height.toFloat()
                    val fitScale = minOf(containerSize.width / imgW, containerSize.height / imgH)
                    val pmX = ((imgW * fitScale * zoom - frameW) / 2f).coerceAtLeast(0f)
                    val pmY = ((imgH * fitScale * zoom - frameH) / 2f).coerceAtLeast(0f)
                    val scale = zoom / baseZoom
                    val normX = if (pmX > 0f) (0.5f - panX / (2f * pmX)).coerceIn(0f, 1f) else 0.5f
                    val normY = if (pmY > 0f) (0.5f - panY / (2f * pmY)).coerceIn(0f, 1f) else 0.5f
                    onConfirm(CropOffset(normX, normY, scale).coerced())
                }) {
                    Text(stringResource(R.string.crop_editor_confirm))
                }
            }
        }
    }
}

@Composable
private fun ImageOrderEditorItem(
    image: GalleryImage,
    index: Int,
    total: Int,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onOpenCropEditor: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    var thumbnail by remember(image.uri) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(image.uri) {
        thumbnail = withContext(Dispatchers.IO) {
            ThumbnailDecoder.decode(context.contentResolver, image.uri)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = RoundedCornerShape(8.dp),
                )
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .clip(RoundedCornerShape(bottomStart = 8.dp))
                    .clickable(enabled = enabled, onClick = onRemove)
                    .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "×",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onError,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .size(16.dp)
                        .padding(0.dp),
                )
            }
        }

        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelSmall,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            IconButton(
                onClick = onMoveLeft,
                enabled = enabled && index > 0,
                modifier = Modifier.size(24.dp),
            ) {
                Text("←", style = MaterialTheme.typography.labelSmall)
            }
            IconButton(
                onClick = onOpenCropEditor,
                enabled = enabled,
                modifier = Modifier.size(24.dp),
            ) {
                Text("✎", style = MaterialTheme.typography.labelSmall)
            }
            IconButton(
                onClick = onMoveRight,
                enabled = enabled && index < total - 1,
                modifier = Modifier.size(24.dp),
            ) {
                Text("→", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun AddImageButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(8.dp),
                )
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = stringResource(R.string.add_images),
            style = MaterialTheme.typography.labelSmall,
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ZoomablePreviewDialog(
    preview: DayPreview,
    cellRects: List<NormalizedCellRect>,
    onSwapImages: (Int, Int) -> Unit,
    onCropOffsetChanged: (Int, CropOffset) -> Unit,
    onDismiss: () -> Unit,
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var selectedCellIndex by remember { mutableStateOf(-1) }
    var editingCropIndex by remember { mutableStateOf(-1) }
    var isDraggingImage by remember { mutableStateOf(false) }
    var dragSourceIndex by remember { mutableStateOf(-1) }
    var dragTargetIndex by remember { mutableStateOf(-1) }
    var pendingCrop by remember { mutableStateOf<Pair<Int, CropOffset>?>(null) }

    val currentCropOffsets by rememberUpdatedState(preview.cropOffsets)
    val currentOnCropChanged by rememberUpdatedState(onCropOffsetChanged)

    LaunchedEffect(pendingCrop) {
        val (idx, offset) = pendingCrop ?: return@LaunchedEffect
        delay(150)
        currentOnCropChanged(idx, offset)
        pendingCrop = null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(cellRects) {
                    val boxW = size.width.toFloat()
                    val boxH = size.height.toFloat()
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.5f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 3f
                            }
                        },
                        onTap = { tapOffset ->
                            val bw = preview.previewBitmap.width.toFloat()
                            val bh = preview.previewBitmap.height.toFloat()
                            val fitScale = minOf(boxW / bw, boxH / bh)
                            val dw = bw * fitScale
                            val dh = bh * fitScale
                            val dl = (boxW - dw) / 2f
                            val dt = (boxH - dh) / 2f
                            val cx = boxW / 2f
                            val cy = boxH / 2f

                            val lx = (tapOffset.x - cx - offsetX) / scale + cx
                            val ly = (tapOffset.y - cy - offsetY) / scale + cy
                            val normX = (lx - dl) / dw
                            val normY = (ly - dt) / dh

                            val cellIndex = hitTestCell(cellRects, normX, normY)
                            if (cellIndex >= 0) {
                                val selected = selectedCellIndex
                                when {
                                    selected < 0 -> selectedCellIndex = cellIndex
                                    selected == cellIndex -> {
                                        editingCropIndex = cellIndex
                                        selectedCellIndex = -1
                                    }
                                    else -> {
                                        onSwapImages(selected, cellIndex)
                                        selectedCellIndex = -1
                                    }
                                }
                            } else {
                                selectedCellIndex = -1
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (isDraggingImage) return@detectTransformGestures

                        val selIdx = selectedCellIndex
                        if (selIdx >= 0) {
                            val rect = cellRects.firstOrNull { it.imageIndex == selIdx }
                            if (rect != null) {
                                val base = pendingCrop?.let { (i, o) ->
                                    if (i == selIdx) o else null
                                } ?: currentCropOffsets[selIdx] ?: CropOffset.CENTER

                                val newCropScale = (base.scale * zoom).coerceIn(1f, 5f)
                                val bw = preview.previewBitmap.width.toFloat()
                                val bh = preview.previewBitmap.height.toFloat()
                                val fitSc = minOf(
                                    size.width.toFloat() / bw,
                                    size.height.toFloat() / bh,
                                )
                                val cellW = (rect.right - rect.left) * bw * fitSc * scale
                                val cellH = (rect.bottom - rect.top) * bh * fitSc * scale
                                val sensX = if (cellW > 0) 1f / (cellW * base.scale) else 0f
                                val sensY = if (cellH > 0) 1f / (cellH * base.scale) else 0f

                                pendingCrop = selIdx to CropOffset(
                                    x = (base.x - pan.x * sensX).coerceIn(0f, 1f),
                                    y = (base.y - pan.y * sensY).coerceIn(0f, 1f),
                                    scale = newCropScale,
                                )
                            }
                        } else {
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = newScale
                            if (newScale > 1f) {
                                val maxX = (newScale - 1f) * size.width / 2f
                                val maxY = (newScale - 1f) * size.height / 2f
                                offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
                }
                .pointerInput(cellRects) {
                    val boxW = size.width.toFloat()
                    val boxH = size.height.toFloat()
                    val bw = preview.previewBitmap.width.toFloat()
                    val bh = preview.previewBitmap.height.toFloat()
                    val fitSc = minOf(boxW / bw, boxH / bh)
                    val dw = bw * fitSc
                    val dh = bh * fitSc
                    val dl = (boxW - dw) / 2f
                    val dt = (boxH - dh) / 2f
                    val cx = boxW / 2f
                    val cy = boxH / 2f

                    fun hitAtScreen(pos: Offset): Int {
                        val lx = (pos.x - cx - offsetX) / scale + cx
                        val ly = (pos.y - cy - offsetY) / scale + cy
                        return hitTestCell(cellRects, (lx - dl) / dw, (ly - dt) / dh)
                    }

                    detectDragGesturesAfterLongPress(
                        onDragStart = { startPos ->
                            val cellIndex = hitAtScreen(startPos)
                            if (cellIndex >= 0) {
                                dragSourceIndex = cellIndex
                                dragTargetIndex = -1
                                isDraggingImage = true
                                selectedCellIndex = -1
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (!isDraggingImage) return@detectDragGesturesAfterLongPress
                            val target = hitAtScreen(change.position)
                            dragTargetIndex = if (target >= 0 && target != dragSourceIndex) target else -1
                        },
                        onDragEnd = {
                            if (dragSourceIndex >= 0 && dragTargetIndex >= 0) {
                                onSwapImages(dragSourceIndex, dragTargetIndex)
                            }
                            isDraggingImage = false
                            dragSourceIndex = -1
                            dragTargetIndex = -1
                        },
                        onDragCancel = {
                            isDraggingImage = false
                            dragSourceIndex = -1
                            dragTargetIndex = -1
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    },
            ) {
                Image(
                    bitmap = preview.previewBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val bw = preview.previewBitmap.width.toFloat()
                    val bh = preview.previewBitmap.height.toFloat()
                    val cs = minOf(size.width / bw, size.height / bh)
                    val dw = bw * cs
                    val dh = bh * cs
                    val dl = (size.width - dw) / 2f
                    val dt = (size.height - dh) / 2f
                    val sw = 3.dp.toPx()

                    fun drawCellBorder(idx: Int, color: Color) {
                        val rect = cellRects.firstOrNull { it.imageIndex == idx }
                            ?: return
                        drawRect(
                            color = color,
                            topLeft = Offset(dl + rect.left * dw, dt + rect.top * dh),
                            size = Size(
                                (rect.right - rect.left) * dw,
                                (rect.bottom - rect.top) * dh,
                            ),
                            style = Stroke(width = sw),
                        )
                    }

                    val srcIdx = dragSourceIndex
                    val tgtIdx = dragTargetIndex
                    if (srcIdx >= 0) {
                        drawCellBorder(srcIdx, Color.Yellow.copy(alpha = 0.85f))
                        if (tgtIdx >= 0) {
                            drawCellBorder(tgtIdx, Color.Cyan.copy(alpha = 0.85f))
                        }
                    } else {
                        val selIdx = selectedCellIndex
                        if (selIdx >= 0) {
                            drawCellBorder(selIdx, Color.Yellow.copy(alpha = 0.85f))
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 16.dp)
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.3f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✕",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Text(
                text = when {
                    isDraggingImage -> stringResource(R.string.zoom_hint_drag)
                    selectedCellIndex >= 0 -> stringResource(R.string.zoom_hint_swap)
                    else -> stringResource(R.string.zoom_hint_select)
                },
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp),
            )
        }
    }

    if (editingCropIndex >= 0 && editingCropIndex in preview.images.indices) {
        val cell = preview.layout.cells.firstOrNull { it.imageIndex == editingCropIndex }
        if (cell != null) {
            CropEditorDialog(
                imageUri = preview.images[editingCropIndex].uri,
                cellAspectRatio = cell.width.toFloat() / cell.height.coerceAtLeast(1),
                currentOffset = preview.cropOffsets[editingCropIndex] ?: CropOffset.CENTER,
                onConfirm = { offset ->
                    onCropOffsetChanged(editingCropIndex, offset)
                    editingCropIndex = -1
                },
                onDismiss = { editingCropIndex = -1 },
            )
        }
    }
}

private const val PREVIEW_CANVAS_WIDTH = 360

private data class NormalizedCellRect(
    val imageIndex: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/**
 * Maps layout cells to normalized (0..1) coordinates in the final preview bitmap,
 * accounting for the OutputAspectRatioFitter center-crop transformation.
 */
private fun computeNormalizedCellRects(
    layout: CollageLayout,
    bitmapWidth: Int,
    bitmapHeight: Int,
): List<NormalizedCellRect> {
    val layoutScale = PREVIEW_CANVAS_WIDTH.toFloat() / layout.canvasWidth
    val naturalHeight = layout.canvasHeight * layoutScale

    val fitterScale = maxOf(bitmapWidth / PREVIEW_CANVAS_WIDTH.toFloat(), bitmapHeight / naturalHeight)
    val drawWidth = PREVIEW_CANVAS_WIDTH * fitterScale
    val drawHeight = naturalHeight * fitterScale
    val offsetX = (bitmapWidth - drawWidth) / 2f
    val offsetY = (bitmapHeight - drawHeight) / 2f

    return layout.cells.map { cell ->
        val cellLeft = cell.left * layoutScale * fitterScale + offsetX
        val cellTop = cell.top * layoutScale * fitterScale + offsetY
        val cellWidth = cell.width * layoutScale * fitterScale
        val cellHeight = cell.height * layoutScale * fitterScale

        NormalizedCellRect(
            imageIndex = cell.imageIndex,
            left = cellLeft / bitmapWidth,
            top = cellTop / bitmapHeight,
            right = (cellLeft + cellWidth) / bitmapWidth,
            bottom = (cellTop + cellHeight) / bitmapHeight,
        )
    }
}

private fun hitTestCell(
    cellRects: List<NormalizedCellRect>,
    normX: Float,
    normY: Float,
): Int {
    return cellRects.firstOrNull { rect ->
        normX in rect.left..rect.right && normY in rect.top..rect.bottom
    }?.imageIndex ?: -1
}

private fun Long.toLocalDate(): LocalDate {
    return Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
}
