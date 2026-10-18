package app.libretv.tv
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
class PosterAdapter(private val items:List<Video>,private val api:LibreTvApi,private val left:()->Unit,private val up:()->Unit,private val focused:(Video,Int)->Unit,private val progress:Map<String,Int>,private val click:(Video,Int)->Unit):RecyclerView.Adapter<PosterAdapter.Holder>() {
 class Holder(val box:LinearLayout,val pic:ImageView,val title:TextView,val progress:ProgressBar):RecyclerView.ViewHolder(box)
 override fun onCreateViewHolder(parent:ViewGroup,type:Int):Holder {
  val c=parent.context;val box=TvUi.vertical(c);box.setPadding(6,6,6,6);box.clipChildren=false
  box.layoutParams=RecyclerView.LayoutParams(TvUi.dp(c,187),TvUi.dp(c,145)).apply{rightMargin=TvUi.dp(c,10)}
  val pic=ImageView(c).apply{scaleType=ImageView.ScaleType.CENTER_CROP;cropToPadding=true;background=TvUi.shape(TvUi.bg);clipToOutline=true};val title=TvUi.label(c,"",16f,false).apply{maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END;setPadding(6,10,6,0)}
  val bar=ProgressBar(c,null,android.R.attr.progressBarStyleHorizontal).apply{max=1000;isFocusable=false;progressTintList=android.content.res.ColorStateList.valueOf(TvUi.cyan)}
  box.addView(pic,LinearLayout.LayoutParams(-1,TvUi.dp(c,97)));box.addView(bar,LinearLayout.LayoutParams(-1,TvUi.dp(c,4)));box.addView(title);TvUi.focus(box);return Holder(box,pic,title,bar)
 }
 override fun getItemCount()=items.size
 override fun onBindViewHolder(h:Holder,p:Int){val v=items[p];TvUi.focus(h.box);val visual=h.box.onFocusChangeListener;h.box.onFocusChangeListener=View.OnFocusChangeListener{view,has->visual?.onFocusChange(view,has);if(has)focused(v,p)};h.progress.visibility=if(progress.containsKey(v.key))View.VISIBLE else View.INVISIBLE;h.progress.progress=progress[v.key]?:0;h.title.text=v.title;h.box.contentDescription=v.title;Images.load(h.pic,api.proxy(v.cover));h.box.setOnClickListener{click(v,p)}
  h.box.setOnKeyListener{_,key,e->if(e.action!=KeyEvent.ACTION_DOWN)false else when{
   key==KeyEvent.KEYCODE_DPAD_LEFT&&p==0->{left();true}
   key==KeyEvent.KEYCODE_DPAD_RIGHT&&p==items.lastIndex->true
   key==KeyEvent.KEYCODE_DPAD_UP->{up();true}
   key==KeyEvent.KEYCODE_DPAD_DOWN->true
   else->false
  }}
 }
}
