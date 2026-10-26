package dev.sequel.app.data.local.converter

import dev.sequel.app.data.local.entity.MediaType
import dev.sequel.app.data.local.entity.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ConvertersTest {

    private lateinit var converters: Converters

    @Before
    fun setUp() {
        converters = Converters()
    }

    @Test
    fun syncStatus_convertsToStringAndBack() {
        for (status in SyncStatus.entries) {
            val stringVal = converters.fromSyncStatus(status)
            assertEquals(status.name, stringVal)
            val recoveredStatus = converters.toSyncStatus(stringVal)
            assertEquals(status, recoveredStatus)
        }
    }

    @Test
    fun mediaType_convertsToLowercaseStringAndBack() {
        for (type in MediaType.entries) {
            val stringVal = converters.fromMediaType(type)
            assertEquals(type.name.lowercase(), stringVal)
            val recoveredType = converters.toMediaType(stringVal)
            assertEquals(type, recoveredType)
        }
    }
}
