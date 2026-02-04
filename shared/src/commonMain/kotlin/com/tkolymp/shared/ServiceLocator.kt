package com.tkolymp.shared

import com.tkolymp.shared.auth.IAuthService
import com.tkolymp.shared.network.IGraphQlClient
import com.tkolymp.shared.storage.TokenStorage

object ServiceLocator {
    lateinit var graphQlClient: IGraphQlClient
    lateinit var authService: IAuthService
    lateinit var tokenStorage: TokenStorage
}
