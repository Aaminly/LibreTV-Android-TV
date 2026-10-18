package app.libretv.tv
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.net.*
class LanInputServerTest {
 private fun request(s:LanInputServer,path:String="/",body:String?=null,token:String=s.url.substringAfter("token="),origin:String?=s.origin):Pair<Int,String>{
  val u=URL(s.origin);return Socket(u.host,u.port).use{socket->socket.soTimeout=3000
   val bytes=body?.toByteArray(Charsets.UTF_8)
   val h="${if(body==null)"GET" else "POST"} $path?token=$token HTTP/1.1\r\nHost: ${u.host}:${u.port}\r\n"+(if(bytes!=null)"Content-Type: application/json\r\nContent-Length: ${bytes.size}\r\n"else "")+(origin?.let{"Origin: $it\r\n"}?:"")+"\r\n"
   socket.getOutputStream().apply{write(h.toByteArray());if(bytes!=null)write(bytes);flush()}
   val input=socket.getInputStream();val header=StringBuilder()
   while(!header.endsWith("\r\n\r\n")){val b=input.read();require(b>=0);header.append(b.toChar())}
   val length=Regex("Content-Length: (\\d+)").find(header)!!.groupValues[1].toInt();val data=ByteArray(length);var offset=0
   while(offset<length){val n=input.read(data,offset,length-offset);require(n>0);offset+=n}
   header.toString().substringBefore("\r\n").split(' ')[1].toInt() to String(data,Charsets.UTF_8)}
 }
 private fun server(action:(String,JSONObject)->JSONObject={_,_->JSONObject().put("ok",true)})=LanInputServer(InetAddress.getByName("127.0.0.1"),"<input>",byteArrayOf(1),action)
 @Test fun repeatedChineseSearchAndStateRemainConnected(){val queries=mutableListOf<String>();server{path,data->if(path=="/search")queries.add(data.getString("query"));JSONObject().put("playing",false)}.use{s->
  assertEquals(200,request(s).first);repeat(2){assertEquals(200,request(s,"/search","{\"query\":\"  疯狂动物城  \"}").first)}
  assertEquals(listOf("疯狂动物城","疯狂动物城"),queries);assertEquals(200,request(s,"/state").first)}}
 @Test fun tokenOriginAndQueryCheckedBeforeCallback(){var calls=0;server{_,_->calls++;JSONObject()}.use{s->
  assertEquals(403,request(s,token="wrong").first)
  assertEquals(403,request(s,"/search","{}",origin="http://evil.invalid").first)
  assertEquals(403,request(s,"/search","{}",origin=null).first)
  for(q in listOf("  ","字".repeat(101),"a\nb"))assertEquals(400,request(s,"/search",JSONObject().put("query",q).toString()).first)
  assertEquals(0,calls)}}
 @Test fun invalidControlCannotReachPlayer(){var calls=0;server{_,_->calls++;JSONObject()}.use{s->
  for(body in listOf("{}","{\"action\":\"toggle\",\"mediaId\":\"a\"}","{\"action\":\"seek\",\"position\":-1,\"mediaId\":\"a\"}","{\"action\":\"seek\",\"position\":1.5,\"mediaId\":\"a\"}"))assertEquals(400,request(s,"/control",body).first)
  assertEquals(0,calls);assertEquals(200,request(s,"/control","{\"action\":\"pause\",\"mediaId\":\"a\"}").first)}}
 @Test fun rejectedCommandIsNotReportedAsSuccess(){server{_,_->error("电视已切换影片")}.use{s->val r=request(s,"/control","{\"action\":\"play\",\"mediaId\":\"old\"}");assertEquals(409,r.first);assertTrue(r.second.contains("电视已切换影片"))}}
 @Test fun closingSessionStopsConnections(){val s=server();s.close();try{request(s);fail("listener still open")}catch(_:java.io.IOException){}}
 @Test fun unknownRoutesAndInvalidSelectionAreRejected(){server().use{s->assertEquals(404,request(s,"/unknown").first);assertEquals(400,request(s,"/play","{\"id\":\"x\",\"episode\":-1}").first)}}
}
