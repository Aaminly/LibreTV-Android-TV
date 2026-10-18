package app.libretv.tv
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
class ModelsTest {
 @Test fun parseRealContracts() {
  val v = Video.from(JSONObject("""{"vodId":"42","sourceKey":"s","sourceName":"源","name":"影片","pic":"https://a/b.jpg","sourceUrl":"https://a/api"}"""))
  assertEquals("42",v.id); assertEquals("影片",v.title); assertEquals(v,Video.from(v.json()))
  val d=Detail.from(JSONObject("""{"episodes":["第一集${'$'}https://a/v.m3u8"],"videoInfo":{"title":"影片","desc":"介绍"}}"""))
  assertEquals(1,d.episodes.size); assertEquals("介绍",d.description)
 }
 @Test fun malformedDetailsFail() {
  try { Detail.from(JSONObject("{}")); fail() } catch (_: Exception) {}
 }
 @Test fun latestRequestWins() {
  val gate=RequestGate(); val first=gate.next();val second=gate.next()
  assertFalse(gate.current(first));assertTrue(gate.current(second))
 }
}
