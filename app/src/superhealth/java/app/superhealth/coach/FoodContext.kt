package app.superhealth.coach

import app.superhealth.nutrition.data.DayFlagEntity

/** Shared semantics for snapshots and on-demand tools. */
internal object FoodContext {
    const val RULES = "Food diary semantics: partial means explicitly marked incomplete by the user; totals are a lower bound, not actual daily intake. Fasted means an intentional fasting day. No entries means unknown intake, not zero eaten. Never ask to complete or compensate calories for a past day, an incomplete day or a fasting day. Never infer a deficit or judge adherence from those days. Today's log is still in progress. Planned meals are not consumed meals. Fresh app data overrides older chat messages."

    fun status(flag: DayFlagEntity?, hasEntries: Boolean, today: Boolean): String = when {
        flag?.partial == true -> "incomplete (user-marked; exclude from intake/adherence conclusions)"
        flag?.fasted == true -> "fasted (user-marked)"
        !hasEntries -> "not logged (unknown intake)"
        today -> "in progress today"
        else -> "logged (not marked incomplete)"
    }
}
