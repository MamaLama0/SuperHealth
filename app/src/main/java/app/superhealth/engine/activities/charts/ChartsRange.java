package app.superhealth.engine.activities.charts;

public enum ChartsRange {
    WEEK(7),
    MONTH(30),
    ;

    private final int days;

    ChartsRange(final int days) {
        this.days = days;
    }

    public int getDays() {
        return days;
    }
}
