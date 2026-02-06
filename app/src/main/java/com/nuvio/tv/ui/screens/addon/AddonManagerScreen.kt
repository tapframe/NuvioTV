package com.nuvio.tv.ui.screens.addon

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.theme.NuvioColors

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AddonManagerScreen(
    viewModel: AddonManagerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val installButtonFocusRequester = remember { FocusRequester() }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopQrMode() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioColors.Background)
    ) {
        TvLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 36.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Text(
                    text = "Addons",
                    style = MaterialTheme.typography.headlineMedium,
                    color = NuvioColors.TextPrimary
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    colors = CardDefaults.cardColors(containerColor = NuvioColors.BackgroundCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Install addon",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = NuvioColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = uiState.installUrl,
                                onValueChange = viewModel::onInstallUrlChange,
                                placeholder = { Text(text = "https://example.com", color = NuvioColors.TextTertiary) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        viewModel.installAddon()
                                        keyboardController?.hide()
                                        focusManager.clearFocus(force = true)
                                        installButtonFocusRequester.requestFocus()
                                    }
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = NuvioColors.BackgroundElevated,
                                    unfocusedContainerColor = NuvioColors.BackgroundElevated,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = NuvioColors.TextPrimary,
                                    unfocusedTextColor = NuvioColors.TextPrimary,
                                    cursorColor = NuvioColors.Primary
                                )
                            )
                            Button(
                                onClick = viewModel::installAddon,
                                enabled = !uiState.isInstalling,
                                modifier = Modifier.focusRequester(installButtonFocusRequester),
                                colors = ButtonDefaults.colors(
                                    containerColor = NuvioColors.BackgroundCard,
                                    contentColor = NuvioColors.TextPrimary,
                                    focusedContainerColor = NuvioColors.FocusBackground,
                                    focusedContentColor = NuvioColors.Primary
                                ),
                                shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
                            ) {
                                Text(text = if (uiState.isInstalling) "Installing" else "Install")
                            }
                        }

                        AnimatedVisibility(visible = uiState.error != null) {
                            Text(
                                text = uiState.error.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = NuvioColors.Error,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                    }
                }
            }

            // Manage from phone card
            item {
                ManageFromPhoneCard(onClick = viewModel::startQrMode)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Installed",
                        style = MaterialTheme.typography.titleLarge,
                        color = NuvioColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    if (uiState.isLoading && uiState.installedAddons.isEmpty()) {
                        LoadingIndicator(modifier = Modifier.height(24.dp))
                    }
                }
            }

            if (uiState.installedAddons.isEmpty() && !uiState.isLoading) {
                item {
                    Text(
                        text = "No addons installed. Add one to get started.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = NuvioColors.TextSecondary
                    )
                }
            } else {
                itemsIndexed(
                    items = uiState.installedAddons,
                    key = { _, addon -> addon.id }
                ) { index, addon ->
                    AddonCard(
                        addon = addon,
                        canMoveUp = index > 0,
                        canMoveDown = index < uiState.installedAddons.lastIndex,
                        onMoveUp = { viewModel.moveAddonUp(addon.baseUrl) },
                        onMoveDown = { viewModel.moveAddonDown(addon.baseUrl) },
                        onRemove = { viewModel.removeAddon(addon.baseUrl) }
                    )
                }
            }
        }

        // QR Code overlay
        AnimatedVisibility(
            visible = uiState.isQrModeActive,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            QrCodeOverlay(
                qrBitmap = uiState.qrCodeBitmap,
                serverUrl = uiState.serverUrl,
                onClose = viewModel::stopQrMode,
                hasPendingChange = uiState.pendingChange != null
            )
        }

        // Confirmation dialog overlay
        AnimatedVisibility(
            visible = uiState.pendingChange != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            uiState.pendingChange?.let { pending ->
                ConfirmAddonChangesDialog(
                    pendingChange = pending,
                    onConfirm = viewModel::confirmPendingChange,
                    onReject = viewModel::rejectPendingChange
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ManageFromPhoneCard(onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = NuvioColors.BackgroundCard,
            focusedContainerColor = NuvioColors.FocusBackground
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, NuvioColors.FocusRing),
                shape = RoundedCornerShape(18.dp)
            )
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.QrCode2,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = if (isFocused) NuvioColors.Secondary else NuvioColors.TextSecondary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Manage from phone",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = NuvioColors.TextPrimary
                    )
                    Text(
                        text = "Scan a QR code to add, remove, and reorder addons from your phone",
                        style = MaterialTheme.typography.bodySmall,
                        color = NuvioColors.TextSecondary
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.PhoneAndroid,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = NuvioColors.TextSecondary
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QrCodeOverlay(
    qrBitmap: Bitmap?,
    serverUrl: String?,
    onClose: () -> Unit,
    hasPendingChange: Boolean = false
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(hasPendingChange) {
        if (!hasPendingChange) {
            focusRequester.requestFocus()
        }
    }

    BackHandler { onClose() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            onClick = { },
            modifier = Modifier
                .width(460.dp),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = NuvioColors.BackgroundCard
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Scan to manage addons",
                    style = MaterialTheme.typography.headlineSmall,
                    color = NuvioColors.TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Scan this QR code with your phone to add, remove, or reorder addons",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NuvioColors.TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.size(256.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (serverUrl != null) {
                    Text(
                        text = serverUrl,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NuvioColors.TextTertiary,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Surface(
                    onClick = onClose,
                    modifier = Modifier.focusRequester(focusRequester),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = NuvioColors.Surface,
                        focusedContainerColor = NuvioColors.FocusBackground
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(2.dp, NuvioColors.FocusRing),
                            shape = RoundedCornerShape(50)
                        )
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = NuvioColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Close",
                            color = NuvioColors.TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ConfirmAddonChangesDialog(
    pendingChange: PendingChangeInfo,
    onConfirm: () -> Unit,
    onReject: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    BackHandler { onReject() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            onClick = { },
            modifier = Modifier
                .width(500.dp),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = NuvioColors.BackgroundCard
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Confirm addon changes",
                    style = MaterialTheme.typography.headlineSmall,
                    color = NuvioColors.TextPrimary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "The following changes were made from your phone:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NuvioColors.TextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (pendingChange.addedUrls.isNotEmpty()) {
                    Text(
                        text = "Added:",
                        style = MaterialTheme.typography.titleSmall,
                        color = NuvioColors.Success,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    )
                    pendingChange.addedUrls.forEach { url ->
                        Text(
                            text = "+ $url",
                            style = MaterialTheme.typography.bodySmall,
                            color = NuvioColors.Success,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, bottom = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (pendingChange.removedUrls.isNotEmpty()) {
                    Text(
                        text = "Removed:",
                        style = MaterialTheme.typography.titleSmall,
                        color = NuvioColors.Error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    )
                    pendingChange.removedUrls.forEach { url ->
                        Text(
                            text = "- $url",
                            style = MaterialTheme.typography.bodySmall,
                            color = NuvioColors.Error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, bottom = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (pendingChange.addedUrls.isEmpty() && pendingChange.removedUrls.isEmpty()) {
                    Text(
                        text = "Addons were reordered",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NuvioColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = "Total addons: ${pendingChange.proposedUrls.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioColors.TextTertiary,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (pendingChange.isApplying) {
                    LoadingIndicator(modifier = Modifier.size(36.dp))
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            onClick = onReject,
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = NuvioColors.Surface,
                                focusedContainerColor = NuvioColors.FocusBackground
                            ),
                            border = ClickableSurfaceDefaults.border(
                                focusedBorder = Border(
                                    border = BorderStroke(2.dp, NuvioColors.FocusRing),
                                    shape = RoundedCornerShape(50)
                                )
                            ),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = NuvioColors.TextPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Reject",
                                    color = NuvioColors.TextPrimary
                                )
                            }
                        }

                        Surface(
                            onClick = onConfirm,
                            modifier = Modifier.focusRequester(focusRequester),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = NuvioColors.Secondary,
                                focusedContainerColor = NuvioColors.SecondaryVariant
                            ),
                            border = ClickableSurfaceDefaults.border(
                                focusedBorder = Border(
                                    border = BorderStroke(2.dp, NuvioColors.FocusRing),
                                    shape = RoundedCornerShape(50)
                                )
                            ),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50))
                        ) {
                            Text(
                                text = "Confirm",
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AddonCard(
    addon: Addon,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = NuvioColors.BackgroundCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = addon.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = NuvioColors.TextPrimary
                    )
                    Text(
                        text = "v${addon.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = NuvioColors.TextSecondary
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onMoveUp,
                        enabled = canMoveUp,
                        colors = ButtonDefaults.colors(
                            containerColor = NuvioColors.BackgroundCard,
                            contentColor = NuvioColors.TextSecondary,
                            focusedContainerColor = NuvioColors.FocusBackground,
                            focusedContentColor = NuvioColors.Primary
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
                    ) {
                        Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = "Move up")
                    }
                    Button(
                        onClick = onMoveDown,
                        enabled = canMoveDown,
                        colors = ButtonDefaults.colors(
                            containerColor = NuvioColors.BackgroundCard,
                            contentColor = NuvioColors.TextSecondary,
                            focusedContainerColor = NuvioColors.FocusBackground,
                            focusedContentColor = NuvioColors.Primary
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
                    ) {
                        Icon(imageVector = Icons.Default.ArrowDownward, contentDescription = "Move down")
                    }
                    Button(
                        onClick = onRemove,
                        colors = ButtonDefaults.colors(
                            containerColor = NuvioColors.BackgroundCard,
                            contentColor = NuvioColors.TextSecondary,
                            focusedContainerColor = NuvioColors.FocusBackground,
                            focusedContentColor = NuvioColors.Error
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
                    ) {
                        Text(text = "Remove")
                    }
                }
            }

            if (!addon.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = addon.description ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NuvioColors.TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = addon.baseUrl,
                style = MaterialTheme.typography.bodySmall,
                color = NuvioColors.TextTertiary
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Catalogs: ${addon.catalogs.size} • Types: ${addon.types.joinToString { it.toApiString() }}",
                style = MaterialTheme.typography.bodySmall,
                color = NuvioColors.TextTertiary
            )
        }
    }
}
