package app.libretv.tv
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.*
object TvUi {
 val bg=Color.rgb(9,17,28);val surface=Color.rgb(21,34,49);val text=Color.rgb(235,242,250);val muted=Color.rgb(153,172,193);val cyan=Color.rgb(0,201,237)
 fun dp(c:Context,n:Int)=(n*c.resources.displayMetrics.density+.5f).toInt()
 fun shape(color:Int,stroke:Int=0)=GradientDrawable().apply{setColor(color);cornerRadius=16f;if(stroke!=0)setStroke(4,stroke)}
 fun label(c:Context,s:String,size:Float=18f,bold:Boolean=false)=TextView(c).apply{this.text=s;textSize=size;setTextColor(TvUi.text);if(bold)setTypeface(null,Typeface.BOLD);includeFontPadding=false}
 fun focus(v:View,primary:Boolean=false) {
  v.stateListAnimator=null;v.isFocusable=true;v.isFocusableInTouchMode=true;v.background=shape(if(primary)cyan else surface);if(v is EditText)v.setPadding(dp(v.context,18),0,dp(v.context,18),0)
  v.onFocusChangeListener=View.OnFocusChangeListener { view,has ->
   view.background=shape(if(primary)cyan else if(has)Color.rgb(34,56,74) else surface,if(has)Color.WHITE else 0)
   view.animate().cancel();view.scaleX=1f;view.scaleY=1f
   view.elevation=0f
  }
 }
 fun button(c:Context,s:String,primary:Boolean=false,click:()->Unit)=Button(c).apply{
  text=s;textSize=18f;isAllCaps=false;setTextColor(if(primary)Color.BLACK else TvUi.text);setPadding(dp(c,18),0,dp(c,18),0);minHeight=dp(c,48);minimumWidth=0;setTypeface(null,Typeface.BOLD)
  focus(this,primary);setOnClickListener{click()}
 }
 fun focusItem(list:androidx.recyclerview.widget.RecyclerView,index:Int){list.scrollToPosition(index);list.post{list.findViewHolderForAdapterPosition(index)?.itemView?.requestFocus()}}
 fun vertical(c:Context)=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL}
}
