package dorai.shared.api

import dorai.shared.model.Problem

/** Outcome of one API call: the data, or the problem the server (or the network) reported. */
sealed interface ApiResult<out T> {
    data class Success<out T>(val value: T) : ApiResult<T>
    data class Failure(val problem: Problem) : ApiResult<Nothing>
}

/** The value, or null on failure. */
fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Success)?.value

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}
