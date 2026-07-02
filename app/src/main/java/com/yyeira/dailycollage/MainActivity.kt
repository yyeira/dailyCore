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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yyeira.dailycollage.model.DayPreview
import com.yyeira.dailycollage.util.PermissionHelper
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.columns))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf(2, 3).forEachIndexed { index, columnCount ->
                            SegmentedButton(
                                selected = uiState.columns == columnCount,
                                onClick = { viewModel.setColumns(columnCount) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                                enabled = !uiState.isProcessing && !uiState.isLoadingPreview,
                            ) {
                                Text(columnCount.toString())
                            }
                        }
                    }
                }
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

                items(uiState.previews, key = { it.dateKey }) { preview ->
                    PreviewDayCard(preview)
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

@Composable
private fun PreviewDayCard(preview: DayPreview) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.preview_day_label, preview.dateKey, preview.imageCount),
                style = MaterialTheme.typography.titleSmall,
            )
            Image(
                bitmap = preview.previewBitmap.asImageBitmap(),
                contentDescription = preview.dateKey,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
            )
        }
    }
}

private fun Long.toLocalDate(): LocalDate {
    return Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
}
