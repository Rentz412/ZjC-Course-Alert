package com.rentz.zjkb

import com.rentz.zjkb.domain.oobe.OobeFlow
import com.rentz.zjkb.domain.oobe.OobeStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** OOBE 步骤机（华珠版：学校写死，无地址步）。 */
class OobeFlowTest {

    @Test fun step_order() {
        assertEquals(
            listOf(OobeStep.Login, OobeStep.Permissions, OobeStep.Reminders, OobeStep.Done),
            OobeFlow.steps,
        )
    }

    @Test fun next_walks_through_all_steps() {
        var s = OobeStep.Login
        s = OobeFlow.next(s)!!
        assertEquals(OobeStep.Permissions, s)
        s = OobeFlow.next(s)!!
        assertEquals(OobeStep.Reminders, s)
        s = OobeFlow.next(s)!!
        assertEquals(OobeStep.Done, s)
        assertNull(OobeFlow.next(s))
    }

    @Test fun previous_from_login_is_null() {
        assertNull(OobeFlow.previous(OobeStep.Login))
        assertEquals(OobeStep.Login, OobeFlow.previous(OobeStep.Permissions))
        assertEquals(OobeStep.Permissions, OobeFlow.previous(OobeStep.Reminders))
    }

    @Test fun start_step_login_when_no_credentials() {
        assertEquals(OobeStep.Login, OobeFlow.startStep(hasCredentials = false))
    }

    @Test fun start_step_permissions_when_logged_in() {
        assertEquals(OobeStep.Permissions, OobeFlow.startStep(hasCredentials = true))
    }

    @Test fun progress_of_done_is_null() {
        assertNull(OobeFlow.progressOf(OobeStep.Done))
        assertEquals(1 to 3, OobeFlow.progressOf(OobeStep.Login))
        assertEquals(3 to 3, OobeFlow.progressOf(OobeStep.Reminders))
    }
}
