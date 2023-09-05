# Custom serializable
-keepclassmembers class * implements java.io.Serializable {
  static final long serialVersionUID;
  java.lang.Object writeReplace();
  java.lang.Object readResolve();
  private static final java.io.ObjectStreamField[] serialPersistentFields;
  private <fields>;
  public <fields>;
}

# Gson uses generic type information stored in a class file when working with
# fields. Proguard removes such information by default, keep it.
-keepattributes Signature
# This is also needed for R8 in compat mode since multiple optimizations will
# remove the generic signature such as class merging and argument removal.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# EventBus
-keepattributes *Annotation*
-keepclassmembers class ** {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }
