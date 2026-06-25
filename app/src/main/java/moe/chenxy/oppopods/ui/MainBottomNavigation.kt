package moe.chenxy.oppopods.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
            shape = RoundedCornerShape(if (floating) 50.dp else 0.dp),
        )
    } else {
        Modifier
    }

    val glassModifier = if (liquidGlassEnabled) {
        Modifier
            .clip(RoundedCornerShape(40.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.15f),
                        Color.White.copy(alpha = 0.05f),
                    )
                )
            )
            .drawWithContent {
                drawContent()
                drawRect(
                    color = Color.White.copy(alpha = 0.12f),
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, 1.dp.toPx()),
                )
            }
    } else {
        Modifier
    }

    if (floating) {
        FloatingNavigationBar(
            modifier = barModifier
                .zIndex(2f)
                .then(glassModifier)
                .then(if (liquidGlassEnabled) Modifier.height(72.dp).padding(horizontal = 16.dp) else Modifier),
            color = if (blur) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
        ) {
            tabs.forEach { tab ->
                val isSelected = selectedTab == tab
                val scale by animateFloatAsState(
                    targetValue = if (isSelected && liquidGlassEnabled) 1.15f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = 0.4f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "itemScale"
                )
                FloatingNavigationBarItem(
                    selected = isSelected,
                    onClick = { onTabClick(tab) },
                    icon = tab.icon,
                    label = tab.title(),
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
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
