package app.libretv.tv

import org.json.JSONObject
import java.io.Closeable
import java.io.InputStream
import java.net.*
import java.security.SecureRandom
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

/** Foreground-only LAN remote; every route requires the session token. */
internal class LanInputServer(
 address:InetAddress, private val html:String, private val icon:ByteArray,
 private val action:(String,JSONObject)->JSONObject
):Closeable {
 private val token=ByteArray(24).also{SecureRandom().nextBytes(it)}.joinToString(""){"%02x".format(it)}
 private val listener=ServerSocket(0,4,address)
 private val closed=AtomicBoolean(false)
 private val workers=ThreadPoolExecutor(4,4,0,TimeUnit.SECONDS,ArrayBlockingQueue(8))
 val origin="http://${address.hostAddress}:${listener.localPort}"
 val url="$origin/?token=$token"
 init {
  Thread({
   while(!closed.get())try {
    val socket=listener.accept();socket.soTimeout=3000
    try{workers.execute{socket.use{serve(it)}}}catch(_:RejectedExecutionException){socket.close()}
   }catch(_:SocketException){close()}catch(_:java.io.IOException){close()}
  },"phone-input-listener").start()
 }
 private fun serve(socket:Socket){
  try {
   val input=socket.getInputStream()
   var budget=8192
   fun line():String {
    val bytes=java.io.ByteArrayOutputStream()
    while(true){require(--budget>=0);val b=input.read();require(b>=0);if(b==10)break;bytes.write(b)}
    val value=bytes.toString("US-ASCII");require(value.endsWith("\r"));return value.dropLast(1)
   }
   val request=line().split(' ');require(request.size==3&&request[2]=="HTTP/1.1")
   val headers=mutableMapOf<String,String>()
   while(true){val line=line();if(line.isEmpty())break;val colon=line.indexOf(':');require(colon>0);val key=line.substring(0,colon).lowercase(java.util.Locale.ROOT);require(!headers.containsKey(key));headers[key]=line.substring(colon+1).trim()}
   require(!headers.containsKey("transfer-encoding"))
   val route=URI(request[1]);require(!route.isAbsolute&&route.rawFragment==null)
   if(headers["host"]!=origin.removePrefix("http://")||route.rawQuery!="token=$token"){respond(socket,403,"二维码无效，请重新扫描");return}
   if(closed.get()){respond(socket,410,"连接已结束，请重新扫码");return}
   when {
    request[0]=="GET"&&route.path=="/"->respond(socket,200,html.replace("__TOKEN__",token),"text/html; charset=utf-8")
    request[0]=="GET"&&route.path=="/icon.png"->respondBytes(socket,200,icon,"image/png")
    request[0]=="GET"&&route.path=="/state"->respond(socket,200,action("/state",JSONObject()).toString(),"application/json; charset=utf-8")
    request[0]=="POST"&&route.path in listOf("/search","/detail","/play","/control")->{
     if(headers["origin"]!=origin){respond(socket,403,"请求来源无效");return}
     require(headers["content-type"]?.substringBefore(';')=="application/json"){"请求格式无效"}
     val length=headers["content-length"]?.toIntOrNull();require(length!=null&&length in 1..4096){"请求内容过长"}
     val body=ByteArray(length);readFully(input,body)
     val data=JSONObject(String(body,Charsets.UTF_8))
     when(route.path){
      "/search"->{val q=data.getString("query").trim();require(q.length in 1..100&&q.none{it.isISOControl()}&&!q.contains('\uFFFD')){"请输入 1–100 个字符"};data.put("query",q)}
      "/detail","/play"->{require(data.getString("id").matches(Regex("[a-zA-Z0-9-]{1,80}"))){"影片标识无效"};if(route.path=="/play")require(data.opt("episode") is Int&&data.getInt("episode") in 0..100000){"剧集无效"}}
      "/control"->{require(data.getString("mediaId").length in 1..160){"播放标识无效"};val command=data.getString("action");require(command in listOf("play","pause","seek")){"播放操作无效"};if(command=="seek"){val value=data.opt("position");require((value is Int||value is Long)&&data.getLong("position") in 0..604800000){"进度无效"}}}
     }
     respond(socket,200,action(route.path,data).toString(),"application/json; charset=utf-8")
    }
    else->respond(socket,404,"页面不存在")
   }
  }catch(e:Exception){try{
   val code=when(e){is IllegalArgumentException,is org.json.JSONException->400;is IllegalStateException->409;else->503}
   respond(socket,code,e.message?:"电视暂时无法完成请求，请重试")
  }catch(_:Exception){/* Client disconnected. */}}
 }

 private fun readFully(input:InputStream,bytes:ByteArray){var offset=0;while(offset<bytes.size){val count=input.read(bytes,offset,bytes.size-offset);require(count>0);offset+=count}}
 private fun respond(socket:Socket,code:Int,text:String,type:String="text/plain; charset=utf-8")=respondBytes(socket,code,text.toByteArray(Charsets.UTF_8),type)
 private fun respondBytes(socket:Socket,code:Int,bytes:ByteArray,type:String){
  val headers="HTTP/1.1 $code ${if(code==200)"OK" else "Error"}\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'none'; script-src 'nonce-$token'; style-src 'unsafe-inline'; img-src 'self' http: https:; connect-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'\r\n\r\n"
  socket.getOutputStream().apply{write(headers.toByteArray(Charsets.US_ASCII));write(bytes);flush()}
 }
 override fun close(){if(closed.compareAndSet(false,true)){listener.close();workers.shutdown()}}
}
