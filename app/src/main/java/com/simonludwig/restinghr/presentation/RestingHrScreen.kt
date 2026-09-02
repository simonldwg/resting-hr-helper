package com.simonludwig.restinghr.presentation

import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.simonludwig.restinghr.R
import com.simonludwig.restinghr.presentation.theme.RestingHRTheme

private const val HEART_RATE_PERMISSION = "android.permission.health.READ_HEART_RATE"

@Composable
fun RestingHrScreen(viewModel: RestingHrViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.start() else viewModel.onPermissionDenied()
    }

    // Checked at press time rather than cached, so granting the permission in system settings and
    // coming back just works.
    val onStart: () -> Unit = {
        val granted = context.checkSelfPermission(HEART_RATE_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.start() else permissionLauncher.launch(HEART_RATE_PERMISSION)
    }

    // The sensor only reports to a foreground app, so leaving the app ends the run.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onInterrupted() }

    KeepScreenOn(enabled = state is RestingHrUiState.Measuring)

    RestingHrContent(state = state, onStart = onStart)
}

@Composable
fun RestingHrContent(
    state: RestingHrUiState,
    onStart: () -> Unit,
) {
    AppScaffold {
        ScreenScaffold { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when (state) {
                    RestingHrUiState.Idle -> Prompt(
                        message = stringResource(R.string.idle_prompt),
                        actionLabel = stringResource(R.string.start_measuring),
                        onAction = onStart,
                    )

                    RestingHrUiState.Measuring -> Measuring()

                    is RestingHrUiState.Result -> Result(
                        bpm = state.bpm,
                        onMeasureAgain = onStart,
                    )

                    is RestingHrUiState.Failed -> Prompt(
                        message = stringResource(state.reason.messageRes),
                        actionLabel = stringResource(R.string.try_again)
                            .takeUnless { state.reason == FailureReason.NOT_SUPPORTED },
                        onAction = onStart,
                    )
                }
            }
        }
    }
}

@Composable
private fun Measuring() {
    CircularProgressIndicator()
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.measuring_hint),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Result(bpm: Int, onMeasureAgain: () -> Unit) {
    Text(
        text = bpm.toString(),
        style = MaterialTheme.typography.numeralExtraLarge,
    )
    Text(
        text = stringResource(R.string.bpm_unit),
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(12.dp))
    Button(onClick = onMeasureAgain) {
        Text(stringResource(R.string.measure_again))
    }
}

@Composable
private fun Prompt(message: String, actionLabel: String?, onAction: () -> Unit) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
    if (actionLabel != null) {
        Spacer(Modifier.height(12.dp))
        Button(onClick = onAction) {
            Text(actionLabel)
        }
    }
}

/** Holds the display awake, but only for as long as a measurement is actually running. */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val window = LocalActivity.current?.window
    DisposableEffect(enabled, window) {
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

private val FailureReason.messageRes: Int
    get() = when (this) {
        FailureReason.NOT_SUPPORTED -> R.string.error_not_supported
        FailureReason.SENSOR_ERROR -> R.string.error_sensor
        FailureReason.NO_READINGS -> R.string.error_no_readings
        FailureReason.INTERRUPTED -> R.string.error_interrupted
        FailureReason.PERMISSION_DENIED -> R.string.error_permission_denied
    }

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun IdlePreview() {
    RestingHRTheme { RestingHrContent(RestingHrUiState.Idle, onStart = {}) }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun MeasuringPreview() {
    RestingHRTheme { RestingHrContent(RestingHrUiState.Measuring, onStart = {}) }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun ResultPreview() {
    RestingHRTheme { RestingHrContent(RestingHrUiState.Result(58), onStart = {}) }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun FailedPreview() {
    RestingHRTheme {
        RestingHrContent(RestingHrUiState.Failed(FailureReason.NO_READINGS), onStart = {})
    }
}
