package dev.sequel.app.util

import retrofit2.HttpException
import java.io.IOException

/**
 * Extension function to map technical exceptions to user-friendly error messages.
 */
fun Exception.toUserFriendlyMessage(): String {
    return when (this) {
        is java.net.UnknownHostException, 
        is java.net.ConnectException, 
        is java.net.SocketTimeoutException,
        is IOException -> {
            "No internet connection. Please check your network and try again."
        }
        is HttpException -> {
            when (this.code()) {
                401, 403 -> "Authentication failed. Please check your settings."
                404 -> "The requested content was not found."
                in 500..599 -> "Server error. Please try again later."
                else -> "An unexpected network error occurred (${this.code()})."
            }
        }
        else -> {
            this.message ?: "An unexpected error occurred."
        }
    }
}
