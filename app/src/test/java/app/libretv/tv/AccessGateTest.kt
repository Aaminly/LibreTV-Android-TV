package app.libretv.tv
import org.junit.Assert.*
import org.junit.Test
class AccessGateTest {
 @Test fun appStartsLockedAndAStoredCookieAloneCannotUnlock(){assertFalse(AccessGate.allowed("https://locked.test"))}
 @Test fun verifiedServiceDoesNotUnlockAnotherAddress(){AccessGate.grant("https://first.test",null);assertTrue(AccessGate.allowed("https://first.test"));assertFalse(AccessGate.allowed("https://second.test"));AccessGate.lock()}
 @Test fun expiredCookieAndOldFailuresCannotUseOrReplaceNewLogin(){val old=SiteSession("old",System.currentTimeMillis()+60000,false);val fresh=SiteSession("fresh",System.currentTimeMillis()+60000,false);SessionCookies.commit("https://gate.test",fresh);AccessGate.grant("https://gate.test",fresh);assertFalse(AccessGate.reject(LoginRequired("https://gate.test",old.token)));assertTrue(AccessGate.allowed("https://gate.test"));assertTrue(AccessGate.reject(LoginRequired("https://gate.test",fresh.token)));assertFalse(AccessGate.allowed("https://gate.test"));SessionCookies.commit("https://gate.test",null)}
}
