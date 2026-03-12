# dnsjava includes optional desktop-specific providers (JNA/JNDI/SLF4J SPI).
# They are not needed on Android runtime, so ignore those missing classes.
-dontwarn com.sun.jna.Library
-dontwarn com.sun.jna.Memory
-dontwarn com.sun.jna.Native
-dontwarn com.sun.jna.Pointer
-dontwarn com.sun.jna.Structure$ByReference
-dontwarn com.sun.jna.Structure$FieldOrder
-dontwarn com.sun.jna.Structure
-dontwarn com.sun.jna.WString
-dontwarn com.sun.jna.platform.win32.Win32Exception
-dontwarn com.sun.jna.ptr.IntByReference
-dontwarn com.sun.jna.win32.W32APIOptions
-dontwarn javax.naming.NamingException
-dontwarn javax.naming.directory.DirContext
-dontwarn javax.naming.directory.InitialDirContext
-dontwarn lombok.Generated
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn org.xbill.DNS.spi.DnsjavaInetAddressResolverProvider
-dontwarn sun.net.spi.nameservice.NameServiceDescriptor

# JNI bridge symbols are looked up by class/method name from native code.
-keep class com.masterdnsvpn.android.HevNativeBridge { *; }
