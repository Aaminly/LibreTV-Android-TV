package app.libretv.tv

import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder

class LanInputServerTest {
 private fun request(url:String, query:String?=null, origin:String?=null):Pair<Int,String> {
  val u=URL(url);val body=query?.let{"query=${URLEncoder.encode(it,"UTF-8")}"}.orEmpty().toByteArray()
  return java.net.Socket(u.host,u.port).use{socket->
   socket.soTimeout=3000
   val headers="${if(query==null)"GET" else "POST"} ${u.file} HTTP/1.1\r\nHost: ${u.host}:${u.port}\r\n"+(if(query!=null)"Content-Type: application/x-www-form-urlencoded\r\nContent-Length: ${body.size}\r\n" else "")+(origin?.let{"Origin: $it\r\n"}?:"")+"\r\n"
   socket.getOutputStream().apply{write(headers.toByteArray());write(body);flush()}
   val input=socket.getInputStream();val header=StringBuilder()
   while(!header.endsWith("\r\n\r\n")){val b=input.read();require(b>=0);header.append(b.toChar())}
   val length=Regex("Content-Length: (\\d+)").find(header)!!.groupValues[1].toInt()
   val data=ByteArray(length);var n=0;while(n<length){val count=input.read(data,n,length-n);require(count>0);n+=count}
   header.toString().substringBefore("\r\n").split(' ')[1].toInt() to String(data,Charsets.UTF_8)
  }
 }
 private fun server(ttl:Long=300000,onQuery:(String)->Boolean={true})=LanInputServer(InetAddress.getByName("127.0.0.1"),"<input>",byteArrayOf(1),ttl,onQuery)
 @Test fun chineseQueryIsDeliveredExactlyOnce(){
  val received=mutableListOf<String>()
  server{received.add(it);true}.use{s->
   assertEquals(200,request(s.url).first)
   assertEquals(200,request(s.url.replace("/?","/search?"),"  流浪地球  ",s.origin).first)
   assertEquals(listOf("流浪地球"),received)
   assertEquals(410,request(s.url.replace("/?","/search?"),"第二次",s.origin).first)
  }
 }
 @Test fun tokenOriginAndInputAreCheckedBeforeCallback(){
  var calls=0
  server{calls++;true}.use{s->
   val post=s.url.replace("/?","/search?")
   assertEquals(403,request(s.origin+"/?token=wrong").first)
   assertEquals(403,request(post,"测试","http://evil.invalid").first)
   assertEquals(403,request(post,"测试").first)
   assertEquals(400,request(post,"  ",s.origin).first)
   assertEquals(400,request(post,"字".repeat(101),s.origin).first)
   assertEquals(400,request(post,"a\nb",s.origin).first)
   assertEquals(0,calls)
  }
 }
 @Test fun expiredOrRejectedSessionNeverReportsSuccess(){
  server(1).use{s->Thread.sleep(10);assertEquals(410,request(s.url).first)}
  server{false}.use{s->assertEquals(410,request(s.url.replace("/?","/search?"),"测试",s.origin).first)}
 }
 @Test fun concurrentSubmissionsOnlyTriggerOneSearchAndCloseStopsListener(){
  val calls=java.util.concurrent.atomic.AtomicInteger()
  val s=server{calls.incrementAndGet();Thread.sleep(50);true}
  val executor=java.util.concurrent.Executors.newFixedThreadPool(2)
  try{
   val start=java.util.concurrent.CountDownLatch(1)
   val requests=(1..2).map{executor.submit<Int>{start.await();request(s.url.replace("/?","/search?"),"测试",s.origin).first}}
   start.countDown()
   assertEquals(listOf(200,410),requests.map{it.get(3,java.util.concurrent.TimeUnit.SECONDS)}.sorted())
   assertEquals(1,calls.get())
  }finally{executor.shutdownNow();s.close()}
  try{request(s.url);fail("Closed listener accepted a connection")}catch(_:java.io.IOException){}
 }

}
