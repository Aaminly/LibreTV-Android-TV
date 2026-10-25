package app.libretv.tv

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID
import java.util.concurrent.*

/** One session across the app's two foreground activities; no background remote control. */
internal object RemoteControl {
 private val handler=Handler(Looper.getMainLooper())
 private val starter=Executors.newSingleThreadExecutor()
 private var foreground=WeakReference<Activity>(null)
 private var main=WeakReference<MainActivity>(null)
 private var server:LanInputServer?=null
 private var starting=false
 @Volatile private var generation=0
 private val apis=ConcurrentHashMap.newKeySet<LibreTvApi>()
 private data class Selection(val video:Video,val base:String,@Volatile var detail:Detail?=null)
 private val selections=java.util.Collections.synchronizedMap(object:LinkedHashMap<String,Selection>(){
  override fun removeEldestEntry(entry:MutableMap.MutableEntry<String,Selection>?)=size>1000
 })
 val url:String? get()=server?.url
 private val stop=Runnable{if(foreground.get()==null)close()}
 fun foreground(activity:Activity){
  if(!AccessGate.allowed(WatchStore(activity).server)){close();return}
  handler.removeCallbacks(stop);foreground=WeakReference(activity)
  if(activity is MainActivity)main=WeakReference(activity)
  if(server!=null){main.get()?.remoteQr(url,null);return}
  start(activity)
 }
 fun background(activity:Activity){if(foreground.get()===activity){foreground.clear();handler.postDelayed(stop,1000)}}
 fun lock(){close()}
 fun restart(){val activity=foreground.get()?:return;if(!AccessGate.allowed(WatchStore(activity).server))return;close();start(activity)}
 private fun close(){generation++;server?.close();server=null;starting=false;apis.forEach{it.cancel()};selections.clear();main.get()?.remoteQr(null,"连接已关闭")}
 private fun start(activity:Activity){
  if(starting)return;starting=true;val current=generation;val context=activity.applicationContext
  main.get()?.remoteQr(null,"正在连接…")
  starter.execute{
   var created:LanInputServer?=null
   try{
    val ip=NetworkInterface.getNetworkInterfaces().toList().filter{it.isUp&&!it.isLoopback}.sortedBy{if(it.name.startsWith("wlan")||it.name.startsWith("eth"))0 else 1}.flatMap{it.inetAddresses.toList()}.filterIsInstance<Inet4Address>().firstOrNull{it.isSiteLocalAddress&&!it.isLoopbackAddress}?:error("请连接局域网")
    val html=context.assets.open("phone-input.html").bufferedReader().use{it.readText()};val icon=context.assets.open("bunny.png").use{it.readBytes()}
    created=LanInputServer(ip,html,icon){path,data->check(current==generation){"连接已结束，请重新扫码"};route(path,data,current)}
    created.image={id->cover(id,current)}
    val ready=created
    handler.post{if(current!=generation||foreground.get()==null){ready.close();if(current==generation)starting=false}else{server=ready;starting=false;main.get()?.remoteQr(ready.url,null)}}
   }catch(e:Exception){created?.close();handler.post{if(current==generation){starting=false;main.get()?.remoteQr(null,e.message?:"连接失败，确定重试")}}}
  }
 }
 private fun <T> ui(current:Int,action:(Activity)->T):T {
  val task=FutureTask<T>{check(current==generation){"连接已结束，请重新扫码"};val activity=foreground.get();check(activity!=null&&!activity.isFinishing&&!activity.isDestroyed){"电视已离开应用"};check(AccessGate.allowed(WatchStore(activity).server)){"请先在电视上完成访问验证"};action(activity)}
  handler.post(task)
  try{return task.get(3,TimeUnit.SECONDS)}catch(e:ExecutionException){throw (e.cause as? Exception?:e)}catch(e:Exception){task.cancel(false);throw IllegalStateException("电视未响应，请重试",e)}
 }
 private fun cover(id:String,current:Int):Pair<ByteArray,String> {
  val item=selections[id]?:error("封面已失效，请重新搜索")
  ui(current){check(WatchStore(it).server==item.base){"服务地址已变化"}}
  val c=java.net.URL(LibreTvApi(item.base).proxy(item.video.cover)).openConnection() as java.net.HttpURLConnection;c.connectTimeout=7000;c.readTimeout=10000
  try{if(c.responseCode==401){val client=LibreTvApi(item.base);apis.add(client);try{client.status()}catch(e:LoginRequired){handler.post{if(current==generation){val activity=foreground.get();if(activity is PlaybackActivity)activity.loginRequired(e)else main.get()?.loginRequired(e)}};throw e}finally{apis.remove(client);client.cancel()};error("封面来源要求额外授权")}
   check(c.responseCode in 200..299){"封面加载失败"};val type=c.contentType?.substringBefore(';').orEmpty();require(type in listOf("image/png","image/jpeg","image/webp","image/gif","image/avif")){"封面格式无效"}
   val bytes=c.inputStream.use{input->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=8*1024*1024){"封面文件过大"};out.write(buffer,0,n)};out.toByteArray()}
   check(current==generation&&AccessGate.allowed(item.base)){"连接已结束"};return bytes to type
  }finally{c.disconnect()}
 }
 private fun route(path:String,data:JSONObject,current:Int):JSONObject {
  if(path=="/state")return ui(current){activity->if(activity is PlaybackActivity)activity.remoteState()else JSONObject().put("active",false)}
  if(path=="/control")return ui(current){activity->check(activity is PlaybackActivity){"电视当前没有播放影片"};activity.remoteCommand(data)}
  if(path=="/play"){
   val item=selections[data.getString("id")]?:error("结果已失效，请重新搜索")
   val detail=item.detail?:error("请先读取影片剧集");val episode=data.getInt("episode");require(episode in detail.episodes.indices){"剧集不存在"}
   return ui(current){activity->
    check(WatchStore(activity).server==item.base){"服务地址已变化，请重新搜索"}
    if(activity is PlaybackActivity)activity.remoteOpen(item.video,item.base,episode)
    else activity.startActivity(Intent(activity,PlaybackActivity::class.java).putExtra("video",item.video.json().toString()).putExtra("server",item.base).putExtra("episode",episode))
    JSONObject().put("accepted",true)
   }
  }
  val item=if(path=="/detail")selections[data.getString("id")]?:error("结果已失效，请重新搜索")else null
  val base=ui(current){WatchStore(it).server};check(item==null||item.base==base){"服务地址已变化，请重新搜索"}
  val api=LibreTvApi(base);apis.add(api)
  try {
   val sources=api.status()
   if(item!=null){val detail=api.detail(item.video,sources);check(current==generation){"连接已结束"};item.detail=detail
    return JSONObject().put("title",item.video.title).put("source",item.video.sourceName).put("episodes",JSONArray(detail.episodes.mapIndexed{i,e->Core.episodeLabel(e,i)}))}
   val (videos,failed)=api.search(data.getString("query"),sources);check(current==generation){"连接已结束"}
   val list=JSONArray();videos.forEach{v->val id=UUID.randomUUID().toString();selections[id]=Selection(v,base);list.put(JSONObject().put("id",id).put("title",v.title).put("source",v.sourceName).put("remarks",v.remarks).put("cover",if(v.cover.isBlank())""else server?.cover(id).orEmpty()))}
   return JSONObject().put("items",list).put("failed",failed)
  }catch(e:LoginRequired){handler.post{if(current==generation){val activity=foreground.get();if(activity is PlaybackActivity)activity.loginRequired(e)else main.get()?.loginRequired(e)}};throw e}finally{apis.remove(api);api.cancel()}
 }
}
