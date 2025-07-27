package com.koriit.positioner.android.lidar

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

object GeoJsonParser {
    fun readFloorPlans(context: Context, uris: List<Uri>): List<List<Pair<Float, Float>>> {
        val result = mutableListOf<List<Pair<Float, Float>>>()
        for (uri in uris) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val text = input.bufferedReader().use { it.readText() }
                result += parse(text)
            }
        }
        return result
    }

    fun parse(json: String): List<List<Pair<Float, Float>>> {
        // Some GeoJSON files may contain non-breaking spaces (U+00A0),
        // which `JSONObject` fails to treat as valid whitespace.
        // Normalize the input before parsing to avoid `JSONException`.
        val normalized = json.replace('\u00A0', ' ')
        val obj = JSONObject(normalized)
        return when (obj.getString("type")) {
            "FeatureCollection" -> parseFeatureCollection(obj)
            "Feature" -> parseFeature(obj)
            "LineString", "Polygon", "MultiPolygon" -> parseGeometry(obj)
            else -> emptyList()
        }
    }

    private fun parseFeatureCollection(obj: JSONObject): List<List<Pair<Float, Float>>> {
        val arr = obj.getJSONArray("features")
        val list = mutableListOf<List<Pair<Float, Float>>>()
        for (i in 0 until arr.length()) {
            list += parseFeature(arr.getJSONObject(i))
        }
        return list
    }

    private fun parseFeature(obj: JSONObject): List<List<Pair<Float, Float>>> {
        val geom = obj.getJSONObject("geometry")
        return parseGeometry(geom)
    }

    private fun parseGeometry(obj: JSONObject): List<List<Pair<Float, Float>>> {
        return when (obj.getString("type")) {
            "LineString" -> listOf(parseCoordinateList(obj.getJSONArray("coordinates")))
            "Polygon" -> {
                val coords = obj.getJSONArray("coordinates")
                (0 until coords.length()).map { idx ->
                    parseCoordinateList(coords.getJSONArray(idx))
                }
            }
            "MultiPolygon" -> {
                val coords = obj.getJSONArray("coordinates")
                val list = mutableListOf<List<Pair<Float, Float>>>()
                for (i in 0 until coords.length()) {
                    val poly = coords.getJSONArray(i)
                    for (j in 0 until poly.length()) {
                        list += parseCoordinateList(poly.getJSONArray(j))
                    }
                }
                list
            }
            else -> emptyList()
        }
    }

    private fun parseCoordinateList(arr: JSONArray): List<Pair<Float, Float>> {
        val list = mutableListOf<Pair<Float, Float>>()
        for (i in 0 until arr.length()) {
            val coord = arr.getJSONArray(i)
            val x = coord.getDouble(0).toFloat()
            val y = coord.getDouble(1).toFloat()
            list += x to y
        }
        return list
    }
}
