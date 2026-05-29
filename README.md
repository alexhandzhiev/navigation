# Navigation — Land Route Calculator

A Spring Boot service that, given two `cca3` country codes, returns the
shortest sequence of border crossings between them — or HTTP 400 when no
land route exists.

## Requirements (from the task brief)

> - Spring Boot, Maven
> - Data link: <https://raw.githubusercontent.com/mledoze/countries/master/countries.json>
> - The application exposes REST endpoint `/routing/{origin}/{destination}`
>   that returns a list of border crossings to get from origin to destination
> - Single route is returned if the journey is possible
> - Algorithm needs to be efficient
> - If there is no land crossing, the endpoint returns HTTP 400
> - Countries are identified by `cca3` field in country data
> - Sample: `GET /routing/CZE/ITA` → `{"route": ["CZE", "AUT", "ITA"]}`

## Tech Stack

| Concern | Choice |
| --- | --- |
| Language / runtime | Java 21 |
| Framework | Spring Boot 3.3.5 (`spring-boot-starter-web`) |
| Build | Maven (`spring-boot-maven-plugin` produces a runnable fat jar) |
| JSON | Jackson (transitive via the web starter) |
| Tests | JUnit 5 + AssertJ via `spring-boot-starter-test` |

## Build and test

You need Java 21 and Maven 3.6+ on the path.

```bash
mvn clean package
```

That compiles, runs the test suite, and produces
`target/navigation-1.0.0.jar`. To run only the tests:

```bash
mvn test
```

## Run

```bash
java -jar target/navigation-1.0.0.jar
```

The service listens on `:8080` and fetches the mledoze dataset from
GitHub once at startup, parsing it into the in-memory adjacency map. The
data source is a Spring `Resource` and is configurable.

### Running offline

Default startup requires internet to reach
`raw.githubusercontent.com`. To run without network, point the property
at any local copy of the dataset. The snapshot at
`src/test/resources/countries.json` works as-is:

```bash
java -jar target/navigation-1.0.0.jar \
  --navigation.countries.source=file:./src/test/resources/countries.json
```

Use `file:/absolute/path/to/countries.json` if you're starting from a
different directory.

### Data source

`CountryDataService` is constructor-injected with a `Resource` resolved
from the `navigation.countries.source` property. Spring's
`DefaultResourceLoader` understands several prefixes, so the same property
covers every realistic source without code changes:

| Value | Resolves to |
| --- | --- |
| `https://…` *(default)* | Remote HTTP fetch via `UrlResource` |
| `classpath:countries.json` | File on the classpath |
| `file:/path/to/countries.json` | Local filesystem file |

Override at runtime:

```bash
java -jar target/navigation-1.0.0.jar \
  --navigation.countries.source=file:/etc/navigation/countries.json
```

### Why fetch from the URL rather than bundle the file?

The brief names the URL as the data source, so the default behaviour
follows the brief. A few design considerations worth flagging:

- **Country borders are stable** — they change on the scale of years, not
  requests — so fetching once at startup is plenty. Hot-reloading or
  per-request fetching would be over-engineered.
- **Failures are loud, not silent.** If the URL is unreachable at startup,
  the service refuses to come up rather than serving stale or empty data.
  That's the right behaviour for a service whose entire purpose depends on
  the dataset.
- **The source is configurable, not hard-coded.** In production I'd
  consider pointing this at an internally hosted, version-pinned copy
  rather than a third-party GitHub raw URL — same code, different property.
  The bundled test resource (`src/test/resources/countries.json`) lets
  unit tests run offline and deterministically.
- **The dataset is not in the production jar** — the only copy in the
  artefact is whatever the URL returns at boot. Keeping it out of `main`
  resources means there is one source of truth, not two that can silently
  diverge.

## Endpoint

### `GET /routing/{origin}/{destination}`

Both codes are `cca3` and are accepted in any case. The response is the
shortest land route, inclusive of both endpoints.

**200 — route found**

```bash
$ curl http://localhost:8080/routing/CZE/ITA
{"route":["CZE","AUT","ITA"]}

$ curl http://localhost:8080/routing/ESP/RUS
{"route":["ESP","FRA","DEU","POL","RUS"]}
```

**400 — no land route, or unknown country code**

