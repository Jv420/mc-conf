# DynathiSMP Analytics Event Systeem

Deze plugin stuurt events naar Firebase Analytics / Google Analytics 4.

## Basis build

```bash
mvn clean package
```

Jar:

```text
target/DynathiGA4Analytics-1.0.0.jar
```

## Config

Zet lokaal op je server in `plugins/DynathiGA4Analytics/config.yml`:

```yml
enabled: true
measurement-id: "G-XXXXXXXXXX"
api-secret: "jouw-measurement-protocol-sleutel"
```

Webshop staat ingesteld op:

```text
https://dynathismp-snowy.vercel.app/
```

## Commands

### Reload

```mcfunction
/dynathiga reload
```

### Verkoop meten

Gebruik dit na een geslaagde betaling via je RCON worker:

```mcfunction
/dynathisale <player> <product_id> <price> <currency> [order_id]
```

Voorbeelden:

```mcfunction
/dynathisale Steve vip_rank 4.99 EUR stripe_1001
/dynathisale Steve legend_rank 14.99 EUR stripe_1002
/dynathisale Steve prime_keys_10 7.50 EUR stripe_1003
```

Dit stuurt `mc_webshop_sale` naar Firebase/GA4.

## Algemene event command

```mcfunction
/dynathievent <event_name> [player] [key=value]...
```

Gebruik `none` als er geen speler is.

Voorbeeld:

```mcfunction
/dynathievent crate_open Steve crate=prime amount=1
```

De plugin maakt hier automatisch `mc_crate_open` van.

## Aanbevolen events

### Crates

```mcfunction
/dynathievent crate_open Steve crate=common amount=1
/dynathievent crate_open Steve crate=gold amount=1
/dynathievent crate_open Steve crate=prime amount=1
/dynathievent crate_key_purchase Steve crate=prime amount=10 value=7.50 currency=EUR
```

### Ranks

```mcfunction
/dynathievent rank_purchase Steve rank=vip value=4.99 currency=EUR
/dynathievent rank_purchase Steve rank=elite value=9.99 currency=EUR
/dynathievent rank_purchase Steve rank=legend value=14.99 currency=EUR
```

### Votes

```mcfunction
/dynathievent vote_received Steve site=minecraft_server_list reward=vote_key
```

### Daily rewards

```mcfunction
/dynathievent daily_reward_claim Steve reward=money amount=5000
/dynathievent daily_reward_claim Steve reward=crate_key crate=common amount=1
```

### RTP

```mcfunction
/dynathievent rtp_used Steve world=survival
```

### Auctions

```mcfunction
/dynathievent auction_sale Steve item=diamond amount=16 value=25000 currency=ingame
/dynathievent auction_purchase Steve item=spawner amount=1 value=100000 currency=ingame
```

### Player shops

```mcfunction
/dynathievent shop_purchase Steve shop=spawn_shop item=diamond amount=4 value=10000 currency=ingame
/dynathievent shop_sale Steve shop=player_shop item=iron amount=64 value=5000 currency=ingame
```

### Blackmarket

```mcfunction
/dynathievent blackmarket_purchase Steve item=netherite_sword value=250000 currency=ingame
```

### Spawners

```mcfunction
/dynathievent spawner_purchase Steve spawner=zombie amount=1 value=50000 currency=ingame
/dynathievent spawner_sell Steve spawner=skeleton amount=1 value=35000 currency=ingame
```

### Warps

```mcfunction
/dynathievent warp_visit Steve warp=spawn
/dynathievent warp_visit Steve warp=crates
/dynathievent warp_visit Steve warp=shop
```

### Duels

```mcfunction
/dynathievent duel_start Steve opponent=Alex kit=diamond
/dynathievent duel_win Steve opponent=Alex kit=diamond
/dynathievent duel_loss Alex opponent=Steve kit=diamond
```

### Playtime milestones

```mcfunction
/dynathievent playtime_milestone Steve hours=1
/dynathievent playtime_milestone Steve hours=10
/dynathievent playtime_milestone Steve hours=50
/dynathievent playtime_milestone Steve hours=100
```

### First join

```mcfunction
/dynathievent player_first_join Steve source=tiktok
```

## Privacy

Standaard stuurt de plugin geen IP, geen chattekst en geen spelernaam. De speler UUID wordt gehasht.

Laat dit zo staan voor privacy:

```yml
privacy:
  hash-player-uuid: true
  send-player-name: false
  send-world-name: true
```
