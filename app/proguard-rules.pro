# Release R8 rules for dev.openbuds.app.
# Debug-level logs must not survive into release builds.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}
