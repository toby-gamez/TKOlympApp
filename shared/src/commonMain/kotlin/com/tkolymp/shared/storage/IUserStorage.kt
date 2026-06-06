package com.tkolymp.shared.storage

interface IUserStorage {
    suspend fun savePersonId(personId: String)
    suspend fun getPersonId(): String?
    suspend fun saveCstsId(cstsId: String)
    suspend fun getCstsId(): String?
    suspend fun saveCoupleIds(coupleIds: List<String>)
    suspend fun getCoupleIds(): List<String>
    suspend fun saveCurrentUserJson(json: String)
    suspend fun getCurrentUserJson(): String?
    suspend fun savePersonDetailsJson(json: String)
    suspend fun getPersonDetailsJson(): String?
    suspend fun clear()
}
