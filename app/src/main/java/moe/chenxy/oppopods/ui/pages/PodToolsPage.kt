package moe.chenxy.oppopods.ui.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xiuxiu391.motobuds.R
import moe.chenxy.oppopods.pods.MotoBudsCustomEq
import moe.chenxy.oppopods.ui.components.GlassSlider
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * Pod tools page: 10-band custom EQ (0x0305/0x0306), fit test (0x0400/0x0401),
 * find-my-device (0x0405) and the detected model / feature list from opcode 0x0000.
 */
@Composable
fun PodToolsPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    eqBands: FloatArray = FloatArray(MotoBudsCustomEq.BAND_COUNT),
    onApplyEq: (FloatArray) -> Unit = {},
    onQueryEq: () -> Unit = {},
    fitLeft: Int? = null,
    fitRight: Int? = null,
    onStartFitTest: () -> Unit = {},
    onStopFitTest: () -> Unit = {},
    fmdLeft: Boolean = false,
    fmdRight: Boolean = false,
    onFmdChange: (Boolean, Boolean) -> Unit = { _, _ -> },
    modelName: String? = null,
    modelId: String? = null,
    featureNames: List<String> = emptyList(),
) {
    var bands by remember(eqBands, eqBands.contentHashCode()) {
        mutableStateOf(eqBands.copyOf(MotoBudsCustomEq.BAND_COUNT))
    }
    var localFmdLeft by remember(fmdLeft) { mutableStateOf(fmdLeft) }
    var localFmdRight by remember(fmdRight) { mutableStateOf(fmdRight) }

    fun updateBand(index: Int, value: Float) {
        val next = bands.copyOf()
        next[index] = value
        bands = next
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
            start = 12.dp,
            end = 12.dp,
        ),
    ) {
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.pod_tools_device_info))
                    Text(
                        text = modelName ?: stringResource(R.string.pod_tools_unknown_model),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (modelId != null) {
                        Text(text = modelId, modifier = Modifier.padding(top = 2.dp))
                    }
                    if (featureNames.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.pod_tools_features) + ": " + featureNames.joinToString(", "),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.padding(top = 12.dp)) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = stringResource(R.string.pod_tools_eq),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    MotoBudsCustomEq.FREQUENCIES.forEachIndexed { index, frequency ->
                        GlassSlider(
                            title = frequencyLabel(frequency),
                            value = bands.getOrElse(index) { 0f },
                            onValueChange = { updateBand(index, it) },
                            valueRange = -3f..3f,
                            valueFormatter = { "%+.1f dB".format(it) },
                        )
                    }
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        TextButton(
                            text = stringResource(R.string.pod_tools_eq_apply),
                            onClick = { onApplyEq(bands.copyOf()) },
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                        TextButton(
                            text = stringResource(R.string.pod_tools_eq_query),
                            onClick = onQueryEq,
                        )
                        TextButton(
                            text = stringResource(R.string.pod_tools_eq_reset),
                            onClick = {
                                bands = FloatArray(MotoBudsCustomEq.BAND_COUNT)
                                onApplyEq(bands.copyOf())
                            },
                        )
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.padding(top = 12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.pod_tools_fit_test))
                    Text(
                        text = fitResultText(
                            left = fitLeft,
                            right = fitRight,
                            good = stringResource(R.string.pod_tools_fit_good),
                            adjust = stringResource(R.string.pod_tools_fit_adjust),
                            leftLabel = stringResource(R.string.pod_tools_fit_left),
                            rightLabel = stringResource(R.string.pod_tools_fit_right),
                        ),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        TextButton(
                            text = stringResource(R.string.pod_tools_fit_start),
                            onClick = onStartFitTest,
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                        TextButton(
                            text = stringResource(R.string.pod_tools_fit_stop),
                            onClick = onStopFitTest,
                        )
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.padding(top = 12.dp)) {
                Text(
                    text = stringResource(R.string.pod_tools_find_my_device),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                SwitchPreference(
                    title = stringResource(R.string.pod_tools_fmd_left),
                    checked = localFmdLeft,
                    onCheckedChange = {
                        localFmdLeft = it
                        onFmdChange(it, localFmdRight)
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.pod_tools_fmd_right),
                    checked = localFmdRight,
                    onCheckedChange = {
                        localFmdRight = it
                        onFmdChange(localFmdLeft, it)
                    },
                )
            }
        }
    }
}

private fun frequencyLabel(frequency: Float): String {
    return if (frequency >= 1000f) {
        (frequency / 1000f).toInt().toString() + " kHz"
    } else {
        frequency.toInt().toString() + " Hz"
    }
}

private fun fitResultText(
    left: Int?,
    right: Int?,
    good: String,
    adjust: String,
    leftLabel: String,
    rightLabel: String,
): String {
    if (left == null && right == null) return "-"
    fun describe(value: Int?): String = when (value) {
        null -> "-"
        0x01 -> good
        else -> adjust
    }
    return leftLabel + ": " + describe(left) + "    " + rightLabel + ": " + describe(right)
}
