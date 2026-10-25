package app.libretv.tv
import android.content.Context
import org.json.JSONArray
class WatchStore(context:Context) {
 private val prefs=context.getSharedPreferences("libretv",Context.MODE_PRIVATE)
 companion object {private val restored=java.util.Collections.newSetFromMap(java.util.IdentityHashMap<android.content.SharedPreferences,Boolean>())}
 init {SessionCookies.install();if(synchronized(restored){restored.add(prefs)})prefs.all.filterKeys{it.startsWith("session:")}.forEach{(key,value)->val base=key.removePrefix("session:");try{SessionCookies.commit(base,SiteSession.read(value as? String?:""))}catch(_:IllegalArgumentException){/* Ignore invalid saved addresses. */}}}
 internal fun connected(api:LibreTvApi){val editor=prefs.edit().putString("server",api.base);val key="session:${SessionCookies.key(api.base)}";prefs.all.keys.filter{it.startsWith("session:")&&it!=key}.forEach{old->try{if(SessionCookies.key(old.removePrefix("session:"))==SessionCookies.key(api.base))editor.remove(old)}catch(_:IllegalArgumentException){}};val session=api.session;if(session!=null&&session.expires>0)editor.putString(key,session.json().toString())else editor.remove(key);check(editor.commit()){"无法保存登录会话，请重试"};SessionCookies.commit(api.base,session)}
 internal fun clearSession(base:String){prefs.edit().remove("session:${SessionCookies.key(base)}").apply();SessionCookies.commit(base,null)}
 var server:String
  get()=prefs.getString("server","http://192.168.31.1:8899")!!
  set(v){prefs.edit().putString("server",Core.server(v)).apply()}
 private fun rows(key:String)=JSONArray(prefs.getString(key,"[]")).objects().map(WatchRecord::from)
 fun history()=rows("history").filter{it.server==server}
 fun favorites()=rows("favorites").filter{it.server==server}
 fun save(record:WatchRecord) { val all=rows("history").filterNot{it.server==record.server && it.video.key==record.video.key};prefs.edit().putString("history",JSONArray((listOf(record)+all).take(150).map{it.json()}).toString()).apply() }
 fun favorite(v:Video)=favorites().any{it.video.key==v.key}
 fun toggle(v:Video):Boolean {
  val old=rows("favorites");val has=old.any{it.server==server && it.video.key==v.key}
  val next=if(has)old.filterNot{it.server==server&&it.video.key==v.key}else listOf(WatchRecord(server,v,0,0,0))+old
  prefs.edit().putString("favorites",JSONArray(next.map{it.json()}).toString()).apply();return !has
 }
}
