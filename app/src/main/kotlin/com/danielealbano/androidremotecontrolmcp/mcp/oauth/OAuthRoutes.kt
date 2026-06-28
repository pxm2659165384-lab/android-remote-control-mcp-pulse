package com.danielealbano.androidremotecontrolmcp.mcp.oauth

import com.danielealbano.androidremotecontrolmcp.data.repository.OAuthClientRepository
import com.danielealbano.androidremotecontrolmcp.mcp.canonicalResource
import com.danielealbano.androidremotecontrolmcp.mcp.effectiveBaseUrl
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.URI
import java.util.UUID

/**
 * Collaborators for the OAuth HTTP layer. [nowMs] is injectable for tests; [publicUrlOverride] pins the
 * host used by every metadata/`aud`/redirect (empty = auto-detect).
 */
class OAuthRouteDeps(
    val clientRepository: OAuthClientRepository,
    val tokenService: JwtTokenService,
    val authorizationCodeStore: AuthorizationCodeStore,
    val approvalCoordinator: OAuthApprovalCoordinator,
    val publicUrlOverride: String,
    val nowMs: () -> Long = { System.currentTimeMillis() },
)

private data class PendingAuthorizeRequest(
    val clientId: String,
    val redirectUri: String,
    val codeChallenge: String,
    val resource: String,
    val scope: String,
    val state: String,
    val expiresAtMs: Long,
)

/** Server-lifetime, single-use, expiry-bounded store of approved-but-unredeemed authorize requests. */
private class PendingAuthorizeStore {
    private val mutex = Mutex()
    private val map = mutableMapOf<String, PendingAuthorizeRequest>()

    suspend fun put(
        id: String,
        request: PendingAuthorizeRequest,
        nowMs: Long,
    ) = mutex.withLock {
        purgeLocked(nowMs)
        map[id] = request
    }

    suspend fun consume(
        id: String,
        nowMs: Long,
    ): PendingAuthorizeRequest? =
        mutex.withLock {
            purgeLocked(nowMs)
            map.remove(id)
        }

    private fun purgeLocked(nowMs: Long) {
        map.entries.removeAll { it.value.expiresAtMs <= nowMs }
    }
}

/**
 * Registers the OAuth endpoints (all UNAUTHENTICATED — the combined-auth plugin excludes them). Mount
 * only when OAuth is enabled.
 */
fun Route.installOAuthRoutes(deps: OAuthRouteDeps) {
    val pendingAuthorize = PendingAuthorizeStore()

    get("/.well-known/oauth-protected-resource") { call.respondProtectedResourceMetadata(deps) }
    get("/.well-known/oauth-protected-resource/{tail...}") { call.respondProtectedResourceMetadata(deps) }
    get("/.well-known/oauth-authorization-server") { call.respondAuthorizationServerMetadata(deps) }
    get("/.well-known/oauth-authorization-server/{tail...}") { call.respondAuthorizationServerMetadata(deps) }
    get("/.well-known/openid-configuration") { call.respondAuthorizationServerMetadata(deps) }

    post("/register") { call.handleRegister(deps) }
    get("/authorize") { call.handleAuthorize(deps, pendingAuthorize) }
    get("/authorize/status") { call.handleAuthorizeStatus(deps, pendingAuthorize) }
    post("/token") { call.handleToken(deps) }
}

private suspend fun ApplicationCall.respondProtectedResourceMetadata(deps: OAuthRouteDeps) {
    respondText(
        OAuthMetadata.protectedResourceMetadata(effectiveBaseUrl(this, deps.publicUrlOverride)),
        ContentType.Application.Json,
    )
}

private suspend fun ApplicationCall.respondAuthorizationServerMetadata(deps: OAuthRouteDeps) {
    respondText(
        OAuthMetadata.authorizationServerMetadata(effectiveBaseUrl(this, deps.publicUrlOverride)),
        ContentType.Application.Json,
    )
}

private suspend fun ApplicationCall.handleRegister(deps: OAuthRouteDeps) {
    val body = runCatching { Json.parseToJsonElement(receiveText()).jsonObject }.getOrNull()
    if (body == null) {
        respondOAuthError(HttpStatusCode.BadRequest, "invalid_request")
        return
    }
    val redirectUris = body.stringArray("redirect_uris")
    if (redirectUris.isEmpty() || redirectUris.any { !OAuthPolicy.isAllowedRedirectUri(it) }) {
        respondOAuthError(HttpStatusCode.BadRequest, "invalid_redirect_uri")
        return
    }
    val clientName = body.stringField("client_name")
    val applicationType = body.stringField("application_type")
    val logoUri = body.stringField("logo_uri")
    val client = deps.clientRepository.register(clientName, redirectUris, applicationType, logoUri, deps.nowMs())
    val json =
        buildJsonObject {
            put("client_id", client.clientId)
            put("client_id_issued_at", deps.nowMs() / MILLIS_PER_SECOND)
            put("token_endpoint_auth_method", "none")
            putJsonArray("redirect_uris") { client.redirectUris.forEach { add(it) } }
            putJsonArray("grant_types") {
                add("authorization_code")
                add("refresh_token")
            }
            putJsonArray("response_types") { add("code") }
            client.clientName?.let { put("client_name", it) }
            client.applicationType?.let { put("application_type", it) }
            client.logoUri?.let { put("logo_uri", it) }
        }
    respondText(Json.encodeToString(json), ContentType.Application.Json, HttpStatusCode.Created)
}

private suspend fun ApplicationCall.handleAuthorize(
    deps: OAuthRouteDeps,
    pendingAuthorize: PendingAuthorizeStore,
) {
    val params = request.queryParameters
    val clientId = params["client_id"]
    val redirectUri = params["redirect_uri"]
    val state = params["state"].orEmpty()
    val canonical = canonicalResource(effectiveBaseUrl(this, deps.publicUrlOverride))

    if (clientId.isNullOrEmpty()) {
        respondAuthorizeError("Missing client_id.")
        return
    }
    val client = deps.clientRepository.getClient(clientId)
    if (client == null) {
        respondAuthorizeError("Unknown client.")
        return
    }
    if (redirectUri.isNullOrEmpty() ||
        redirectUri !in client.redirectUris ||
        !OAuthPolicy.isAllowedRedirectUri(redirectUri)
    ) {
        // Open-redirect guard: never redirect to an unvalidated URI.
        respondAuthorizeError("Invalid redirect_uri.")
        return
    }
    if (params["response_type"] != "code") {
        redirectWithError(redirectUri, "unsupported_response_type", state)
        return
    }
    if (params["code_challenge_method"] != "S256") {
        redirectWithError(redirectUri, "invalid_request", state)
        return
    }
    val codeChallenge = params["code_challenge"]
    if (codeChallenge.isNullOrEmpty()) {
        redirectWithError(redirectUri, "invalid_request", state)
        return
    }
    val resourceParam = params["resource"]
    if (!resourceParam.isNullOrEmpty() && !OAuthPolicy.resourceMatches(resourceParam, canonical)) {
        redirectWithError(redirectUri, "invalid_target", state)
        return
    }
    val displayName = client.clientName ?: host(redirectUri) ?: "Unknown"
    val approval = deps.approvalCoordinator.createPending(displayName, host(redirectUri) ?: "Unknown", deps.nowMs())
    pendingAuthorize.put(
        approval.id,
        PendingAuthorizeRequest(
            clientId = clientId,
            redirectUri = redirectUri,
            codeChallenge = codeChallenge,
            resource = canonical,
            scope = params["scope"].orEmpty().ifEmpty { "mcp" },
            state = state,
            expiresAtMs = approval.expiresAtMs,
        ),
        deps.nowMs(),
    )
    respondText(consentPageHtml(approval.id, approval.matchCode, displayName), ContentType.Text.Html)
}

private suspend fun ApplicationCall.handleAuthorizeStatus(
    deps: OAuthRouteDeps,
    pendingAuthorize: PendingAuthorizeStore,
) {
    val id = request.queryParameters["id"]
    if (id.isNullOrEmpty()) {
        respondStatus("expired")
        return
    }
    when (deps.approvalCoordinator.stateOf(id, deps.nowMs())) {
        ApprovalState.APPROVED -> {
            val pending = pendingAuthorize.consume(id, deps.nowMs())
            if (pending == null) {
                respondStatus("expired")
                return
            }
            val code =
                deps.authorizationCodeStore.create(
                    clientId = pending.clientId,
                    redirectUri = pending.redirectUri,
                    codeChallenge = pending.codeChallenge,
                    resource = pending.resource,
                    scope = pending.scope,
                    nowMs = deps.nowMs(),
                )
            val redirect =
                URLBuilder(pending.redirectUri)
                    .apply {
                        parameters.append("code", code)
                        parameters.append("state", pending.state)
                    }.buildString()
            respondText(
                Json.encodeToString(
                    buildJsonObject {
                        put("state", "approved")
                        put("redirect", redirect)
                    },
                ),
                ContentType.Application.Json,
            )
        }

        ApprovalState.PENDING -> respondStatus("pending")
        ApprovalState.DENIED -> respondStatus("denied")
        ApprovalState.EXPIRED -> respondStatus("expired")
    }
}

private suspend fun ApplicationCall.handleToken(deps: OAuthRouteDeps) {
    val form = receiveParameters()
    when (form["grant_type"]) {
        "authorization_code" -> handleAuthorizationCodeGrant(deps, form)
        "refresh_token" -> handleRefreshTokenGrant(deps, form)
        else -> respondOAuthError(HttpStatusCode.BadRequest, "unsupported_grant_type")
    }
}

