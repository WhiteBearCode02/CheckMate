package com.whitebearcode02.checkmate.vehicle.data.auth

import java.util.UUID

/** An opaque development credential; identity is verified by its issuing authority. */
class AuthenticatedSession internal constructor(
    val userId: UUID,
    internal val credential: UUID,
) {
    override fun toString() = "AuthenticatedSession([redacted])"
}

interface Authentication {
    fun currentSession(): AuthenticatedSession?
}

/**
 * Trusted in-process fixture, not a login service or a security boundary for real users.
 * A real backend must verify its own signed credential and apply server-side access rules.
 */
class FakeAuthAuthority {
    private val issued = mutableMapOf<UUID, AuthenticatedSession>()

    @Synchronized
    internal fun issue(userId: UUID): AuthenticatedSession =
        AuthenticatedSession(userId, UUID.randomUUID()).also { issued[it.credential] = it }

    @Synchronized
    internal fun revoke(session: AuthenticatedSession) {
        if (issued[session.credential] === session) issued.remove(session.credential)
    }

    @Synchronized
    fun verifiedUserId(session: AuthenticatedSession): UUID? =
        session.userId.takeIf { issued[session.credential] === session }
}

/** Explicit test/development sign-in; choosing a UUID is not production authentication. */
class FakeAuthentication(private val authority: FakeAuthAuthority) : Authentication {
    @Volatile private var session: AuthenticatedSession? = null

    override fun currentSession(): AuthenticatedSession? = session

    @Synchronized
    fun signIn(userId: UUID): AuthenticatedSession {
        signOut()
        return authority.issue(userId).also { session = it }
    }

    @Synchronized
    fun signOut() {
        session?.let(authority::revoke)
        session = null
    }
}