```bash
$ curl http://localhost:8080/routing/ISL/DEU   # Iceland: island, no borders
{"error":"NO_ROUTE","message":"No land route from ISL to DEU"}

$ curl http://localhost:8080/routing/USA/AUS   # different continents
{"error":"NO_ROUTE","message":"No land route from USA to AUS"}

$ curl http://localhost:8080/routing/XXX/DEU   # XXX is not a cca3
{"error":"NO_ROUTE","message":"Unknown origin country: XXX"}
```

`origin == destination` returns a one-element route, e.g. `{"route":["DEU"]}`.

### Error response contract

Every 4xx / 5xx response from this service uses the same JSON shape:

```json
{ "error": "<CODE>", "message": "<human readable detail>" }
```

| Status | Code | Triggered by |
| --- | --- | --- |
| 400 | `NO_ROUTE` | No land route, or unknown `cca3` |
| 404 | `NOT_FOUND` | Wrong path / wrong arity |
| 405 | `METHOD_NOT_ALLOWED` | Non-GET on `/routing/{a}/{b}` |
| 406 | `NOT_ACCEPTABLE` | Client demands an unsupported media type |
| 500 | `INTERNAL_ERROR` | Unhandled error |

Malformed-URL rejections that happen below the application layer (Tomcat's HTTP parser rejecting null bytes or path-traversal sequences) still return the correct 4xx status, but the body shape comes from Tomcat's defaults rather than this service.

## How the routing works

`CountryDataService` parses the dataset once at startup and builds an
immutable `Map<String, Set<String>>` keyed by `cca3`. The graph is
**symmetrised during construction** — every declared border is added in
both directions, and references to unknown codes are discarded. This
guarantees that `findRoute(a, b)` and `findRoute(b, a)` agree on whether
a route exists (see the catch below for why this matters).

`RoutingService.findRoute` runs breadth-first search from origin. BFS visits
each country at most once and is `O(V + E)`; with ~250 nodes and a few
hundred edges, a query completes in microseconds. Because BFS expands in
hop order, the first time it reaches the destination it has found a route
with the minimum number of border crossings, satisfying the "efficient"
requirement and the "single route" contract.

`NoRouteException` is thrown for an unknown code or when BFS exhausts the
reachable component without finding the destination.
`GlobalExceptionHandler` (`@RestControllerAdvice`) translates it to a
structured JSON 400. `JsonErrorController` does the same for every other
forwarded error (404, 405, 406, 500).

### Catch: one asymmetric border in the upstream dataset

While testing, I scanned the dataset for symmetry and found **exactly one**
asymmetric pair across all 250 countries:

- **LKA** (Sri Lanka) lists **IND** (India) in its `borders` array.
- **IND** does not list **LKA**.

Without the symmetrisation step, BFS would give direction-dependent answers:
`findRoute("LKA", "IND")` returns `["LKA","IND"]` but `findRoute("IND", "LKA")`
returns HTTP 400. Same pair, different result.

Worth noting: geographically, Sri Lanka and India don't share a land border
(they're separated by the Palk Strait), so LKA's declaration is the wrong
one and IND's omission is correct. A stricter implementation would *drop*
asymmetric edges instead of accepting them, but that's a data-cleansing
decision, not a routing-algorithm decision; the brief uses the upstream
as-is, and the routing layer's job is just to behave consistently. The
regression test `routingIsDirectionAgnostic` in `RoutingServiceTest` pins
this behaviour.

## Layout

```
src/main/java/com/example/navigation/
  NavigationApplication.java            Spring Boot entry point
  controller/RoutingController.java     REST endpoint
  controller/GlobalExceptionHandler.java @RestControllerAdvice → JSON 400 for NoRouteException
  controller/JsonErrorController.java   Replaces BasicErrorController, JSON for all forwarded errors
  service/CountryDataService.java       Loads JSON, symmetrises and exposes adjacency map
  service/RoutingService.java           BFS shortest-path
  model/Country.java                    Jackson record (cca3 + non-null borders)
  model/RouteResponse.java              record { "route": [...] }
  model/ErrorResponse.java              record { "error": "...", "message": "..." }
  exception/NoRouteException.java       Thrown when no path / unknown code

src/main/resources/
  application.properties                Server port + data source

src/test/java/com/example/navigation/
  RoutingServiceTest.java               BFS / graph unit tests
  RoutingControllerTest.java            HTTP-layer integration tests (RANDOM_PORT)

src/test/resources/
  countries.json                        Dataset snapshot used by tests

src/test/java/com/example/navigation/
  RoutingServiceTest.java            BFS unit tests
```
