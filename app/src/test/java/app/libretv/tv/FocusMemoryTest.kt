package app.libretv.tv
import org.junit.Test
import org.junit.Assert.*
class FocusMemoryTest {
 @Test fun restoresByStableIdAndClampsDeletedItem(){val m=FocusMemory();m.remember("home","b",1);assertEquals(2,m.index("home",listOf("x","a","b")));assertEquals(0,m.index("home",listOf("a")));assertEquals(-1,m.index("home",emptyList()));assertEquals(0,m.index("history",listOf("z")))}
 @Test fun positionsIndependentAcrossPages(){val m=FocusMemory();m.remember("home","b",1);m.remember("search","z",0);assertEquals(1,m.index("home",listOf("a","b")))}
}
