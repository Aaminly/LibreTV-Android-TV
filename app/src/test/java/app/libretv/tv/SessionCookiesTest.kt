package app.libretv.tv

import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class SessionCookiesTest {
 @Test fun mediaCookiesStayInsideExactServiceOriginAndApiPath(){val base="https://movie.test:8443/tv";val session=SiteSession("token",System.currentTimeMillis()+60000,true);SessionCookies.commit(base,session)
  fun cookie(url:String)=SessionCookies.get(URI(url),emptyMap())["Cookie"]?.first()
  assertEquals("ltv_session=token",cookie("$base/api/proxy?url=x"))
  listOf("https://movie.test/tv/api/proxy","https://other.test:8443/tv/api/proxy","http://movie.test:8443/tv/api/proxy","$base/other","https://movie.test:8443/tv-other/api/proxy").forEach{assertNull(cookie(it))}
  SessionCookies.commit(base,null)
 }
 @Test fun failedCandidateCannotUseOrReplaceCommittedSession(){val base="https://candidate.test";val saved=SiteSession("saved",System.currentTimeMillis()+60000,false);SessionCookies.commit(base,saved)
  SessionCookies.using(base,null){assertTrue(SessionCookies.get(URI("$base/api/status"),emptyMap()).isEmpty())}
  SessionCookies.using(base,SiteSession("new",System.currentTimeMillis()+60000,false)){assertEquals(listOf("ltv_session=new"),SessionCookies.get(URI("$base/api/status"),emptyMap())["Cookie"])}
  assertEquals(saved,SessionCookies.session(base));SessionCookies.commit(base,null)
 }
 @Test fun expiredSessionsAreNotSentOrRestored(){val expired=SiteSession("old",1,false);SessionCookies.commit("https://expired.test",expired);assertNull(SessionCookies.session("https://expired.test"));assertNull(SiteSession.read(expired.json().toString()));SessionCookies.commit("https://expired.test",null)}
 @Test fun cookieRoundTripRetainsExpiryAndSecure(){val s=SiteSession("signed.123",System.currentTimeMillis()+60000,true,"/tv/");assertEquals(s,SiteSession.read(s.json().toString()))}
 @Test fun equivalentOriginsUseMostRecentlyCommittedLogin(){SessionCookies.commit("http://EXAMPLE.test:80/tv",SiteSession("old",System.currentTimeMillis()+60000,false));SessionCookies.commit("http://example.test/tv",SiteSession("fresh",System.currentTimeMillis()+60000,false));assertEquals(listOf("ltv_session=fresh"),SessionCookies.get(URI("http://example.test/tv/api/proxy"),emptyMap())["Cookie"]);assertEquals("fresh",SessionCookies.session("http://EXAMPLE.test:80/tv")?.token);SessionCookies.commit("http://example.test/tv",null)}

}
