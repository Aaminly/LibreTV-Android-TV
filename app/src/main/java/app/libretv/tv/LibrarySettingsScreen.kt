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

import app.libretv.tv.MainActivity.TextHolder

internal fun MainActivity.library(favorites:Boolean){
 reset(if(favorites)"favorites"else "history");val box=pageColumn(if(favorites)"我的收藏"else "观看记录")
 val items=(if(favorites)store.favorites()else store.history()).map{it.video}
 val header=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
 header.addView(TvUi.label(this,if(items.isEmpty())"为下一次观影留个位置"else "${items.size} 部影片 · ${if(favorites)"喜欢的故事，随时重温"else "进度自动保存，随时接着看"}",16f).apply{setTextColor(TvUi.muted)},LinearLayout.LayoutParams(0,d(48),1f))
 val search=TvUi.button(this,"搜索影片"){searchScreen()};header.addView(search,LinearLayout.LayoutParams(d(144),d(48)));box.addView(header,LinearLayout.LayoutParams(-1,d(48)).apply{bottomMargin=d(20)});mainAction=search
 if(items.isEmpty()){
  val empty=TvUi.vertical(this).apply{gravity=Gravity.CENTER;setPadding(d(32),d(32),d(32),d(32));background=TvUi.shape(TvUi.surface)}
  empty.addView(TvUi.label(this,if(favorites)"收藏你的下一部好片"else "你的故事，从这里续上",24f,true).apply{gravity=Gravity.CENTER})
  empty.addView(TvUi.label(this,if(favorites)"在影片详情页选择「收藏」，即可在这里找到它"else "播放影片后，这里会保留集数与观看进度",17f).apply{setTextColor(TvUi.muted)},LinearLayout.LayoutParams(-2,-2).apply{topMargin=d(16)})
  box.addView(empty,LinearLayout.LayoutParams(-1,d(240)))
 }else box.addView(resultList(items),LinearLayout.LayoutParams(-1,0,1f))
 search.setOnKeyListener{_,key,e->if(e.action==0&&key==KeyEvent.KEYCODE_DPAD_DOWN&&items.isNotEmpty()){focusRow();true}else false};search.requestFocus()
}

internal fun MainActivity.settings(){
 reset("settings");val box=pageColumn("设置")
 box.addView(TvUi.label(this,"连接你的影片服务",23f,true),LinearLayout.LayoutParams(-1,d(32)))
 box.addView(TvUi.label(this,"使用电视能够访问的 LibreTV 地址，观看记录与收藏保存在本机。",16f).apply{setTextColor(TvUi.muted)},LinearLayout.LayoutParams(-1,d(34)))
 val panel=TvUi.vertical(this).apply{setPadding(d(24),d(16),d(24),d(16));background=TvUi.shape(TvUi.surface)}
 panel.addView(TvUi.label(this,"服务地址",16f).apply{setTextColor(TvUi.muted)},LinearLayout.LayoutParams(-1,d(30)))
 val input=EditText(this).apply{setText(store.server);setSingleLine();textSize=20f;setTextColor(TvUi.text);inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI;imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_DONE;contentDescription="影片服务地址"};TvUi.focus(input);panel.addView(input,LinearLayout.LayoutParams(-1,d(56)))
 val status=TvUi.label(this,"检测连接成功后才会保存新地址",16f).apply{setTextColor(TvUi.muted);maxLines=2}
 val save=TvUi.button(this,"检测并保存",true){val raw=input.text.toString();status.text="正在检测服务连接…";async({val candidate=LibreTvApi(raw);candidateApi=candidate;candidate to candidate.status()},{(candidate,list)->store.server=candidate.base;api=candidate;sources=list;results=emptyList();recommendations=emptyList();homeLoaded=false;status.text="连接成功 · ${list.size} 个视频源"},{status.text=it})}
 panel.addView(save,LinearLayout.LayoutParams(d(180),d(48)).apply{topMargin=d(12);bottomMargin=d(8)});panel.addView(status,LinearLayout.LayoutParams(-1,d(44)));box.addView(panel)
 val scan=TvUi.button(this,"扫码输入    ·    用手机输入片名，在电视上搜索"){}.apply{textSize=17f;gravity=Gravity.CENTER_VERTICAL;contentDescription="扫码输入"}
 scan.setOnClickListener{phoneInput=PhoneInputDialog(this,scan);phoneInput?.show()}
 box.addView(scan,LinearLayout.LayoutParams(-1,d(52)).apply{topMargin=d(16)})
 box.addView(TvUi.label(this,"LibreTV TV  1.2.0    ·    专为遥控器设计",13f).apply{setTextColor(TvUi.muted)},LinearLayout.LayoutParams(-1,d(28)).apply{topMargin=d(8)})
 input.id=View.generateViewId();save.id=View.generateViewId();scan.id=View.generateViewId()
 input.nextFocusDownId=save.id;save.nextFocusUpId=input.id;save.nextFocusDownId=scan.id;scan.nextFocusUpId=save.id;scan.nextFocusDownId=scan.id
 listOf(save,scan).forEach{button->button.setOnKeyListener{_,key,e->if(e.action==0&&key==KeyEvent.KEYCODE_DPAD_LEFT){navButtons[4].requestFocus();true}else false}}

 input.setOnKeyListener{_,key,e->if(e.action!=0)false else when{key==KeyEvent.KEYCODE_DPAD_DOWN->{save.requestFocus();true};key==KeyEvent.KEYCODE_DPAD_LEFT&&input.selectionStart==0->{navButtons[4].requestFocus();true};else->false}}
 mainAction=input;input.requestFocus()
}
