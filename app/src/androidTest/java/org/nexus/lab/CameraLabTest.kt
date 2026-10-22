package org.nexus.lab

import android.Manifest
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraLabTest {
    private fun status(s: ActivityScenario<MainActivity>): String { var text="";s.onActivity { text=it.labStatus() };return text }
    private fun await(s: ActivityScenario<MainActivity>, maxMs:Long=18000, predicate:(String)->Boolean):String {
        val until=SystemClock.elapsedRealtime()+maxMs
        var last=""
        while(SystemClock.elapsedRealtime()<until) {
            last=status(s)
            if(predicate(last)) return last
            if(last.contains("failed",true)||last.contains("refused",true)) fail(last)
            Thread.sleep(20)
        }
        fail("Timed out; $last");return last
    }
    private fun record(name:String,text:String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(0,Bundle().apply {putString("nexus_camera_$name",text)})
    }
    private fun result(text: String): JSONObject {
        val start = text.indexOf('{'); val end = text.lastIndexOf('}')
        assertTrue("SDK JSON result required", start >= 0 && end > start)
        return JSONObject(text.substring(start, end + 1))
    }
    private fun assertComplete(text: String, secured: Boolean) {
        val r = result(text)
        assertEquals(0, r.getInt("sdk_status"))
        assertEquals(1, r.getInt("decision"))
        assertTrue(r.getInt("sampling_accepted") >= 5)
        assertTrue(r.getInt("window_sampling_accepted") >= 5)
        assertTrue(r.getInt("distinct") >= 3)
        assertTrue(r.getLong("span_ms") >= 400)
        assertTrue(r.getBoolean("current_result_valid"))
        assertEquals(if (secured) 1 else 0, r.getInt("secured"))
        assertEquals(if (secured) 1 else 0, r.getInt("required_checks_complete"))
        assertEquals(0, r.getInt("product_eligible"))
        assertEquals(0, r.getInt("provenance"))
        assertEquals(0, r.getInt("manipulation"))
    }
    @Test fun validIncompleteIsNotComplete() {
        Native.load(InstrumentationRegistry.getInstrumentation().targetContext)
        val session = Native.open(); var capture = 0L; var result = 0L
        try {
            capture = Native.create(session); result = Native.finish(capture)
            val r = JSONObject(Native.read(capture, result))
            assertEquals(0, r.getInt("sdk_status")); assertEquals(2, r.getInt("decision"))
            assertTrue(r.getBoolean("current_result_valid")); assertEquals(0, r.getInt("secured"))
            assertEquals(0, r.getInt("required_checks_complete")); assertEquals(0, r.getInt("product_eligible"))
        } finally { Native.release(session, capture, result) }
    }
    @Test fun securedStagingRequiresAuthenticatedEnrollment() {
        org.junit.Assume.assumeTrue(BuildConfig.NEXUS_STAGING)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await(scenario) { it.contains("SDK loaded") }
            scenario.onActivity { it.labStartSecured() }
            val final = await(scenario, 30000) { it.contains("\"current_result_valid\": true") }
            assertComplete(final, true)
            assertTrue(final.contains("STAGING enrollment="))
            assertTrue(final.contains("Validated key level="))
            assertFalse(final.contains("PREVIEW"))
            record("secured_staging", final)
            // SDK monotonic expiry, not the app wall clock, decides when a held result is stale.
            val expired = await(scenario, 40000) { it.contains("\"current_result_valid\": false") }
            assertFalse(result(expired).getBoolean("current_result_valid"))
            assertNotEquals(0, result(expired).getInt("validation_status"))
            record("secured_staging_expiry", expired)
        }
    }
    @Test fun realCameraPortraitReversePortraitBackgroundCancelRestartAndFinalValidation() {
        assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,
            androidx.core.content.ContextCompat.checkSelfPermission(
                InstrumentationRegistry.getInstrumentation().targetContext, Manifest.permission.CAMERA))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await(scenario) { it.contains("SDK loaded") }
            for((orientation,rotation) in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to 90,ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT to 270)) {
                scenario.onActivity { it.requestedOrientation=orientation }
                // Wait for CameraX metadata from the new display context, not a fixed delay.
                var oriented=false;val orientationDeadline=SystemClock.elapsedRealtime()+10000
                while(!oriented && SystemClock.elapsedRealtime()<orientationDeadline) {
                    scenario.onActivity { oriented=it.labObservedRotation()==rotation }
                    if(!oriented) Thread.sleep(10)
                }
                assertTrue("CameraX must deliver expected portrait rotation",oriented)
                scenario.onActivity { it.labStart() }
                val measured=await(scenario) {
                    // Delivered frames do not imply SDK cadence/span completion.
                    it.contains("rotation=$rotation") && it.contains("\"current_result_valid\": true") &&
                        result(it).getInt("decision") == 1
                }
                assertTrue(measured.contains("rotation=$rotation"))
                scenario.onActivity { it.labFinish() }
                val final=await(scenario) { it.contains("\"current_result_valid\": true") }
                assertComplete(final, false)
                record("rotation_$rotation",final)
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                await(scenario) { it.contains("Previous result is no longer current") }
                scenario.onActivity { it.labStart() }
                await(scenario) { it.contains("delivered=") }
                scenario.onActivity { it.labCancel() }
                await(scenario) { it.contains("Cancelled") && it.contains("no longer current") }
            }
            // Observe actual submit activity before closing. Cancellation can race its return;
            // release remains queued behind the analyzer, never frees its direct buffer early.
            scenario.onActivity { it.labStart() }
            var observed=false;val until=SystemClock.elapsedRealtime()+8000
            while(!observed && SystemClock.elapsedRealtime()<until) {
                scenario.onActivity { observed=it.labSdkInFlight() }
                if(!observed) Thread.sleep(1)
            }
            assertTrue("Must observe native submit in flight",observed)
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            await(scenario) { it.contains("no longer current") }
            scenario.onActivity { it.labStart() }
            await(scenario) { it.contains("delivered=") }
            record("close_restart", "PASS: observed SDK submit in flight, background cleanup, fresh capture delivers frames")
        }
    }
}
