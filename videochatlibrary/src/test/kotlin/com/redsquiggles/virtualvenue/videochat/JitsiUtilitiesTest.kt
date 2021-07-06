package com.redsquiggles.virtualvenue.videochat

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.redsquiggles.communications.bus.Context
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.*
import java.security.interfaces.RSAPublicKey

internal class JitsiUtilitiesTest {

    val testPrivateKey = "MIIJQQIBADANBgkqhkiG9w0BAQEFAASCCSswggknAgEAAoICAQDc8xBM56H/gSkw" +
            "t7hgIdYbWTv8eJvhx5/Nqcfq+t6kpHuUBUcVJICndhReXBjA5LI5M5cbxHTIB4ik" +
            "UyKJs5PP7zWXpx+GlyiZw0sef443sQtHWOOBlmzPLb7kmpVcSe6AjcJpHrhWoW7I" +
            "nKVbaATkxjnZ1LRijYGV0e1Imo/Wu3Y6M2JzyVfTFNRIDgoQBwlVD65K2vqtbTbH" +
            "y8SkXiV3LCWE4BuwZYl8+mwEn0th88rBSAeBdGYJpllQ4bHmnlq2IFGvQURElw/F" +
            "C2nqOnoBI5r00qlFupI8CNoHWOuR1L9tnlBXhGKqSGAY6EWhfK7yALWEM21WEJvz" +
            "C5QZdKlbM6Wb2KS9MJc8Ez6q0yhDsh/q64K1KKV7D6L7zK8//M1BGXST6crpFX98" +
            "5MNHLxR1E4mQor6OTrZHfBYG6heYUeDyDZEoDmG0KEgVq5IHqM+6HisCKXV7RDJy" +
            "eJYQJVkReTQtFy2QKJpBWzFsQ1MGoWHDp0Db9dVWd6LCgtJJhpgEWeE5VqEGjRzH" +
            "oIczu2lGqNk3/4QsEQvsPboEixhJZq64ibwNYlJABYXWG/DhE0SgvVXY2dY+maRZ" +
            "50/KOJVML2YEVl70lsrwuQcHnSA1lu+1x+Sh8LvDWeOwbfwIbEeaKsgxfNQxzU9f" +
            "7ElFATPgqYLCmNza57YpLXiEk0XDLwIDAQABAoICACw5wuOB4d1AvzvvKkqjuzWS" +
            "MP2iLqGM0aHbABc6y+HswoeoXsgOnGnoqr8QdCv4Gux0NSTrt+xqBaHOujUR6t5O" +
            "JU4Lt7W6//d3LcwXACKJn5ZSZoeD0pfNsk4T2x2z9rdoqKdd7Mv6WDBzmm2nboNU" +
            "YjQF7W1kobGZaYOE3JHAyDNyIZzHinrHyo35sW3v2qBaGSsGlfKsz7BCS0QTTDCE" +
            "d6YXLbOrP0/y2Dg8olwi7kyt3EK/R8VatLoJ0xA0VjfqVC9eQwnvqspoNHUb61vb" +
            "AqNKn3NDmw3FP88Er3JrQT3x2GcMKqum/QU/SODAEkyCc9LYcGQvwudtQ+GshlFJ" +
            "13+UzkgvlZj4CJ5ZKzL/gtulQzF+c+lGQOOMiwzOgTSW3PxzIwwY4CKerNI/9Uf4" +
            "/Ka4Atek4GYU7l3+/o+Fdqj2umtfQvWEy6W2zqoIRttA3iKcfrVplBdPKoz+kWCY" +
            "MwVp3TnZpuXCesTSKxflwMapeaHIGxOWSp3or6VGsPX0XPuJIl001cgDrnhQ6i64" +
            "BBNlzKJPH9mkVDfYQ9cX+iegOgkrOA8pU5UwIaKLM6Ak0DqvjHaIWo7F6DAR9xKc" +
            "DbAdoxwvR9NS334gQogcTtTRxkMidwdk4QtdFkSnUT9ninfu2fDNO+2eYn9pn8sO" +
            "7LzasampRO/pizw9gfVxAoIBAQDwyjwZYYDiRuc3vrJLL04V5uFmk0XiDqGlBxki" +
            "PURqUiR2bsEPGIuxz+Ni6Y2+3EeY6SwtWXnAFxhm4aun6wOC9245IKXKZPgysT1J" +
            "s6zakzX3VF7Y+qvQQaCD/v4zMSrUcE1W8DUZbWRbaQOldK6HkapM6Pp4a9K1SGem" +
            "wjfNEzKfCKOFyi7OGwbau4Mj4SHXlJ3/H3W/vGtgx7fXccLIds9RMsM/yufLgeFN" +
            "AtBwv8iR12RlV6e5kWkaFPDSbdAZ0NSWSZuc/9oFWv96ZgQM/Cu2MxKhglMOJFPp" +
            "uiRexiKB5GDu7Pur4VSV+BfOqBbGJVL4uUjof521mfTH2KilAoIBAQDq5/34Xd+6" +
            "gpd9SegGhIdV7pVw0XvPtuYFIHmvbe+tN2lyei++fNSC8dgOmtQUs9O+NwQoZYQw" +
            "etP1xr2+l42qTFTZaItxpXJ4UXB5g3nG/5lZvvTqHc2I6OExFwIn5LUBWkP2rK0B" +
            "gAJnAf33rdQBcd8nXSoXaL11V5RxZsuen07p0f22Vu8GGdFy9r2F4V/T1BaquKAT" +
            "Nc5zrzP8cr6/SrE+b7yMHu5Za5xfA465vRmTgKZ/DJ6CYUCmEE58sQQzPXFpm3WT" +
            "zozzPIqobrOcUPS6Kp06pvXmBqBFTwKVI1nnOU0nBYt3jjpI2W939R5B+n+ZnFy9" +
            "yVqXxhgI+SBDAoIBAGZ3mUWvwXoJdEG7rAHkupUFcGwHRhjh4xXoRGDWs7OPCyc/" +
            "EHcNGf1sGzavbvuGoA1JRNxzlCUTbvXxGOxXTWJBSy2SYBsWBq0D0bH4fRlyxedf" +
            "hxFM8yqnktg4/hHo3XIT7EWP6PjOHYPs13lkgxT7/v3FszjloYA1tK45PifOAJ++" +
            "vF+l328j/zG71B3Do3QrMWUtDR2v3/4KX4iWR66pKKZENOSDw0pOFgOArBUe+Apg" +
            "H8AkoiKM564KtUTHKGSkAqOF5WvVUQEwbUG4AgdIlI1vhIEohZGiEfeHmAygwnSG" +
            "LF3giWTmeelr7y2pSC/AGGPx7T3LeOCcCJe28mUCggEACLL8scTwMx0A9S/Wet9P" +
            "j7wdJqdrWV7hl9EDEJ8WoCiMLCQgPoKq8Ap6wNe1AOtD7ShmbASOZ6k0zkZE4pkE" +
            "MwlOnxqDB3tkCNajPqzCxr7SdANhWIYwBWDybfn3J3kNxvYLzEfKxGJ0NFJbkF+M" +
            "mV0ZMYD+vQ0w7vaGVcjDw6UMrBYNukHv6h7spcBDdoDJJrQU/s+FhhCZC6myWCf7" +
            "rmPauoAy0FGY7BDTIqWkFHbPSqoaAFx8RkHJvhyee3mmuSsOVyXdrdKZX6yfSeuH" +
            "lUNePMK1PeXO92zJZKevZcyAWNLDa62F47CNsgLzYNLgAvt+3DZxLMoXI8W/QB3R" +
            "twKCAQBlvU897pK57iWOBeTiVG6ID1cOB8WjKr1Q3mSznGFseSc+xkku4eIcBbVu" +
            "Y8wsGhVaM77sws3SP6kjwCrMle1r0ZjHh8LEGkRQdcscVsqkUknE4KLb0aW0vW0M" +
            "WP5hT5+M5e3AQCGRLE8ynTW5VtpAhUpGuFQXtBVTDPdDiJO3vszRJQcN791Yfj0w" +
            "O427z6CQ6+ovwwC58n+lxH0ITZgZisRne5sKiopVoTugIv44u5WuXygcBYXmw//D" +
            "qzSNPPwVvIZ73pmbGrrCrhymj2WCVsRYC5+qbJSXuICzPnQMjcpkV4iuo0lK2jHU" +
            "p4nO9tsNEK6zQns/40hZrFzShifA"

