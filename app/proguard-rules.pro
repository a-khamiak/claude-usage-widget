# No reflection in app code. WorkManager ships its own consumer rules.
# The app never logs; strip android.util.Log anyway in case one slips in.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
