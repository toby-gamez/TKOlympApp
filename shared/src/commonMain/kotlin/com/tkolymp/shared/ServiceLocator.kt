package com.tkolymp.shared

import com.tkolymp.shared.auth.IAuthService
import com.tkolymp.shared.network.IGraphQlClient
import com.tkolymp.shared.storage.TokenStorage
import com.tkolymp.shared.event.IEventService

object ServiceLocator {
    lateinit var graphQlClient: IGraphQlClient
    lateinit var authService: IAuthService
    lateinit var tokenStorage: TokenStorage
    lateinit var eventService: IEventService
}
