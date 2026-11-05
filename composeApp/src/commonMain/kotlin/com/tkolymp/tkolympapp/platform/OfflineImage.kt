package com.tkolymp.tkolympapp.platform

import androidx.compose.runtime.Composable

/**
 * Keeps a copy of the logged-in account's own photo on the device so it still shows without network.
 * Re-downloaded on each start while online; a null [url] (logged out) deletes the copy.
 */
@Composable
expect fun PersistOwnImageOffline(url: String?)
