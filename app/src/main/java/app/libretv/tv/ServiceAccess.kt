package app.libretv.tv

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*

internal fun MainActivity.checkService(raw:String=store.server){
 accessHandler.removeCallbacksAndMessages(null);authDialog?.dismiss();authDialog=null;AccessGate.lock();RemoteControl.lock();sources=emptyList();results=emptyList();recommendations=emptyList();homeLoaded=false
 reset("access");val box=pageColumn("连接影片服务")
 val status=TvUi.label(this,"正在验证服务访问权限…",18f).apply{setTextColor(TvUi.muted)};box.addView(status,LinearLayout.LayoutParams(-1,d(68)))
 val change=TvUi.button(this,"更换服务地址"){settings(raw)};box.addView(change,LinearLayout.LayoutParams(d(220),d(48)));mainAction=change;change.requestFocus()
 val candidate=try{LibreTvApi(raw)}catch(e:Exception){settings(raw);showError(e.message?:"地址无效"){settings(raw)};return}
 runAccess(candidate,null,{passwordDialog(candidate)},{message->status.text=message;val retry=TvUi.button(this,"重试"){checkService(raw)};box.addView(retry,LinearLayout.LayoutParams(d(140),d(48)).apply{topMargin=d(12)});retry.requestFocus()})
}

private fun MainActivity.runAccess(candidate:LibreTvApi,password:String?,needPassword:()->Unit,error:(String)->Unit){
 val id=gate.next();pending?.cancel(true);candidateApi?.cancel();candidateApi=candidate;authChecking=true
 pending=pool.submit{
  try{val configured=if(password==null)candidate.status()else candidate.login(password)
   runOnUiThread{if(!isDestroyed&&gate.current(id))try{
    store.connected(candidate);api=candidate;candidateApi=null;authChecking=false;AccessGate.grant(api.base,api.session)
    authDialog?.dismiss();authDialog=null;sources=configured;section=0;home();scheduleAccessExpiry();if(resumed)RemoteControl.foreground(this)
   }catch(e:Exception){authChecking=false;error(e.message?:"无法保存连接")}}
  }catch(e:Exception){if(!Thread.currentThread().isInterrupted)runOnUiThread{if(!isDestroyed&&gate.current(id)){authChecking=false;if(e is LoginRequired)needPassword()else error(e.message?:"连接失败")}}}
 }
}

private fun MainActivity.passwordDialog(candidate:LibreTvApi){
 if(isFinishing||isDestroyed||!resumed)return
 val dialog=Dialog(this);authDialog=dialog
 val panel=TvUi.vertical(this).apply{setPadding(d(28),d(24),d(28),d(24));background=TvUi.shape(TvUi.surface)}
 panel.addView(TvUi.label(this,"访问验证",25f,true),LinearLayout.LayoutParams(-1,d(42)))
 panel.addView(TvUi.label(this,"请输入此 LibreTV 网页的访问密码",17f).apply{setTextColor(TvUi.muted)},LinearLayout.LayoutParams(-1,d(34)))
 panel.addView(TvUi.label(this,candidate.base,14f).apply{setTextColor(TvUi.muted);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.MIDDLE},LinearLayout.LayoutParams(-1,d(30)))
 val input=EditText(this).apply{setSingleLine();textSize=20f;setTextColor(TvUi.text);setHintTextColor(TvUi.muted);hint="访问密码";contentDescription="网页访问密码";inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD;imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_GO;isSaveEnabled=false;if(android.os.Build.VERSION.SDK_INT>=26)importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS};TvUi.focus(input);panel.addView(input,LinearLayout.LayoutParams(-1,d(56)))
 val status=TvUi.label(this,"验证成功后会记住登录，下次打开无需重复输入",14f).apply{setTextColor(TvUi.muted);maxLines=2};panel.addView(status,LinearLayout.LayoutParams(-1,d(46)).apply{topMargin=d(8)})
 val buttons=LinearLayout(this);panel.addView(buttons)
 lateinit var submit:Button
 val change=TvUi.button(this,"更换地址"){gate.next();pending?.cancel(true);candidate.cancel();authChecking=false;dialog.dismiss();authDialog=null;settings(candidate.base)}
 val exit=TvUi.button(this,"退出应用"){dialog.dismiss();finish()}
 submit=TvUi.button(this,"验证并进入",true){
  val password=input.text.toString();if(password.isEmpty()){status.text="请输入网页访问密码";input.requestFocus();return@button}
  (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken,0)
  submit.isEnabled=false;input.isEnabled=false;status.text="正在验证…"
  fun failed(message:String){submit.isEnabled=true;input.isEnabled=true;status.text=message;input.requestFocus()}
  runAccess(candidate,password,{failed("登录会话未通过验证，请重新输入")},{failed(it)})
 }
 buttons.addView(submit,LinearLayout.LayoutParams(0,d(48),1f));buttons.addView(change,LinearLayout.LayoutParams(d(128),d(48)).apply{leftMargin=d(12)});buttons.addView(exit,LinearLayout.LayoutParams(d(128),d(48)).apply{leftMargin=d(12)})
 input.id=View.generateViewId();submit.id=View.generateViewId();input.nextFocusDownId=submit.id;submit.nextFocusUpId=input.id
 input.setOnEditorActionListener{_,_,_->if(submit.isEnabled)submit.performClick();true}
 input.setOnKeyListener{_,key,e->e.action==0&&when(key){KeyEvent.KEYCODE_DPAD_DOWN->{submit.requestFocus();true};KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_LEFT->input.selectionStart==0;else->false}}
 listOf(submit,change,exit).forEach{button->button.setOnKeyListener{_,key,e->e.action==0&&when(key){KeyEvent.KEYCODE_DPAD_UP->{input.requestFocus();true};KeyEvent.KEYCODE_DPAD_DOWN->true;KeyEvent.KEYCODE_DPAD_LEFT->button===submit;KeyEvent.KEYCODE_DPAD_RIGHT->button===exit;else->false}}}
 dialog.setContentView(panel);dialog.setCancelable(false);dialog.setCanceledOnTouchOutside(false);dialog.setOnKeyListener{_,key,event->if(key==KeyEvent.KEYCODE_BACK){if(event.action==KeyEvent.ACTION_UP)change.requestFocus();true}else false}
 dialog.show();dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT));dialog.window?.setLayout(d(620),-2);dialog.window?.decorView?.systemUiVisibility=5894;input.requestFocus()
}

internal fun MainActivity.loginRequired(error:LoginRequired){
 if(error.base!=api.base)return
 if(!AccessGate.reject(error)&&AccessGate.allowed(api.base))return
 store.clearSession(error.base);checkService(error.base)
}

internal fun MainActivity.ensureAccess():Boolean {if(AccessGate.allowed(store.server))return true;if(!authChecking&&authDialog==null)checkService();return false}
internal fun MainActivity.scheduleAccessExpiry(){
 accessHandler.removeCallbacksAndMessages(null);val client=api;val expires=client.session?.expires?:return;if(expires==0L||!resumed)return
 accessHandler.postDelayed({if(resumed&&api===client&&!AccessGate.allowed(client.base))loginRequired(LoginRequired(client.base,client.session?.token))},(expires-System.currentTimeMillis()).coerceAtLeast(1))
}
