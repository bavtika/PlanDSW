package app.plandsw.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsosApiTest {
    @Test
    fun finishedWhenBackOnUsoswebHome() {
        assertTrue(UsosApi.isAfterLogin("https://usosweb.ideis.pl/kontroler.php?_action=home/index"))
    }

    @Test
    fun notFinishedOnCasCallback() {
        assertFalse(UsosApi.isAfterLogin("https://usosweb.ideis.pl/kontroler.php?_action=logowaniecas/index&ticket=ST-1"))
    }

    @Test
    fun notFinishedOnCasOrMicrosoft() {
        assertFalse(UsosApi.isAfterLogin("https://login.wsb.pl/cas/login?service=https%3A%2F%2Fusosweb.ideis.pl%2Fkontroler.php"))
        assertFalse(UsosApi.isAfterLogin("https://login.microsoftonline.com/common/saml2"))
    }

    @Test
    fun silentLoginSkipsCasChooserPage() {
        assertEquals(
            "https://login.wsb.pl/cas/clientredirect?client_name=SAML2CASClient&locale=pl" +
                "&service=https%3A%2F%2Fusosweb.ideis.pl%2Fkontroler.php%3F_action%3Dlogowaniecas%2Findex",
            UsosApi.SILENT_LOGIN_URL,
        )
    }
}
