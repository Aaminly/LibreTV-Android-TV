package app.libretv.tv
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import org.json.JSONObject
import java.util.concurrent.Executors

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackActivity:Activity() {
 private lateinit var video:Video
 private lateinit var api:LibreTvApi
 private lateinit var store:WatchStore
 private lateinit var root:FrameLayout
 private lateinit var playerView:PlayerView
 private lateinit var controls:LinearLayout
 private lateinit var status:TextView
 private lateinit var progress:SeekBar
 private lateinit var clock:TextView
 private lateinit var pause:Button
 private var player:ExoPlayer?=null
 private var episodes=listOf<String>()
 private var episode=0
 private var resume=0L
 private var alive=true
 private var foreground=false
 private var generation=0
 private var ready=false
 private val pool=Executors.newSingleThreadExecutor()
 private val handler=Handler(Looper.getMainLooper())
 private val update=object:Runnable{override fun run(){val p=player;if(p!=null){val duration=p.duration.coerceAtLeast(0);if(!progress.isPressed)progress.progress=if(duration>0)(p.currentPosition*1000/duration).toInt()else 0;clock.text="${time(p.currentPosition)} / ${time(duration)}";pause.text=if(p.isPlaying)"暂停"else "播放"};handler.postDelayed(this,1000)}}
 private val saveTick=object:Runnable{override fun run(){save();handler.postDelayed(this,10000)}}
 private val hide=Runnable{if(player?.isPlaying==true)controls.visibility=View.GONE}
 private fun d(n:Int)=TvUi.dp(this,n)
 override fun onCreate(state:Bundle?){super.onCreate(state);window.decorView.systemUiVisibility=5894;window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  store=WatchStore(this);api=LibreTvApi(intent.getStringExtra("server")?:store.server);video=Video.from(JSONObject(intent.getStringExtra("video")!!));episode=state?.getInt("episode")?:intent.getIntExtra("episode",0);resume=state?.getLong("position")?:intent.getLongExtra("position",0)
  root=FrameLayout(this).apply{setBackgroundColor(Color.BLACK)};setContentView(root)
  playerView=PlayerView(this).apply{useController=false;isFocusable=false};root.addView(playerView,FrameLayout.LayoutParams(-1,-1))
  status=TvUi.label(this,"正在准备播放…",20f).apply{gravity=Gravity.CENTER;setBackgroundColor(0xBB09111C.toInt())};root.addView(status,FrameLayout.LayoutParams(-1,d(80),Gravity.CENTER))
  controls=TvUi.vertical(this).apply{setPadding(d(48),d(38),d(48),d(28));background=android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(0x0009111C,0xF509111C.toInt(),TvUi.bg))};root.addView(controls,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
  controls.addView(TvUi.label(this,video.title,26f,true));clock=TvUi.label(this,"",16f).apply{setTextColor(TvUi.muted);setPadding(0,d(10),0,d(6))};controls.addView(clock)
  progress=SeekBar(this).apply{max=1000;isFocusable=true;progressTintList=android.content.res.ColorStateList.valueOf(TvUi.cyan);thumbTintList=android.content.res.ColorStateList.valueOf(Color.WHITE)};controls.addView(progress,LinearLayout.LayoutParams(-1,d(36)))
  progress.setOnFocusChangeListener{_,has->progress.background=if(has)TvUi.shape(0x3322384A,Color.WHITE)else null}
  progress.setOnKeyListener{_,key,e->if(e.action==KeyEvent.ACTION_DOWN&&(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT)){seek(if(key==KeyEvent.KEYCODE_DPAD_RIGHT)10000 else -10000);true}else false}
  progress.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onProgressChanged(s:SeekBar?,v:Int,user:Boolean){if(user)player?.let{p->if(p.duration>0)p.seekTo(v.toLong()*p.duration/1000)}};override fun onStartTrackingTouch(s:SeekBar?){};override fun onStopTrackingTouch(s:SeekBar?){save()}})
  val buttons=LinearLayout(this);controls.addView(buttons)
  pause=TvUi.button(this,"播放",true){player?.let{if(it.isPlaying)it.pause()else it.play()};showControls()}
  val list=listOf(pause,TvUi.button(this,"选集"){chooseEpisode()},TvUi.button(this,"下一集"){if(episode<episodes.lastIndex){save();episode++;resume=0;prepare()}else Toast.makeText(this,"已经是最后一集",Toast.LENGTH_SHORT).show()},TvUi.button(this,"重试"){resume=player?.currentPosition?:resume;load()},TvUi.button(this,"换源"){save();setResult(RESULT_FIRST_USER,android.content.Intent().putExtra("query",video.title));finish()})
  list.forEachIndexed{i,b->buttons.addView(b,LinearLayout.LayoutParams(d(140),d(50)).apply{rightMargin=d(14)});b.setOnKeyListener{_,key,e->if(e.action!=0)false else when{key==KeyEvent.KEYCODE_DPAD_UP->{progress.requestFocus();true};key==KeyEvent.KEYCODE_DPAD_DOWN->true;key==KeyEvent.KEYCODE_DPAD_LEFT&&i==0->true;key==KeyEvent.KEYCODE_DPAD_RIGHT&&i==list.lastIndex->true;else->false}}}
  pause.requestFocus();handler.post(update);handler.post(saveTick);load()
 }
 private fun load(){val token=++generation;status.visibility=View.VISIBLE;status.text="正在获取播放地址…";pool.execute{try{val sources=api.status();val detail=api.detail(video,sources);runOnUiThread{if(alive&&token==generation){episodes=detail.episodes;episode=episode.coerceIn(0,episodes.lastIndex);prepare()}}}catch(e:Exception){runOnUiThread{if(alive&&token==generation)error(e.message?:"获取资源失败")}}}}
 private fun prepare(){
  player?.release();player=null;ready=false
  try{val raw=Core.episode(episodes[episode]).second;val http=DefaultHttpDataSource.Factory().setConnectTimeoutMs(10000).setReadTimeoutMs(20000).setUserAgent("LibreTV-TV/1.0")
   val p=ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(http)).build();player=p;playerView.player=p
   p.addListener(object:Player.Listener{
    override fun onPlaybackStateChanged(state:Int){if(state==Player.STATE_READY){ready=true;status.visibility=View.GONE;android.util.Log.i("LibreTVPlayback","READY episode=$episode duration=${p.duration}")}else if(state==Player.STATE_BUFFERING){status.text="正在缓冲…";status.visibility=View.VISIBLE}else if(state==Player.STATE_ENDED){save();if(episode<episodes.lastIndex){episode++;resume=0;prepare()}else showControls()}}
    override fun onPlayerError(e:PlaybackException){error("播放失败：${e.errorCodeName}。可重试或返回详情换源")}
    override fun onRenderedFirstFrame(){android.util.Log.i("LibreTVPlayback","FIRST_FRAME episode=$episode")}
   })
   val item=MediaItem.Builder().setUri(api.proxy(raw)).apply{if(raw.contains(".m3u8",true))setMimeType(MimeTypes.APPLICATION_M3U8)}.build()
   p.setMediaItem(item);p.seekTo(resume);p.prepare();p.playWhenReady=foreground;showControls()
  }catch(e:Exception){error(e.message?:"无法播放该资源")}
 }
 private fun error(s:String){status.text=s;status.visibility=View.VISIBLE;showControls();pause.requestFocus();android.util.Log.e("LibreTVPlayback",s)}
 private fun chooseEpisode(){
  if(episodes.isEmpty())return;handler.removeCallbacks(hide)
  val panel=TvUi.vertical(this).apply{setPadding(d(24),d(24),d(24),d(24));background=TvUi.shape(TvUi.bg)}
  panel.addView(TvUi.label(this,"选择剧集",24f,true),LinearLayout.LayoutParams(-1,d(46)))
  val grid=androidx.recyclerview.widget.RecyclerView(this).apply{layoutManager=androidx.recyclerview.widget.GridLayoutManager(this@PlaybackActivity,4);itemAnimator=null}
  val dialog=android.app.Dialog(this);dialog.setContentView(panel)
  grid.adapter=object:androidx.recyclerview.widget.RecyclerView.Adapter<MainActivity.TextHolder>(){
   override fun getItemCount()=episodes.size
   override fun onCreateViewHolder(p:ViewGroup,t:Int)=MainActivity.TextHolder(TvUi.button(this@PlaybackActivity,""){}).apply{button.layoutParams=androidx.recyclerview.widget.RecyclerView.LayoutParams(-1,d(48)).apply{setMargins(d(4),d(4),d(4),d(4))}}
   override fun onBindViewHolder(h:MainActivity.TextHolder,i:Int){h.button.text=episodes[i].substringBefore('$').takeIf{!it.startsWith("http")}?:"第 ${i+1} 集";h.button.maxLines=1;h.button.ellipsize=android.text.TextUtils.TruncateAt.END;TvUi.focus(h.button,i==episode);h.button.setTextColor(if(i==episode)Color.BLACK else TvUi.text);h.button.setOnKeyListener{_,k,e->val pos=h.bindingAdapterPosition;if(e.action==0&&k==KeyEvent.KEYCODE_DPAD_DOWN&&pos>=0){TvUi.focusItem(grid,Core.gridDown(pos,episodes.size,4));true}else e.action==0&&((k==KeyEvent.KEYCODE_DPAD_LEFT&&pos%4==0)||(k==KeyEvent.KEYCODE_DPAD_RIGHT&&(pos%4==3||pos==episodes.lastIndex))||(k==KeyEvent.KEYCODE_DPAD_UP&&pos<4))};h.button.setOnClickListener{val index=h.bindingAdapterPosition;if(index!=androidx.recyclerview.widget.RecyclerView.NO_POSITION){save();episode=index;resume=0;dialog.dismiss();prepare()}}}
  }
  panel.addView(grid,LinearLayout.LayoutParams(-1,d(280)));panel.addView(TvUi.label(this,"方向键选择 · 确定播放 · 返回关闭",14f).apply{setTextColor(TvUi.muted);setPadding(d(4),d(12),0,0)})
  dialog.setOnDismissListener{pause.requestFocus();showControls()};dialog.show();dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));dialog.window?.setLayout(d(720),-2);grid.scrollToPosition(episode);grid.post{grid.findViewHolderForAdapterPosition(episode)?.itemView?.requestFocus()}
 }
 private fun showControls(){controls.visibility=View.VISIBLE;handler.removeCallbacks(hide);handler.postDelayed(hide,7000)}
 private fun seek(delta:Long){player?.let{p->if(p.isCurrentMediaItemSeekable){p.seekTo(Core.position(p.currentPosition+delta,p.duration));save()}else Toast.makeText(this,"当前媒体不支持调整进度",Toast.LENGTH_SHORT).show()};showControls()}
 override fun dispatchKeyEvent(e:KeyEvent):Boolean{
  if(e.action==KeyEvent.ACTION_DOWN){when(e.keyCode){
   KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,KeyEvent.KEYCODE_MEDIA_PLAY,KeyEvent.KEYCODE_MEDIA_PAUSE->{player?.let{if(e.keyCode==KeyEvent.KEYCODE_MEDIA_PAUSE||e.keyCode==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE&&it.isPlaying)it.pause()else it.play()};showControls();return true}
   KeyEvent.KEYCODE_MEDIA_FAST_FORWARD->{seek(30000);return true};KeyEvent.KEYCODE_MEDIA_REWIND->{seek(-30000);return true}
  }
  if(controls.visibility!=View.VISIBLE&&e.keyCode in listOf(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN)){
   showControls();if(e.keyCode==KeyEvent.KEYCODE_DPAD_LEFT||e.keyCode==KeyEvent.KEYCODE_DPAD_RIGHT){progress.requestFocus();seek(if(e.keyCode==KeyEvent.KEYCODE_DPAD_RIGHT)10000 else -10000)}else pause.requestFocus();return true
  }
  if(e.keyCode in listOf(19,20,21,22,23))showControls()
  };return super.dispatchKeyEvent(e)
 }
 private fun save(){val p=player?:return;if(ready){resume=p.currentPosition;store.save(WatchRecord(api.base,video,episode,resume,p.duration.coerceAtLeast(0)))}}
 private fun time(ms:Long):String {val s=ms.coerceAtLeast(0)/1000;return "%02d:%02d".format(s/60,s%60)}
 override fun onResume(){super.onResume();foreground=true}
 override fun onPause(){foreground=false;save();player?.pause();super.onPause()}
 override fun onSaveInstanceState(out:Bundle){save();out.putInt("episode",episode);out.putLong("position",resume);super.onSaveInstanceState(out)}
 @Deprecated("TV back") override fun onBackPressed(){if(controls.visibility==View.VISIBLE){controls.visibility=View.GONE}else{save();super.onBackPressed()}}
 override fun onDestroy(){alive=false;generation++;handler.removeCallbacksAndMessages(null);api.cancel();pool.shutdownNow();playerView.player=null;player?.release();super.onDestroy()}
}
