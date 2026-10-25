package app.libretv.tv

import android.app.Instrumentation
import android.os.Bundle
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DataSpec

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object AuthenticationNetworkChecks {
 fun run(test:Instrumentation,activity:MainActivity,base:String,out:Bundle){
  val connection=URL("$base/api/redirect?via=connection").openConnection() as HttpURLConnection
  try{check(connection.responseCode==200);check(connection.inputStream.use{it.readBytes()}.isNotEmpty())}finally{connection.disconnect()}
  test.runOnMainSync{val view=ImageView(activity);Images.load(view,"$base/api/redirect?via=image")}
  val source=DefaultHttpDataSource.Factory().createDataSource()
  try{source.open(DataSpec(android.net.Uri.parse("$base/api/redirect?via=media")));check(source.read(ByteArray(128),0,128)>0)}finally{source.close()}
  var records=JSONArray();val end=System.currentTimeMillis()+12000
  while(System.currentTimeMillis()<end){records=JSONArray(URL("$base/fixture/log").readText());if((0 until records.length()).map{records.getJSONObject(it)}.count{it.optInt("port")==18893&&it.optString("path")=="/api/destination"}>=3)break;Thread.sleep(100)}
  val requests=(0 until records.length()).map{records.getJSONObject(it)}
  val first=requests.filter{it.optInt("port")==18892&&it.optString("path")=="/api/redirect"};val redirected=requests.filter{it.optInt("port")==18893&&it.optString("path")=="/api/destination"}
  check(first.size>=3&&first.all{it.getBoolean("cookie_valid")}){"Same-origin requests did not carry session"}
  check(redirected.size>=3&&redirected.none{it.getBoolean("has_cookie")}){"Session leaked across a redirected port"}
  out.putString("redirectIsolation","PASS Android HttpURLConnection, Images and Media3 transport send cookie to service, omit cookie after cross-port redirect")
 }
}
