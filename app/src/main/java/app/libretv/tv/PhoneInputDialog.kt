package app.libretv.tv

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

internal class PhoneInputDialog(private val activity:MainActivity,private val entry:View):Dialog(activity) {
 private val handler=Handler(Looper.getMainLooper())
 private val starter=Executors.newSingleThreadExecutor()
 private var generation=0
 private var server:LanInputServer?=null
 private val qr=ImageView(activity)
 private val status=TvUi.label(activity,"正在建立连接…",15f)
 private val address=TvUi.label(activity,"",13f)
 private lateinit var refresh:Button
 private val timer=object:Runnable{override fun run(){
  val s=server?:return
  val seconds=s.remainingSeconds
  if(seconds==0L){s.close();server=null;qr.setImageDrawable(null);status.text="二维码已过期，请重新生成";address.text="";refresh.text="重新生成二维码";refresh.requestFocus()}
  else{status.text="等待手机输入 · ${seconds/60}:${(seconds%60).toString().padStart(2,'0')} 后失效";handler.postDelayed(this,1000)}
 }}
 init {
  requestWindowFeature(Window.FEATURE_NO_TITLE)
  val box=TvUi.vertical(activity).apply{setPadding(d(28),d(24),d(28),d(20));setBackgroundColor(TvUi.surface)}
  val brand=LinearLayout(activity).apply{gravity=Gravity.CENTER_VERTICAL}
  brand.addView(ImageView(activity).apply{setImageResource(R.drawable.app_icon);contentDescription="LibreTV 兔子"},LinearLayout.LayoutParams(d(42),d(42)))
  brand.addView(TvUi.label(activity,"手机输入 · 大屏搜索",16f,true),LinearLayout.LayoutParams(-1,-1).apply{leftMargin=d(12)})
  box.addView(brand,LinearLayout.LayoutParams(-1,d(42)))
  box.addView(TvUi.label(activity,"扫码，轻松搜影片",25f,true),LinearLayout.LayoutParams(-1,d(40)).apply{topMargin=d(14)})
  box.addView(TvUi.label(activity,"手机与电视连接同一 Wi-Fi\n扫码输入片名，电视自动开始搜索",15f).apply{setTextColor(TvUi.muted);setLineSpacing(d(4).toFloat(),1f)},LinearLayout.LayoutParams(-1,d(48)))
  qr.apply{contentDescription="手机输入二维码";setBackgroundColor(Color.WHITE);scaleType=ImageView.ScaleType.FIT_CENTER;setPadding(d(10),d(10),d(10),d(10))}
  box.addView(qr,LinearLayout.LayoutParams(d(208),d(208)).apply{gravity=Gravity.CENTER_HORIZONTAL;topMargin=d(12)})
  status.apply{gravity=Gravity.CENTER;setTextColor(TvUi.cyan)};box.addView(status,LinearLayout.LayoutParams(-1,d(32)))
  address.apply{gravity=Gravity.CENTER;setTextColor(TvUi.muted);maxLines=1};box.addView(address,LinearLayout.LayoutParams(-1,d(22)))
  val actions=LinearLayout(activity)
  refresh=TvUi.button(activity,"刷新二维码"){startSession()}.apply{textSize=15f}
  val close=TvUi.button(activity,"关闭"){dismiss()}.apply{textSize=15f}
  actions.addView(refresh,LinearLayout.LayoutParams(0,d(48),1f).apply{rightMargin=d(12)})
  actions.addView(close,LinearLayout.LayoutParams(d(88),d(48)))
  box.addView(actions,LinearLayout.LayoutParams(-1,d(48)).apply{topMargin=d(10)})
  refresh.id=View.generateViewId();close.id=View.generateViewId()
  refresh.nextFocusRightId=close.id;refresh.nextFocusLeftId=refresh.id;close.nextFocusLeftId=refresh.id;close.nextFocusRightId=close.id
  listOf(refresh,close).forEach{it.nextFocusUpId=it.id;it.nextFocusDownId=it.id}
  setContentView(box);setCanceledOnTouchOutside(true)
  setOnDismissListener{generation++;server?.close();server=null;handler.removeCallbacks(timer);starter.shutdown();if(entry.isAttachedToWindow&&activity.page=="settings")entry.requestFocus()}
 }
 override fun show(){super.show();window?.apply{setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT));setLayout(d(380),-1);setGravity(Gravity.RIGHT);addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);setDimAmount(.55f);decorView.systemUiVisibility=5894};refresh.requestFocus();startSession()}
 private fun d(value:Int)=activity.d(value)
 private fun startSession(){
  val current=++generation;server?.close();server=null;handler.removeCallbacks(timer);qr.setImageDrawable(null);status.text="正在建立连接…";address.text="";refresh.text="刷新二维码"
  starter.execute{
   var created:LanInputServer?=null
   try {
    val network=NetworkInterface.getNetworkInterfaces().toList().filter{it.isUp&&!it.isLoopback}.sortedBy{if(it.name.startsWith("wlan")||it.name.startsWith("eth"))0 else 1}
    val ip=network.flatMap{it.inetAddresses.toList()}.filterIsInstance<Inet4Address>().firstOrNull{it.isSiteLocalAddress&&!it.isLoopbackAddress}?:error("未连接局域网，请检查电视网络")
    val html=activity.assets.open("phone-input.html").bufferedReader().use{it.readText()}
    val icon=activity.assets.open("bunny.png").use{it.readBytes()}
    created=LanInputServer(ip,html,icon){query->
     val action=FutureTask<Boolean>{
      if(!isShowing||generation!=current||activity.isFinishing||activity.isDestroyed||activity.page!="settings")false
      else{activity.query=query;activity.results=emptyList();activity.searchScreen();activity.runSearch();true}
     }
     handler.post(action)
     try{action.get(3,TimeUnit.SECONDS)}catch(_:Exception){action.cancel(false);false}
    }
    val s=created
    val matrix=QRCodeWriter().encode(s.url,BarcodeFormat.QR_CODE,512,512,mapOf(EncodeHintType.MARGIN to 2))
    val pixels=IntArray(512*512){if(matrix[it%512,it/512])Color.BLACK else Color.WHITE}
    val bitmap=Bitmap.createBitmap(pixels,512,512,Bitmap.Config.ARGB_8888)
    handler.post{
     if(!isShowing||current!=generation){s.close();bitmap.recycle()}
     else{server=s;qr.setImageBitmap(bitmap);address.text="电视地址  ${ip.hostAddress}";handler.post(timer)}
    }
   }catch(e:Exception){created?.close();handler.post{if(isShowing&&current==generation){status.text=e.message?:"连接失败，请重试";refresh.text="重新连接"}}}
  }
 }
}
