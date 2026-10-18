package app.libretv.tv
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.media3.exoplayer.ExoPlayer
class TvInstrumentation:Instrumentation() {
 private var remote=false
 private var playback=false
 private var searchTerm="流浪地球"
 override fun onCreate(arguments:Bundle?) {super.onCreate(arguments);remote=arguments?.getString("remote")=="true";playback=arguments?.getString("playback")=="true";searchTerm=arguments?.getString("query")?:"流浪地球";start()}
 private fun all(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{all(v.getChildAt(it))}else emptyList()
 private fun waitFor(ms:Long=45000,condition:()->Boolean){val end=System.currentTimeMillis()+ms;while(System.currentTimeMillis()<end){var yes=false;runOnMainSync{yes=condition()};if(yes)return;Thread.sleep(200)};error("Timed out waiting for expected UI")}
 private fun shot(name:String){Thread.sleep(350);val bitmap=uiAutomation.takeScreenshot()?:error("Screenshot unavailable");val f=java.io.File(targetContext.getExternalFilesDir(null),"qa-$name.png");f.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
 override fun onStart(){val out=Bundle();try{
  if(remote){remoteTest(out);finish(0,out);return}
  val source=android.graphics.Bitmap.createBitmap(200,100,android.graphics.Bitmap.Config.ARGB_8888);val canvas=android.graphics.Canvas(source);canvas.drawColor(android.graphics.Color.GREEN);val paint=android.graphics.Paint();paint.color=android.graphics.Color.RED;canvas.drawRect(0f,0f,50f,100f,paint);paint.color=android.graphics.Color.BLUE;canvas.drawRect(150f,0f,200f,100f,paint)
  val target=android.graphics.Bitmap.createBitmap(60,90,android.graphics.Bitmap.Config.ARGB_8888);(Images::class.java.declaredMethods.first{it.name.startsWith("cover$")}.invoke(Images,source) as android.graphics.drawable.Drawable).apply{setBounds(0,0,60,90);draw(android.graphics.Canvas(target))};check(target.getPixel(1,45)==android.graphics.Color.GREEN&&target.getPixel(58,45)==android.graphics.Color.GREEN){"Cover must crop proportionally rather than stretch side stripes"};source.recycle();target.recycle();out.putString("coverCrop","PASS landscape image center crop preserves proportions")
  val isolated=object:android.content.ContextWrapper(targetContext){override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences("qa-"+name,mode)}
  val prefs=isolated.getSharedPreferences("libretv",0);prefs.edit().clear().commit()
  try {val store=WatchStore(isolated);val v=Video("qa","测试","","source","测试源","https://source.test");store.toggle(v);store.save(WatchRecord(store.server,v,2,42000,90000));val restored=WatchStore(isolated);check(restored.favorite(v));check(restored.history().first().position==42000L);restored.server="http://127.0.0.1:1234";check(restored.history().isEmpty());restored.server="http://192.168.31.1:8899";check(restored.history().first().episode==2)}finally{prefs.edit().clear().commit()}
  out.putString("persistence","PASS history/favorites reopen and server isolation")
  val activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
  waitFor{activity.currentFocus!=null};Thread.sleep(7000);shot("home")
  sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);waitForIdleSync()
  runOnMainSync{check((activity.currentFocus as? Button)?.text=="搜索"){"Down from 首页 must reach 搜索"}}
  sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);runOnMainSync{check((activity.currentFocus as? Button)?.text=="首页")}
  sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);runOnMainSync{check(activity.currentFocus!=null)}
  sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT)
  repeat(8){sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)}
  runOnMainSync{check(activity.currentFocus?.contentDescription=="手机选片二维码"){"Sidebar bottom must reach QR"}}
  repeat(8){sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)}
  runOnMainSync{check((activity.currentFocus as? Button)?.text=="首页"){"Sidebar top wrapped"}}
  sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
  sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT)
  repeat(4){sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)}
  sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
  waitFor{activity.currentFocus is EditText};Thread.sleep(300)
  runOnMainSync{
   val field=activity.currentFocus as EditText
   check(field.scaleX==1f && field.scaleY==1f){"Focused input expands outside its layout and clips its left edge"}
   check(field.paddingLeft>=TvUi.dp(activity,16)){"Input text lacks safe inset"}
   val visible=android.graphics.Rect();check(field.getGlobalVisibleRect(visible)&&visible.width()==field.width){"Input is clipped"}
  }
  shot("settings")
  out.putString("inputBounds","PASS focused field stays within layout with safe text inset")
  sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
  sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);repeat(2){sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)};sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);shot("favorites");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
  sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);repeat(3){sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)};sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);shot("history");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
  if(playback){
   sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
   waitFor{all(activity.window.decorView).any{it is EditText}}
   sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);shot("search");runOnMainSync{val field=activity.currentFocus as EditText;check(field.scaleX==1f);val rect=android.graphics.Rect();check(field.getGlobalVisibleRect(rect)&&rect.width()==field.width)}
   runOnMainSync{val input=all(activity.window.decorView).filterIsInstance<EditText>().first();input.setText(searchTerm);all(activity.window.decorView).filterIsInstance<Button>().first{it.contentDescription=="执行影片搜索"}.performClick()}
   waitFor{all(activity.window.decorView).filterIsInstance<Button>().any{it.text.contains(searchTerm)&&it.text.contains("资源")}}
   shot("results")
   repeat(6){var before:View?=null;runOnMainSync{before=activity.currentFocus};sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);waitFor(3000){activity.currentFocus!==before}}
   repeat(6){sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);waitForIdleSync()};out.putString("gridScroll","PASS six rows down across viewport and back up")
   var original=""
   runOnMainSync{val match=all(activity.window.decorView).filterIsInstance<Button>().filter{it.text.contains(searchTerm)&&it.text.contains("资源")};val b=match.getOrElse(1){match.first()};original=b.text.toString();b.requestFocus()}
   sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
   waitFor{all(activity.window.decorView).filterIsInstance<Button>().any{it.text=="立即播放"||it.text=="继续观看"}}
   shot("detail");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
   waitFor{(activity.currentFocus as? Button)?.text?.toString()==original}
   out.putString("restore","PASS detail back restores exact source result")
   runOnMainSync{all(activity.window.decorView).filterIsInstance<Button>().first{it.text.contains(searchTerm)&&it.text.contains("资源")}.requestFocus()}
   sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
   waitFor{all(activity.window.decorView).filterIsInstance<Button>().any{it.text=="立即播放"||it.text=="继续观看"}}
   val monitor=addMonitor(PlaybackActivity::class.java.name,null,false)
   runOnMainSync{all(activity.window.decorView).filterIsInstance<Button>().first{it.text=="立即播放"||it.text=="继续观看"}.requestFocus()}
   sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
   val screen=waitForMonitorWithTimeout(monitor,15000) as? PlaybackActivity?:error("Player did not open")
   fun player():ExoPlayer? {val f=PlaybackActivity::class.java.getDeclaredField("player");f.isAccessible=true;return f.get(screen) as? ExoPlayer}
   waitFor(60000){player()?.isPlaying==true&&player()!!.currentPosition>3000}
   sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);shot("player")
   runOnMainSync{all(screen.window.decorView).filterIsInstance<Button>().first{it.text=="选集"}.performClick()};shot("episodes");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
   if(searchTerm=="狂飙"){
    runOnMainSync{all(screen.window.decorView).filterIsInstance<Button>().first{it.text=="下一集"}.performClick()}
    waitFor(60000){val f=PlaybackActivity::class.java.getDeclaredField("episode");f.isAccessible=true;f.getInt(screen)>=1&&player()?.isPlaying==true&&player()!!.currentPosition>1000}
    out.putString("nextEpisode","PASS real series next episode playback")
   }
   var before=0L;runOnMainSync{before=player()!!.currentPosition}
   Thread.sleep(5000);runOnMainSync{check(player()!!.currentPosition>before+2500){"Playback position did not advance"}}
   sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PAUSE);runOnMainSync{check(!player()!!.playWhenReady)}
   sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);Thread.sleep(2500)
   runOnMainSync{check(player()!!.currentPosition>before+20000){"Seek failed"}}
   sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PLAY);Thread.sleep(4000)
   sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
   waitFor{activity.hasWindowFocus()}
   var saved=0L;runOnMainSync{saved=WatchStore(activity).history().first().position;check(saved>25000){"Progress not saved"}}
   val again=addMonitor(PlaybackActivity::class.java.name,null,false)
   runOnMainSync{all(activity.window.decorView).filterIsInstance<Button>().first{it.text=="继续观看"}.performClick()}
   val second=waitForMonitorWithTimeout(again,15000) as? PlaybackActivity?:error("Resume player did not open")
   runOnMainSync{check(second.intent.getLongExtra("position",0)>=saved){"Resume used stale position"};second.finish()}
   out.putString("playback","PASS live search/detail/play, advancing position, pause, +30s seek, save, resume intent")
  }
  out.putString("focus","PASS exact sidebar direction targets and non-null content focus");finish(0,out)
 }catch(e:Throwable){out.putString("error",e.stackTraceToString());finish(1,out)}}
 private fun remoteTest(out:Bundle){
  val activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
  val bridge=Class.forName("app.libretv.tv.RemoteControl");val instance=bridge.getField("INSTANCE").get(null)
  fun url()=bridge.getMethod("getUrl").invoke(instance) as? String
  waitFor{url()!=null};val endpoint=url()!!;java.io.File(targetContext.getExternalFilesDir(null),"remote-url.txt").writeText(endpoint)
  fun api(path:String,body:org.json.JSONObject?=null):org.json.JSONObject{
   val uri=java.net.URI(endpoint);val c=java.net.URL("http://${uri.authority}$path?${uri.rawQuery}").openConnection() as java.net.HttpURLConnection
   c.connectTimeout=5000;c.readTimeout=60000
   if(body!=null){c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Origin","http://${uri.authority}");c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(body.toString().toByteArray())}}
   try{val code=c.responseCode;val text=(if(code==200)c.inputStream else c.errorStream).bufferedReader().use{it.readText()};check(code==200){"HTTP $code: $text"};return org.json.JSONObject(text)}finally{c.disconnect()}
  }
  runOnMainSync{all(activity.window.decorView).first{it.contentDescription=="手机选片二维码"}.requestFocus()};shot("remote-sidebar")
  check(!api("/state").getBoolean("active"))
  val results=api("/search",org.json.JSONObject().put("query",searchTerm)).getJSONArray("items");check(results.length()>0)
  runOnMainSync{check(all(activity.window.decorView).filterIsInstance<EditText>().none{it.text.toString()==searchTerm}){"Phone search changed TV page"}}
  val item=results.getJSONObject(0);val detail=api("/detail",org.json.JSONObject().put("id",item.getString("id")));check(detail.getJSONArray("episodes").length()>0)
  val monitor=addMonitor(PlaybackActivity::class.java.name,null,false)
  api("/play",org.json.JSONObject().put("id",item.getString("id")).put("episode",0))
  val playback=waitForMonitorWithTimeout(monitor,15000) as? PlaybackActivity?:error("Remote play did not open player")
  fun snapshot():org.json.JSONObject=api("/state")
  fun awaitState(condition:(org.json.JSONObject)->Boolean):org.json.JSONObject{val end=System.currentTimeMillis()+90000;while(System.currentTimeMillis()<end){val value=snapshot();check(value.optString("error").isBlank()){value.optString("error")};if(condition(value))return value;Thread.sleep(500)};error("Remote state timed out")}
  android.util.Log.i("RemoteQA","await initial playback");var state=awaitState{it.optBoolean("playing")&&it.optLong("duration")>60000&&it.optBoolean("seekable")};val media=state.getString("mediaId")
  fun command(action:String,position:Long?=null)=api("/control",org.json.JSONObject().put("mediaId",media).put("action",action).apply{if(position!=null)put("position",position)})
  android.util.Log.i("RemoteQA","pause");command("pause");state=awaitState{it.optBoolean("paused")};val paused=state.getLong("position");Thread.sleep(1000);check(kotlin.math.abs(snapshot().getLong("position")-paused)<1000)
  android.util.Log.i("RemoteQA","seek");command("seek",30000);awaitState{kotlin.math.abs(it.optLong("position")-30000)<1500}
  android.util.Log.i("RemoteQA","resume");command("play");awaitState{it.optBoolean("playing")&&it.optLong("position")>30500}
  android.util.Log.i("RemoteQA","TV pause");sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PAUSE);awaitState{it.optBoolean("paused")}
  android.util.Log.i("RemoteQA","TV resume");sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PLAY);awaitState{it.optBoolean("playing")}
  android.util.Log.i("RemoteQA","capture playback");shot("remote-playback");out.putString("remotePlayback","PASS real search/detail/play; pause, seek 30s, resume; TV key state synchronized")
  // A second selection keeps the same session and rejects a command from the old media.
  runOnMainSync{
   all(playback.window.decorView).filterIsInstance<Button>().first{it.text=="选集"}.performClick()
   val df=PlaybackActivity::class.java.getDeclaredField("episodeDialog");df.isAccessible=true;val picker=df.get(playback) as android.app.Dialog;check(picker.isShowing)
   val vf=PlaybackActivity::class.java.getDeclaredField("video");vf.isAccessible=true;val v=vf.get(playback) as Video
   val method=PlaybackActivity::class.java.declaredMethods.first{it.name.startsWith("remoteOpen")}
   method.invoke(playback,v,"http://127.0.0.1:9",0);check(!picker.isShowing){"Old episode picker survived film switch"}
   val ef=PlaybackActivity::class.java.getDeclaredField("episodes");ef.isAccessible=true;check((ef.get(playback) as List<*>).isEmpty()){"Switching films retained old episodes"}
  }
  api("/play",org.json.JSONObject().put("id",item.getString("id")).put("episode",0));awaitState{it.optBoolean("playing")&&it.optString("mediaId")!=media}
  var staleRejected=false;try{command("pause")}catch(e:IllegalStateException){staleRejected=e.message?.contains("409")==true};check(staleRejected)
  runOnMainSync{playback.finish()};waitFor{activity.hasWindowFocus()};check(!snapshot().getBoolean("active"));check(url()==endpoint)
  runOnMainSync{all(activity.window.decorView).first{it.contentDescription=="手机选片二维码"}.performClick()}
  targetContext.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(1800)
  var closed=false;try{snapshot()}catch(_:java.io.IOException){closed=true};check(closed)
  targetContext.startActivity(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));waitFor{url()!=null&&url()!=endpoint&&activity.hasWindowFocus()}
  java.io.File(targetContext.getExternalFilesDir(null),"remote-url.txt").writeText(url()!!)
  out.putString("remoteLifecycle","PASS session across activities; stale media rejected; background closes; foreground rotates token")
 }
}
