package app.cartogliss.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun repo(scope: CoroutineScope, builtIn: Map<KeyProvider, String> = emptyMap()): SettingsRepository {
        val file = tmp.newFile("settings.preferences_pb").also { it.delete() }
        return SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file }, builtIn)
    }

    @Test
    fun builtInKeys_usedUntilOverridden() = runTest(UnconfinedTestDispatcher()) {
        val r = repo(backgroundScope, mapOf(KeyProvider.MAPBOX to "built-in"))
        var s = r.settings.first()
        assertEquals("built-in", s.apiKeys[KeyProvider.MAPBOX])
        assertTrue(s.userKeys.isEmpty())
        r.setApiKey(KeyProvider.MAPBOX, "mine")
        s = r.settings.first()
        assertEquals("mine", s.apiKeys[KeyProvider.MAPBOX])
        r.setApiKey(KeyProvider.MAPBOX, "")
        assertEquals("built-in", r.settings.first().apiKeys[KeyProvider.MAPBOX])
    }

    @Test
    fun storageFolder_roundTrip() = runTest(UnconfinedTestDispatcher()) {
        val r = repo(backgroundScope)
        assertEquals(false, r.settings.first().storagePrompted)
        r.setStorageTree("content://tree/x")
        val s = r.settings.first()
        assertEquals("content://tree/x", s.storageTree)
        assertTrue(s.storagePrompted)
        r.setStorageTree(null)
        assertNull(r.settings.first().storageTree)
    }

    @Test
    fun defaults_matchDesign() = runTest(UnconfinedTestDispatcher()) {
        val s = repo(backgroundScope).settings.first()
        assertEquals(Viewport(0.0, 0.0, 2, 1, "osm-standard"), s.viewport)
        assertEquals(DistanceUnit.METRIC, s.distanceUnit)
        assertNull(s.target)
        assertTrue(s.apiKeys.isEmpty())
    }

    @Test
    fun roundTrip_allFields() = runTest(UnconfinedTestDispatcher()) {
        val r = repo(backgroundScope)
        r.setDistanceUnit(DistanceUnit.NAUTICAL)
        r.setSpeedUnit(SpeedUnit.KNOTS)
        r.setAngleFormat(AngleFormat.DMS)
        r.setApiKey(KeyProvider.AZURE, "  abc  ")
        r.setStarred("cyclosm", true)
        r.setStarred("opentopomap", false)
        r.setTrackSettings(TrackSettings(10, 20, 50))
        r.setTarget(Target(51.5, -0.12, "Home"))
        r.setViewport(Viewport(45.0, 6.0, 14, 4, "opentopomap"))

        val s = r.settings.first()
        assertEquals(DistanceUnit.NAUTICAL, s.distanceUnit)
        assertEquals(SpeedUnit.KNOTS, s.speedUnit)
        assertEquals(AngleFormat.DMS, s.angleFormat)
        assertEquals(mapOf(KeyProvider.AZURE to "abc"), s.apiKeys)
        assertEquals(setOf("osm-standard", "cyclosm"), s.starredSources)
        assertEquals(TrackSettings(10, 20, 50), s.track)
        assertEquals(Target(51.5, -0.12, "Home"), s.target)
        assertEquals(Viewport(45.0, 6.0, 14, 4, "opentopomap"), s.viewport)
    }

    @Test
    fun clearingValues() = runTest(UnconfinedTestDispatcher()) {
        val r = repo(backgroundScope)
        r.setApiKey(KeyProvider.MAPBOX, "k")
        r.setApiKey(KeyProvider.MAPBOX, " ")
        r.setTarget(Target(1.0, 2.0))
        r.setTarget(null)
        val s = r.settings.first()
        assertTrue(s.apiKeys.isEmpty())
        assertNull(s.target)
    }

    @Test
    fun invalidOverzoom_fallsBackToOne() = runTest(UnconfinedTestDispatcher()) {
        val r = repo(backgroundScope)
        r.setViewport(Viewport(overzoom = 3))
        assertEquals(1, r.settings.first().viewport.overzoom)
    }
}
