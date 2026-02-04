package com.tkolymp.shared

import android.content.Context
import com.tkolymp.shared.auth.AuthService
import com.tkolymp.shared.network.GraphQlClientImpl
import com.tkolymp.shared.storage.TokenStorage
import com.tkolymp.shared.event.EventService
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import io.ktor.serialization.kotlinx.json.*

suspend fun initNetworking(context: Context, baseUrl: String) {
    val storage = TokenStorage(context)

    val client = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(15, TimeUnit.SECONDS)
                readTimeout(15, TimeUnit.SECONDS)
                writeTimeout(15, TimeUnit.SECONDS)
            }
        }

        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    val gql = GraphQlClientImpl(client, baseUrl)
    val auth = AuthService(storage, gql)
    val eventSvc = EventService(gql)

    ServiceLocator.graphQlClient = gql
    ServiceLocator.authService = auth
    ServiceLocator.tokenStorage = storage
    ServiceLocator.eventService = eventSvc

    auth.initialize()
}
