package com.koriit.positioner.android.lidar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GeoJsonParserTest {
    @Test
    fun parsesJsonWithNbsp() {
        val json = "{\n\u00A0 \"type\": \"Feature\",\n\u00A0 \"geometry\": {\n\u00A0\u00A0 \"type\": \"Polygon\",\n\u00A0\u00A0 \"coordinates\": [[[10.0, 20.0], [13.42, 20.0], [13.42, 22.1], [10.0, 22.1], [10.0, 20.0]]]\n\u00A0 }\n}"
        val result = GeoJsonParser.parse(json)
        assertEquals(1, result.size)
        assertEquals(5, result[0].size)
        assertEquals(10.0f to 20.0f, result[0][0])
        assertEquals(13.42f to 22.1f, result[0][2])
    }
}

