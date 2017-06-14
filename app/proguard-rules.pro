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

-renamesourcefileattribute SourceFile
-keepattributes SourceFile, LineNumberTable
