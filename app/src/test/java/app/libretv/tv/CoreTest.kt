package app.libretv.tv
import org.junit.Test
import org.junit.Assert.*
class CoreTest {
 @Test fun validatesServer() {
  assertEquals("http://192.168.31.1:8899", Core.server(" http://192.168.31.1:8899/ "))
  for (bad in listOf("file:///tmp/a", "javascript:bad", "http://", "http://user:pass@host", "http://host/?token=x")) {
   try { Core.server(bad); fail(bad) } catch (_: IllegalArgumentException) {}
  }
 }
 @Test fun boundedFocus() {
  assertEquals(0, Core.move(0, -1, 5)); assertEquals(4, Core.move(4, 1, 5))
  assertEquals(1, Core.move(4, 0, 2)); assertEquals(-1, Core.move(0, 1, 0))
 }
 @Test fun progressLimits() {
  assertEquals(0L, Core.position(-10, 100)); assertEquals(100L, Core.position(200,100))
  assertEquals(300L, Core.position(300,0))
 }
 @Test fun unnamedEpisodesHaveDistinctLabels(){
  assertEquals("第 1 集",Core.episodeLabel("https://media.test/one.m3u8",0))
  assertEquals("第 2 集",Core.episodeLabel("https://media.test/two.m3u8",1))
  assertEquals("特别篇",Core.episodeLabel("特别篇\$https://media.test/special.m3u8",2))
 }
 @Test fun episodeParsing() {
  assertEquals("https://media.test/a.m3u8?x=1", Core.episode("第1集$" + "https://media.test/a.m3u8?x=1").second)
  assertEquals("第1集", Core.episode("第1集$" + "https://media.test/a.m3u8").first)
  try { Core.episode("javascript:bad"); fail() } catch (_: IllegalArgumentException) {}
 }
}
