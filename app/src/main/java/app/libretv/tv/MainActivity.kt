package app.libretv.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import java.util.concurrent.Executors
import java.util.concurrent.Future

class MainActivity:Activity() {
 internal lateinit var store:WatchStore
 internal lateinit var api:LibreTvApi
 internal lateinit var shell:FrameLayout
 internal lateinit var content:FrameLayout
 internal lateinit var nav:LinearLayout
 internal val navButtons=mutableListOf<View>()
 internal val pool=Executors.newSingleThreadExecutor()
 internal val gate=RequestGate()
 internal var pending:Future<*>?=null
 internal var sources=listOf<Source>()
 internal var recommendations=listOf<Video>()
 internal var section=0
 internal var page="home"
 internal var query=""
 internal var results=listOf<Video>()
 internal var resultPosition=0
 internal var detailVideo:Video?=null
 internal var returnPage="home"
 internal var row:RecyclerView?=null
 internal var mainAction:View?=null
 internal var initialized=false
 internal val focusMemory=FocusMemory()
 internal var rowItems=listOf<Video>()
 internal var lastContent:View?=null
 internal var searchInput:EditText?=null
 internal var loadingHome=false
 internal var homeLoaded=false
 internal var phoneInput:PhoneInputDialog?=null
 @Volatile internal var candidateApi:LibreTvApi?=null
 internal fun d(n:Int)=TvUi.dp(this,n)
 override fun onCreate(state:Bundle?) {
  super.onCreate(state);window.decorView.systemUiVisibility=5894;store=WatchStore(this);api=LibreTvApi(store.server)
  query=state?.getString("query").orEmpty();buildShell();home()
 }
 override fun onSaveInstanceState(out:Bundle){out.putString("query",query);super.onSaveInstanceState(out)}
 override fun onResume(){super.onResume();if(initialized&&page=="home")home();if(initialized&&page=="detail"){(mainAction as? Button)?.text=if(store.history().any{it.video.key==detailVideo?.key})"继续观看"else "立即播放"};initialized=true}
 internal fun buildShell(){
  shell=FrameLayout(this).apply{setBackgroundColor(TvUi.bg);clipChildren=false};setContentView(shell)
  content=FrameLayout(this).apply{clipChildren=false};shell.addView(content,FrameLayout.LayoutParams(-1,-1).apply{leftMargin=d(148)})
  content.viewTreeObserver.addOnGlobalFocusChangeListener { _, focused ->
   var ancestor=focused
   while(ancestor!=null && ancestor!==content)ancestor=ancestor.parent as? View
   if(ancestor===content)lastContent=focused
  }
  nav=TvUi.vertical(this).apply{setPadding(d(24),d(32),d(16),d(24));setBackgroundColor(TvUi.bg);clipChildren=false;clipToPadding=false};shell.addView(nav,FrameLayout.LayoutParams(d(148),-1))
  val brand=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(0,0,0,d(20))}
  brand.addView(ImageView(this).apply{setImageResource(R.drawable.app_icon);contentDescription="LibreTV 兔子"},LinearLayout.LayoutParams(d(32),d(32)))
  brand.addView(TvUi.label(this,"LibreTV",18f,true).apply{setTextColor(TvUi.cyan)},LinearLayout.LayoutParams(-1,-2).apply{leftMargin=d(4)})
  nav.addView(brand,LinearLayout.LayoutParams(-1,d(64)))
  val names=listOf("首页","搜索","收藏","历史","设置","扫码输入");val icons=listOf(R.drawable.ic_home,R.drawable.ic_search,R.drawable.ic_favorite_border,R.drawable.ic_history,R.drawable.ic_settings,R.drawable.ic_qr_code)
  names.forEachIndexed{i,name->
   val b=TvUi.button(this,name){if(i==5){select(4);phoneInput=PhoneInputDialog(this,navButtons[5]);phoneInput?.show()}else select(i)}.apply{textSize=17f;gravity=Gravity.CENTER_VERTICAL;setPadding(d(10),0,d(6),0);setCompoundDrawablesWithIntrinsicBounds(icons[i],0,0,0);compoundDrawablePadding=d(10);if(i==5){textSize=15f;compoundDrawablePadding=d(4);val icon=getDrawable(icons[i])!!.apply{setBounds(0,0,d(20),d(20))};setCompoundDrawables(icon,null,null,null)}}
   val focusStyle=b.onFocusChangeListener;b.onFocusChangeListener=View.OnFocusChangeListener{v,has->focusStyle?.onFocusChange(v,has);if(!has)v.background=TvUi.shape(if(i==section)TvUi.surface else TvUi.bg)};b.background=TvUi.shape(if(i==section)TvUi.surface else TvUi.bg);nav.addView(b,LinearLayout.LayoutParams(-1,d(48)).apply{bottomMargin=d(4)});navButtons.add(b)
   b.setOnKeyListener{_,key,e->if(e.action!=KeyEvent.ACTION_DOWN)false else when(key){
    KeyEvent.KEYCODE_DPAD_UP->{navButtons[Core.move(i,-1,names.size)].requestFocus();true}
    KeyEvent.KEYCODE_DPAD_DOWN->{navButtons[Core.move(i,1,names.size)].requestFocus();true}
    KeyEvent.KEYCODE_DPAD_RIGHT->{if(lastContent?.isAttachedToWindow==true)lastContent?.requestFocus()else mainAction?.requestFocus();true}
    KeyEvent.KEYCODE_DPAD_LEFT->true
    else->false
   }}
  }
 }
 internal fun select(i:Int){section=i;when(i){0->home();1->searchScreen();2->library(true);3->library(false);4->settings()};navButtons[i].requestFocus()}
 internal fun reset(name:String){phoneInput?.dismiss();phoneInput=null;searchInput?.let{query=it.text.toString()};searchInput=null;gate.next();pending?.cancel(true);api.cancel();candidateApi?.cancel();loadingHome=false;lastContent=null;page=name;content.removeAllViews();row=null;rowItems=emptyList();mainAction=null;navButtons.forEachIndexed{i,v->(v as Button).setTextColor(if(i==section)TvUi.cyan else TvUi.text);v.compoundDrawableTintList=android.content.res.ColorStateList.valueOf(if(i==section)TvUi.cyan else TvUi.text);if(!v.hasFocus())v.background=TvUi.shape(if(i==section)TvUi.surface else TvUi.bg)}}
 internal fun connect(){if(loadingHome)return;loadingHome=true;async({val configured=api.status();configured to api.recommended()},{(configured,items)->sources=configured;recommendations=items;homeLoaded=true;loadingHome=false;home(false)},{error->loadingHome=false;showError(error){connect()}})}
 internal fun loadRecommendations(){if(page!="home")return;async({api.recommended()},{recommendations=it;home(false)},{e->showError(e){loadRecommendations()}})}
 internal fun <T> async(work:()->T,done:(T)->Unit,error:(String)->Unit={showError(it){select(section)}}){
  val id=gate.next();pending?.cancel(true);pending=pool.submit{try{val value=work();runOnUiThread{if(!isDestroyed&&gate.current(id))done(value)}}catch(e:Exception){if(!Thread.currentThread().isInterrupted)runOnUiThread{if(!isDestroyed&&gate.current(id))error(e.message?:"连接失败")}}}
 }
 internal fun frame(view:View,w:Int,h:Int,x:Int,y:Int){content.addView(view,FrameLayout.LayoutParams(if(w<0)w else d(w),if(h<0)h else d(h)).apply{leftMargin=d(x);topMargin=d(y)})}



 internal fun pageColumn(title:String):LinearLayout {
  val box=TvUi.vertical(this).apply{setPadding(d(32),d(32),d(32),d(24));clipChildren=false};frame(box,-1,-1,0,0);box.addView(TvUi.label(this,title,26f,true).apply{maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END},LinearLayout.LayoutParams(-1,d(60)));return box
 }


 class TextHolder(val button:Button):RecyclerView.ViewHolder(button)




 @Deprecated("Activity result") override fun onActivityResult(request:Int,result:Int,data:Intent?){super.onActivityResult(request,result,data);if(request==41&&result==RESULT_FIRST_USER){query=data?.getStringExtra("query")?:detailVideo?.title.orEmpty();searchScreen();runSearch()}}


 internal fun showError(message:String,retry:()->Unit){
  val panel=TvUi.vertical(this).apply{setPadding(d(24),d(18),d(24),d(18));background=TvUi.shape(TvUi.surface)}
  panel.addView(TvUi.label(this,message,18f).apply{maxLines=3});val b=TvUi.button(this,"重试"){content.removeView(panel);retry()};panel.addView(b,LinearLayout.LayoutParams(d(130),d(48)).apply{topMargin=d(12)})
  content.addView(panel,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM).apply{setMargins(d(28),0,d(32),d(32))});mainAction=b;b.requestFocus()
 }

 @Deprecated("TV legacy back handling") override fun onBackPressed(){when(page){"detail"->backFromDetail();"home"->if(currentFocus !in navButtons)navButtons[0].requestFocus()else super.onBackPressed();else->{section=0;home()}}}
 override fun onPause(){phoneInput?.dismiss();phoneInput=null;super.onPause()}
 override fun onDestroy(){gate.next();pending?.cancel(true);api.cancel();candidateApi?.cancel();pool.shutdownNow();super.onDestroy()}
}
