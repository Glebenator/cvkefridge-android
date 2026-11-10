package com.cvke.fridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cvke.fridge.BuildConfig
import com.cvke.fridge.R
import com.cvke.fridge.core.AppVersion
import com.cvke.fridge.ui.theme.CvkeFridgeTheme
import com.cvke.fridge.ui.theme.IconTile
import com.cvke.fridge.ui.theme.Space

/** The only screen of the first build: the icon, the name, a short note, and the version. */
@Composable
fun StartScreen(modifier: Modifier = Modifier) {
    val colors = CvkeFridgeTheme.colors
    val version = AppVersion(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE).label
    Column(
        modifier
            .fillMaxSize()
            .background(colors.bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(Space.s6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconTile(R.drawable.ic_fridge)
        Spacer(Modifier.height(Space.s4))
        Text(
            stringResource(R.string.start_title),
            Modifier.testTag("start_title").semantics { heading() },
            color = colors.ink,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(Space.s2))
        Text(
            stringResource(R.string.start_body),
            Modifier.widthIn(max = 320.dp),
            color = colors.inkMuted,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(Space.s6))
        Text(
            stringResource(R.string.start_version, version),
            color = colors.inkMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
