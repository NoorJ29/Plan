# Keep all app classes: widget action callbacks and receivers are created by name.
-keep class com.borealis.planner.** { *; }
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
