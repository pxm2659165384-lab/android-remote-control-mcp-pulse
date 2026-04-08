package com.danielealbano.androidremotecontrolmcp.services.channel.geofence

import android.content.Context
import com.danielealbano.androidremotecontrolmcp.data.model.GeofenceZone
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.LocationServices
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("GeofenceManagerImpl")
class GeofenceManagerImplTest {
    private val context = mockk<Context>(relaxed = true)
    private val geofencingClient = mockk<GeofencingClient>(relaxed = true)

    @BeforeEach
    fun setup() {
        mockkStatic(LocationServices::class)
        every { LocationServices.getGeofencingClient(context) } returns geofencingClient
    }

    @AfterEach
    fun teardown() {
        unmockkStatic(LocationServices::class)
    }

    private val enterOnlyZone =
        GeofenceZone(
            id = "z1",
            name = "Office",
            latitude = 40.7128,
            longitude = -74.006,
            radiusMeters = 200f,
            notifyOnEnter = true,
            notifyOnExit = false,
        )

    private val exitOnlyZone =
        GeofenceZone(
            id = "z2",
            name = "Home",
            latitude = 51.5074,
            longitude = -0.1278,
            radiusMeters = 300f,
            notifyOnEnter = false,
            notifyOnExit = true,
        )

    @Nested
    @DisplayName("transition types")
    inner class TransitionTypes {
        @Test
        fun `enter-only zone sets ENTER transition only`() {
            val transitionTypes =
                (if (enterOnlyZone.notifyOnEnter) Geofence.GEOFENCE_TRANSITION_ENTER else 0) or
                    (if (enterOnlyZone.notifyOnExit) Geofence.GEOFENCE_TRANSITION_EXIT else 0)
            assertEquals(Geofence.GEOFENCE_TRANSITION_ENTER, transitionTypes)
        }

        @Test
        fun `exit-only zone sets EXIT transition only`() {
            val transitionTypes =
                (if (exitOnlyZone.notifyOnEnter) Geofence.GEOFENCE_TRANSITION_ENTER else 0) or
                    (if (exitOnlyZone.notifyOnExit) Geofence.GEOFENCE_TRANSITION_EXIT else 0)
            assertEquals(Geofence.GEOFENCE_TRANSITION_EXIT, transitionTypes)
        }

        @Test
        fun `both enter and exit sets both transitions`() {
            val zone = enterOnlyZone.copy(notifyOnExit = true)
            val transitionTypes =
                (if (zone.notifyOnEnter) Geofence.GEOFENCE_TRANSITION_ENTER else 0) or
                    (if (zone.notifyOnExit) Geofence.GEOFENCE_TRANSITION_EXIT else 0)
            assertEquals(
                Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT,
                transitionTypes,
            )
        }
    }

    @Nested
    @DisplayName("zone parameters")
    inner class ZoneParameters {
        @Test
        fun `zone latitude and longitude are correct`() {
            assertEquals(40.7128, enterOnlyZone.latitude)
            assertEquals(-74.006, enterOnlyZone.longitude)
        }

        @Test
        fun `zone radius is correct`() {
            assertEquals(200f, enterOnlyZone.radiusMeters)
        }

        @Test
        fun `zone request ID matches zone ID`() {
            assertEquals("z1", enterOnlyZone.id)
        }
    }
}
