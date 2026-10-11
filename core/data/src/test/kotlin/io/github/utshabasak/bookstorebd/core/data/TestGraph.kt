package io.github.utshabasak.bookstorebd.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.utshabasak.bookstorebd.core.data.cache.ResponseCache
import io.github.utshabasak.bookstorebd.core.data.inbox.ChatRepository
import io.github.utshabasak.bookstorebd.core.data.inbox.LiveUpdates
import io.github.utshabasak.bookstorebd.core.data.inbox.NotificationRepository
import io.github.utshabasak.bookstorebd.core.data.repository.AuthRepository
import io.github.utshabasak.bookstorebd.core.data.repository.CartRepository
import io.github.utshabasak.bookstorebd.core.data.repository.CommunityRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ImageUploader
import io.github.utshabasak.bookstorebd.core.data.repository.OrderRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ProfileRepository
import io.github.utshabasak.bookstorebd.core.data.repository.SellerRepository
import io.github.utshabasak.bookstorebd.core.data.session.SessionManager
import io.github.utshabasak.bookstorebd.core.database.BookStoreDatabase
import io.github.utshabasak.bookstorebd.core.datastore.DeviceKeys
import io.github.utshabasak.bookstorebd.core.datastore.SecretCipher
import io.github.utshabasak.bookstorebd.core.datastore.SessionStore
import io.github.utshabasak.bookstorebd.core.datastore.SettingsStore
import io.github.utshabasak.bookstorebd.core.network.AccountMappers
import io.github.utshabasak.bookstorebd.core.network.ImageUrls
import io.github.utshabasak.bookstorebd.core.network.InboxMappers
import io.github.utshabasak.bookstorebd.core.network.Mappers
import io.github.utshabasak.bookstorebd.core.network.NetworkConfig
import io.github.utshabasak.bookstorebd.core.network.SellerMappers
import io.github.utshabasak.bookstorebd.core.network.UpdateGate
import io.github.utshabasak.bookstorebd.core.network.di.AccountNetworkModule
import io.github.utshabasak.bookstorebd.core.network.di.NetworkModule
import io.github.utshabasak.bookstorebd.core.testing.InMemoryPreferences
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import mockwebserver3.MockWebServer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/** A P-256 key in memory, standing in for the keystore (which tests do not have). */
class SoftwareDeviceKeys : DeviceKeys {
    var pair: KeyPair? = null
    var created = 0

    override fun createKey(): String {
        created++
        pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        return publicKey()!!
    }

    override fun publicKey(): String? = pair?.public?.encoded?.let(Base64.getEncoder()::encodeToString)

    override fun sign(payload: ByteArray): String? {
        val key = pair?.private ?: return null
        return Base64.getEncoder().encodeToString(
            Signature.getInstance("SHA256withECDSA").apply { initSign(key) }.run {
                update(payload)
                sign()
            },
        )
    }

    override fun deleteKey() {
        pair = null
    }
}

class ReversingCipher : SecretCipher {
    override fun encrypt(plain: String) = "sealed:" + plain.reversed()

    override fun decrypt(encoded: String) = encoded.removePrefix("sealed:").reversed()
}

/** The data layer, wired as the app wires it, against a mock server. */
class TestGraph(
    server: MockWebServer,
) {
    private val context: Context = ApplicationProvider.getApplicationContext()
    val config =
        NetworkConfig(
            apiBaseUrl = server.url("/api/v1/").toString(),
            siteUrl = server.url("/").toString(),
            versionCode = 100,
            versionName = "0.1.0",
            deviceName = "Test Phone",
            androidRelease = "15",
            logHttp = false,
        )
    val json = NetworkModule.providesJson()
    val database: BookStoreDatabase =
        Room.inMemoryDatabaseBuilder(context, BookStoreDatabase::class.java).allowMainThreadQueries().build()
    val cache = ResponseCache(database.cachedResponses(), json)
    val keys = SoftwareDeviceKeys()
    val sessionStore = SessionStore(InMemoryPreferences(), ReversingCipher())
    val settings = SettingsStore(InMemoryPreferences())
    val gate = UpdateGate()
    private val dispatcher = UnconfinedTestDispatcher()
    private val base = NetworkModule.providesBaseClient(config, gate, context)
    private val sessionApi = NetworkModule.providesSessionApi(base, config, json)
    val mappers = Mappers(ImageUrls(config.siteUrl))
    val session = SessionManager(sessionStore, sessionApi, keys, mappers, cache, config, dispatcher)
    private val apiClient = NetworkModule.providesApiClient(base, session, session)
    val api = NetworkModule.providesBookStoreApi(apiClient, config, json)
    val auth = AuthRepository(api, sessionApi, session, keys, settings, mappers, config, json, dispatcher)
    val cart = CartRepository(api, cache, mappers, json, session)
    val accountMappers = AccountMappers(ImageUrls(config.siteUrl), config.siteUrl)
    private val orderApi = AccountNetworkModule.providesOrderApi(apiClient, config, json)
    val uploader = ImageUploader(orderApi, base, json, dispatcher)
    val profiles =
        ProfileRepository(
            AccountNetworkModule.providesAccountApi(apiClient, config, json),
            cache,
            accountMappers,
            json,
            session,
            context,
            dispatcher,
        )
    val orders = OrderRepository(orderApi, cache, accountMappers, json, session, uploader)
    val community = CommunityRepository(AccountNetworkModule.providesCommunityApi(apiClient, config, json), accountMappers, json)
    val sellers =
        SellerRepository(
            AccountNetworkModule.providesSellerApi(apiClient, config, json),
            orderApi,
            SellerMappers(mappers, accountMappers),
            accountMappers,
            uploader,
            session,
            json,
        )
    val inboxMappers = InboxMappers(ImageUrls(config.siteUrl))
    val notifications =
        NotificationRepository(AccountNetworkModule.providesNotificationApi(apiClient, config, json), inboxMappers, session, settings, json)
    val chats = ChatRepository(AccountNetworkModule.providesChatApi(apiClient, config, json), inboxMappers, session, json)

    /** The live channel, on [scope], over the same client as the API. */
    fun live(scope: kotlinx.coroutines.CoroutineScope) = LiveUpdates(base, config, session, inboxMappers, json, scope)

    fun close() = database.close()
}
