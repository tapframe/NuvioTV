@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioColors
import com.nuvio.tv.updater.UpdateViewModel

@Composable
fun AboutScreen(
    onBackPress: () -> Unit = {}
) {
    BackHandler { onBackPress() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioColors.Background)
            .padding(horizontal = 48.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AboutSettingsContent()
    }
}

@Composable
fun AboutSettingsContent() {
    val context = LocalContext.current
    val updateViewModel: UpdateViewModel = hiltViewModel(context as ComponentActivity)

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "About",
            style = MaterialTheme.typography.headlineMedium,
            color = NuvioColors.Secondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Image(
            painter = painterResource(id = R.drawable.app_logo_wordmark),
            contentDescription = "NuvioTV",
            modifier = Modifier
                .width(180.dp)
                .height(50.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Made with \u2764\uFE0F by Tapframe and friends",
            style = MaterialTheme.typography.bodySmall,
            color = NuvioColors.TextSecondary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = NuvioColors.TextSecondary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Check for updates
        var updateFocused by remember { mutableStateOf(false) }
        Card(
            onClick = {
                updateViewModel.checkForUpdates(force = true, showNoUpdateFeedback = true)
            },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .onFocusChanged { updateFocused = it.isFocused },
            colors = CardDefaults.colors(
                containerColor = NuvioColors.BackgroundElevated,
                focusedContainerColor = NuvioColors.FocusBackground
            ),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(2.dp, NuvioColors.FocusRing),
                    shape = RoundedCornerShape(12.dp)
                )
            ),
            shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
            scale = CardDefaults.scale(focusedScale = 1.02f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Check for updates",
                        style = MaterialTheme.typography.titleSmall,
                        color = NuvioColors.TextPrimary
                    )
                    Text(
                        text = "Download latest release",
                        style = MaterialTheme.typography.bodySmall,
                        color = NuvioColors.TextSecondary
                    )
                }

                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (updateFocused) NuvioColors.Primary else NuvioColors.TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Privacy Policy
        var isFocused by remember { mutableStateOf(false) }

        Card(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://tapframe.github.io/NuvioStreaming/#privacy-policy"))
                context.startActivity(intent)
            },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .onFocusChanged { isFocused = it.isFocused },
            colors = CardDefaults.colors(
                containerColor = NuvioColors.BackgroundElevated,
                focusedContainerColor = NuvioColors.FocusBackground
            ),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(2.dp, NuvioColors.FocusRing),
                    shape = RoundedCornerShape(12.dp)
                )
            ),
            shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
            scale = CardDefaults.scale(focusedScale = 1.02f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Privacy Policy",
                        style = MaterialTheme.typography.titleSmall,
                        color = NuvioColors.TextPrimary
                    )
                    Text(
                        text = "View our privacy policy",
                        style = MaterialTheme.typography.bodySmall,
                        color = NuvioColors.TextSecondary
                    )
                }

                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (isFocused) NuvioColors.Primary else NuvioColors.TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}
