package app.localhealth.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import nodomain.freeyourgadget.gadgetbridge.R

/** How a metric is summarised over a period and drawn in its detail chart. */
enum class Aggregation { SUM, AVERAGE, LAST }

enum class ChartKind { BARS, LINE, DOTS }

/** Google Health colour family a metric is drawn with (see ExtendedColorScheme). */
enum class Hue { BLUE, BLUE_MARINE, GOLDENROD, GREEN, LILAC, MAGENTA, TANGERINE, TEAL, NEUTRAL }

/** Google Health "Stats importantes" categories. */
enum class Category(@StringRes val label: Int, @DrawableRes val icon: Int) {
    HEART(R.string.lh_cat_heart, R.drawable.gs_ecg_heart_fill1_vd_theme_24),
    METABOLISM(R.string.lh_cat_metabolism, R.drawable.gs_accessibility_new_vd_theme_24),
    RESPIRATION(R.string.lh_cat_respiration, R.drawable.gs_pulmonology_fill1_vd_theme_24),
    TEMPERATURE(R.string.lh_cat_temperature, R.drawable.gs_thermometer_vd_theme_24),
    FITNESS(R.string.lh_cat_fitness, R.drawable.gs_directions_run_vd_theme_24),
    SLEEP(R.string.lh_cat_sleep, R.drawable.gs_bedtime_vd_theme_24),
    NUTRITION(R.string.lh_cat_nutrition, R.drawable.gs_nutrition_fill1_vd_theme_24),
    MIND(R.string.lh_cat_mind, R.drawable.gs_mindfulness_fill1_vd_theme_24),
    VITALS(R.string.lh_cat_vitals, R.drawable.gs_vital_signs_vd_theme_24),
}

