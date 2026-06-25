package moe.chenxy.oppopods.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MainBottomNavigation(
    tabs: List<MainTab>,
    selectedTab: MainTab,
    floating: Boolean,
    blur: Boolean,
    backdrop: LayerBackdrop?,
    onTabClick: (MainTab) -> Unit,
    liquidGlassEnabled: Boolean = false,
) {
    val barModifier = if (blur && backdrop != null) {
        Modifier.textureBlur(
            backdrop = backdrop,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(if (floating) 50.dp else 0.dp),
        )
    } else {
        Modifier
    }

    val glassModifier = if (liquidGlassEnabled) {
        Modifier
            .graphicsLayer {
                renderEffect = android.graphics.RenderEffect.createBlurEffect(
                    20f, 20f, android.graphics.Shader.TileMode.CLAMP
                ).asComposeRenderEffect()
            }
            .drawWithContent {
                drawContent()
                drawRect(color = Color.White.copy(alpha = 0.10f))
                drawRect(
                    color = Color.White.copy(alpha = 0.18f),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()),
                )
            }
    } else {
        Modifier
    }

    if (floating) {
        FloatingNavigationBar(
            modifier = barModifier
                .zIndex(2f)
                .then(glassModifier),
            color = if (blur) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
        ) {
            tabs.forEach { tab ->
                FloatingNavigationBarItem(
                    selected = selectedTab == tab,
                    onClick = { onTabClick(tab) },
                    icon = tab.icon,
                    label = tab.title(),
                )
            }
        }
    } else {
        NavigationBar(
            modifier = barModifier
                .zIndex(2f)
                .then(glassModifier),
            color = if (blur) Color.Transparent else MiuixTheme.colorScheme.surface,
            showDivider = false,
        ) {
            tabs.forEach { tab ->
                NavigationBarItem(
                    selected = selectedTab == tab,
                    onClick = { onTabClick(tab) },
                    icon = tab.icon,
                    label = tab.title(),
                )
            }
        }
    }
}
