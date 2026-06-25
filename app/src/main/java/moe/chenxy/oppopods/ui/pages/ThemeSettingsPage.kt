package moe.chenxy.oppopods.ui.pages

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xiuxiu391.motobuds.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun ThemeSettingsPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    themeMode: MutableState<Int> = mutableStateOf(0),
    onThemeModeChange: (Int) -> Unit = {},
    accentMode: MutableState<Int> = mutableStateOf(0),
    onAccentModeChange: (Int) -> Unit = {},
    floatingBottomBar: MutableState<Boolean> = mutableStateOf(false),
    onFloatingBottomBarChange: (Boolean) -> Unit = {},
    blurBottomBar: MutableState<Boolean> = mutableStateOf(false),
    onBlurBottomBarChange: (Boolean) -> Unit = {},
    liquidGlassEnabled: MutableState<Boolean> = mutableStateOf(false),
    onLiquidGlassChange: (Boolean) -> Unit = {},
) {
    val themeOptions = listOf(
        stringResource(R.string.theme_follow_system),
        stringResource(R.string.theme_light),
        stringResource(R.string.theme_dark),
    )
    val accentOptions = listOf(
        stringResource(R.string.color_default),
        stringResource(R.string.color_monet),
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(
            top = 12.dp,
            bottom = 12.dp,
            start = 12.dp,
            end = 12.dp,
        ),
    ) {
        item {
            Card {
                OverlayDropdownPreference(
                    title = stringResource(R.string.theme_title),
                    items = themeOptions,
                    selectedIndex = themeMode.value.coerceIn(themeOptions.indices),
                    onSelectedIndexChange = { onThemeModeChange(it) },
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.theme_color),
                    summary = stringResource(R.string.theme_color_summary),
                    items = accentOptions,
                    selectedIndex = accentMode.value.coerceIn(accentOptions.indices),
                    onSelectedIndexChange = { onAccentModeChange(it) },
                )
            }
        }

        item {
            Card(
                modifier = Modifier.padding(top = 12.dp)
            ) {
                SwitchPreference(
                    title = stringResource(R.string.liquid_glass),
                    summary = stringResource(R.string.liquid_glass_summary),
                    checked = liquidGlassEnabled.value,
                    onCheckedChange = { onLiquidGlassChange(it) },
                )
            }
        }

        if (liquidGlassEnabled.value) {
            item {
                Card(
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.floating_bottom_bar),
                        summary = stringResource(R.string.floating_bottom_bar_summary),
                        checked = floatingBottomBar.value,
                        onCheckedChange = { onFloatingBottomBarChange(it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.blur_bottom_bar),
                        summary = stringResource(R.string.blur_bottom_bar_summary),
                        checked = blurBottomBar.value,
                        onCheckedChange = { onBlurBottomBarChange(it) },
                    )
                }
            }
        }
    }
}
