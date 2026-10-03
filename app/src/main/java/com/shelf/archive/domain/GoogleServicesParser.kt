package com.shelf.archive.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ParsedGoogleServices(
    val config: FirebaseConfig,
    val packageName: String,
    val warning: String?,
)

object GoogleServicesParser {
    fun parse(json: String, preferredPackage: String): ParsedGoogleServices {
        val root = try {
            Json.parseToJsonElement(json).jsonObject
        } catch (error: Exception) {
            throw IllegalArgumentException("That file is not a google-services.json from Firebase.")
        }
        val info = root.obj("project_info")
            ?: throw IllegalArgumentException("This JSON has no project_info block.")
        val projectId = info.string("project_id")
            ?: throw IllegalArgumentException("project_id is missing from google-services.json.")
        val bucket = info.string("storage_bucket")
            ?: throw IllegalArgumentException("storage_bucket is missing. Create Storage, then download the file again.")
        val firebaseUrl = info.string("firebase_url")?.takeIf { it.isNotBlank() }
        val clients = root["client"]?.jsonArray
            ?: throw IllegalArgumentException("This JSON has no client list.")
        val androidClients = clients.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val clientInfo = obj.obj("client_info") ?: return@mapNotNull null
            val androidInfo = clientInfo.obj("android_client_info") ?: return@mapNotNull null
            val packageName = androidInfo.string("package_name") ?: return@mapNotNull null
            val appId = clientInfo.string("mobilesdk_app_id") ?: return@mapNotNull null
            val apiKey = obj["api_key"]?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.string("current_key")
                ?: return@mapNotNull null
            AndroidClient(packageName, appId, apiKey)
        }
        if (androidClients.isEmpty()) {
            throw IllegalArgumentException("No Android app was found in this google-services.json.")
        }
        val chosen = androidClients.firstOrNull { it.packageName == preferredPackage }
            ?: androidClients.first()
        val warning = if (chosen.packageName != preferredPackage) {
            "This file is for ${chosen.packageName}. Shelf is $preferredPackage. Add an Android app with that package in Firebase, or uploads can be rejected."
        } else {
            null
        }
        return ParsedGoogleServices(
            config = FirebaseConfig(
                projectId = projectId,
                applicationId = chosen.appId,
                apiKey = chosen.apiKey,
                storageBucket = bucket,
                databaseUrl = firebaseUrl ?: FirebaseConfig.databaseUrlFor(projectId),
            ),
            packageName = chosen.packageName,
            warning = warning,
        )
    }
}

private data class AndroidClient(
    val packageName: String,
    val appId: String,
    val apiKey: String,
)

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
