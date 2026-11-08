# Règles R8 spécifiques. Compose, DataStore et Coil fournissent leurs propres règles consommateur.

# commons-compress / junrar : dépendances optionnelles non embarquées (zstd, brotli, asm, slf4j…).
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**
-dontwarn org.osgi.**
-dontwarn org.slf4j.**
-dontwarn javax.crypto.**
-dontwarn java.awt.**
-dontwarn org.apache.commons.compress.harmony.**

# libVLC : classes appelées depuis le code natif.
-keep class org.videolan.libvlc.** { *; }
-dontwarn org.videolan.libvlc.**
