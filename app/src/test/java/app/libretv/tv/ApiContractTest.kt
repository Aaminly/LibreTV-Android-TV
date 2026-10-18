package app.libretv.tv
import java.net.ServerSocket
import kotlin.concurrent.thread
import org.junit.Test
import org.junit.Assert.*
class ApiContractTest {
 private fun serve(status:Int,body:String,test:(LibreTvApi)->Unit){
  val server=ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1"))
  val worker=thread { server.accept().use { socket ->
   val input=socket.getInputStream().bufferedReader();var line=input.readLine();while(!line.isNullOrEmpty())line=input.readLine()
   val bytes=body.toByteArray();val headers="HTTP/1.1 $status Test\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
   socket.getOutputStream().write(headers.toByteArray()+bytes)
  } }
  try{test(LibreTvApi("http://127.0.0.1:${server.localPort}"))}finally{worker.join(3000);server.close()}
 }

 @Test fun errorsAreNotSuccessfulEmptyResults(){serve(502,"""{"error":"源不可用"}"""){api->try{api.request("/api/test");fail()}catch(e:IllegalStateException){assertEquals("源不可用",e.message)}}}
 @Test fun malformedJsonIsExplicit(){serve(200,"<html>bad</html>"){api->try{api.request("/api/test");fail()}catch(e:IllegalStateException){assertTrue(e.message!!.contains("无效数据"))}}}
 @Test fun loginRequirementIsExplicit(){serve(200,"""{"passwordRequired":true,"verified":false}"""){api->try{api.status();fail()}catch(e:IllegalStateException){assertTrue(e.message!!.contains("登录"))}}}
 @Test fun partialSourceFailureRetained(){serve(200,"""{"list":[{"vodId":"1","name":"电影","sourceKey":"s"}],"failures":[{"sourceKey":"bad","error":"timeout"}]}"""){api->val (list,count)=api.search("电影",listOf(Source("s","源","https://source.test")));assertEquals(1,list.size);assertEquals(1,count)}}
}
