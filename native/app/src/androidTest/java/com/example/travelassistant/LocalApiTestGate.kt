package com.example.travelassistant

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Explicit opt-in plus server health guard: instrumentation must never trigger a paid AI call. */
object LocalApiTestGate {
 fun requireSimulation() {
  assumeTrue("Requires localApiPhase=true",InstrumentationRegistry.getArguments().getString("localApiPhase")=="true")
  val c=URL("http://127.0.0.1:8787/health").openConnection() as HttpURLConnection
  try {c.connectTimeout=5000;c.readTimeout=5000;val health=JSONObject(c.inputStream.bufferedReader().use{it.readText()});assertTrue("Enable mock and forced rate limit before running API tests",health.getBoolean("mockAllowed")&&health.getBoolean("rateLimitSimulation"))}finally{c.disconnect()}
 }
}
