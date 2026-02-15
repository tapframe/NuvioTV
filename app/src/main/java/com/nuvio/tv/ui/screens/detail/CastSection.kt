package com.nuvio.tv.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.MetaCastMember
import com.nuvio.tv.ui.theme.NuvioColors

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CastSection(
    cast: List<MetaCastMember>,
    modifier: Modifier = Modifier,
    title: String = "Cast",
    leadingCast: List<MetaCastMember> = emptyList(),
    preferredFocusedCastTmdbId: Int? = null,
    onCastMemberClick: (MetaCastMember) -> Unit = {}
) {
    if (cast.isEmpty() && leadingCast.isEmpty()) return

    val itemWidth = 150.dp
    val cardSize = 100.dp
    val dividerOffset = itemWidth - cardSize

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = NuvioColors.TextPrimary,
            modifier = Modifier.padding(horizontal = 48.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            val standardGap = 8.dp
            val deadSpace = itemWidth - cardSize

            if (leadingCast.isNotEmpty()) {
                items(
                    items = leadingCast,
                    key = { member ->
                        "leading|" + (member.tmdbId?.toString() ?: member.name) + "|" + (member.character ?: "") + "|" + (member.photo ?: "")
                    }
                ) { member ->
                    val isLastLeading = member == leadingCast.last()
                    val endPadding = if (isLastLeading && cast.isNotEmpty()) 0.dp else standardGap
                    val focusRequester = remember(member.tmdbId, member.name, member.photo, member.character) { FocusRequester() }
                    val shouldRestoreFocus = preferredFocusedCastTmdbId != null && member.tmdbId == preferredFocusedCastTmdbId

                    LaunchedEffect(shouldRestoreFocus) {
                        if (shouldRestoreFocus) {
                            focusRequester.requestFocus()
                        }
                    }

                    Box(modifier = Modifier.padding(end = endPadding)) {
                        CastMemberItem(
                            member = member,
                            modifier = Modifier.focusRequester(focusRequester),
                            itemWidth = itemWidth,
                            cardSize = cardSize,
                            onClick = { onCastMemberClick(member) }
                        )
                    }
                }
            }

            if (leadingCast.isNotEmpty() && cast.isNotEmpty()) {
                item(key = "role_divider") {
                    Box(
                        modifier = Modifier.height(cardSize),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(72.dp)
                                .offset(x = -deadSpace / 2)
                                .background(NuvioColors.SurfaceVariant.copy(alpha = 0.9f))
                        )
                    }
                }
            }

            items(
                items = cast,
                key = { member ->
                    (member.tmdbId?.toString() ?: member.name) + "|" + (member.character ?: "") + "|" + (member.photo ?: "")
                }
            ) { member ->
                val focusRequester = remember(member.tmdbId, member.name, member.photo, member.character) { FocusRequester() }
                val shouldRestoreFocus = preferredFocusedCastTmdbId != null && member.tmdbId == preferredFocusedCastTmdbId

                LaunchedEffect(shouldRestoreFocus) {
                    if (shouldRestoreFocus) {
                        focusRequester.requestFocus()
                    }
                }

                Box(modifier = Modifier.padding(end = standardGap)) {
                    CastMemberItem(
                        member = member,
                        modifier = Modifier.focusRequester(focusRequester),
                        itemWidth = itemWidth,
                        cardSize = cardSize,
                        onClick = { onCastMemberClick(member) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CastMemberItem(
    member: MetaCastMember,
    modifier: Modifier = Modifier,
    itemWidth: Dp = 150.dp,
    cardSize: Dp = 100.dp,
    onClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier.width(itemWidth),
        horizontalAlignment = Alignment.Start
    ) {
        Card(
            onClick = onClick,
            modifier = modifier
                .size(cardSize)
                .align(Alignment.Start),
            shape = CardDefaults.shape(
                shape = CircleShape
            ),
            colors = CardDefaults.colors(
                containerColor = NuvioColors.SurfaceVariant,
                focusedContainerColor = NuvioColors.FocusBackground
            ),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = androidx.compose.foundation.BorderStroke(2.dp, NuvioColors.FocusRing),
                    shape = CircleShape
                )
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val photo = member.photo
                if (!photo.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(photo)
                            .crossfade(true)
                            .size(
                                width = with(LocalDensity.current) { cardSize.roundToPx() },
                                height = with(LocalDensity.current) { cardSize.roundToPx() }
                            )
                            .build(),
                        contentDescription = member.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = member.name.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleLarge,
                        color = NuvioColors.TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = member.name,
            style = MaterialTheme.typography.labelMedium,
            color = NuvioColors.TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        val character = member.character
        if (!character.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = character,
                style = MaterialTheme.typography.labelSmall,
                color = NuvioColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
