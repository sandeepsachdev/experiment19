# Free-to-Air TV Recommender

A Java / Spring Boot app that suggests the **best things to watch on free-to-air (broadcast) TV
today and over the next few days**, ranked using real-time crowd ratings and review sources.

It pulls the live broadcast schedule from [TVmaze](https://www.tvmaze.com/api), aggregates every
airing across the chosen window, enriches the strongest candidates with review-site ratings
(IMDb / Rotten Tomatoes / Metacritic via [OMDb](https://www.omdbapi.com)), blends those into a
single score, and explains *why* each title is worth watching.

> TVmaze's `/schedule` endpoint returns **broadcast network** airings (e.g. BBC One, ITV, NBC),
> not streaming-only content — which is exactly what "free-to-air" means here.

## Features

- **Real schedule data** — what's actually on broadcast TV, per country, for today + N days.
- **Ordered by what's on soonest** — results are sorted by the next upcoming airing.
- **Past airings hidden** — anything that has already started is dropped; a show only appears if it
  still has an upcoming broadcast.
- **Multi-source ratings** — TVmaze crowd scores plus IMDb / Rotten Tomatoes / Metacritic.
- **Composite scoring** — configurable weighted blend, re-normalised across whatever sources exist.
- **Deduplication** — each show appears once, with all of its upcoming airings (channel + time).
- **Scannable times** — each airing is shown as a friendly day + time (e.g. "Today · 21:00 · ABC").
- **Friendly pickers** — country and genre are dropdowns using country *names*; defaults to Australia.
- **Two surfaces** — a JSON REST API and a server-rendered web page.
- **Resilient** — a failing upstream day degrades gracefully instead of breaking the request.
- **Cached** — responses are cached (Caffeine, 30 min TTL) to protect the free upstream APIs.

## Requirements

- Java 21+
- Maven 3.9+
- **Outbound network access** to `api.tvmaze.com` (required) and `www.omdbapi.com` (optional).
  In locked-down/allowlisted environments these hosts must be permitted, or the app will simply
  return no recommendations (it never crashes on blocked upstreams).

## Run

```bash
mvn spring-boot:run
# or
mvn clean package && java -jar target/freetotv-recommender-0.1.0.jar
```

Then open <http://localhost:8080/>.

### Optional: enable review-site ratings (OMDb)

OMDb enrichment is off until you provide a free API key
([get one here](https://www.omdbapi.com/apikey.aspx)):

```bash
export OMDB_ENABLED=true
export OMDB_API_KEY=your_key_here
mvn spring-boot:run
```

Without a key the app still works, ranking purely on TVmaze crowd ratings.

## REST API

```
GET /api/recommendations
```

| Param       | Type   | Default     | Description                                          |
|-------------|--------|-------------|------------------------------------------------------|
| `country`   | string | `Australia` | Country name (e.g. `Australia`) or ISO code (`AU`)   |
| `days`      | int    | `3`         | Days from today to include (1–7)                     |
| `limit`     | int    | `25`        | Max recommendations (1–100)                          |
| `minRating` | double | `0`         | Minimum composite score (0–10)                       |
| `genre`     | string | —           | Case-insensitive genre filter (e.g. `Drama`)         |

Example:

```bash
curl "http://localhost:8080/api/recommendations?country=Australia&days=3&genre=Drama&minRating=7"
```

Results are ordered by the **soonest upcoming airing** (already-aired showings are excluded).
Each item includes the title, type, genres, summary, image, composite score, the individual
rating sources behind it, every upcoming airing (channel + a scannable `dayLabel`/`time` + the raw
`start` + episode), and a plain-language `why`.

Health check: `GET /actuator/health`.

## Configuration

All settings live under the `tv.*` namespace in
[`application.yml`](src/main/resources/application.yml) and can be overridden via environment
variables or command-line args. Key ones:

| Property                      | Default                   | Description                          |
|-------------------------------|---------------------------|--------------------------------------|
| `tv.defaults.country`         | `Australia`               | Default country (name or ISO code)   |
| `tv.defaults.days`            | `3`                       | Default day range                    |
| `tv.omdb.enabled`             | `false` (`OMDB_ENABLED`)  | Turn on review-site enrichment       |
| `tv.omdb.api-key`             | — (`OMDB_API_KEY`)        | OMDb API key                         |
| `tv.omdb.max-lookups`         | `40`                      | Max OMDb calls per request           |
| `tv.scoring.imdb`             | `0.40`                    | Weight for IMDb                      |
| `tv.scoring.rotten-tomatoes`  | `0.25`                    | Weight for Rotten Tomatoes           |
| `tv.scoring.metacritic`       | `0.20`                    | Weight for Metacritic                |
| `tv.scoring.tvmaze`           | `0.15`                    | Weight for TVmaze crowd rating       |

## How it works

1. Fetch the broadcast schedule for each day in the window from TVmaze.
2. Group airings by show (deduplicate) and **drop anything already aired**; a show is kept only if
   it still has an upcoming broadcast.
3. Rank candidates by their TVmaze crowd rating and enrich the **top `max-lookups`** with OMDb
   (only those with an IMDb id), to stay within free-tier limits.
4. Normalise every rating to 0–10 and compute a **weighted average**, re-normalised across only
   the sources that are actually present for that title.
5. Apply genre / minimum-rating filters, **order by the soonest upcoming airing** (ties broken by
   the higher score), and cap to the requested limit.

## Tech & structure

Spring Boot 3.3 · Java 21 · `RestClient` · Caffeine cache · Thymeleaf.

```
client/tvmaze   TVmaze schedule client + DTOs
client/omdb     OMDb ratings client + DTO
service         RecommendationService (aggregation) + ScoringService (rating math)
domain          Recommendation, Airing, RatingSource
web             REST + page controllers, request mapping, error handling
config          properties, RestClient, cache
```

## Tests

```bash
mvn test
```

Tests run fully offline — the TVmaze client is tested with `MockRestServiceServer`, the service
with mocked collaborators, and the API with `@WebMvcTest`.
