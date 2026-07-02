package com.yyeira.dailycollage

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yyeira.dailycollage.model.DayPreview
import com.yyeira.dailycollage.model.GalleryImage
import com.yyeira.dailycollage.model.LayoutRule
import com.yyeira.dailycollage.model.OutputAspectRatio
import com.yyeira.dailycollage.util.PermissionHelper
import com.yyeira.dailycollage.util.ThumbnailDecoder
import kotlinx.coroutines.Dispatchers
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(0.dp))
            }

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
        LayoutRule.GRID_2 -> R.string.layout_rule_grid_2
        LayoutRule.GRID_3 -> R.string.layout_rule_grid_3
        LayoutRule.GRID_4 -> R.string.layout_rule_grid_4
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
) {
    var selectedIndex by remember(preview.dateKey) { mutableStateOf<Int?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.preview_day_label,
                    preview.dateKey,
                    preview.imageCount,
                    preview.layoutDescription,
                ),
                style = MaterialTheme.typography.titleSmall,
            )

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = preview.previewBitmap.asImageBitmap(),
                    contentDescription = preview.dateKey,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit,
                )
                if (isRebuilding) {
                    CircularProgressIndicator()
                }
            }

            if (preview.images.size > 1) {
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
                                when (val selected = selectedIndex) {
                                    null -> selectedIndex = index
                                    index -> selectedIndex = null
                                    else -> {
                                        onSwapImages(selected, index)
                                        selectedIndex = null
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
                        )
                    }
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
        }

        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelSmall,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            IconButton(
                onClick = onMoveLeft,
                enabled = enabled && index > 0,
                modifier = Modifier.size(32.dp),
            ) {
                Text("←")
            }
            IconButton(
                onClick = onMoveRight,
                enabled = enabled && index < total - 1,
                modifier = Modifier.size(32.dp),
            ) {
                Text("→")
            }
        }
    }
}

private fun Long.toLocalDate(): LocalDate {
    return Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
}
