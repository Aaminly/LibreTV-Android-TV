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

internal fun MainActivity.searchScreen(){section=1;reset("search");val box=pageColumn("搜索影片")
  box.addView(TvUi.label(this,"输入片名，搜索所有已连接的视频源",16f).apply{setTextColor(TvUi.muted)},LinearLayout.LayoutParams(-1,d(36)));val line=LinearLayout(this);val input=EditText(this).apply{setSingleLine();setText(query);hint="输入影片名称";textSize=20f;setTextColor(TvUi.text);setHintTextColor(TvUi.muted);background=TvUi.shape(TvUi.surface);setPadding(d(16),0,d(16),0);imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH;contentDescription="影片名称"}
  TvUi.focus(input);searchInput=input;input.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){query=s.toString()};override fun afterTextChanged(s:android.text.Editable?){} });line.addView(input,LinearLayout.LayoutParams(0,d(52),1f));val button=TvUi.button(this,"搜索",true){query=input.text.toString().trim();(getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken,0);runSearch()};button.contentDescription="执行影片搜索";line.addView(button,LinearLayout.LayoutParams(d(112),d(52)).apply{leftMargin=d(16)});box.addView(line);mainAction=input
  input.setOnEditorActionListener{_,_,_->button.performClick();true};input.setOnKeyListener{_,k,e->if(e.action!=0)false else when{k==KeyEvent.KEYCODE_DPAD_LEFT&&input.selectionStart==0->{navButtons[1].requestFocus();true};k==KeyEvent.KEYCODE_DPAD_RIGHT&&input.selectionStart==input.length()->{button.requestFocus();true};k==KeyEvent.KEYCODE_DPAD_DOWN&&results.isNotEmpty()->{focusRow();true};else->false}}
  button.setOnKeyListener{_,k,e->if(e.action==0&&k==KeyEvent.KEYCODE_DPAD_DOWN&&results.isNotEmpty()){focusRow();true}else false}
  if(results.isEmpty())box.addView(TvUi.label(this,"按确定键输入片名，完成后选择右侧「搜索」\n结果会按影片与来源展示，可进入详情选择剧集",18f).apply{setTextColor(TvUi.muted);setLineSpacing(d(10).toFloat(),1f);setPadding(d(24),d(28),d(24),d(28));background=TvUi.shape(TvUi.surface)},LinearLayout.LayoutParams(-1,d(140)).apply{topMargin=d(24)})
  if(results.isNotEmpty()){box.addView(TvUi.label(this,"${results.size} 个结果 · 选择影片和来源",17f),LinearLayout.LayoutParams(-1,d(56)));box.addView(resultList(results),LinearLayout.LayoutParams(-1,0,1f))}
  input.requestFocus()
 }

internal fun MainActivity.resultList(items:List<Video>):RecyclerView {
  val list=RecyclerView(this).apply{layoutManager=androidx.recyclerview.widget.GridLayoutManager(this@resultList,2);itemAnimator=null;clipToPadding=false;setPadding(d(4),d(4),d(4),d(4))}
  rowItems=items;val owner=page;val records=store.history().associateBy{it.video.key};list.adapter=object:RecyclerView.Adapter<TextHolder>(){
   override fun getItemCount()=items.size
   override fun onCreateViewHolder(parent:ViewGroup,type:Int):TextHolder{val b=TvUi.button(this@resultList,""){};b.gravity=Gravity.CENTER_VERTICAL;b.layoutParams=RecyclerView.LayoutParams(-1,d(122)).apply{setMargins(d(4),d(4),d(4),d(8))};return TextHolder(b)}
   override fun onBindViewHolder(h:TextHolder,p:Int){val v=items[p];TvUi.focus(h.button);val visual=h.button.onFocusChangeListener;h.button.onFocusChangeListener=View.OnFocusChangeListener{view,has->visual?.onFocusChange(view,has);if(has){focusMemory.remember(owner,v.key,h.bindingAdapterPosition.coerceAtLeast(0));lastContent=view}};val history=records[v.key];h.button.text="${v.title}\n${v.sourceName}  ·  ${v.remarks}"+(if(owner=="history"&&history!=null)"\n第 ${history.episode+1} 集 · 已看 ${history.position/60000} 分钟"else "");val styled=android.text.SpannableString(h.button.text);val split=styled.indexOf('\n');if(split>0){styled.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD),0,split,0);styled.setSpan(android.text.style.ForegroundColorSpan(TvUi.muted),split,styled.length,0);styled.setSpan(android.text.style.RelativeSizeSpan(.82f),split,styled.length,0)};h.button.setTypeface(null,android.graphics.Typeface.NORMAL);h.button.text=styled;Images.load(h.button,api.proxy(v.cover),d(60),d(90));h.button.textSize=17f;h.button.setLineSpacing(d(5).toFloat(),1f);h.button.maxLines=3;h.button.ellipsize=android.text.TextUtils.TruncateAt.END;h.button.setOnClickListener{resultPosition=h.bindingAdapterPosition.coerceAtLeast(0);openDetail(v)};h.button.setOnKeyListener{_,k,e->if(e.action!=0)false else when{ k==KeyEvent.KEYCODE_DPAD_LEFT&&p%2==0->{navButtons[section].requestFocus();true};k==KeyEvent.KEYCODE_DPAD_UP&&p<2->{mainAction?.requestFocus();true};k==KeyEvent.KEYCODE_DPAD_RIGHT&&(p%2==1||p==items.lastIndex)->true;k==KeyEvent.KEYCODE_DPAD_DOWN->{TvUi.focusItem(list,Core.gridDown(p,items.size,2));true};else->false}}}
  };row=list;return list
 }

internal fun MainActivity.runSearch(){
  if(query.isBlank()){Toast.makeText(this,"请输入影片名称",Toast.LENGTH_SHORT).show();return}
  reset("search");val box=pageColumn("搜索：$query");val cancel=TvUi.button(this,"修改搜索"){searchScreen()};box.addView(cancel,LinearLayout.LayoutParams(d(160),d(48)).apply{bottomMargin=d(24)});mainAction=cancel;cancel.requestFocus();val status=TvUi.label(this,"正在搜索各个视频源…",18f).apply{setTextColor(TvUi.muted);setPadding(d(24),d(24),d(24),d(24));background=TvUi.shape(TvUi.surface)};box.addView(status)
  async({val current=sources.ifEmpty{api.status()};sources=current;api.search(query,current)},{(items,failed)->val navFocus=navButtons.indexOf(currentFocus);results=items;searchScreen();if(navFocus>=0)navButtons[navFocus].requestFocus()else if(items.isNotEmpty())focusRow(0)else showError("未找到相关影片${if(failed>0)"，$failed 个来源请求失败" else ""}"){searchScreen()};if(failed>0&&items.isNotEmpty())Toast.makeText(this,"$failed 个来源请求失败，已显示其余结果",Toast.LENGTH_LONG).show()},{e->showError(e){runSearch()}})
 }
