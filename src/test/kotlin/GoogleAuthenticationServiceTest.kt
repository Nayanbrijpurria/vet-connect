package com.vetplatform

import org.mindrot.jbcrypt.BCrypt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GoogleAuthenticationServiceTest {
    private val customerPasswordHash = BCrypt.hashpw("customer-password", BCrypt.gensalt(4))

    @Test
    fun `a new verified identity creates a customer with an unusable random password hash`() = runTest {
        val store = FakeGoogleIdentityStore()
        val service = service(store)

        val result = service.signIn("new-google")

        val account = assertIs<GoogleSignInResult.Authenticated>(result).account
        assertEquals("CUSTOMER", account.role)
        assertEquals("ACTIVE", account.accountStatus)
        assertNotNull(account.passwordHash)
        assertFalse(PasswordService.verifyPassword("customer-password", account.passwordHash))
        assertEquals(account.userId, store.subjectOwners["google-sub-new"])
        assertEquals(1, store.accounts.size)
    }

    @Test
    fun `a linked customer can sign in repeatedly without creating another user`() = runTest {
        val store = FakeGoogleIdentityStore()
        val account = store.addAccount(role = "CUSTOMER")
        store.subjectOwners["google-sub-new"] = account.userId
        store.userSubjects[account.userId] = "google-sub-new"

        val result = service(store).signIn("new-google")

        assertEquals(account, assertIs<GoogleSignInResult.Authenticated>(result).account)
        assertEquals(1, store.accounts.size)
    }

    @Test
    fun `matching email never silently links an existing customer`() = runTest {
        val store = FakeGoogleIdentityStore()
        store.addAccount(email = "Customer@Example.test", role = "CUSTOMER", passwordHash = customerPasswordHash)

        val result = service(store).signIn("new-google")

        assertEquals(GoogleSignInResult.ExistingAccountMustLink, result)
        assertTrue(store.subjectOwners.isEmpty())
        assertEquals(1, store.accounts.size)
    }

    @Test
    fun `Google identities cannot authenticate veterinarian or administrator accounts`() = runTest {
        for (role in listOf("VET", "ADMIN")) {
            val store = FakeGoogleIdentityStore()
            val account = store.addAccount(email = "customer@example.test", role = role, passwordHash = customerPasswordHash)
            store.subjectOwners["google-sub-new"] = account.userId
            store.userSubjects[account.userId] = "google-sub-new"

            assertEquals(GoogleSignInResult.NotCustomer, service(store).signIn("new-google"))
        }
    }

    @Test
    fun `inactive linked customers cannot authenticate with Google`() = runTest {
        val store = FakeGoogleIdentityStore()
        val account = store.addAccount(role = "CUSTOMER", status = "DISABLED")
        store.subjectOwners["google-sub-new"] = account.userId

        assertEquals(GoogleSignInResult.InactiveAccount, service(store).signIn("new-google"))
    }

    @Test
    fun `linking requires the current VetConnect password and only links the verified subject`() = runTest {
        val store = FakeGoogleIdentityStore()
        val account = store.addAccount(role = "CUSTOMER", passwordHash = customerPasswordHash)
        val service = service(store)

        assertEquals(GoogleLinkResult.INVALID_PASSWORD, service.link(account.userId, "new-google", "wrong-password"))
        assertTrue(store.subjectOwners.isEmpty())

        assertEquals(GoogleLinkResult.LINKED, service.link(account.userId, "new-google", "customer-password"))
        assertEquals(account.userId, store.subjectOwners["google-sub-new"])
        assertEquals(GoogleLinkResult.ALREADY_LINKED, service.link(account.userId, "new-google", "customer-password"))
    }

    @Test
    fun `Google-only account cannot attach a different identity without its undisclosed password`() = runTest {
        val store = FakeGoogleIdentityStore()
        val account = store.addAccount(role = "CUSTOMER")

        assertEquals(GoogleLinkResult.INVALID_PASSWORD, service(store).link(account.userId, "new-google", ""))
        assertFalse(store.subjectOwners.containsKey("google-sub-new"))
    }

    @Test
    fun `a Google subject already owned by another account cannot be linked`() = runTest {
        val store = FakeGoogleIdentityStore()
        val first = store.addAccount(role = "CUSTOMER", passwordHash = customerPasswordHash)
        val other = store.addAccount(role = "CUSTOMER", passwordHash = customerPasswordHash)
        store.subjectOwners["google-sub-new"] = other.userId

        assertEquals(
            GoogleLinkResult.IDENTITY_IN_USE,
            service(store).link(first.userId, "new-google", "customer-password")
        )
    }

    @Test
    fun `Google email belonging to another VetConnect account cannot be linked to this customer`() = runTest {
        val store = FakeGoogleIdentityStore()
        val customer = store.addAccount(email = "customer-a@example.test", role = "CUSTOMER", passwordHash = customerPasswordHash)
        store.addAccount(email = "customer@example.test", role = "VET", passwordHash = customerPasswordHash)

        assertEquals(
            GoogleLinkResult.IDENTITY_IN_USE,
            service(store).link(customer.userId, "new-google", "customer-password")
        )
        assertFalse(store.subjectOwners.containsKey("google-sub-new"))
    }

    @Test
    fun `unrecognized credentials are rejected by the verifier`() = runTest {
        val store = FakeGoogleIdentityStore()
        val verifier = GoogleIdentityVerifier { throw InvalidGoogleCredentialException() }

        kotlin.test.assertFailsWith<InvalidGoogleCredentialException> {
            GoogleAuthenticationService(verifier, store).signIn("invalid")
        }
        assertTrue(store.accounts.isEmpty())
    }

    @Test
    fun `the generated Google-only password secret cannot authenticate a known password`() {
        val passwordHash = PasswordService.hashUnusablePassword()
        assertFalse(PasswordService.verifyPassword("any-password", passwordHash))
    }

    @Test
    fun `Google token verifier rejects malformed credentials and missing client configuration`() = runTest {
        val configuredVerifier = GoogleIdTokenIdentityVerifier { "test-google-client-id" }
        kotlin.test.assertFailsWith<InvalidGoogleCredentialException> {
            configuredVerifier.verify("not-a-google-id-token")
        }

        val missingConfigVerifier = GoogleIdTokenIdentityVerifier { null }
        kotlin.test.assertFailsWith<GoogleConfigurationMissingException> {
            missingConfigVerifier.verify("syntactically-valid-enough-token")
        }
    }

    private fun service(store: FakeGoogleIdentityStore) = GoogleAuthenticationService(
        verifier = GoogleIdentityVerifier { credential ->
            if (credential != "new-google") throw InvalidGoogleCredentialException()
            VerifiedGoogleIdentity("google-sub-new", "customer@example.test", "Pet Parent")
        },
        store = store
    )

    private fun runTest(block: suspend () -> Unit) = kotlinx.coroutines.runBlocking { block() }

    private class FakeGoogleIdentityStore : GoogleIdentityStore {
        val accounts = mutableMapOf<Long, GoogleAccount>()
        val subjectOwners = mutableMapOf<String, Long>()
        val userSubjects = mutableMapOf<Long, String>()
        private var nextId = 1L
        private val unusableHash = BCrypt.hashpw("unusable-test-password", BCrypt.gensalt(4))

        fun addAccount(
            email: String = "customer${nextId}@example.test",
            role: String,
            status: String = "ACTIVE",
            passwordHash: String = unusableHash
        ): GoogleAccount {
            val account = GoogleAccount(nextId++, "Test User", email, role, status, passwordHash)
            accounts[account.userId] = account
            return account
        }

        override suspend fun findByGoogleSubject(subject: String): GoogleAccount? =
            subjectOwners[subject]?.let(accounts::get)

        override suspend fun findByEmail(email: String): GoogleAccount? =
            accounts.values.firstOrNull { it.email.equals(email, ignoreCase = true) }

        override suspend fun createGoogleCustomer(identity: VerifiedGoogleIdentity): GoogleAccount {
            val account = addAccount(identity.email, "CUSTOMER")
            subjectOwners[identity.subject] = account.userId
            userSubjects[account.userId] = identity.subject
            return account
        }

        override suspend fun findAccountById(userId: Long): GoogleAccount? = accounts[userId]

        override suspend fun linkGoogleIdentity(
            userId: Long,
            subject: String,
            expectedPasswordHash: String
        ): GoogleLinkResult {
            val owner = subjectOwners[subject]
            if (owner != null) return if (owner == userId) GoogleLinkResult.ALREADY_LINKED else GoogleLinkResult.IDENTITY_IN_USE
            if (userSubjects.containsKey(userId)) return GoogleLinkResult.IDENTITY_IN_USE
            val account = accounts[userId] ?: return GoogleLinkResult.ACCOUNT_NOT_FOUND
            if (account.passwordHash != expectedPasswordHash) return GoogleLinkResult.INVALID_PASSWORD
            subjectOwners[subject] = userId
            userSubjects[userId] = subject
            return GoogleLinkResult.LINKED
        }
    }
}
