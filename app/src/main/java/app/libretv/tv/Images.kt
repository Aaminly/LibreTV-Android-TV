package app.libretv.tv
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
object Images {
 private val pool=Executors.newFixedThreadPool(3)
 private val cache=object:LruCache<String,Bitmap>(16*1024*1024){override fun sizeOf(k:String,b:Bitmap)=b.byteCount}
 fun load(view:ImageView,url:String) {
  view.tag=url;view.setImageDrawable(placeholder(view.context,"加载中"));fetch(url,{view.post{if(view.tag==url)view.setImageDrawable(placeholder(view.context,"无封面"))}}){bitmap->view.post{if(view.tag==url)view.setImageBitmap(bitmap)}}
 }
 fun load(view:android.widget.TextView,url:String,width:Int,height:Int){
  view.tag=url;view.compoundDrawablePadding=TvUi.dp(view.context,16)
  fun state(label:String){view.setCompoundDrawables(placeholder(view.context,label).apply{setBounds(0,0,width,height)},null,null,null)};state("加载中")
  fetch(url,{view.post{if(view.tag==url)state("无封面")}}){bitmap->view.post{if(view.tag==url){val drawable=cover(bitmap).apply{setBounds(0,0,width,height)};view.setCompoundDrawables(drawable,null,null,null)}}}
 }
 internal fun cover(bitmap:Bitmap)=object:android.graphics.drawable.Drawable(){
  private val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
  override fun draw(canvas:android.graphics.Canvas){val scale=maxOf(bounds.width().toFloat()/bitmap.width,bounds.height().toFloat()/bitmap.height);val matrix=android.graphics.Matrix().apply{setScale(scale,scale);postTranslate(bounds.exactCenterX()-bitmap.width*scale/2,bounds.exactCenterY()-bitmap.height*scale/2)};val save=canvas.save();canvas.clipRect(bounds);canvas.drawBitmap(bitmap,matrix,paint);canvas.restoreToCount(save)}
  override fun setAlpha(alpha:Int){paint.alpha=alpha};override fun setColorFilter(filter:android.graphics.ColorFilter?){paint.colorFilter=filter};override fun getOpacity()=android.graphics.PixelFormat.TRANSLUCENT
 }
 private fun placeholder(context:android.content.Context,label:String)=object:android.graphics.drawable.Drawable(){
  private val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
  override fun draw(canvas:android.graphics.Canvas){paint.color=TvUi.surface;canvas.drawRoundRect(android.graphics.RectF(bounds),16f,16f,paint);paint.color=TvUi.muted;paint.textSize=TvUi.dp(context,12).toFloat();paint.textAlign=android.graphics.Paint.Align.CENTER;canvas.drawText(label,bounds.exactCenterX(),bounds.exactCenterY()-(paint.ascent()+paint.descent())/2,paint)}
  override fun setAlpha(alpha:Int){paint.alpha=alpha};override fun setColorFilter(filter:android.graphics.ColorFilter?){paint.colorFilter=filter};override fun getOpacity()=android.graphics.PixelFormat.TRANSLUCENT
 }
 private fun fetch(url:String,failed:()->Unit,ready:(Bitmap)->Unit){
  if(url.isBlank()){failed();return}
  cache.get(url)?.let{ready(it);return}
  pool.execute {
   try {
    val c=URL(url).openConnection() as HttpURLConnection;c.connectTimeout=7000;c.readTimeout=10000
    val bytes=try{c.inputStream.use{input->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);var count=input.read(buffer);while(count>=0){check(out.size()+count<=8*1024*1024){"封面文件过大"};out.write(buffer,0,count);count=input.read(buffer)};out.toByteArray()}}finally{c.disconnect()}
    val size=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,size)
    val opt=BitmapFactory.Options().apply{inSampleSize=1;while(size.outWidth/inSampleSize>1280||size.outHeight/inSampleSize>1280)inSampleSize*=2}
    val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size,opt)?:throw IllegalStateException("无效封面图片")
    cache.put(url,bitmap);ready(bitmap)
   }catch(e:Exception){android.util.Log.w("LibreTV","封面加载失败: ${e.javaClass.simpleName}");failed()}
  }
 }
}
