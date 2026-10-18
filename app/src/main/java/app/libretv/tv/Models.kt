package app.libretv.tv
import org.json.JSONObject
import org.json.JSONArray
fun JSONArray.objects()=(0 until length()).map { getJSONObject(it) }
data class Source(val key:String,val name:String,val url:String,val detail:String="") {
 fun json()=JSONObject().put("key",key).put("name",name).put("url",url).put("detail",detail)
 companion object { fun from(j:JSONObject)=Source(j.getString("key"),j.getString("name"),j.getString("url"),j.optString("detail")) }
}
data class Video(val id:String,val title:String,val cover:String="",val sourceKey:String="",val sourceName:String="",val sourceUrl:String="",val remarks:String="") {
 val key get()="$sourceKey:$id"
 fun json()=JSONObject().put("vodId",id).put("name",title).put("pic",cover).put("sourceKey",sourceKey).put("sourceName",sourceName).put("sourceUrl",sourceUrl).put("remarks",remarks)
 companion object {
  fun from(j:JSONObject)=Video(j.optString("vodId",j.optString("id")),j.optString("name",j.optString("title")),j.optString("pic",j.optString("cover")),j.optString("sourceKey"),j.optString("sourceName"),j.optString("sourceUrl"),j.optString("remarks"))
 }
}
data class Detail(val episodes:List<String>,val title:String,val description:String) {
 companion object { fun from(j:JSONObject):Detail { val a=j.getJSONArray("episodes");require(a.length()>0){"未找到播放资源"}; val info=j.getJSONObject("videoInfo"); return Detail((0 until a.length()).map{a.getString(it)},info.optString("title"),info.optString("desc")) } }
}
data class WatchRecord(val server:String,val video:Video,val episode:Int,val position:Long,val duration:Long) {
 fun json()=JSONObject().put("server",server).put("video",video.json()).put("episode",episode).put("position",position).put("duration",duration)
 companion object { fun from(j:JSONObject)=WatchRecord(j.getString("server"),Video.from(j.getJSONObject("video")),j.getInt("episode"),j.getLong("position"),j.optLong("duration")) }
}
