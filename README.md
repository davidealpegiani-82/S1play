# Sky UNO Player — progetto Android

Progetto sorgente Android Studio (Kotlin) per una WebView con:
- apertura della pagina iniziale configurata;
- blocco delle finestre popup create come nuove finestre;
- fullscreen HTML5;
- pulsante Ricarica;
- memorizzazione locale del player selezionato quando la pagina espone un elemento con etichetta esatta `Player 1` … `Player 7`.

## Importante
Questo progetto è un punto di partenza tecnico, non un APK già compilato né una verifica di compatibilità con il sito. Il riconoscimento dei sette pulsanti dipende dalla struttura HTML effettiva della pagina e potrebbe richiedere un adattamento. La WebView non rimuove gli annunci incorporati nella pagina o nel flusso e non aggira autenticazioni, DRM o altre protezioni. Usare soltanto sorgenti e contenuti per i quali si dispone dei diritti o dell'autorizzazione.

## Compilazione
1. Installare Android Studio e un JDK 17.
2. Aprire questa cartella in Android Studio.
3. Attendere la sincronizzazione Gradle e installare Android SDK 35 quando richiesto.
4. Selezionare **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. L'APK debug viene generato in `app/build/outputs/apk/debug/app-debug.apk`.

La build non è stata eseguita in questo ambiente: il file APK non è incluso.

## Compilazione automatica tramite GitHub Actions

È incluso il workflow `.github/workflows/android.yml`. Dopo il push, aprire la scheda **Actions**. Al termine, scaricare l'artefatto `SkyUnoPlayer-debug-apk` dalla sezione Artifacts dell'esecuzione.

GitHub Actions è soggetto alle quote e alle condizioni del proprio account/repository; non è garantito un utilizzo illimitato gratuito. Questa procedura produce un APK debug, non firmato per la distribuzione sul Play Store.