private suspend fun ApplicationCall.handleAuthorizationCodeGrant(
    deps: OAuthRouteDeps,
    form: io.ktor.http.Parameters,
) {
    val codeValue = form["code"]
    val code = if (codeValue != null) deps.authorizationCodeStore.consume(codeValue, deps.nowMs()) else null
    if (code == null) {
        respondOAuthError(HttpStatusCode.BadRequest, "invalid_grant")
        return
    }
    if (form["redirect_uri"] != code.redirectUri) {
        respondOAuthError(HttpStatusCode.BadRequest, "invalid_grant")
        return
    }
    val clientId = form["client_id"]
    if (clientId.isNullOrEmpty()) {
        respondOAuthError(HttpStatusCode.BadRequest, "invalid_request")
        return
    }
    if (clientId != code.clientId) {
        respondOAuthError(HttpStatusCode.BadRequest, "invalid_grant")
        return
    }
    val resourceForm = form["resource"]
    if (!resourceForm.isNullOrEmpty() && !OAuthPolicy.resourceMatches(resourceForm, code.resource)) {
        respondOAuthError(HttpStatusCode.BadRequest, "invalid_grant")
        return
    }
    if (!Pkce.verifyS256(form["code_verifier"].orEmpty(), code.codeChallenge)) {
        respondOAuthError(HttpStatusCode.BadRequest, "invalid_grant")
        return
    }
    val jti = UUID.randomUUID().toString()
    deps.clientRepository.setRefreshJti(code.clientId, jti)
    deps.clientRepository.touchLastUsed(code.clientId, deps.nowMs())
    respondTokens(
        access = deps.tokenService.issueAccessToken(code.clientId, code.resource),
        refresh = deps.tokenService.issueRefreshToken(code.clientId, jti, code.resource),
    )
}

private suspend fun ApplicationCall.handleRefreshTokenGrant(
    deps: OAuthRouteDeps,
    form: io.ktor.http.Parameters,
) {
    val claims = deps.tokenService.verifyRefreshToken(form["refresh_token"].orEmpty())
    if (claims == null) {
        respondOAuthError(HttpStatusCode.BadRequest, "invalid_grant")
        return
    }
    val clientIdForm = form["client_id"]
    if (!clientIdForm.isNullOrEmpty() && clientIdForm != claims.clientId) {
        respondOAuthError(HttpStatusCode.BadRequest, "invalid_grant")
        return
    }
    val client = deps.clientRepository.getClient(claims.clientId)
    if (client == null) {
        respondOAuthError(HttpStatusCode.BadRequest, "invalid_grant")
        return
    }
    if (claims.jti != client.currentRefreshJti) {
        // Stale/rotated refresh token — reject only (do NOT revoke the client).
        respondOAuthError(HttpStatusCode.BadRequest, "invalid_grant")
        return
    }
    val resource = canonicalResource(effectiveBaseUrl(this, deps.publicUrlOverride))
    val newJti = UUID.randomUUID().toString()
    deps.clientRepository.setRefreshJti(claims.clientId, newJti)
    deps.clientRepository.touchLastUsed(claims.clientId, deps.nowMs())
    respondTokens(
        access = deps.tokenService.issueAccessToken(claims.clientId, resource),
        refresh = deps.tokenService.issueRefreshToken(claims.clientId, newJti, resource),
    )
}

private suspend fun ApplicationCall.respondTokens(
    access: String,
    refresh: String,
) {
    response.header(HttpHeaders.CacheControl, "no-store")
    respondText(
        Json.encodeToString(
            buildJsonObject {
                put("access_token", access)
                put("token_type", "Bearer")
                put("expires_in", OAuthPolicy.ACCESS_TOKEN_TTL_SECONDS)
                put("refresh_token", refresh)
                put("scope", "mcp")
            },
        ),
        ContentType.Application.Json,
    )
}

private suspend fun ApplicationCall.respondStatus(state: String) {
    respondText(
        Json.encodeToString(buildJsonObject { put("state", state) }),
        ContentType.Application.Json,
    )
}

private suspend fun ApplicationCall.respondOAuthError(
    status: HttpStatusCode,
    error: String,
) {
    respondText(
        Json.encodeToString(buildJsonObject { put("error", error) }),
        ContentType.Application.Json,
        status,
    )
}

private suspend fun ApplicationCall.respondAuthorizeError(message: String) {
    respondText(
        "<!DOCTYPE html><html><body><h1>Authorization error</h1><p>$message</p></body></html>",
        ContentType.Text.Html,
        HttpStatusCode.BadRequest,
    )
}

private suspend fun ApplicationCall.redirectWithError(
    redirectUri: String,
    error: String,
    state: String,
) {
    val url =
        URLBuilder(redirectUri)
            .apply {
                parameters.append("error", error)
                if (state.isNotEmpty()) parameters.append("state", state)
            }.buildString()
    respondRedirect(url)
}

private fun host(uri: String): String? = runCatching { URI(uri).host }.getOrNull()

private fun JsonObject.stringField(key: String): String? =
    runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()

private fun JsonObject.stringArray(key: String): List<String> =
    runCatching { this[key]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } }.getOrNull() ?: emptyList()

private const val MILLIS_PER_SECOND = 1000L
