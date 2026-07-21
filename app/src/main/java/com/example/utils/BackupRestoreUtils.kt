package com.example.utils

import com.example.data.ButtonConfig
import org.json.JSONArray
import org.json.JSONObject

object BackupRestoreUtils {
    fun exportConfig(settings: Map<String, String>, buttons: List<ButtonConfig>): String {
        val root = JSONObject()
        
        val settingsJson = JSONObject()
        settings.forEach { (k, v) ->
            settingsJson.put(k, v)
        }
        root.put("settings", settingsJson)
        
        val buttonsArray = JSONArray()
        buttons.forEach { button ->
            val btnJson = JSONObject().apply {
                put("id", button.id)
                put("originalName", button.originalName)
                put("name", button.name)
                put("smsCode", button.smsCode)
                put("iconName", button.iconName)
                put("colorHex", button.colorHex ?: JSONObject.NULL)
                put("isEnabled", button.isEnabled)
                put("groupId", button.groupId)
                put("position", button.position)
            }
            buttonsArray.put(btnJson)
        }
        root.put("buttons", buttonsArray)
        
        return root.toString(2)
    }

    fun importConfig(jsonString: String): Pair<Map<String, String>, List<ButtonConfig>> {
        val root = JSONObject(jsonString)
        
        val settingsMap = mutableMapOf<String, String>()
        if (root.has("settings")) {
            val settingsJson = root.getJSONObject("settings")
            settingsJson.keys().forEach { key ->
                settingsMap[key] = settingsJson.getString(key)
            }
        }
        
        val buttonsList = mutableListOf<ButtonConfig>()
        if (root.has("buttons")) {
            val buttonsArray = root.getJSONArray("buttons")
            for (i in 0 until buttonsArray.length()) {
                val btnJson = buttonsArray.getJSONObject(i)
                val colorHex = if (btnJson.isNull("colorHex")) null else btnJson.getString("colorHex")
                buttonsList.add(
                    ButtonConfig(
                        id = btnJson.getString("id"),
                        originalName = btnJson.getString("originalName"),
                        name = btnJson.getString("name"),
                        smsCode = btnJson.getString("smsCode"),
                        iconName = btnJson.getString("iconName"),
                        colorHex = colorHex,
                        isEnabled = btnJson.getBoolean("isEnabled"),
                        groupId = btnJson.getInt("groupId"),
                        position = btnJson.getInt("position")
                    )
                )
            }
        }
        
        return Pair(settingsMap, buttonsList)
    }
}
