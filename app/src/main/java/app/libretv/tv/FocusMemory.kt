package app.libretv.tv
class FocusMemory {
 private val positions=mutableMapOf<String,Pair<String,Int>>()
 fun remember(page:String,key:String,index:Int){positions[page]=key to index}
 fun index(page:String,keys:List<String>):Int {if(keys.isEmpty())return -1;val saved=positions[page]?:return 0;val found=keys.indexOf(saved.first);return if(found>=0)found else saved.second.coerceIn(0,keys.lastIndex)}
}
