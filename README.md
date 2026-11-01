# Tetra-Escape

Pusselspel för Android: välta en genomskinlig tetraeder över ett bräde av trekanter och ta dig till målet innan steg och tid tar slut.

## Spelregler

- Pjäsen glider inte, den välts över en kant till en av de tre grannrutorna. Symbolen som ligger nedåt ändras därför vid varje drag.
- Blå rutor ger extra steg, men bara om rätt symbol ligger nedåt. Utan bonusstegen räcker inte stegen till målet.
- Lila rutor vrider pjäsen ett tredjedels varv.
- Guldrutan är målet. Visar den en symbol måste du komma dit med den symbolen nedåt.
- Tiden startar vid första draget.

Banorna genereras slumpmässigt ur ett bananummer och kontrolleras av en inbyggd lösare: varje bana går att klara, bonusstegen behövs, och svårighetsgraden mäts i antal drag.

## Bygga

Projektet byggs med Gradle och Android SDK. Ingen Android Studio behövs.

    ./gradlew assembleDebug

GitHub Actions bygger en test-APK vid varje push till `main` och publicerar den som utgåvan `0.1.0-test.<byggnummer>` (äldre testutgåvor tas bort). Senaste bygget finns alltid här:

    https://github.com/skitknas-ctrl/Tetra-Escape/releases/latest/download/tetra-escape-debug.apk

Med Obtainium: lägg till `https://github.com/skitknas-ctrl/Tetra-Escape`, inga extra inställningar behövs.

Testbyggen signeras med nyckeln i `app/debug.keystore` (endast för test) så att de kan installeras ovanpå varandra. Riktiga utgåvor (taggar `v*`) signeras med en nyckel som lämnas via GitHub Secrets: `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.

## Struktur

- `app/src/main/kotlin/.../engine` – spelregler, lösare och bangenerator (ren Kotlin, inga Android-beroenden)
- `app/src/main/kotlin/.../ui` – ritning av bräde och pjäs, animationer, pekhantering
- `app/src/test` – enhetstester för geometri, regler och generator
- `metadata/` – beskrivningar för F-Droid (fastlane-struktur)

## Licens

GPL-3.0-or-later. Se `LICENSE`.