    val testPublicKey = "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA3PMQTOeh/4EpMLe4YCHW" +
            "G1k7/Hib4cefzanH6vrepKR7lAVHFSSAp3YUXlwYwOSyOTOXG8R0yAeIpFMiibOT" +
            "z+81l6cfhpcomcNLHn+ON7ELR1jjgZZszy2+5JqVXEnugI3CaR64VqFuyJylW2gE" +
            "5MY52dS0Yo2BldHtSJqP1rt2OjNic8lX0xTUSA4KEAcJVQ+uStr6rW02x8vEpF4l" +
            "dywlhOAbsGWJfPpsBJ9LYfPKwUgHgXRmCaZZUOGx5p5atiBRr0FERJcPxQtp6jp6" +
            "ASOa9NKpRbqSPAjaB1jrkdS/bZ5QV4RiqkhgGOhFoXyu8gC1hDNtVhCb8wuUGXSp" +
            "WzOlm9ikvTCXPBM+qtMoQ7If6uuCtSilew+i+8yvP/zNQRl0k+nK6RV/fOTDRy8U" +
            "dROJkKK+jk62R3wWBuoXmFHg8g2RKA5htChIFauSB6jPuh4rAil1e0QycniWECVZ" +
            "EXk0LRctkCiaQVsxbENTBqFhw6dA2/XVVneiwoLSSYaYBFnhOVahBo0cx6CHM7tp" +
            "RqjZN/+ELBEL7D26BIsYSWauuIm8DWJSQAWF1hvw4RNEoL1V2NnWPpmkWedPyjiV" +
            "TC9mBFZe9JbK8LkHB50gNZbvtcfkofC7w1njsG38CGxHmirIMXzUMc1PX+xJRQEz" +
            "4KmCwpjc2ue2KS14hJNFwy8CAwEAAQ=="

//    val issuerUri = createIssuerURI(issuer)
//    val providerUrl = createProviderURL(issuerUri)
//    val provider = com.auth0.jwk.UrlJwkProvider(providerUrl)
//    val algorithm = Algorithm.RSA256(provider.get(jwt.keyId).publicKey as RSAPublicKey, null)
//
//    return JWT.require(algorithm)
//    .withAudience(audience)
//    .withIssuer(issuer)
//    .acceptLeeway(600)



