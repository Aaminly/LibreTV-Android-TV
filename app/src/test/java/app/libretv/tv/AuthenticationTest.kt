package app.libretv.tv

import java.net.ServerSocket
import java.net.InetAddress
import kotlin.concurrent.thread
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AuthenticationTest {
 private data class Reply(val body:String,val code:Int=200,val headers:String="")
 private fun server(handler:(String,Map<String,String>,String)->Reply,test:(String)->Unit){
  val s=ServerSocket(0,10,InetAddress.getByName("127.0.0.1"));val worker=thread(isDaemon=true){while(!s.isClosed)try{s.accept().use{socket->
   socket.soTimeout=5000;val input=socket.getInputStream();fun line():String{val b=java.io.ByteArrayOutputStream();while(true){val c=input.read();check(c>=0);if(c==10)break;b.write(c)};return b.toString("US-ASCII").trimEnd('\r')};val path=line().split(' ')[1];val headers=mutableMapOf<String,String>();var header=line();while(header.isNotEmpty()){headers[header.substringBefore(':').lowercase()]=header.substringAfter(':').trim();header=line()}
   val body=ByteArray(headers["content-length"]?.toInt()?:0);var count=0;while(count<body.size){val n=input.read(body,count,body.size-count);if(n<0)break;count+=n}
   val reply=handler(path,headers,String(body,Charsets.UTF_8));val bytes=reply.body.toByteArray();socket.getOutputStream().write(("HTTP/1.1 ${reply.code} Test\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n${reply.headers}\r\n").toByteArray()+bytes)
  }}catch(e:java.io.IOException){if(!s.isClosed)throw e}}
  try{test("http://127.0.0.1:${s.localPort}")}finally{s.close();worker.join(3000)}
 }
 @Test fun loginCarriesSessionToStatusAndSearch(){
  var password="";var searchCookie=""
  server({path,h,body->when(path){
   "/api/auth"->{password=JSONObject(body).getString("password");Reply("""{"success":true}""",headers="Set-Cookie: ltv_session=signed-token; Path=/; HttpOnly; Max-Age=3600\r\n")}
   "/api/status"->Reply("""{"passwordRequired":true,"verified":${h["cookie"]=="ltv_session=signed-token"},"defaultSources":[]}""")
   else->{searchCookie=h["cookie"].orEmpty();Reply("""{"list":[],"failures":[]}""")}
  }}){base->val api=LibreTvApi(base);api.request("/api/auth",JSONObject().put("password"," 密码 with spaces "));assertEquals(" 密码 with spaces ",password);api.status();api.search("片名",listOf(Source("s","源","http://source.test")));assertEquals("ltv_session=signed-token",searchCookie)}
 }
 @Test fun loginRedirectDoesNotForwardPassword(){var leaked=false;server({_,_,_->leaked=true;Reply("{}")}){other->server({_,_,_->Reply("{}",302,"Location: $other/steal\r\n")}){base->try{LibreTvApi(base).request("/api/auth",JSONObject().put("password","secret"));fail()}catch(_:IllegalStateException){};assertFalse(leaked)}}}
 @Test fun failedLoginIsNotAccepted(){server({_,_,_->Reply("""{"error":"密码错误"}""",401)}){base->try{LibreTvApi(base).request("/api/auth",JSONObject().put("password","wrong"));fail()}catch(e:IllegalStateException){assertEquals("密码错误",e.message)}}}
 @Test fun loginWithoutSessionCannotReuseOldCredentials(){server({_,_,_->Reply("""{"success":true}""")}){base->SessionCookies.commit(base,SiteSession("old",System.currentTimeMillis()+60000,false));try{LibreTvApi(base).login("new");fail("Missing session accepted")}catch(e:IllegalStateException){assertTrue(e.message!!.contains("会话"))}finally{SessionCookies.commit(base,null)}}}
 @Test fun secureCookieOverHttpIsRejected(){server({_,_,_->Reply("""{"success":true}""",headers="Set-Cookie: ltv_session=secure-token; Path=/; Secure; HttpOnly; Max-Age=3600\r\n")}){base->try{LibreTvApi(base).login("correct");fail()}catch(e:IllegalStateException){assertTrue(e.message!!.contains("HTTPS"))}}}
 @Test fun rateLimitAndServerConfigErrorsRemainExplicit(){for(code in listOf(429,503)){server({_,_,_->Reply("""{"error":"blocked-$code"}""",code)}){base->try{LibreTvApi(base).login("password");fail()}catch(e:IllegalStateException){assertEquals("blocked-$code",e.message)}}}}

 @Test fun concurrentCandidateLoginsDoNotShareCredentials(){server({path,h,body->when(path){
  "/api/auth"->Reply("""{"success":true}""",headers="Set-Cookie: ltv_session=${JSONObject(body).getString("password")}; Path=/; HttpOnly; Max-Age=3600\r\n")
  "/api/status"->Reply("""{"passwordRequired":true,"verified":${h["cookie"] in listOf("ltv_session=alpha","ltv_session=beta")},"defaultSources":[]}""")
  else->if(h["cookie"]=="ltv_session=${JSONObject(body).getString("wd")}")Reply("""{"list":[],"failures":[]}""")else Reply("""{"error":"session mixed"}""",401)
 }}){base->val pool=java.util.concurrent.Executors.newFixedThreadPool(2);try{val tasks=listOf("alpha","beta").map{password->pool.submit(java.util.concurrent.Callable{val api=LibreTvApi(base);api.login(password);api.search(password,listOf(Source("qa","QA","http://source.test")));true})};tasks.forEach{assertTrue(it.get(10,java.util.concurrent.TimeUnit.SECONDS))}}finally{pool.shutdownNow()}}}

}
