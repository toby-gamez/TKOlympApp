package com.tkolymp.tkolympapp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform