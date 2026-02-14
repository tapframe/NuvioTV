package com.nuvio.tv.data.repository

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.data.local.TraktAuthDataStore
import com.nuvio.tv.data.local.TraktAuthState
import com.nuvio.tv.data.remote.api.TraktApi
import com.nuvio.tv.data.remote.dto.trakt.TraktDeviceCodeRequestDto
import com.nuvio.tv.data.remote.dto.trakt.TraktDeviceCodeResponseDto
import com.nuvio.tv.data.remote.dto.trakt.TraktDeviceTokenRequestDto
import com.nuvio.tv.data.remote.dto.trakt.TraktRefreshTokenRequestDto
import com.nuvio.tv.data.remote.dto.trakt.TraktRevokeRequestDto
import android.util.Log
import kotlinx.coroutines.flow.first
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface TraktTokenPollResult {
    data object Pending : TraktTokenPollResult
    data object Expired : TraktTokenPollResult
    data object Denied : TraktTokenPollResult
    data class SlowDown(val pollIntervalSeconds: Int) : TraktTokenPollResult
    data class Approved(val username: String?) : TraktTokenPollResult
    data class Failed(val reason: String) : TraktTokenPollResult
}

@Singleton
class TraktAuthService @Inject constructor(
    private val traktApi: TraktApi,
    private val traktAuthDataStore: TraktAuthDataStore
) {
    private val refreshLeewaySeconds = 60L

    fun hasRequiredCredentials(): Boolean {
        return BuildConfig.TRAKT_CLIENT_ID.isNotBlank() && BuildConfig.TRAKT_CLIENT_SECRET.isNotBlank()
    }

    suspend fun getCurrentAuthState(): TraktAuthState = traktAuthDataStore.state.first()

    suspend fun startDeviceAuth(): Result<TraktDeviceCodeResponseDto> {
        if (!hasRequiredCredentials()) {
            return Result.failure(IllegalStateException("Missing TRAKT credentials"))
        }

        val response = traktApi.requestDeviceCode(
            TraktDeviceCodeRequestDto(clientId = BuildConfig.TRAKT_CLIENT_ID)
        )
        val body = response.body()
        if (!response.isSuccessful || body == null) {
            return Result.failure(
                IllegalStateException("Failed to start Trakt auth (${response.code()})")
            )
        }

        traktAuthDataStore.saveDeviceFlow(body)
        return Result.success(body)
    }

    suspend fun pollDeviceToken(): TraktTokenPollResult {
        if (!hasRequiredCredentials()) {
            return TraktTokenPollResult.Failed("Missing TRAKT credentials")
        }

        val state = getCurrentAuthState()
        val deviceCode = state.deviceCode
        if (deviceCode.isNullOrBlank()) {
            return TraktTokenPollResult.Failed("No active Trakt device code")
        }

        val response = traktApi.requestDeviceToken(
            TraktDeviceTokenRequestDto(
                code = deviceCode,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                clientSecret = BuildConfig.TRAKT_CLIENT_SECRET
            )
        )

        val tokenBody = response.body()
        if (response.isSuccessful && tokenBody != null) {
            traktAuthDataStore.saveToken(tokenBody)
            traktAuthDataStore.clearDeviceFlow()
            val user = fetchUserSettings()
            return TraktTokenPollResult.Approved(user)
        }

        return when (response.code()) {
            400, 409 -> TraktTokenPollResult.Pending
            404 -> {
                traktAuthDataStore.clearDeviceFlow()
                TraktTokenPollResult.Failed("Invalid device code")
            }
            410 -> {
                traktAuthDataStore.clearDeviceFlow()
                TraktTokenPollResult.Expired
            }
            418 -> {
                traktAuthDataStore.clearDeviceFlow()
                TraktTokenPollResult.Denied
            }
            429 -> {
                val nextInterval = ((state.pollInterval ?: 5) + 5).coerceAtMost(60)
                traktAuthDataStore.updatePollInterval(nextInterval)
                TraktTokenPollResult.SlowDown(nextInterval)
            }
            else -> TraktTokenPollResult.Failed("Token polling failed (${response.code()})")
        }
    }

    suspend fun refreshTokenIfNeeded(force: Boolean = false): Boolean {
        if (!hasRequiredCredentials()) return false

        val state = getCurrentAuthState()
        val refreshToken = state.refreshToken ?: return false
        if (!force && !isTokenExpiredOrExpiring(state)) {
            return true
        }

        val response = try {
            traktApi.refreshToken(
                TraktRefreshTokenRequestDto(
                    refreshToken = refreshToken,
                    clientId = BuildConfig.TRAKT_CLIENT_ID,
                    clientSecret = BuildConfig.TRAKT_CLIENT_SECRET
                )
            )
        } catch (e: IOException) {
            Log.w("TraktAuthService", "Network error while refreshing token", e)
            return false
        }

        val tokenBody = response.body()
        if (!response.isSuccessful || tokenBody == null) {
            if (response.code() == 401 || response.code() == 403) {
                traktAuthDataStore.clearAuth()
            }
            return false
        }

        traktAuthDataStore.saveToken(tokenBody)
        return true
    }

    suspend fun revokeAndLogout() {
        val state = getCurrentAuthState()
        if (hasRequiredCredentials()) {
            state.accessToken?.let { accessToken ->
                runCatching {
                    traktApi.revokeToken(
                        TraktRevokeRequestDto(
                            token = accessToken,
                            clientId = BuildConfig.TRAKT_CLIENT_ID,
                            clientSecret = BuildConfig.TRAKT_CLIENT_SECRET
                        )
                    )
                }
            }
        }
        traktAuthDataStore.clearAuth()
    }

    suspend fun fetchUserSettings(): String? {
        val response = executeAuthorizedRequest { authHeader ->
            traktApi.getUserSettings(authorization = authHeader)
        } ?: return null

        if (!response.isSuccessful) return null

        val username = response.body()?.user?.username
        val slug = response.body()?.user?.ids?.slug
        traktAuthDataStore.saveUser(username = username, userSlug = slug)
        return username
    }

    suspend fun <T> executeAuthorizedRequest(
        call: suspend (authorizationHeader: String) -> Response<T>
    ): Response<T>? {
        val firstToken = getValidAccessToken() ?: return null
        val response = try {
            call("Bearer $firstToken")
        } catch (e: IOException) {
            Log.w("TraktAuthService", "Network error during authorized request", e)
            return null
        }
        if (response.code() == 401 && refreshTokenIfNeeded(force = true)) {
            val refreshedToken = getCurrentAuthState().accessToken ?: return response
            return try {
                call("Bearer $refreshedToken")
            } catch (e: IOException) {
                Log.w("TraktAuthService", "Network error during retry request", e)
                null
            }
        }
        return response
    }

    private suspend fun getValidAccessToken(): String? {
        val state = getCurrentAuthState()
        if (state.accessToken.isNullOrBlank()) return null
        if (refreshTokenIfNeeded(force = false)) {
            return getCurrentAuthState().accessToken
        }
        return null
    }

    private fun isTokenExpiredOrExpiring(state: TraktAuthState): Boolean {
        val createdAt = state.createdAt ?: return true
        val expiresIn = state.expiresIn ?: return true
        val expiresAt = createdAt + expiresIn
        val nowSeconds = System.currentTimeMillis() / 1000L
        return nowSeconds >= (expiresAt - refreshLeewaySeconds)
    }
}
