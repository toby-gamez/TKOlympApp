package com.tkolymp.shared

import com.tkolymp.shared.auth.IAuthService
import com.tkolymp.shared.network.IGraphQlClient
import com.tkolymp.shared.storage.TokenStorage
import com.tkolymp.shared.event.IEventService
import com.tkolymp.shared.storage.UserStorage
import com.tkolymp.shared.user.UserService

object ServiceLocator {
    lateinit var graphQlClient: IGraphQlClient
    lateinit var authService: IAuthService
    lateinit var tokenStorage: TokenStorage
    lateinit var eventService: IEventService
    lateinit var userStorage: UserStorage
    lateinit var userService: UserService
}
