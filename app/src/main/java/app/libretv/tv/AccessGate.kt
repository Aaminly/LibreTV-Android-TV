package app.libretv.tv

/** A persisted cookie is not proof of access until this process checks the server. */
internal object AccessGate {
 private data class Verified(val base:String,val session:SiteSession?)
 @Volatile private var verified:Verified?=null
 fun grant(base:String,session:SiteSession?){verified=Verified(Core.server(base),session)}
 fun lock(){verified=null}
 fun allowed(base:String):Boolean {val v=verified?:return false;return v.base==Core.server(base)&&(v.session==null||v.session.valid()&&SessionCookies.session(base)?.token==v.session.token)}
 fun reject(error:LoginRequired):Boolean {val v=verified?:return false;if(v.base!=error.base||v.session?.token!=error.token)return false;lock();return true}
}
