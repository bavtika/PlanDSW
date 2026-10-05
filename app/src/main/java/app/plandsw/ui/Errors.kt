package app.plandsw.ui

import androidx.annotation.StringRes
import app.plandsw.R
import app.plandsw.data.UnexpectedResponseException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** An error shown to the user as is, using text from resources. */
class UserFacingException(@StringRes val messageRes: Int) : Exception()

@StringRes
fun Throwable.toErrorRes(): Int = when (this) {
    is UserFacingException -> messageRes
    is UnknownHostException, is ConnectException -> R.string.error_no_internet
    is SocketTimeoutException -> R.string.error_timeout
    is UnexpectedResponseException -> R.string.error_site_changed
    else -> R.string.error_generic
}
