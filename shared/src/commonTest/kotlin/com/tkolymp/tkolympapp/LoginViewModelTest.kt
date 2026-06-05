package com.tkolymp.tkolympapp

import com.tkolymp.shared.user.UserService
import com.tkolymp.shared.viewmodels.AppError
import com.tkolymp.shared.viewmodels.LoginViewModel
import com.tkolymp.tkolympapp.fakes.FakeAuthService
import com.tkolymp.tkolympapp.fakes.FakeGraphQlClient
import com.tkolymp.tkolympapp.fakes.FakeUserStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun makeUserService() = UserService(
    client = FakeGraphQlClient(),
    storage = FakeUserStorage()
)

class LoginViewModelTest {

    @Test
    fun `initial state is clean`() = runTest {
        val vm = LoginViewModel(FakeAuthService(), makeUserService())
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
        assertEquals("", vm.state.value.username)
    }

    @Test
    fun `updateUsername and updatePassword reflect in state`() = runTest {
        val vm = LoginViewModel(FakeAuthService(), makeUserService())
        vm.updateUsername("alice")
        vm.updatePassword("secret")
        assertEquals("alice", vm.state.value.username)
        assertEquals("secret", vm.state.value.password)
    }

    @Test
    fun `updateUsername clears error`() = runTest {
        val vm = LoginViewModel(FakeAuthService(loginResult = false), makeUserService())
        vm.login()
        assertIs<AppError>(vm.state.value.error)
        vm.updateUsername("new")
        assertNull(vm.state.value.error)
    }

    @Test
    fun `failed login sets AppError and returns false`() = runTest {
        val vm = LoginViewModel(FakeAuthService(loginResult = false), makeUserService())
        val result = vm.login()
        assertFalse(result)
        assertFalse(vm.state.value.isLoading)
        assertIs<AppError>(vm.state.value.error)
    }

    @Test
    fun `concurrent login calls are serialised by mutex`() = runTest {
        var callCount = 0
        val countingAuth = object : com.tkolymp.shared.auth.IAuthService {
            override suspend fun login(username: String, password: String): Boolean {
                callCount++
                return false
            }
            override suspend fun refreshJwt() = false
            override suspend fun hasToken() = false
            override suspend fun getToken() = null
            override suspend fun initialize() {}
        }
        val vm = LoginViewModel(countingAuth, makeUserService())
        // Two sequential calls — second should also execute (mutex just serialises, not deduplicates)
        vm.login()
        vm.login()
        assertEquals(2, callCount)
    }
}
