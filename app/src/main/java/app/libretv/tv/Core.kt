package app.libretv.tv
import java.net.URI
object Core {
 fun server(raw: String): String {
  val s=raw.trim().trimEnd('/'); val u=try { URI(s) } catch(e:Exception) { throw IllegalArgumentException("服务地址格式不正确",e) }
  require(u.scheme in listOf("http","https") && !u.host.isNullOrBlank() && u.userInfo==null && u.query==null && u.fragment==null) { "请输入完整的 HTTP 或 HTTPS 服务地址" }
  return s
 }
 fun gridDown(index:Int,count:Int,columns:Int)=if(index/columns==(count-1)/columns)index else (index+columns).coerceAtMost(count-1)
 fun move(index:Int, delta:Int, count:Int)=if(count==0) -1 else (index+delta).coerceIn(0,count-1)
 fun position(value:Long,duration:Long)=if(duration>0) value.coerceIn(0,duration) else value.coerceAtLeast(0)
 fun episodeLabel(raw:String,index:Int)=raw.substringBefore('$').takeIf{it.isNotBlank()&&!it.startsWith("http")}?:"第 ${index+1} 集"
 fun episode(raw:String):Pair<String,String> {
  val i=raw.indexOf('$');val name=if(i>=0)raw.substring(0,i) else "播放"
  val url=if(i>=0)raw.substring(i+1) else raw
  val u=URI(url);require(u.scheme in listOf("http","https") && !u.host.isNullOrBlank()) { "该集不是可直接播放的媒体地址" }
  return name to url
 }
}
class RequestGate { private var id=0; fun next()=++id; fun current(value:Int)=value==id }
