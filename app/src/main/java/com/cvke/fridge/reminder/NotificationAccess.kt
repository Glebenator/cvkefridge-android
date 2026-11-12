package com.cvke.fridge.reminder

/** If Android shows the reminders of the app, and what the person can do when it does not. */
enum class NotificationAccess {
    /** Android shows the reminders. */
    ALLOWED,

    /** Android 13 or later: the app did not ask for the notification permission yet. The app explains it, then asks. */
    ASK,

    /** Android 13 or later: the person did not allow notifications, and Android can ask again. */
    DENIED,

    /**
     * The notifications of the app are off, and only the settings of Android can turn them on: the person did not
     * allow them two times, or turned them off in the settings.
     */
    BLOCKED,

    /** The notifications of the app are on, but the person turned off the notification category of the reminders. */
    CHANNEL_OFF;

    companion object {
        /** The first Android version with the notification permission (Android 13). */
        const val PERMISSION_SDK = 33

        /**
         * The access from what Android says.
         *
         * @param sdk The version of Android.
         * @param granted True when the app has the notification permission. It is always true before Android 13.
         * @param appEnabled True when the notifications of the app are on (`areNotificationsEnabled`).
         * @param channelOff True when the notification category of the reminders exists and is off.
         * @param mayAskAgain True when Android can show its question again (`shouldShowRequestPermissionRationale`).
         * @param asked True when the app asked for the permission before.
         */
        fun of(sdk: Int, granted: Boolean, appEnabled: Boolean, channelOff: Boolean, mayAskAgain: Boolean, asked: Boolean): NotificationAccess =
            when {
                sdk >= PERMISSION_SDK && !granted -> when {
                    mayAskAgain -> DENIED
                    // Android also gives "do not show the question" for a permission that the app never asked for.
                    asked -> BLOCKED
                    else -> ASK
                }
                !appEnabled -> BLOCKED
                channelOff -> CHANNEL_OFF
                else -> ALLOWED
            }
    }
}
