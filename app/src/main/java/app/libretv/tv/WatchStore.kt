package app.libretv.tv
import android.content.Context
import org.json.JSONArray
class WatchStore(context:Context) {
 private val prefs=context.getSharedPreferences("libretv",Context.MODE_PRIVATE)
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
