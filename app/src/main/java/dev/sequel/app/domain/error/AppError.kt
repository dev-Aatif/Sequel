package dev.sequel.app.domain.error

import retrofit2.HttpException
import java.io.IOException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.exceptions.HttpRequestException

sealed class AppError(val message: String) {
    // Network Errors
    object NoInternet : AppError("No internet connection. Please check your network and try again.")
    object Timeout : AppError("The request timed out. Please try again.")
    object ServerError : AppError("Server error. Please try again later.")
    
    // Auth Errors
    object AuthFailed : AppError("Authentication failed. Please check your settings.")
    object SessionExpired : AppError("Your session has expired. Please log in again.")
    class InvalidCredentials(msg: String = "Invalid email or password.") : AppError(msg)
    class WeakPassword(msg: String = "Password is too weak.") : AppError(msg)
    class Validation(msg: String) : AppError(msg)

    // Data Errors
    object NotFound : AppError("The requested content was not found.")
    object SyncFailed : AppError("Failed to sync your data. It will retry automatically.")
    class DatabaseError(msg: String = "A local database error occurred.") : AppError(msg)
    class ParseError(msg: String = "Failed to parse the data.") : AppError(msg)

    // Generic
    class Unknown(msg: String = "An unexpected error occurred.") : AppError(msg)
}

fun Throwable.toAppError(): AppError {
    return when (this) {
        is java.net.UnknownHostException,
        is java.net.ConnectException -> AppError.NoInternet
        is java.net.SocketTimeoutException -> AppError.Timeout
        is IOException -> AppError.NoInternet
        is HttpRequestException -> AppError.NoInternet
        is RestException -> {
            val errorString = this.error.lowercase()
            val description = this.description?.lowercase() ?: ""
            when {
                errorString.contains("invalid_credentials") || description.contains("invalid login") -> AppError.InvalidCredentials()
                errorString.contains("user_already_exists") || description.contains("already registered") -> AppError.Validation("User already exists.")
                errorString.contains("weak_password") || description.contains("weak password") -> AppError.WeakPassword()
                errorString.contains("not_found") -> AppError.NotFound
                errorString.contains("unauthorized") -> AppError.AuthFailed
                errorString.contains("rate_limit") || description.contains("rate limit") || errorString.contains("too_many_requests") -> AppError.Validation("Too many attempts. Please try again later.")
                else -> AppError.Unknown("An unexpected network error occurred.")
            }
        }
        is HttpException -> {
            when (this.code()) {
                401 -> AppError.AuthFailed
                403 -> AppError.SessionExpired
                404 -> AppError.NotFound
                in 500..599 -> AppError.ServerError
                else -> AppError.Unknown("An unexpected network error occurred.")
            }
        }
        else -> AppError.Unknown("An unexpected error occurred.")
    }
}
