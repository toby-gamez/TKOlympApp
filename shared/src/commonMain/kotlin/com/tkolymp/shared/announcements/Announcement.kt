package com.tkolymp.shared.announcements

import kotlinx.serialization.Serializable

@Serializable
data class Author(
    val id: String? = null,
    val uJmeno: String? = null,
    val uPrijmeni: String? = null
)

@Serializable
data class Announcement(
    val id: String,
    val title: String? = null,
    val body: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val isSticky: Boolean = false,
    val isVisible: Boolean = false,
    val author: Author? = null
)
