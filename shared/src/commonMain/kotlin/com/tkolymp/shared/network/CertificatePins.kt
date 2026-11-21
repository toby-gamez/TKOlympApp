package com.tkolymp.shared.network

const val PINNED_HOST = "tkolymp.cz"

// SHA-256 public-key pins for tkolymp.cz (served via Google Trust Services: leaf -> WE1 -> GTS Root R4).
// Pin the root key rather than the leaf or intermediate: the leaf key changes on every renewal and
// Google rotates between intermediates (WE1, WE2, WR1, ...). A connection passes if any certificate
// in the validated chain matches one pin. Shared by the Android (OkHttp) and iOS (Darwin) pinners.
// All pins belong to Google Trust Services: if the host moves to another CA, add that CA's pin
// before shipping the change.
val CERTIFICATE_PIN_HASHES: List<String> = listOf(
    "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=", // GTS Root R4 (ECDSA)
    "sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc=", // GTS Root R1 (RSA, backup)
    "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=", // GTS WE1 intermediate (backup)
)
