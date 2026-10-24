# streetviewer

Mapa comunitario de cámaras de vigilancia estatales, para Android.

- Mapa claro tipo Google Maps (OpenFreeMap, sin clave).
- Muestra las cámaras cargadas en OpenStreetMap: rojo = estatal, gris = privada o sin datos.
- Filtros: todas, estatales, lectoras de patentes.
- Buscador de lugares y botón de "mi ubicación".
- "Marcar punto": ubicás el pin, completás tipo, organismo y hacia dónde apunta,
  y se publica en OpenStreetMap con tu cuenta (gratis).

## Conseguir el APK

Cada cambio en `main` se compila solo en GitHub Actions y se publica en **Releases**.

## Configurar el inicio de sesión de OpenStreetMap

1. En openstreetmap.org → Mi cuenta → Aplicaciones OAuth 2 → Registrar nueva aplicación.
2. URI de redirección `camarasmap://oauth`, sin "confidencial", permisos `write_api` y `read_prefs`.
3. Copiá el Client ID en `app/src/main/java/com/cogito/camaras/Config.kt` (`OSM_CLIENT_ID`).

Sin Client ID, la app abre el editor web de OpenStreetMap para agregar cámaras.

## Datos

© colaboradores de OpenStreetMap, licencia ODbL. Marcá solo cámaras que viste en persona.
