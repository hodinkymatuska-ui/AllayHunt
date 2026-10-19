# Allay Hunt (Paper 1.21.x)

Minihra: v kazdom kole sa niekde na mape objavi Allay. Najdi ho a daj mu knihu "Gift for Allay".

## Build
Potrebujes Java 21 a Maven:

    mvn package

Jar bude v `target/AllayHunt.jar` -> hod ho do `plugins/` na Paper serveri.
Ak mas inu verziu Paperu, zmen `paper-api` verziu v `pom.xml`.

## Lobby z Bedrocku
Java nevie citat Bedrock svety priamo, preto:
1. Svet z Bedrocku prekonvertuj na Java Edition cez Chunker (chunker.app).
2. Vysledny priecinok daj do `plugins/AllayHunt/import/<meno>`.
3. Na serveri: `/ah importlobby <meno>`
4. Postav sa kam maju hraci spawnovat a daj `/ah setlobby`.

## Prikazy
- `/ah setlobby`, `/ah importlobby <folder>`, `/ah start`, `/ah stop`, `/ah reload` (op)
- `/lobby` - spat do lobby (len ak nie si v zapase)
- `/spectate [hrac]` - spectate (alebo kompas v lobby)

## Nastavenia
Vsetko v `plugins/AllayHunt/config.yml` (dlzka kola, min. hracov, mapa, napovedy, XP...).
