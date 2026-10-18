package app.libretv.tv

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.view.*
import android.widget.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

internal class RemoteQr(private val activity:MainActivity):LinearLayout(activity){
 private val qr=ImageView(activity)
 private val caption=TvUi.label(activity,"手机选片 · 遥控",12f)
 private var bitmap:Bitmap?=null
 private var preview:Dialog?=null
 fun dismissPreview(){preview?.dismiss();preview=null}
 private var address:String?=null
 init{
  orientation=VERTICAL;gravity=Gravity.CENTER;setPadding(4,4,4,4);contentDescription="手机选片二维码";TvUi.focus(this)
  qr.setBackgroundColor(Color.WHITE);qr.contentDescription="扫码连接电视";addView(qr,LayoutParams(activity.d(100),activity.d(100)))
  caption.gravity=Gravity.CENTER;caption.setTextColor(TvUi.muted);addView(caption,LayoutParams(-1,activity.d(24)))
  setOnKeyListener{_,key,e->if(e.action!=KeyEvent.ACTION_DOWN)false else when(key){KeyEvent.KEYCODE_DPAD_UP->{activity.navButtons[4].requestFocus();true};KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_LEFT->true;KeyEvent.KEYCODE_DPAD_RIGHT->{activity.mainAction?.requestFocus();true};else->false}}
  setOnClickListener{if(address==null)RemoteControl.restart()else enlarge()}
 }
 fun update(url:String?,error:String?){
  if(url==address&&error==null)return;dismissPreview();address=url
  bitmap=url?.let{val m=QRCodeWriter().encode(it,BarcodeFormat.QR_CODE,512,512,mapOf(EncodeHintType.MARGIN to 4));Bitmap.createBitmap(IntArray(512*512){if(m[it%512,it/512])Color.BLACK else Color.WHITE},512,512,Bitmap.Config.ARGB_8888)}
  qr.setImageBitmap(bitmap);caption.text=error?:"手机选片 · 遥控"
 }
 private fun enlarge(){
  dismissPreview();val dialog=Dialog(activity);preview=dialog;val box=TvUi.vertical(activity).apply{setPadding(activity.d(28),activity.d(24),activity.d(28),activity.d(24));setBackgroundColor(TvUi.surface)}
  box.addView(TvUi.label(activity,"手机选片，电视播放",24f,true),LayoutParams(-1,activity.d(48)))
  box.addView(TvUi.label(activity,"连接同一 Wi-Fi，扫码搜索与遥控播放",15f),LayoutParams(-1,activity.d(40)))
  box.addView(ImageView(activity).apply{setImageBitmap(bitmap)},LayoutParams(activity.d(256),activity.d(256)).apply{gravity=Gravity.CENTER_HORIZONTAL})
  val close=TvUi.button(activity,"关闭"){dialog.dismiss()};box.addView(close,LayoutParams(-1,activity.d(48)).apply{topMargin=activity.d(20)})
  dialog.setContentView(box);dialog.setOnDismissListener{if(preview===dialog)preview=null;if(isAttachedToWindow)requestFocus()};dialog.show();dialog.window?.setLayout(activity.d(400),-2);close.requestFocus()
 }
}
