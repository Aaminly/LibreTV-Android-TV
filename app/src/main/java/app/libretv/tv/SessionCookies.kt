package app.libretv.tv

import java.net.CookieHandler
import java.net.HttpCookie
import java.net.URI
import org.json.JSONObject

internal data class SiteSession(val token:String,val expires:Long,val secure:Boolean,val path:String="/") {
 fun valid()=expires==0L||expires>System.currentTimeMillis()
 fun json()=JSONObject().put("token",token).put("expires",expires).put("secure",secure).put("path",path)
 companion object {
  fun read(value:String):SiteSession?=try{val j=JSONObject(value);SiteSession(j.getString("token"),j.getLong("expires"),j.getBoolean("secure"),j.optString("path","/")).takeIf{it.valid()&&it.token.matches(Regex("[A-Za-z0-9._~-]{1,2048}"))&&it.path.startsWith('/')}}catch(_:Exception){null}
  fun from(headers:Map<out String?,List<String>>,base:String):SiteSession? {
   val uri=URI(base)
   val cookie=headers.entries.filter{it.key.equals("Set-Cookie",true)}.flatMap{it.value}.flatMap{try{HttpCookie.parse(it)}catch(_:IllegalArgumentException){emptyList()}}.lastOrNull{it.name=="ltv_session"}?:return null
   require(cookie.domain==null||cookie.domain.trimStart('.').equals(uri.host,true)){"登录会话域名与服务不一致"}
   require(cookie.value.matches(Regex("[A-Za-z0-9._~-]{1,2048}"))&&cookie.maxAge!=0L){"服务返回了无效登录会话"}
   val expiry=if(cookie.maxAge<0)0L else System.currentTimeMillis()+cookie.maxAge.coerceAtMost(90*24*3600L)*1000
   return SiteSession(cookie.value,expiry,cookie.secure,cookie.path?:"/")
  }
 }
}

/** Only committed sessions reach image/media requests; candidate logins stay on their request thread. */
internal object SessionCookies:CookieHandler() {
 private val sessions=java.util.concurrent.ConcurrentHashMap<String,SiteSession>()
 private data class Candidate(val base:String,val session:SiteSession?,var response:Map<String,List<String>> = emptyMap())
 private val candidate=ThreadLocal<Candidate?>()
 fun install(){if(getDefault()!==this)setDefault(this)}
 fun key(base:String):String{val uri=URI(Core.server(base));return URI(uri.scheme.lowercase(java.util.Locale.ROOT),null,uri.host.lowercase(java.util.Locale.ROOT),if(uri.port>=0)uri.port else if(uri.scheme=="https")443 else 80,uri.path.orEmpty().trimEnd('/'),null,null).toASCIIString()}
 fun commit(base:String,session:SiteSession?){install();if(session==null)sessions.remove(key(base))else sessions[key(base)]=session}
 fun session(base:String)=sessions[key(base)]?.takeIf{it.valid()}
 fun <T> using(base:String,session:SiteSession?,work:()->T):T{install();val old=candidate.get();candidate.set(Candidate(base,session));try{return work()}finally{if(old==null)candidate.remove()else candidate.set(old)}}
 private fun matches(base:String,uri:URI,session:SiteSession):Boolean {
  val site=URI(base);fun port(u:URI)=if(u.port>=0)u.port else if(u.scheme=="https")443 else 80
  val scope=site.path.orEmpty().trimEnd('/')+"/api/";val path=uri.path.orEmpty()
  return session.valid()&&(!session.secure||uri.scheme=="https")&&site.scheme.equals(uri.scheme,true)&&site.host.equals(uri.host,true)&&port(site)==port(uri)&&path.startsWith(scope)&&path.startsWith(session.path)&& (session.path.endsWith('/')||path.length==session.path.length||path.getOrNull(session.path.length)=='/')
 }
 override fun get(uri:URI,requestHeaders:Map<String,List<String>>):Map<String,List<String>> {
  val current=candidate.get();val pair=if(current!=null)current.session?.let{current.base to it}else sessions.entries.sortedByDescending{it.key.length}.firstOrNull{matches(it.key,uri,it.value)}?.let{it.key to it.value}
  return if(pair!=null&&matches(pair.first,uri,pair.second))mapOf("Cookie" to listOf("ltv_session=${pair.second.token}"))else emptyMap()
 }
 fun received():Map<String,List<String>> = candidate.get()?.response.orEmpty()
 override fun put(uri:URI,responseHeaders:Map<String,List<String>>){candidate.get()?.let{it.response=responseHeaders}} // Capture candidate headers without committing them.
}
internal class LoginRequired(val base:String,val token:String?=null):IllegalStateException("此站点需要登录，请在电视上输入访问密码")
