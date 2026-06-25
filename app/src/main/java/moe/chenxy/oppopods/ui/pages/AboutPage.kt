package moe.chenxy.oppopods.ui.pages

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xiuxiu391.motobuds.R
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card

@Composable
fun AboutPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current

    fun openUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {}
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
            start = 12.dp,
            end = 12.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card {
                BasicComponent(
                    title = stringResource(R.string.about),
                    summary = stringResource(R.string.app_subtitle)
                )
                BasicComponent(
                    title = "Motobuds for Hyper",
                    summary = "https://github.com/pubglite55/motobuds-for-hyper",
                    onClick = { openUrl("https://github.com/pubglite55/motobuds-for-hyper") }
                )
            }
        }
        item {
            Card {
                BasicComponent(
                    title = stringResource(R.string.based_on),
                    summary = "OppoPods-Enhanced"
                )
                BasicComponent(
<<<<<<< HEAD
                    title = "OppoPods-Enhanced",
                    summary = "https://github.com/1812z/OppoPods",
                    onClick = { openUrl("https://github.com/1812z/OppoPods") }
=======
                    title = "HyperPods",
                    summary = "https://github.com/Art-Chen/HyperPods",
                    onClick = { openUrl("https://github.com/Art-Chen/HyperPods") }
                )
                BasicComponent(
                    title = "LiquidGlass",
                    summary = "https://github.com/QmDeve/AndroidLiquidGlassView",
                    onClick = { openUrl("https://github.com/QmDeve/AndroidLiquidGlassView") }
>>>>>>> dec64d3 (v1.1: UI improvements and bug fixes)
                )
            }
        }
    }
}
