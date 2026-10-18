package app.libretv.tv
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
class LibreTvApi(raw:String) {
 val base=Core.server(raw)
 private val connections=java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<HttpURLConnection,Boolean>())
 fun cancel(){connections.forEach{it.disconnect()};connections.clear()}
 fun request(path:String,body:JSONObject?=null):JSONObject {
  check(!Thread.currentThread().isInterrupted){"请求已取消"}
  val c=URL(base+path).openConnection() as HttpURLConnection
  connections.add(c);c.connectTimeout=10000;c.readTimeout=25000;c.setRequestProperty("Accept","application/json")
  try {
   if(body!=null){c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(body.toString().toByteArray())}}
   val code=c.responseCode; val text=(if(code in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty()
   val j=try{JSONObject(text)}catch(e:Exception){throw IllegalStateException("服务返回了无效数据（HTTP $code）",e)}
   check(code in 200..299 && !j.has("error")){j.optString("error","请求失败 HTTP $code")}
   return j
  }finally{connections.remove(c);c.disconnect()}
 }
 fun status():List<Source> { val j=request("/api/status");check(!j.optBoolean("passwordRequired")||j.optBoolean("verified")){"服务要求登录，请在服务端配置访问方式"};return j.getJSONArray("defaultSources").objects().map(Source::from) }
 fun search(query:String,sources:List<Source>):Pair<List<Video>,Int> {
  require(query.isNotBlank() && query.length<=100){"请输入 1–100 字的片名"};require(sources.isNotEmpty()){"服务未配置点播源"}
  val j=request("/api/search",JSONObject().put("wd",query).put("sources",JSONArray(sources.map{it.json()})).put("filterAdult",true))
  return j.getJSONArray("list").objects().map(Video::from) to (j.optJSONArray("failures")?.length()?:0)
 }
 fun detail(v:Video,sources:List<Source>):Detail { val s=sources.find{it.key==v.sourceKey}?:Source(v.sourceKey,v.sourceName,v.sourceUrl);return Detail.from(request("/api/detail?id=${encode(v.id)}&source=${encode(s.json().toString())}")) }
 fun recommended()=request("/api/hot-list?id=douban_movie_weekly").getJSONArray("items").objects().map(Video::from)
 fun proxy(url:String)="$base/api/proxy?url=${encode(url)}"
 companion object {fun encode(s:String)=URLEncoder.encode(s,"UTF-8")}
}
