# ProCamera Ultra — POCO F8 Ultra

Prototipo nativo para Android. Está diseñado para probar captura, guardado y procesamiento fotográfico en el POCO F8 Ultra; todavía no es un port de Google Camera ni una implementación equivalente a Google Pixel Camera.

## Funciones implementadas en esta versión

- Visor en vivo con CameraX.
- Captura JPEG convencional y guardado en Galería, en `Pictures/ProCamera Ultra`.
- Grabación MP4 con audio, cuando la cámara admite la combinación de casos de uso y se concede permiso de micrófono. Los videos se guardan en `Movies/ProCamera Ultra`.
- Cambio entre cámara frontal y trasera.
- Controles de zoom limitados al rango que Android CameraX expone.
- Control de compensación de exposición.
- Modo Noche experimental: captura tres JPEG y promedia sus píxeles para reducir ruido en escenas estáticas.
- Modo HDR experimental: intenta variar la compensación de exposición y combina las capturas mediante fusión por píxeles.
- Compilación automática del APK de depuración mediante GitHub Actions.

## Limitaciones conocidas

Los modos Noche/HDR son experimentales y **no equivalen** a HDR+, Night Sight ni Super Res Zoom de Google Pixel. La primera versión fusiona JPEG procesados por el ISP del teléfono, no datos RAW. Su alineación de movimiento es limitada; no resuelve parallax, movimiento local ni sujetos móviles, por lo que puede generar dobles contornos. En HDR, una foto JPEG ya recortada no recupera información perdida en luces o sombras.

Esta versión todavía no tiene:
- captura DNG/RAW guardada correctamente;
- controles manuales independientes de ISO y tiempo de exposición;
- acceso garantizado a los tres sensores traseros como cámaras independientes;
- grabación LOG real, procesamiento temporal dedicado de video o estabilización de tipo gimbal;
- validación física en un POCO F8 Ultra ni medidas comparativas de calidad.

Las resoluciones y frecuencias efectivas dependen de los perfiles expuestos por la cámara, HyperOS, CameraX y el codificador. No se deben inferir a partir de las especificaciones comerciales del teléfono.

## Compilar la APK sin instalar Android Studio

1. Abre la pestaña [Actions](https://github.com/londonoandres18/ProCameraUltra/actions).
2. Selecciona **Build ProCamera Ultra APK**. El flujo también se ejecuta automáticamente después de cada cambio en `main`.
3. Abre la ejecución más reciente. Cuando termine con estado **Success**, busca **Artifacts**.
4. Descarga `ProCameraUltra-debug-APK` y extrae `app-debug.apk`.
5. Pasa el APK al teléfono, ábrelo y autoriza la instalación desde el navegador o gestor de archivos cuando Android lo solicite.
6. Abre ProCamera Ultra y concede el permiso de cámara. Al comenzar una grabación, concede también el permiso de micrófono.

Si la ejecución falla, abre el registro del paso **Compile debug APK**. El flujo intenta adjuntar diagnósticos de Gradle cuando existen.

El APK debug se firma con una clave temporal de depuración de Android; sirve para pruebas, no como versión de distribución para Play Store.

## Privacidad

Las fotografías y videos se guardan en MediaStore. La aplicación no incorpora servicios de subida ni envía imágenes a servidores externos. El código fuente se encuentra en este repositorio.
