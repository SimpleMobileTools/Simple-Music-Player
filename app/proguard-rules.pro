# Custom serializable
-keepclassmembers class * implements java.io.Serializable {
  static final long serialVersionUID;
  java.lang.Object writeReplace();
  java.lang.Object readResolve();
  private static final java.io.ObjectStreamField[] serialPersistentFields;
  private <fields>;
  public <fields>;
}

# Otto
-keepattributes *Annotation*
-keepclassmembers class ** {
    @com.squareup.otto.Subscribe public *;
    @com.squareup.otto.Produce public *;
}

# Picasso
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn okhttp3.internal.platform.ConscryptPlatform
