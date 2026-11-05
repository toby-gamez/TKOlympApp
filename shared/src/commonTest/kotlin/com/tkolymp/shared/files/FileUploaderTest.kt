package com.tkolymp.shared.files

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileUploaderTest {
    @Test fun bareNumber() = assertEquals("239", parseUploadedFileId(" 239\n"))
    @Test fun jsonId() = assertEquals("239", parseUploadedFileId("""{"id":"239","name":"x.jpg"}"""))
    @Test fun jsonNumericId() = assertEquals("239", parseUploadedFileId("""{"id":239}"""))
    @Test fun nestedFile() = assertEquals("239", parseUploadedFileId("""{"file":{"id":"239"}}"""))
    @Test fun urlInJson() = assertEquals("239", parseUploadedFileId("""{"url":"/f/239/tob.png"}"""))
    @Test fun locationHeader() = assertEquals("239", parseUploadedFileId("", "https://tkolymp.cz/f/239/tob.png?w=512"))
    @Test fun unknownShape() = assertNull(parseUploadedFileId("""{"ok":true}"""))
}
