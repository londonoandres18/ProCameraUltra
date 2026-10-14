# ProCamera Ultra — POCO F8 Ultra

Aplicación nativa Android centrada en fotografía y captura de video. Usa CameraX y las funciones de cámara que el controlador de Android/HyperOS expone al dispositivo. No contiene el código interno ni los modelos propietarios de Google Camera (GCam).

## Funciones implementadas

- Vista previa en vivo con CameraX y captura fotográfica configurada para priorizar calidad, con JPEG al 100 %.
- Salida Ultra HDR JPEG cuando Android 14 o posterior y la cámara seleccionada anuncian compatibilidad.
- Resoluciones fotográficas seleccionables: máxima disponible, aproximadamente 12 MP, 8 MP y 4 MP. La resolución real depende de los tamaños que CameraX pueda negociar con el sensor y la sesión.
- Modos Noche y HDR del fabricante mediante CameraX Extensions, únicamente cuando el controlador los anuncia como disponibles. Si no existen, la app no simula falsamente una mejora computacional.
- Enfoque y medición de exposición al tocar la vista previa.
- Zoom por pellizco y accesos rápidos de zoom limitados al rango comunicado por CameraX.
- Compensación de exposición EV, flash apagado/automático/encendido y luz continua cuando hay flash disponible.
- Grabación de video MP4 con audio y perfiles de 4K, 1080p y 720p; si el perfil solicitado no está disponible, CameraX puede aplicar la alternativa admitida por la cámara.
- Cambio entre cámara frontal y trasera.
- Miniatura de la captura más reciente y galería dentro de la app para revisar fotografías y videos guardados por ProCamera Ultra.
- Guardado mediante MediaStore en `Pictures/ProCamera Ultra` y `Movies/ProCamera Ultra`.
- Construcción automática del APK debug mediante GitHub Actions.

## Comparación con GCam: limitaciones conocidas

Esta aplicación **no es una implementación equivalente a GCam HDR+ o Night Sight**. GCam emplea una canalización propietaria de fotografía computacional, procesamiento multiframe y modelos internos que no se pueden importar simplemente desde su APK o archivo de configuración. ProCamera Ultra usa APIs públicas de Android/CameraX y delega los modos Noche/HDR al fabricante cuando están expuestos.

Todavía no incluye:
- captura DNG/RAW;
- controles manuales independientes de ISO, tiempo de exposición y enfoque;
- un algoritmo propio de fusión multiframe alineada comparable a HDR+ o Night Sight;
- acceso garantizado a los sensores ultra gran angular y telefoto como cámaras independientes;
- grabación 8K, selección independiente de 30/60 fps, cámara lenta o estabilización avanzada;
- comparación fotográfica objetiva ni validación física en un POCO F8 Ultra.

La opción **Máxima** solicita la resolución más alta que la sesión de CameraX pueda usar; no garantiza una foto de 50 MP en todas las combinaciones de cámara, relación de aspecto y modo. Del mismo modo, la disponibilidad real de Noche, HDR, Ultra HDR y los perfiles de video depende del modelo, el controlador y la versión de HyperOS.

## Instalar una compilación de prueba

1. Descarga el APK debug de la conversación o desde los artefactos de la ejecución más reciente de [GitHub Actions](https://github.com/londonoandres18/ProCameraUltra/actions).
2. Copia el archivo al teléfono, ábrelo y autoriza la instalación desde la fuente indicada por Android.
3. Abre ProCamera Ultra y concede el permiso de cámara. Al iniciar una grabación, concede también permiso de micrófono.
4. Para usar video, selecciona primero el modo **Video**, después 4K/1080p/720p y pulsa **REC**. Pulsa **STOP** para cerrar el archivo y guardarlo.

El APK debug se firma con una clave de depuración y sirve para pruebas, no como versión de distribución de Play Store.

## Privacidad

Las fotos y los videos se guardan localmente mediante MediaStore. La aplicación no implementa subida de archivos ni envío de imágenes a servidores externos. El código fuente está disponible en este repositorio.
