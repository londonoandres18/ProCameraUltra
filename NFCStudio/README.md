# NFC Studio — Android

Aplicación Android Kotlin, sin root.

## Funciones
- Detección de tecnologías NFC que expone Android.
- Lectura de metadatos y datos NDEF accesibles.
- Escritura de texto o URL en etiquetas NDEF compatibles.
- Diagnóstico de capacidad, solo lectura y errores de acceso.
- Historial local.
- Perfil HCE de demostración con AID propio F0010203040506.

## Límites
No copia identificadores UID ni clona credenciales protegidas. No intenta adivinar claves, leer sectores protegidos ni eludir autenticación. HCE responde solo al AID de demostración. El teléfono debe tener NFC; HCE depende del soporte del dispositivo.

## Compilar
Abre esta carpeta en Android Studio con JDK 17 y Android SDK 35. También se compila con Gradle 8.13 mediante `gradle --no-daemon clean assembleDebug`. El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.
