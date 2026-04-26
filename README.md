# Hola Backup (Android)

App Android (Kotlin + Compose) para respaldo de:
- SMS
- Registro de llamadas
- Archivos multimedia (foto/video/audio)
- Archivos generales desde carpeta elegida (modo tipo gestor de archivos, usando SAF)

Destino: Telegram Bot API (`sendDocument`).

## Límite de 50MB

Telegram Bot API limita archivos enviados por `sendDocument` a 50 MB.
Esta app divide automáticamente el `.zip` en partes de 49 MB y envía todas las partes al chat.

## Estructura principal

- `app/src/main/java/com/hola/backup/MainActivity.kt`: UI y ejecución manual.
- `app/src/main/java/com/hola/backup/work/ScheduledBackupWorker.kt`: backup automático con WorkManager.
- `app/src/main/java/com/hola/backup/domain/BackupOrchestrator.kt`: orquestación de backup.
- `app/src/main/java/com/hola/backup/data/TelegramUploader.kt`: subida a Telegram + particionado.

## Notas importantes

- Para publicar en Google Play, los permisos de SMS/Call Log están restringidos por política y requieren justificación/aprobación.
- El backup actual genera JSON + ZIP y sube las partes al bot.
- Para detección avanzada de archivos, selecciona una carpeta con el botón `Elegir carpeta para detección avanzada`.
- Restauración no implementada aún en este MVP.

## Ejecutar

1. Abrir carpeta `/root/Hola` en Android Studio.
2. Sincronizar Gradle.
3. Ejecutar en dispositivo Android real.
4. Configurar `bot token` y `chat_id` dentro de la app.
5. Otorgar permisos cuando se soliciten.

## Build + Install automático con Shizuku

Script incluido:
- `/root/Hola/scripts/auto_build_install_shizuku.sh`

Qué hace:
1. Dispara el workflow de GitHub Actions (`android-debug-apk.yml`).
2. Espera a que termine el build.
3. Descarga el artifact con `app-debug.apk`.
4. Instala el APK usando Shizuku (`pm install -r`).

Uso:

```bash
cd /root/Hola
export GITHUB_TOKEN="TU_TOKEN_GITHUB"
export GITHUB_REPO="tu_usuario/tu_repo"
export GITHUB_BRANCH="main"
bash /root/Hola/scripts/auto_build_install_shizuku.sh
```

Requisitos:
- Shizuku activo en el dispositivo.
- Repositorio ya subido a GitHub con el workflow en `.github/workflows/android-debug-apk.yml`.
- Token de GitHub con permisos para Actions y Artifacts.
