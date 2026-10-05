package app.plandsw.data

import java.io.IOException

/** The site responded, but not with something we can parse (markup changed, empty or unrelated response). */
class UnexpectedResponseException(message: String) : IOException(message)
