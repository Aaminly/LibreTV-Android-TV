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

internal fun MainActivity.openDetail(v:Video){returnPage=page;detailVideo=v;renderDetail(v)}

internal fun MainActivity.renderDetail(v:Video){reset("detail");val box=pageColumn(v.title);val status=TvUi.label(this,"正在获取剧集…",18f);box.addView(status);val back=TvUi.button(this,"返回"){backFromDetail()};box.addView(back);mainAction=back;back.requestFocus()
  async({val current=sources.ifEmpty{api.status()};sources=current;api.detail(v,current)},{detail->
   val navFocus=navButtons.indexOf(currentFocus);box.removeView(status);box.removeView(back)
   val summary=LinearLayout(this)
   val cover=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;clipToOutline=true;background=TvUi.shape(TvUi.surface)};Images.load(cover,api.proxy(v.cover));summary.addView(cover,LinearLayout.LayoutParams(d(88),d(124)))
   val info=TvUi.vertical(this);info.addView(TvUi.label(this,"${v.sourceName}  ·  ${detail.episodes.size} 集  ·  ${v.remarks}",16f).apply{setTextColor(TvUi.cyan)},LinearLayout.LayoutParams(-1,d(32)))
   info.addView(TvUi.label(this,android.text.Html.fromHtml(detail.description).toString().ifBlank{"该来源暂未提供影片简介"},17f).apply{maxLines=3;ellipsize=android.text.TextUtils.TruncateAt.END;setTextColor(TvUi.muted);setLineSpacing(d(3).toFloat(),1f)})
   summary.addView(info,LinearLayout.LayoutParams(0,d(124),1f).apply{leftMargin=d(22)});box.addView(summary,LinearLayout.LayoutParams(-1,d(124)).apply{bottomMargin=d(20)})
   val actions=LinearLayout(this);val history=store.history().find{it.video.key==v.key};val play=TvUi.button(this,if(history!=null)"继续观看"else "立即播放",true){val latest=store.history().find{it.video.key==v.key};play(v,latest?.episode?.coerceIn(0,detail.episodes.lastIndex)?:0,latest?.position?:0)}
   actions.addView(play,LinearLayout.LayoutParams(d(170),d(50)));mainAction=play
   val favorite=TvUi.button(this,if(store.favorite(v))"取消收藏"else "收藏"){};favorite.setOnClickListener{favorite.text=if(store.toggle(v))"取消收藏"else "收藏"};actions.addView(favorite,LinearLayout.LayoutParams(d(130),d(50)).apply{leftMargin=d(16)})
   actions.addView(TvUi.button(this,"更换来源"){query=v.title;searchScreen();runSearch()},LinearLayout.LayoutParams(d(140),d(50)).apply{leftMargin=d(16)});box.addView(actions)
   box.addView(TvUi.label(this,"选择剧集 · 共 ${detail.episodes.size} 集",20f,true),LinearLayout.LayoutParams(-1,d(48)))
   val episodes=RecyclerView(this).apply{layoutManager=androidx.recyclerview.widget.GridLayoutManager(this@renderDetail,4);clipToPadding=false;setPadding(d(4),d(4),d(4),d(4));itemAnimator=null}
   episodes.adapter=object:RecyclerView.Adapter<TextHolder>(){
    override fun getItemCount()=detail.episodes.size
    override fun onCreateViewHolder(parent:ViewGroup,type:Int)=TextHolder(TvUi.button(this@renderDetail,""){}).apply{button.layoutParams=RecyclerView.LayoutParams(-1,d(49)).apply{setMargins(d(4),d(4),d(4),d(4))}}
    override fun onBindViewHolder(h:TextHolder,p:Int){h.button.text=Core.episodeLabel(detail.episodes[p],p);h.button.maxLines=1;h.button.setOnClickListener{play(v,p,0)};h.button.setOnKeyListener{_,k,e->if(e.action!=0)false else when{ k==KeyEvent.KEYCODE_DPAD_UP&&p<4->{play.requestFocus();true};k==KeyEvent.KEYCODE_DPAD_LEFT&&p%4==0->{navButtons[section].requestFocus();true};k==KeyEvent.KEYCODE_DPAD_RIGHT&&(p%4==3||p==detail.episodes.lastIndex)->true;k==KeyEvent.KEYCODE_DPAD_DOWN->{TvUi.focusItem(episodes,Core.gridDown(p,detail.episodes.size,4));true};else->false}}}
   };box.addView(episodes,LinearLayout.LayoutParams(-1,0,1f));if(navFocus>=0)navButtons[navFocus].requestFocus()else play.requestFocus()
  },{e->showError(e){renderDetail(v)}})
 }

internal fun MainActivity.play(v:Video,episode:Int,position:Long){startActivityForResult(Intent(this,PlaybackActivity::class.java).putExtra("video",v.json().toString()).putExtra("server",store.server).putExtra("episode",episode).putExtra("position",position),41)}

internal fun MainActivity.backFromDetail(){when(returnPage){"search"->{searchScreen();focusRow(resultPosition)};"favorites"->{library(true);focusRow()};"history"->{library(false);focusRow()};else->{home();focusRow()}}}
