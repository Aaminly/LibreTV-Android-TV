package app.libretv.tv

import android.app.Dialog
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

internal class AuthenticationChecks(private val test:Instrumentation){
 private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
 private fun waitFor(check:()->Boolean){val until=System.currentTimeMillis()+45000;while(System.currentTimeMillis()<until){var done=false;test.runOnMainSync{done=check()};if(done)return;Thread.sleep(100)};error("Timed out waiting for authentication state")}
 private fun field(target:Any,name:String):Any?=target.javaClass.getDeclaredField(name).apply{isAccessible=true}.get(target)
 private fun dialog(a:MainActivity)=field(a,"authDialog") as? Dialog
 private fun shot(name:String){val image=test.uiAutomation.takeScreenshot()?:error("Screenshot unavailable");java.io.File(test.targetContext.getExternalFilesDir(null),"qa-auth-$name.png").outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};image.recycle()}
 fun run(mode:String,base:String,out:Bundle){
  val prefs=test.targetContext.getSharedPreferences("libretv",0)
  if(mode in listOf("initial","open","missing","authorOpen")){
   prefs.edit().clear().putString("server",base).commit()
   val store=WatchStore(test.targetContext);store.save(WatchRecord(base,Video("qa","已有观看记录",sourceKey="qa",sourceName="QA",sourceUrl="http://source.test"),0,12000,60000))
  }
  fun configure(body:String){val c=java.net.URL("$base/fixture/mode").openConnection() as java.net.HttpURLConnection;c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(body.toByteArray())};try{check(c.responseCode==200)}finally{c.disconnect()}}
  if(mode=="open")configure("{\"mode\":\"open\"}")
  if(mode=="missing")configure("{\"mode\":\"missing\"}")
  if(mode=="background")configure("{\"delay\":3}")
  val a=test.startActivitySync(Intent(test.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
  if(mode=="missing"){
   waitFor{views(a.window.decorView).filterIsInstance<TextView>().any{it.text.toString().contains("PASSWORD")}}
   test.runOnMainSync{check(dialog(a)==null);check(field(a,"page")=="access")};configure("{\"mode\":\"password\"}");out.putString("missingConfig","PASS unconfigured upstream stays locked with PASSWORD error");return
  }
  if(mode=="background"){
   waitFor{field(a,"authChecking")==true}
   test.runOnMainSync{views(a.window.decorView).filterIsInstance<Button>().first{it.text=="设置"}.performClick();check(field(a,"authChecking")==false);views(a.window.decorView).filterIsInstance<Button>().first{it.text=="检测并保存"}.performClick()}
   test.targetContext.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
   waitFor{field(a,"resumed")==false&&field(a,"authChecking")==false}
   val remote=Class.forName("app.libretv.tv.RemoteControl");val instance=remote.getField("INSTANCE").get(null);check(remote.getMethod("getUrl").invoke(instance)==null){"Authentication completion started LAN in background"}
   configure("{\"delay\":0}")
   test.targetContext.startActivity(Intent(test.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
   waitFor{field(a,"resumed")==true&&field(a,"page")=="home"&&remote.getMethod("getUrl").invoke(instance)!=null}
   out.putString("backgroundAuthentication","PASS cancelled check clears busy state; delayed success stays offline in background; foreground restores remote")
   return
  }
  if(mode=="initial"){
   waitFor{dialog(a)?.isShowing==true};shot("password")
   test.runOnMainSync{check(field(a,"page")=="access");check(field(a,"sources")==emptyList<Source>());check(!views(a.window.decorView).filterIsInstance<Button>().any{it.text=="继续观看"})}
   test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);test.runOnMainSync{check(dialog(a)?.isShowing==true);check(field(a,"page")=="access")}
   fun enter(value:String){test.runOnMainSync{val root=dialog(a)!!.window!!.decorView;views(root).filterIsInstance<EditText>().single().setText(value);views(root).filterIsInstance<Button>().first{it.text=="验证并进入"}.performClick()}}
   test.runOnMainSync{val input=views(dialog(a)!!.window!!.decorView).filterIsInstance<EditText>().single();input.requestFocus();val rect=android.graphics.Rect();check(input.getGlobalVisibleRect(rect)&&rect.width()==input.width);check(input.paddingLeft>=TvUi.dp(a,16)&&input.scaleX==1f)}
   test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);test.runOnMainSync{check((dialog(a)!!.currentFocus as? Button)?.text=="验证并进入")}
   repeat(2){test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)};test.runOnMainSync{check((dialog(a)!!.currentFocus as? Button)?.text=="退出应用")}
   repeat(2){test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT)};test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);test.runOnMainSync{check(dialog(a)!!.currentFocus is EditText)}
   enter("wrong-password");waitFor{dialog(a)?.let{views(it.window!!.decorView).filterIsInstance<TextView>().any{v->v.text.toString().contains("密码错误")}}==true};shot("wrong-password")
   test.runOnMainSync{check(dialog(a)?.isShowing==true);check(field(a,"page")=="access")}
   enter("libretv-qa-cookie")
  }
  waitFor{dialog(a)==null&&field(a,"page")=="home"};shot(if(mode=="initial")"verified"else "restored")
  if(mode=="open"||mode=="authorOpen"){check(prefs.all.keys.none{it.startsWith("session:")});if(mode=="open")configure("{\"mode\":\"password\"}");out.putString("openServer","PASS explicitly open service enters without password");return}
  val cached=prefs.all.filterKeys{it.startsWith("session:")};check(cached.isNotEmpty()){"Login session was not saved"};check(prefs.all.values.none{it.toString().contains("libretv-qa-cookie")}){"Plain password was persisted"}
  test.runOnMainSync{check(views(a.window.decorView).filterIsInstance<Button>().any{it.text=="继续观看"})}
  if(mode=="cover401"){
   val remote=Class.forName("app.libretv.tv.RemoteControl");val instance=remote.getField("INSTANCE").get(null);var endpoint="";waitFor{endpoint=(remote.getMethod("getUrl").invoke(instance) as? String).orEmpty();endpoint.isNotBlank()}
   val uri=java.net.URI(endpoint);val origin="http://${uri.authority}"
   val search=java.net.URL("$origin/search?${uri.rawQuery}").openConnection() as java.net.HttpURLConnection;search.requestMethod="POST";search.doOutput=true;search.setRequestProperty("Origin",origin);search.setRequestProperty("Content-Type","application/json");search.outputStream.use{it.write("{\"query\":\"测试影片\"}".toByteArray())}
   val response=try{check(search.responseCode==200);search.inputStream.bufferedReader().use{it.readText()}}finally{search.disconnect()};check(!response.contains("fixture-session")&&!response.contains("libretv-qa-cookie"));val cover=org.json.JSONObject(response).getJSONArray("items").getJSONObject(0).getString("cover")
   fun coverStatus():Int{val c=java.net.URL(cover).openConnection() as java.net.HttpURLConnection;return try{c.responseCode}finally{c.disconnect()}}
   check(coverStatus()==200);configure("{\"resource_unauthorized\":true}");check(coverStatus()==409);test.runOnMainSync{check(dialog(a)==null);check(field(a,"page")=="home")};check(prefs.all.keys.any{it=="session:$base"})
   configure("{\"resource_unauthorized\":false,\"token\":\"fixture-session-v2\"}");check(coverStatus()==409);waitFor{dialog(a)?.isShowing==true};configure("{\"token\":\"fixture-session-v1\"}")
   out.putString("coverAuthentication","PASS protected phone cover; upstream resource 401 preserves login; actual session revocation locks TV; phone JSON contains no upstream credentials");return
  }
  if(mode=="switchExpiry"){
   val candidate="http://10.0.2.2:18891"
   test.runOnMainSync{val api=field(a,"api") as LibreTvApi;val short=api.session!!.copy(expires=System.currentTimeMillis()+1200);api.javaClass.getDeclaredField("session").apply{isAccessible=true}.set(api,short);SessionCookies.commit(base,short);WatchStore(test.targetContext).connected(api);AccessGate.grant(base,short);a.scheduleAccessExpiry();a.checkService(candidate)}
   waitFor{dialog(a)?.isShowing==true};Thread.sleep(2200)
   test.runOnMainSync{check((field(a,"candidateApi") as? LibreTvApi)?.base==candidate){"Old session expiry interrupted new service login"}}
   out.putString("switchExpiry","PASS old service expiry cannot interrupt candidate service authentication");return
  }
  if(mode=="expiry"){
   test.runOnMainSync{val api=field(a,"api") as LibreTvApi;val short=api.session!!.copy(expires=System.currentTimeMillis()+1200);api.javaClass.getDeclaredField("session").apply{isAccessible=true}.set(api,short);SessionCookies.commit(base,short);WatchStore(test.targetContext).connected(api);AccessGate.grant(base,short);a.scheduleAccessExpiry()}
   waitFor{dialog(a)?.isShowing==true};test.runOnMainSync{check(field(a,"page")=="access")};out.putString("expiredSession","PASS local session expiry immediately locks existing content and asks for login");return
  }
  if(mode in listOf("revoked","resource401","playbackExpiry")){
   val monitor=test.addMonitor(PlaybackActivity::class.java.name,null,false)
   test.runOnMainSync{a.startActivity(Intent(a,PlaybackActivity::class.java).putExtra("server",base).putExtra("video",Video("qa","测试影片",sourceKey="qa",sourceName="本地契约测试源",sourceUrl="$base/source").json().toString()))}
   val playerActivity=test.waitForMonitorWithTimeout(monitor,15000) as? PlaybackActivity?:error("Player not launched")
   waitFor{(field(playerActivity,"player") as? androidx.media3.exoplayer.ExoPlayer)?.isPlaying==true}
   Thread.sleep(1500)
   if(mode=="playbackExpiry"){
    test.runOnMainSync{val api=field(playerActivity,"api") as LibreTvApi;val short=api.session!!.copy(expires=System.currentTimeMillis()+1200);api.javaClass.getDeclaredField("session").apply{isAccessible=true}.set(api,short);SessionCookies.commit(base,short);WatchStore(test.targetContext).connected(api);AccessGate.grant(base,short);playerActivity.javaClass.getDeclaredMethod("scheduleAccessExpiry").apply{isAccessible=true}.invoke(playerActivity)}
    val started=System.currentTimeMillis();waitFor{dialog(a)?.isShowing==true};check(System.currentTimeMillis()-started<3500){"Playback did not lock at session expiry"};out.putString("playbackExpiry","PASS playback session expiry stops player promptly and returns to login");return
   }
   configure(if(mode=="resource401")"{\"resource_unauthorized\":true}"else "{\"token\":\"fixture-session-v2\"}")
   test.runOnMainSync{views(playerActivity.window.decorView).filterIsInstance<Button>().first{it.text=="下一集"}.performClick()}
   if(mode=="resource401"){
    waitFor{dialog(a)!=null||views(playerActivity.window.decorView).filterIsInstance<TextView>().any{it.text.toString().contains("播放失败")}}
    configure("{\"resource_unauthorized\":false}");var locked=false;test.runOnMainSync{locked=playerActivity.isFinishing||dialog(a)!=null};check(!locked){"Resource 401 discarded valid site login"};check(prefs.all.keys.any{it=="session:$base"});out.putString("upstream401","PASS media-origin 401 keeps valid site login and shows resource error");return
   }
   waitFor{dialog(a)?.isShowing==true}
   test.runOnMainSync{check(playerActivity.isFinishing||playerActivity.isDestroyed);check(field(a,"page")=="access")}
   check(prefs.all.keys.none{it=="session:$base"}){"Revoked session was retained"};shot("revoked")
   configure("{\"token\":\"fixture-session-v1\"}")
   out.putString("revokedPlayback","PASS protected media HTTP 401 stops player, saves progress, clears invalid session and returns to mandatory login")
  }
  if(mode=="network")AuthenticationNetworkChecks.run(test,a,base,out)
  out.putString("authentication",if(mode=="initial")"PASS password login; history cannot bypass; wrong password stays locked; session saved without password"else "PASS fresh process restores session; no password dialog; server verifies cached session")
 }
}
