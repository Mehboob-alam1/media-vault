package com.shelf.archive.data

import com.google.firebase.storage.StorageException
import com.shelf.archive.domain.FirebaseFailureHint
import com.shelf.archive.domain.explainFirebaseFailure

fun Throwable.explainFirebase(): String {
    return explainFirebaseFailure(messageChain(), firebaseHint())
}

private fun Throwable.firebaseHint(): FirebaseFailureHint {
    val storage = generateSequence(this) { it.cause }.filterIsInstance<StorageException>().firstOrNull()
    return when (storage?.errorCode) {
        StorageException.ERROR_NOT_AUTHORIZED,
        StorageException.ERROR_NOT_AUTHENTICATED,
        -> FirebaseFailureHint.UNAUTHORIZED
        StorageException.ERROR_BUCKET_NOT_FOUND,
        StorageException.ERROR_PROJECT_NOT_FOUND,
        -> FirebaseFailureHint.BUCKET_MISSING
        StorageException.ERROR_QUOTA_EXCEEDED -> FirebaseFailureHint.QUOTA
        StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> FirebaseFailureHint.RETRY
        else -> when {
            messageChain().contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) ->
                FirebaseFailureHint.APP_NOT_REGISTERED
            else -> FirebaseFailureHint.NONE
        }
    }
}

private fun Throwable.messageChain(): String =
    generateSequence(this) { it.cause }.mapNotNull { it.message }.joinToString(" ")
