package com.example.torrent.cardigann.manager

/**
 * Bundled high-performance Prowlarr V11 YAML definitions.
 * Sourced directly from Prowlarr/Indexers repository specifications.
 */
object BundledCardigannDefinitions {

    val DEFINITIONS: Map<String, String> = mapOf(
        "1337x" to """
---
id: 1337x
name: 1337x
description: "1337x is a Public torrent site offering verified movie and TV torrents"
language: en-US
type: public
encoding: UTF-8
links:
  - https://1337x.to/
  - https://1337x.st/
  - https://x1337x.se/
  - https://1337x.is/
  - https://1337x.gd/

caps:
  categorymappings:
    - { id: Movies, cat: Movies, desc: "Movies" }
    - { id: Television, cat: TV, desc: "TV" }
    - { id: Anime, cat: Anime, desc: "Anime" }
    - { id: Documentaries, cat: Other, desc: "Documentaries" }
    - { id: XXX, cat: XXX, desc: "XXX" }

  modes:
    search: [q]
    tv-search: [q, season, ep, imdbid]
    movie-search: [q, imdbid]

settings:
  - name: sort
    type: select
    label: Sort requested from site
    default: seeders
    options:
      time: created
      seeders: seeders
      size: size
  - name: type
    type: select
    label: Order requested from site
    default: desc
    options:
      desc: desc
      asc: asc

search:
  paths:
    - path: "{{ if .Keywords }}sort-search/{{ .Keywords }}/{{ .Config.sort }}/{{ .Config.type }}/1/{{ else }}trending{{ end }}"
  rows:
    selector: table.table-list > tbody > tr:has(a[href*="/torrent/"])
  fields:
    category:
      selector: td.coll-1.name i
      attribute: class
    title:
      selector: a[href*="/torrent/"]
      attribute: href
      filters:
        - name: regexp
          args: "/torrent/\\d+/([^/]+)/"
        - name: re_replace
          args: ["-", " "]
    details:
      selector: a[href*="/torrent/"]
      attribute: href
    download:
      selector: a[href*="/torrent/"]
      attribute: href
    size:
      selector: td.coll-4.size
      filters:
        - name: regexp
          args: "([\\d\\.]+\\s+[KMGTP]B)"
    seeders:
      selector: td.coll-2.seeds
    leechers:
      selector: td.coll-3.leeches
    date:
      selector: td.coll-date
    magnet:
      selector: a[href^="magnet:?"]
      attribute: href
""".trimIndent(),

        "yts" to """
---
id: yts
name: YTS
description: "YTS is the official home of YIFY Movies torrents"
language: en-US
type: public
encoding: UTF-8
links:
  - https://yts.mx/
  - https://yts.pm/
  - https://yts.do/

caps:
  categorymappings:
    - { id: Movies, cat: Movies, desc: "Movies" }

  modes:
    search: [q]
    movie-search: [q, imdbid]

search:
  paths:
    - path: "api/v2/list_movies.json?query_term={{ if .Query.IMDBID }}{{ .Query.IMDBID }}{{ else }}{{ .Keywords }}{{ end }}&limit=50&sort_by=seeds"
  response:
    type: json
  rows:
    selector: data.movies
  fields:
    title:
      selector: title_long
    year:
      selector: year
    category:
      text: Movies
    details:
      selector: url
    download:
      selector: torrents.0.url
    infohash:
      selector: torrents.0.hash
    size:
      selector: torrents.0.size_bytes
    seeders:
      selector: torrents.0.seeds
    leechers:
      selector: torrents.0.peers
    date:
      selector: torrents.0.date_uploaded
""".trimIndent(),

        "torrentgalaxy" to """
---
id: torrentgalaxy
name: TorrentGalaxy
description: "TorrentGalaxy is a Public torrent site for HD and 4K Movies and TV Shows"
language: en-US
type: public
encoding: UTF-8
links:
  - https://torrentgalaxy.to/
  - https://torrentgalaxy.mx/
  - https://torrentgalaxy.su/

caps:
  categorymappings:
    - { id: 1, cat: Movies, desc: "Movies" }
    - { id: 41, cat: TV, desc: "TV Shows" }
    - { id: 28, cat: Anime, desc: "Anime" }
    - { id: 3, cat: XXX, desc: "XXX" }

  modes:
    search: [q]
    tv-search: [q, season, ep, imdbid]
    movie-search: [q, imdbid]

search:
  paths:
    - path: "torrents.php?search={{ if .Query.IMDBID }}{{ .Query.IMDBID }}{{ else }}{{ .Keywords }}{{ end }}&sort=seeders&order=desc"
  rows:
    selector: div.tgxtablerow:has(a[href*="magnet:?"])
  fields:
    title:
      selector: a[title][href^="/torrent/"]
      attribute: title
    details:
      selector: a[title][href^="/torrent/"]
      attribute: href
    magnet:
      selector: a[href^="magnet:?"]
      attribute: href
    size:
      selector: span.badge.bg-secondary
    seeders:
      selector: font[color="green"]
    leechers:
      selector: font[color="#ff0000"], font[color="red"]
    category:
      selector: a[href*="parent_cat="]
      attribute: href
      filters:
        - name: querystring
          args: parent_cat
""".trimIndent(),

        "eztv" to """
---
id: eztv
name: EZTV
description: "EZTV is a Public torrent site dedicated exclusively to TV Shows and Series"
language: en-US
type: public
encoding: UTF-8
links:
  - https://eztvx.to/
  - https://eztv.wf/
  - https://eztv.tf/

caps:
  categorymappings:
    - { id: TV, cat: TV, desc: "TV Shows" }

  modes:
    search: [q]
    tv-search: [q, season, ep, imdbid]

search:
  paths:
    - path: "api/get-torrents?limit=100&{{ if .Query.IMDBIDShort }}imdb_id={{ .Query.IMDBIDShort }}{{ else }}search={{ .Keywords }}{{ end }}"
  response:
    type: json
  rows:
    selector: torrents
  fields:
    title:
      selector: title
    magnet:
      selector: magnet_url
    infohash:
      selector: hash
    size:
      selector: size_bytes
    seeders:
      selector: seeds
    leechers:
      selector: peers
    category:
      text: TV
    season:
      selector: season
    episode:
      selector: episode
    details:
      selector: episode_url
""".trimIndent(),

        "nyaasi" to """
---
id: nyaasi
name: Nyaa.si
description: "Nyaa is a dedicated anime and East Asian media public tracker"
language: en-US
type: public
encoding: UTF-8
links:
  - https://nyaa.si/
  - https://nyaa.iss.ink/

caps:
  categorymappings:
    - { id: 1_2, cat: Anime, desc: "Anime - English-translated" }
    - { id: 1_4, cat: Anime, desc: "Anime - Raw" }
    - { id: 2_0, cat: XXX, desc: "Non-English translated" }

  modes:
    search: [q]
    tv-search: [q, season, ep]
    movie-search: [q]

search:
  paths:
    - path: "?f=0&c=1_2&q={{ .Keywords }}&s=seeders&o=desc"
  rows:
    selector: table.torrent-list > tbody > tr
  fields:
    title:
      selector: td:nth-child(2) a:not(.comments)
      attribute: title
    details:
      selector: td:nth-child(2) a:not(.comments)
      attribute: href
    magnet:
      selector: a[href^="magnet:?"]
      attribute: href
    size:
      selector: td:nth-child(4)
    seeders:
      selector: td:nth-child(6)
    leechers:
      selector: td:nth-child(7)
    category:
      text: Anime
    date:
      selector: td:nth-child(5)
""".trimIndent(),

        "subsplease" to """
---
id: subsplease
name: SubsPlease
description: "SubsPlease is a trusted anime release group distributing fast, high-quality subtitled releases"
language: en-US
type: public
encoding: UTF-8
links:
  - https://subsplease.org/

caps:
  categorymappings:
    - { id: 5070, cat: Anime, desc: "Anime" }

  modes:
    search: [q]
    tv-search: [q, season, ep]

search:
  paths:
    - path: "api/?f=search&tz=America/New_York&s={{ .Keywords }}"
  response:
    type: json
  rows:
    selector: response
  fields:
    title:
      selector: release_date
    details:
      selector: page
    category:
      text: Anime
    magnet:
      selector: downloads.0.magnet
""".trimIndent(),

        "solidtorrents" to """
---
id: solidtorrents
name: SolidTorrents
description: "SolidTorrents is a Clean and fast DHT search engine with direct magnet resolution"
language: en-US
type: public
encoding: UTF-8
links:
  - https://solidtorrents.to/
  - https://solidtorrents.net/

caps:
  categorymappings:
    - { id: Video, cat: Movies, desc: "Movies / TV" }
    - { id: Audio, cat: Audio, desc: "Audio" }
    - { id: Anime, cat: Anime, desc: "Anime" }

  modes:
    search: [q]
    tv-search: [q, season, ep]
    movie-search: [q]

search:
  paths:
    - path: "api/v1/search?q={{ .Keywords }}&category=all&sort=seeders"
  response:
    type: json
  rows:
    selector: results
  fields:
    title:
      selector: title
    infohash:
      selector: infoHash
    magnet:
      selector: magnet
    size:
      selector: size
    seeders:
      selector: swarm.seeders
    leechers:
      selector: swarm.leechers
    category:
      selector: category
    date:
      selector: imported
""".trimIndent(),

        "bitsearch" to """
---
id: bitsearch
name: BitSearch
description: "BitSearch is a modern, privacy-focused BitTorrent and magnet search engine"
language: en-US
type: public
encoding: UTF-8
links:
  - https://bitsearch.to/

caps:
  categorymappings:
    - { id: 2000, cat: Movies, desc: "Movies" }
    - { id: 5000, cat: TV, desc: "TV" }

  modes:
    search: [q]
    tv-search: [q, season, ep]
    movie-search: [q]

search:
  paths:
    - path: "search?q={{ .Keywords }}&sort=seeders"
  rows:
    selector: li.card.search-result:has(a[href^="magnet:?"])
  fields:
    title:
      selector: h5.title a
    details:
      selector: h5.title a
      attribute: href
    magnet:
      selector: a[href^="magnet:?"]
      attribute: href
    size:
      selector: div.stats div:nth-child(2)
    seeders:
      selector: div.stats div:nth-child(3) font
    leechers:
      selector: div.stats div:nth-child(4) font
    date:
      selector: div.stats div:nth-child(1)
    category:
      selector: div.category
""".trimIndent(),

        "thepiratebay" to """
---
id: thepiratebay
name: The Pirate Bay
description: "The Pirate Bay is the galaxy's most resilient BitTorrent site"
language: en-US
type: public
encoding: UTF-8
links:
  - https://apibay.org/
  - https://thepiratebay.org/

caps:
  categorymappings:
    - { id: 200, cat: Movies, desc: "Video" }
    - { id: 201, cat: Movies, desc: "Movies" }
    - { id: 205, cat: TV, desc: "TV Shows" }
    - { id: 208, cat: Movies, desc: "HD Movies" }

  modes:
    search: [q]
    tv-search: [q, season, ep]
    movie-search: [q]

search:
  paths:
    - path: "q.php?q={{ .Keywords }}&cat=200"
  response:
    type: json
  rows:
    selector: ""
  fields:
    title:
      selector: name
    infohash:
      selector: info_hash
    size:
      selector: size
    seeders:
      selector: seeders
    leechers:
      selector: leechers
    category:
      selector: category
    date:
      selector: added
""".trimIndent(),

        "limetorrents" to """
---
id: limetorrents
name: LimeTorrents
description: "LimeTorrents is a Verified general torrent indexer"
language: en-US
type: public
encoding: UTF-8
links:
  - https://www.limetorrents.lol/
  - https://www.limetorrents.co/
  - https://limetorrents.cc/

caps:
  categorymappings:
    - { id: Movies, cat: Movies, desc: "Movies" }
    - { id: TV-shows, cat: TV, desc: "TV" }
    - { id: Anime, cat: Anime, desc: "Anime" }

  modes:
    search: [q]
    tv-search: [q, season, ep]
    movie-search: [q]

search:
  paths:
    - path: "search/all/{{ .Keywords }}/seeds/1/"
  rows:
    selector: table.table2 > tbody > tr:has(a[href*="/torrent/"])
  fields:
    title:
      selector: div.tt-name a:nth-of-type(2)
    details:
      selector: div.tt-name a:nth-of-type(2)
      attribute: href
    download:
      selector: div.tt-name a:nth-of-type(1)
      attribute: href
    size:
      selector: td.tdnormal:nth-of-type(3)
    seeders:
      selector: td.tdseed
    leechers:
      selector: td.tdleech
    date:
      selector: td.tdnormal:nth-of-type(2)
""".trimIndent(),

        "rutor" to """
---
id: rutor
name: RuTor
description: "RuTor is a popular Russian and multi-audio public torrent indexer"
language: ru-RU
type: public
encoding: UTF-8
links:
  - http://rutor.info/
  - http://rutor.is/

caps:
  categorymappings:
    - { id: 1, cat: Movies, desc: "Movies" }
    - { id: 4, cat: TV, desc: "TV Series" }
    - { id: 6, cat: Movies, desc: "HD Video" }

  modes:
    search: [q]
    tv-search: [q, season, ep]
    movie-search: [q]

search:
  paths:
    - path: "search/0/0/300/2/{{ .Keywords }}"
  rows:
    selector: div#index table tr:has(a[href^="magnet:?"])
  fields:
    title:
      selector: a[href^="/torrent/"]
    details:
      selector: a[href^="/torrent/"]
      attribute: href
    magnet:
      selector: a[href^="magnet:?"]
      attribute: href
    size:
      selector: td:nth-child(4)
    seeders:
      selector: span.green
    leechers:
      selector: span.red
    date:
      selector: td:nth-child(1)
""".trimIndent(),

        "tokyotoshokan" to """
---
id: tokyotoshokan
name: Tokyo Toshokan
description: "Tokyo Toshokan is a clean East Asian media and anime library tracker"
language: en-US
type: public
encoding: UTF-8
links:
  - https://www.tokyotosho.info/

caps:
  categorymappings:
    - { id: 1, cat: Anime, desc: "Anime" }
    - { id: 3, cat: Audio, desc: "Music" }

  modes:
    search: [q]
    tv-search: [q, season, ep]

search:
  paths:
    - path: "search.php?terms={{ .Keywords }}&type=1&size_min=&size_max=&username="
  rows:
    selector: table.listing tr.category_0, table.listing tr.category_1
  fields:
    title:
      selector: td.desc-top a[type="application/x-bittorrent"]
    magnet:
      selector: a[href^="magnet:?"]
      attribute: href
    details:
      selector: td.desc-top a:not([type])
      attribute: href
    size:
      selector: td.desc-bot
      filters:
        - name: regexp
          args: "Size:\\s*([\\d\\.]+\\s*[KMGTP]B)"
""".trimIndent()
    )
}
