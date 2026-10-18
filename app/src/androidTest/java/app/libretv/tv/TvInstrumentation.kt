package app.libretv.tv
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.media3.exoplayer.ExoPlayer
class TvInstrumentation:Instrumentation() {
 private var playback=false
 private var searchTerm="流浪地球"
 override fun onCreate(arguments:Bundle?) {super.onCreate(arguments);playback=arguments?.getString("playback")=="true";searchTerm=arguments?.getString("query")?:"流浪地球";start()}
 private fun all(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{all(v.getChildAt(it))}else emptyList()
 private fun waitFor(ms:Long=45000,condition:()->Boolean){val end=System.currentTimeMillis()+ms;while(System.currentTimeMillis()<end){var yes=false;runOnMainSync{yes=condition()};if(yes)return;Thread.sleep(200)};error("Timed out waiting for expected UI")}
 private fun shot(name:String){waitForIdleSync();Thread.sleep(350);val bitmap=uiAutomation.takeScreenshot()?:error("Screenshot unavailable");val f=java.io.File(targetContext.getExternalFilesDir(null),"qa-$name.png");f.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
 override fun onStart(){val out=Bundle();try{
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
  runOnMainSync{check((activity.currentFocus as? Button)?.text=="设置"){"Sidebar bottom wrapped"}}
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
}
