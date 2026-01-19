package org.alkaline.taskbrain.util

import org.junit.Assert.*
import org.junit.Test

class PermissionHelperTest {

    // region AlarmPermissionStatus Tests

    @Test
    fun `canShowAlarms is true when notification permission granted and notifications enabled`() {
        val status = PermissionHelper.AlarmPermissionStatus(
            hasNotificationPermission = true,
            canScheduleExactAlarms = false,
            areNotificationsEnabled = true
        )

        assertTrue(status.canShowAlarms)
    }

    @Test
    fun `canShowAlarms is false when notification permission not granted`() {
        val status = PermissionHelper.AlarmPermissionStatus(
            hasNotificationPermission = false,
            canScheduleExactAlarms = true,
            areNotificationsEnabled = true
        )

        assertFalse(status.canShowAlarms)
    }

    @Test
    fun `canShowAlarms is false when notifications disabled in settings`() {
        val status = PermissionHelper.AlarmPermissionStatus(
            hasNotificationPermission = true,
            canScheduleExactAlarms = true,
            areNotificationsEnabled = false
        )

        assertFalse(status.canShowAlarms)
    }

    @Test
    fun `hasAllPermissions is true when all permissions granted`() {
        val status = PermissionHelper.AlarmPermissionStatus(
            hasNotificationPermission = true,
            canScheduleExactAlarms = true,
            areNotificationsEnabled = true
        )

        assertTrue(status.hasAllPermissions)
    }

    @Test
    fun `hasAllPermissions is false when notification permission missing`() {
        val status = PermissionHelper.AlarmPermissionStatus(
            hasNotificationPermission = false,
            canScheduleExactAlarms = true,
            areNotificationsEnabled = true
        )

        assertFalse(status.hasAllPermissions)
    }

    @Test
    fun `hasAllPermissions is false when exact alarm permission missing`() {
        val status = PermissionHelper.AlarmPermissionStatus(
            hasNotificationPermission = true,
            canScheduleExactAlarms = false,
            areNotificationsEnabled = true
        )

        assertFalse(status.hasAllPermissions)
    }

    @Test
    fun `hasAllPermissions is false when notifications disabled`() {
        val status = PermissionHelper.AlarmPermissionStatus(
            hasNotificationPermission = true,
            canScheduleExactAlarms = true,
            areNotificationsEnabled = false
        )

        assertFalse(status.hasAllPermissions)
    }

    @Test
    fun `hasAllPermissions is false when multiple permissions missing`() {
        val status = PermissionHelper.AlarmPermissionStatus(
            hasNotificationPermission = false,
            canScheduleExactAlarms = false,
            areNotificationsEnabled = false
        )

        assertFalse(status.hasAllPermissions)
        assertFalse(status.canShowAlarms)
    }

    // endregion
}