enum class Metric(
    @StringRes val label: Int,
    @StringRes val unit: Int?,
    @DrawableRes val icon: Int,
    val hue: Hue,
    val aggregation: Aggregation,
    val chart: ChartKind,
    val category: Category,
    /** Fraction digits when displayed. */
    val digits: Int = 0,
    /** Logged by hand on the phone (stored in [ManualLogStore]) rather than by a device. */
    val manual: Boolean = false,
    @StringRes val about: Int? = null,
) {
    STEPS(R.string.lh_steps, null, R.drawable.gs_steps_fill1_vd_24, Hue.TEAL, Aggregation.SUM, ChartKind.BARS, Category.FITNESS, about = R.string.lh_about_steps),
    DISTANCE(R.string.lh_distance, R.string.lh_unit_km, R.drawable.gs_distance_vd_theme_24, Hue.TEAL, Aggregation.SUM, ChartKind.BARS, Category.FITNESS, digits = 2),
    CALORIES(R.string.lh_calories_burned, R.string.lh_unit_cal, R.drawable.gs_fitbit_calories_vd_theme_24, Hue.TEAL, Aggregation.SUM, ChartKind.BARS, Category.METABOLISM, about = R.string.lh_about_calories),
    ACTIVE_MINUTES(R.string.lh_active_minutes, R.string.lh_unit_min, R.drawable.gs_fitbit_hourly_activity_vd_theme_24, Hue.TEAL, Aggregation.SUM, ChartKind.BARS, Category.FITNESS),
    CARDIO_WEEK(R.string.lh_cardio_week, R.string.lh_unit_min, R.drawable.gs_cardio_load_fill1_vd_theme_24, Hue.BLUE, Aggregation.LAST, ChartKind.BARS, Category.FITNESS, about = R.string.lh_about_cardio),
    HEART_RATE(R.string.lh_heart_rate, R.string.lh_unit_bpm, R.drawable.gs_fitbit_heart_rate_fill1_vd_theme_24, Hue.MAGENTA, Aggregation.AVERAGE, ChartKind.LINE, Category.HEART),
    RESTING_HR(R.string.lh_resting_hr, R.string.lh_unit_bpm, R.drawable.gs_hr_resting_vd_theme_24, Hue.MAGENTA, Aggregation.AVERAGE, ChartKind.DOTS, Category.HEART, about = R.string.lh_about_resting_hr),
    HRV(R.string.lh_hrv, R.string.lh_unit_ms, R.drawable.gs_monitor_heart_vd_theme_24, Hue.MAGENTA, Aggregation.AVERAGE, ChartKind.DOTS, Category.HEART, about = R.string.lh_about_hrv),
    SLEEP(R.string.lh_sleep, null, R.drawable.gs_bedtime_fill1_vd_theme_24, Hue.LILAC, Aggregation.AVERAGE, ChartKind.BARS, Category.SLEEP),
    SLEEP_SCORE(R.string.lh_sleep_score, null, R.drawable.gs_sleep_score_vd_theme_24, Hue.LILAC, Aggregation.AVERAGE, ChartKind.DOTS, Category.SLEEP),
    READINESS(R.string.lh_readiness, null, R.drawable.gs_fitbit_readiness_vd_theme_24, Hue.NEUTRAL, Aggregation.LAST, ChartKind.DOTS, Category.FITNESS, about = R.string.lh_about_readiness),
    BODY_ENERGY(R.string.lh_body_energy, null, R.drawable.gs_bolt_vd_theme_24, Hue.GREEN, Aggregation.LAST, ChartKind.LINE, Category.MIND),
    STRESS(R.string.lh_stress, null, R.drawable.gs_stress_management_vd_theme_24, Hue.GOLDENROD, Aggregation.AVERAGE, ChartKind.BARS, Category.MIND),
    SPO2(R.string.lh_spo2, R.string.lh_unit_percent, R.drawable.gs_spo2_vd_theme_24, Hue.BLUE, Aggregation.AVERAGE, ChartKind.DOTS, Category.RESPIRATION),
    RESPIRATORY_RATE(R.string.lh_respiratory_rate, R.string.lh_unit_brpm, R.drawable.gs_respiratory_rate_vd_theme_24, Hue.BLUE, Aggregation.AVERAGE, ChartKind.DOTS, Category.RESPIRATION, digits = 1),
    VO2MAX(R.string.lh_vo2max, null, R.drawable.gs_vo2_max_vd_theme_24, Hue.TANGERINE, Aggregation.LAST, ChartKind.DOTS, Category.FITNESS, digits = 1),
    TEMPERATURE(R.string.lh_temperature, R.string.lh_unit_celsius, R.drawable.gs_thermometer_vd_theme_24, Hue.TANGERINE, Aggregation.AVERAGE, ChartKind.DOTS, Category.TEMPERATURE, digits = 1),
    WEIGHT(R.string.lh_weight, R.string.lh_unit_kg, R.drawable.gs_monitor_weight_vd_theme_24, Hue.BLUE, Aggregation.LAST, ChartKind.DOTS, Category.METABOLISM, digits = 1, manual = true, about = R.string.lh_about_weight),
    BODY_FAT(R.string.lh_body_fat, R.string.lh_unit_percent, R.drawable.gs_body_fat_vd_theme_24, Hue.BLUE, Aggregation.LAST, ChartKind.DOTS, Category.METABOLISM, digits = 1, manual = true),
    HYDRATION(R.string.lh_hydration, R.string.lh_unit_ml, R.drawable.gs_water_full_fill1_vd_theme_24, Hue.BLUE, Aggregation.SUM, ChartKind.BARS, Category.NUTRITION, manual = true),
    GLUCOSE(R.string.lh_glucose, R.string.lh_unit_mgdl, R.drawable.gs_glucose_vd_theme_24, Hue.MAGENTA, Aggregation.AVERAGE, ChartKind.DOTS, Category.VITALS, manual = true),
    BODY_TEMPERATURE(R.string.lh_body_temperature, R.string.lh_unit_celsius, R.drawable.gs_thermometer_gain_vd_theme_24, Hue.TANGERINE, Aggregation.AVERAGE, ChartKind.DOTS, Category.TEMPERATURE, digits = 1, manual = true),
}

/** Metrics offered in the "Enregistrer manuellement" sheet, in Google Health's order. */
val LOGGABLE_METRICS = listOf(Metric.GLUCOSE, Metric.HYDRATION, Metric.BODY_FAT, Metric.WEIGHT, Metric.BODY_TEMPERATURE)
