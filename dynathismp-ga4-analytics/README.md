# DynathiGA4Analytics

Paper plugin voor DynathiSMP waarmee je Minecraft statistieken naar je Firebase Analytics en Google Analytics app kunt sturen.

Ingesteld voor DynathiSMP.mcsh.io en Maven.

## Wat kan deze plugin?

Standaard meet hij:

- mc_server_start
- mc_server_stop
- mc_player_join
- mc_player_quit
- mc_player_death
- mc_online_count elke 5 minuten

Optioneel, standaard uit:

- mc_player_command, alleen de command naam
- mc_player_chat, alleen berichtlengte

## Data per event

Bij elk event stuurt hij mee:

```text
firebase_project: DynathiSMP
analytics_source: firebase_ga4
server_id: dynathismp
server_name: DynathiSMP
server_host: DynathiSMP.mcsh.io
server_platform: mcsh
minecraft_version
online_players
max_players
```

Bij speler-events ook:

```text
player_id: gehashte UUID
game_mode
client_type: java of bedrock
world
```

Standaard stuurt hij geen IP, geen chattekst en geen spelernaam.

## Bouwen met Maven

Gebruik Java 21.

```bash
mvn clean package
```

De jar komt daarna hier:

```text
target/DynathiGA4Analytics-1.0.0.jar
```

Zet deze jar in je servermap:

```text
plugins/DynathiGA4Analytics-1.0.0.jar
```

Daarna server starten en de config invullen in:

```text
plugins/DynathiGA4Analytics/config.yml
```

## Firebase / Google Analytics instellen

1. Open Firebase Console.
2. Open jouw DynathiSMP project.
3. Ga naar Analytics.
4. Open de gekoppelde Google Analytics property.
5. Ga naar Admin.
6. Ga naar Data Streams.
7. Kies of maak een Web stream.
8. Kopieer je Measurement ID, bijvoorbeeld G-XXXXXXXXXX.
9. Maak een Measurement Protocol sleutel aan.
10. Vul deze lokaal in de plugin config in.
11. Zet enabled op true.
12. Herstart de server of gebruik /dynathiga reload.

## Command

```mcfunction
/dynathiga reload
```

Permission:

```text
dynathiga.admin
```
