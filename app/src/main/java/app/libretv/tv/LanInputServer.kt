package app.libretv.tv

import java.io.Closeable
import java.io.InputStream
import java.net.*
import java.security.SecureRandom
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

/** Temporary, single-use LAN endpoint owned by the QR dialog. */
internal class LanInputServer(
 address:InetAddress, private val html:String, private val icon:ByteArray,
 private val ttlMillis:Long=300000, private val onQuery:(String)->Boolean
):Closeable {
 private val token=ByteArray(24).also{SecureRandom().nextBytes(it)}.joinToString(""){"%02x".format(it)}
 private val listener=ServerSocket(0,4,address)
 private val started=System.nanoTime()
 private val closed=AtomicBoolean(false)
 private val consumed=AtomicBoolean(false)
 private val workers=ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,ArrayBlockingQueue(4))
 val origin="http://${address.hostAddress}:${listener.localPort}"
 val url="$origin/?token=$token"
 val remainingSeconds:Long get()=((ttlMillis-(System.nanoTime()-started)/1000000).coerceAtLeast(0)+999)/1000
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
   if(closed.get()||remainingSeconds==0L||consumed.get()){respond(socket,410,"二维码已失效，请在电视上重新打开扫码输入");return}
   when {
    request[0]=="GET"&&route.path=="/"->respond(socket,200,html.replace("__TOKEN__",token),"text/html; charset=utf-8")
    request[0]=="GET"&&route.path=="/icon.png"->respondBytes(socket,200,icon,"image/png")
    request[0]=="POST"&&route.path=="/search"->{
     if(headers["origin"]!=origin){respond(socket,403,"请求来源无效");return}
     require(headers["content-type"]?.substringBefore(';')=="application/x-www-form-urlencoded")
     val length=headers["content-length"]?.toIntOrNull();require(length!=null&&length in 1..4096)
     val body=ByteArray(length);readFully(input,body)
     val form=String(body,Charsets.UTF_8);require(form.startsWith("query=")&&!form.contains('&'))
     val query=URLDecoder.decode(form.substring(6),"UTF-8").trim()
     require(query.length in 1..100&&query.none{it.isISOControl()}&&!query.contains('\uFFFD'))
     if(remainingSeconds==0L||closed.get()||!consumed.compareAndSet(false,true)){respond(socket,410,"二维码已失效");return}
     val accepted=try{onQuery(query)}catch(_:Exception){false}
     respond(socket,if(accepted)200 else 410,if(accepted)"已发送，电视正在搜索" else "电视已离开扫码页面，请重新打开")
    }
    else->respond(socket,404,"页面不存在")
   }
  }catch(_:Exception){try{respond(socket,400,"请输入 1–100 个字符，并重新提交")}catch(_:Exception){/* Client disconnected. */}}
 }
 private fun readFully(input:InputStream,bytes:ByteArray){var offset=0;while(offset<bytes.size){val count=input.read(bytes,offset,bytes.size-offset);require(count>0);offset+=count}}
 private fun respond(socket:Socket,code:Int,text:String,type:String="text/plain; charset=utf-8")=respondBytes(socket,code,text.toByteArray(Charsets.UTF_8),type)
 private fun respondBytes(socket:Socket,code:Int,bytes:ByteArray,type:String){
  val headers="HTTP/1.1 $code ${if(code==200)"OK" else "Error"}\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'none'; script-src 'nonce-$token'; style-src 'unsafe-inline'; img-src 'self'; connect-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'\r\n\r\n"
  socket.getOutputStream().apply{write(headers.toByteArray(Charsets.US_ASCII));write(bytes);flush()}
 }
 override fun close(){if(closed.compareAndSet(false,true)){listener.close();workers.shutdown()}}
}