    @org.junit.jupiter.api.Test
    fun `Generated Token Is Verified When Round Tripped`() {
        // Given an default Jitsi token with the following aribtary values
        val apiKey= "asldkfjds"
        val appKey = "Asdfadsf"
        val roomName = "awrogin"
        var userId = "24h0824gg"
        var userName = "42g9u-2hy42h4"
        var expected = JitsiJWTClaimBuilder.default().apply {
            this.apiKey = apiKey
            this.jitsiAppKey = appKey
            this.roomName = roomName
            this.userId = userId
            this.userName = userName

        }

        println(testPrivateKey)
       // When it is used to create a token with the test private key and the token is then
       // decoded and verified using the corresponding public key
       var token = expected.build().signJitsi(parseRSAPrivateKey(testPrivateKey))

        var decoded = JWT.decode(token)
        var publicKey = parseRSAPublicKey(testPublicKey)
        val algorithm = Algorithm.RSA256(publicKey, null)
        val verified = JWT.require(algorithm)
            .withAudience(JitsiJWTClaimBuilder.aud)
            .withIssuer(JitsiJWTClaimBuilder.issuer)
            .acceptLeeway(600)
            .build()
            .verify(token)

        // then the verification step does not throw an exception and the decoded values match the originals
        Assertions.assertEquals(apiKey, verified.keyId)
        Assertions.assertEquals(appKey, verified.subject)
        Assertions.assertEquals(roomName, verified.claims["room"]!!.asString())
        Assertions.assertEquals(expected.featureClaims, verified.claims["context"]!!.asMap()["features"])
        Assertions.assertEquals(expected.userClaims, verified.claims["context"]!!.asMap()["user"])
        Assertions.assertEquals(expected.userName, (verified.claims["context"]!!.asMap()["user"] as Map<String,String>)!!["name"])
        Assertions.assertEquals(expected.userId, (verified.claims["context"]!!.asMap()["user"] as Map<String,String>)!!["id"])




    }
}