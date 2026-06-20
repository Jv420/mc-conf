# DynathiGA4Analytics

Paper plugin voor DynathiSMP waarmee je server-statistieken naar Google Analytics 4 kunt sturen via GA4 Measurement Protocol.

## Events

Standaard meet de plugin:

- server_start
- server_stop
- player_join
- player_quit
- player_death
- online_count elke 5 minuten

De plugin stuurt standaard geen speler-IP, geen chattekst en geen spelernaam. De speler UUID wordt standaard gehasht.

## GA4 instellen

1. Open Google Analytics 4.
2. Ga naar Admin.
3. Ga naar Data Streams.
4. Kies je Web stream.
5. Kopieer je Measurement ID, bijvoorbeeld G-XXXXXXXXXX.
6. Maak bij Measurement Protocol een API secret aan.
7. Zet deze waarden lokaal in plugins/DynathiGA4Analytics/config.yml.
8. Zet enabled op true.
9. Herstart de server of gebruik /dynathiga reload.

Voorbeeld config lokaal op je server:

```yml
enabled: true
measurement-id: "G-XXXXXXXXXX"
api-secret: "vul-hier-je-eigen-waarde-in"
```

## Bouwen

Gebruik Java 21.

Linux/macOS:

```bash
./gradlew build
```

Windows:

```bat
gradlew.bat build
```

De jar komt in build/libs/ en kan daarna in je plugins map.

## Privacy advies

Voor Nederland/EU:

- laat use-eu-endpoint op true staan
- laat send-player-name op false staan
- laat hash-player-uuid op true staan
- zet je echte API secret niet openbaar in GitHub

## Command

```mcfunction
/dynathiga reload
```

Permission:

```text
dynathiga.admin
```
