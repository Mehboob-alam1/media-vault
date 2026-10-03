package com.shelf.archive.domain

enum class FirebaseFailureHint {
    NONE,
    UNAUTHORIZED,
    BUCKET_MISSING,
    QUOTA,
    RETRY,
    APP_NOT_REGISTERED,
}

fun explainFirebaseFailure(
    raw: String,
    hint: FirebaseFailureHint = FirebaseFailureHint.NONE,
): String {
    val text = raw.lowercase()
    if (hint == FirebaseFailureHint.APP_NOT_REGISTERED || "configuration_not_found" in text) {
        return "Firebase does not recognize this Android app. Add package com.shelf.archive in Project settings, including the SHA-1 shown below."
    }
    if (
        hint == FirebaseFailureHint.UNAUTHORIZED ||
        "permission denied" in text ||
        "unauthorized" in text ||
        "not authorized" in text ||
        "permission_denied" in text
    ) {
        return "Firebase refused this request. Enable Anonymous sign-in, then publish database and storage rules that allow signed-in users."
    }
    if (
        hint == FirebaseFailureHint.BUCKET_MISSING ||
        "object does not exist" in text ||
        "bucket" in text && "not" in text
    ) {
        return "Couldn't find that Storage bucket. Check the bucket name. It usually ends in .appspot.com or .firebasestorage.app."
    }
    if (hint == FirebaseFailureHint.QUOTA || "quota" in text) {
        return "This Firebase project is over its Storage quota."
    }
    if (hint == FirebaseFailureHint.RETRY || "retry limit" in text) {
        return "The upload kept failing. Check the connection and try again."
    }
    if (
        "admin_only_operation" in text ||
        "operation_not_allowed" in text ||
        "restricted to administrators" in text ||
        "sign-in provider is disabled" in text ||
        "provider is disabled" in text
    ) {
        return "Anonymous sign-in is blocked. In Firebase Authentication, enable Anonymous. Then open Settings and turn on Enable create (sign-up)."
    }
    if ("api key not valid" in text || "api_key_invalid" in text || "invalid api key" in text) {
        return "That API key was rejected. Import google-services.json again, and allow this Android package on the key."
    }
    if (
        "unable to resolve host" in text ||
        "timeout" in text ||
        "network" in text ||
        "failed to connect" in text
    ) {
        return "This phone couldn't reach Firebase. Check the internet connection and try again."
    }
    val cleaned = raw.trim()
    if (cleaned.isBlank()) return "Something went wrong talking to Firebase."
    return cleaned.take(240)
}

object FirebaseRules {
    val database: String = """
        {
          "rules": {
            "library": {
              ".read": "auth != null",
              ".write": "auth != null"
            }
          }
        }
    """.trimIndent()

    val storage: String = """
        rules_version = '2';
        service firebase.storage {
          match /b/{bucket}/o {
            match /library/{allPaths=**} {
              allow read: if request.auth != null;
              allow create, update: if request.auth != null
                && request.resource.size < 100 * 1024 * 1024;
              allow delete: if request.auth != null;
            }
          }
        }
    """.trimIndent()
}
