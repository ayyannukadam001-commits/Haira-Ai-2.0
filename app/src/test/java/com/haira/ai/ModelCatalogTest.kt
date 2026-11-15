package com.haira.ai

import com.haira.ai.ai.ModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    @Test fun containsTwoKeyModelEntries() {
        assertEquals(2, ModelCatalog.models.size)
        assertTrue(ModelCatalog.models.all { it.url.startsWith("https://huggingface.co/") })
        assertTrue(ModelCatalog.models.all { it.quant == "Q4_K_M" })
        assertTrue(ModelCatalog.models.sumOf { it.sizeBytes } < 1_000_000_000L)
    }
}
