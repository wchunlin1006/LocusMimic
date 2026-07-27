package com.locusmimic.app.data

import com.locusmimic.app.data.model.FavoriteLocation
import com.locusmimic.app.data.model.LastClickedLocation
import org.json.JSONArray
import org.json.JSONObject

/** Small, reflection-free codecs for the few preference payloads shared by manager and hooks. */
object JsonCodec {
    fun encodeLocation(location: LastClickedLocation): String = JSONObject()
        .put("latitude", location.latitude)
        .put("longitude", location.longitude)
        .toString()

    fun decodeLocation(json: String): LastClickedLocation {
        val value = JSONObject(json)
        return LastClickedLocation(
            latitude = value.getDouble("latitude"),
            longitude = value.getDouble("longitude")
        )
    }

    fun encodeStrings(values: Collection<String>): String {
        val array = JSONArray()
        values.forEach(array::put)
        return array.toString()
    }

    fun decodeStringSet(json: String): Set<String> {
        val array = JSONArray(json)
        return buildSet(array.length()) {
            repeat(array.length()) { index ->
                array.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }

    fun encodeFavorites(values: List<FavoriteLocation>): String {
        val array = JSONArray()
        values.forEach { favorite ->
            array.put(
                JSONObject()
                    .put("name", favorite.name)
                    .put("address", favorite.address)
                    .put("latitude", favorite.latitude)
                    .put("longitude", favorite.longitude)
            )
        }
        return array.toString()
    }

    fun decodeFavorites(json: String): List<FavoriteLocation> {
        val array = JSONArray(json)
        return buildList(array.length()) {
            repeat(array.length()) { index ->
                val value = array.getJSONObject(index)
                add(
                    FavoriteLocation(
                        name = value.getString("name"),
                        address = value.optString("address", ""),
                        latitude = value.getDouble("latitude"),
                        longitude = value.getDouble("longitude")
                    )
                )
            }
        }
    }
}
