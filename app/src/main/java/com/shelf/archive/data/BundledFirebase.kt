package com.shelf.archive.data

import android.content.Context
import com.shelf.archive.domain.FirebaseConfig
import com.shelf.archive.domain.GoogleServicesParser

object BundledFirebase {
    fun read(context: Context): FirebaseConfig {
        val json = context.assets.open("google-services.json").bufferedReader().use { it.readText() }
        val parsed = GoogleServicesParser.parse(json, context.packageName)
        val config = parsed.config.normalized()
        config.validationError()?.let { message ->
            throw IllegalArgumentException(message)
        }
        return config
    }
}
