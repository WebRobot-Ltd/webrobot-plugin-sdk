# WebRobot Plugin SDK

Public API for building ETL plugins for the [WebRobot](https://webrobot.eu) platform.

- **Zero ETL internal dependencies** —  no Spark, no internal engine classes
- **Zero runtime dependencies** — the ETL engine provides everything at runtime
- Plugins are discovered automatically via Java **ServiceLoader**

---

## Quick start

### 1. Generate a new plugin project

Use the **WebRobot CLI** software factory:

```bash
webrobot plugin new my-etl-plugin --group com.mycompany
cd my-etl-plugin
webrobot plugin add stage ExtractPrice --type transform
webrobot plugin add resolver EurPrice
webrobot plugin add stage FilterEmpty  --type filter
```

CLI source and installation: **[github.com/WebRobot-Ltd/WebRobot-CLI](https://github.com/WebRobot-Ltd/WebRobot-CLI)**

### 2. Add the SDK dependency

`build.gradle.kts`:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/WebRobot-Ltd/webrobot-plugin-sdk")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    compileOnly("eu.webrobot:webrobot-plugin-sdk:0.2.0")
    compileOnly("org.scala-lang:scala-library:2.13.12")
}
```

> Both dependencies are `compileOnly` — the ETL engine provides them at runtime.

---

## Plugin types

### Transform stage — `WTransformStage`

Processes each row independently. Most common type.

```scala
import eu.webrobot.plugin.sdk.{WArgs, WRow, WTransformStage}

class UpperCaseStage extends WTransformStage {
  override def name: String = "upper_case"

  override def transform(row: WRow, args: WArgs): WRow = {
    val field = args.string(0, "text")           // first positional arg
    row.str(field).fold(row)(v => row.set(field, v.toUpperCase))
  }
}
```

Register in `src/main/resources/META-INF/services/eu.webrobot.plugin.sdk.WTransformStage`:
```
com.mycompany.UpperCaseStage
```

### Filter stage — `WFilterStage`

Keeps or discards rows based on a predicate.

```scala
import eu.webrobot.plugin.sdk.{WArgs, WRow, WFilterStage}

class FilterNonEmptyStage extends WFilterStage {
  override def name: String = "filter_non_empty"

  override def include(row: WRow, args: WArgs): Boolean = {
    val field = args.string(0, "value")
    row.str(field).exists(_.trim.nonEmpty)
  }
}
```

### Aggregate stage — `WAggregateStage`

Groups rows by key and combines them pairwise (like `reduceByKey`).

```scala
import eu.webrobot.plugin.sdk.{WArgs, WRow, WAggregateStage}

class SumByKeyStage extends WAggregateStage {
  override def name: String = "sum_by_key"

  override def groupBy(row: WRow): String =
    row.str("category").getOrElse("")

  override def combine(left: WRow, right: WRow, args: WArgs): WRow = {
    val field = args.string(0, "amount")
    val sum   = left.double(field).getOrElse(0.0) + right.double(field).getOrElse(0.0)
    left.set(field, sum)
  }
}
```

### Attribute resolver — `WResolver`

Extracts a value from the text content of an HTML element.

```scala
import eu.webrobot.plugin.sdk.WResolver

class PriceResolver extends WResolver {
  override def name: String = "price_resolver"

  private val pattern = """([0-9]+(?:[.,][0-9]{1,2})?)""".r

  override def extract(text: String): Option[String] =
    pattern.findFirstIn(text).map(_.replace(',', '.'))
}
```

---

## Context-aware stages (v0.2.0+)

These stages receive a `WebroStageContext` with access to DB, storage, HTTP and config.
They are **not serialized to Spark workers** — the engine instantiates them on each executor and injects the context.

### Source stage — `WSourceStage`

Produces rows from an external source (DB, REST API, file). Called once; the engine parallelizes the result.

```scala
import eu.webrobot.plugin.sdk.{WArgs, WRow, WSourceStage, WebroStageContext}

class LoadActiveMatchesStage extends WSourceStage {
  override def name: String = "load_active_matches"

  override def produce(args: WArgs, ctx: WebroStageContext): Iterator[WRow] = {
    val maxAgeHours = args.int(0, 6)
    val batchSize   = args.int(1, 500)
    ctx.query(
      "SELECT * FROM pc_matches WHERE is_active = true AND last_verified_at < NOW() - INTERVAL '? hours' LIMIT ?",
      Seq(maxAgeHours, batchSize)
    )
  }
}
```

Register in `META-INF/services/eu.webrobot.plugin.sdk.WSourceStage`.

### Sink stage — `WSinkStage`

Writes each row to an external destination, passes it through unchanged (pipeline can continue).

```scala
import eu.webrobot.plugin.sdk.{WArgs, WRow, WSinkStage, WebroStageContext}

class SavePricesStage extends WSinkStage {
  override def name: String = "save_prices"

  override def consume(row: WRow, args: WArgs, ctx: WebroStageContext): WRow = {
    val price    = row.double(args.string(0, "price")).getOrElse(0.0)
    val currency = row.str(args.string(1, "currency")).getOrElse("EUR")
    val matchId  = row.int("match_id").getOrElse(0)
    ctx.execute(
      "INSERT INTO pc_price_history(match_id, price, currency, scraped_at) VALUES (?, ?, ?, NOW())",
      Seq(matchId, price, currency)
    )
    row.set("_saved", true)
  }
}
```

Register in `META-INF/services/eu.webrobot.plugin.sdk.WSinkStage`.

### Partition stage — `WPartitionStage`

Operates on a whole Spark partition at once (`mapPartitions`). Use for:
- Batch DB writes (collect N rows, single INSERT batch)
- Bulk HTTP calls
- ANN index lookups (load FAISS index once per partition, process all rows)
- Partition-local deduplication

```scala
import eu.webrobot.plugin.sdk.{WArgs, WRow, WPartitionStage, WebroStageContext}

class ImageAnnMatchStage extends WPartitionStage {
  override def name: String = "image_ann_match"

  override def transformPartition(
    rows: Iterator[WRow], args: WArgs, ctx: WebroStageContext
  ): Iterator[WRow] = {
    val threshold  = args.double(0, 0.85)
    val indexPath  = args.string(1, "faiss.index")
    // Load index once per partition (heavy resource — amortized across all rows)
    val indexBytes = ctx.storageGet(indexPath)
    // ... load FAISS / call Python ANN service ...
    rows.map { row =>
      val imageUrl = row.str("image_url").getOrElse("")
      // perform ANN lookup, enrich row with match result
      row.set("match_confidence", 0.92).set("match_method", "image_sim")
    }
  }
}
```

Register in `META-INF/services/eu.webrobot.plugin.sdk.WPartitionStage`.

### Group stage — `WGroupStage`

Groups all rows by a key and reduces the full group to one row. Unlike `WAggregateStage` (pairwise fold), this receives the complete `Iterable` — use for min/max/avg or best-match selection.

```scala
import eu.webrobot.plugin.sdk.{WArgs, WRow, WGroupStage, WebroStageContext}

class BestPriceStage extends WGroupStage {
  override def name: String = "best_price"
  override def groupBy(row: WRow): String = row.str("ean").getOrElse("")

  override def aggregate(rows: Iterable[WRow], args: WArgs, ctx: WebroStageContext): WRow = {
    val priceField = args.string(0, "price")
    val mode       = args.string(1, "min")
    val rowList    = rows.toList
    val withPrice  = rowList.flatMap(r => r.double(priceField).map(_ -> r))
    val best = if (mode == "max") withPrice.maxBy(_._1)._2 else withPrice.minBy(_._1)._2
    best.set("site_count", rowList.size)
  }
}
```

Register in `META-INF/services/eu.webrobot.plugin.sdk.WGroupStage`.

### `WebroStageContext` API

| Method | Description |
|--------|-------------|
| `ctx.query(sql, params)` | SELECT → `Iterator[WRow]` |
| `ctx.execute(sql, params)` | INSERT/UPDATE/DELETE → affected rows count |
| `ctx.transaction { conn => }` | Block wrapped in DB transaction |
| `ctx.storageGet(path)` | Download from MinIO/S3 → `Array[Byte]` |
| `ctx.storagePut(path, data)` | Upload to MinIO/S3 |
| `ctx.storageExists(path)` | Check object exists |
| `ctx.storageList(prefix)` | List object keys |
| `ctx.httpGet(url, headers)` | HTTP GET → response body String |
| `ctx.httpPost(url, body, headers)` | HTTP POST → response body String |
| `ctx.config(key, default)` | Read platform config value |
| `ctx.buildType` | `"development"` / `"staging"` / `"production"` |
| `ctx.log(msg)` | INFO log |
| `ctx.warn(msg)` | WARN log |
| `ctx.error(msg, cause)` | ERROR log |

---

### Browser action — `WAction`

Produces a browser action descriptor from named parameters.

> **Note:** Hot-loading of actions requires an ETL engine restart after deploying the JAR.

```scala
import eu.webrobot.plugin.sdk.{ActionSpec, WAction, WActionArgs}

class SleepAction extends WAction {
  override def name: String = "sleep"

  override def build(args: WActionArgs): ActionSpec =
    ActionSpec(actionType = "sleep", params = Map("ms" -> args.int("ms", 500)))
}
```

---

## `WRow` API

| Method | Description |
|--------|-------------|
| `row.str("field")` | `Option[String]` |
| `row.int("field")` | `Option[Int]` |
| `row.double("field")` | `Option[Double]` |
| `row.bool("field")` | `Option[Boolean]` |
| `row.set("field", value)` | Returns new `WRow` with field added/updated |
| `row.remove("field")` | Returns new `WRow` with field removed |
| `row.hasField("field")` | `Boolean` |
| `row.rename("from", "to")` | Returns new `WRow` with field renamed |
| `row.fieldNames` | `Set[String]` of all field names |

## `WArgs` API (positional stage arguments)

| Method | Description |
|--------|-------------|
| `args.string(idx, default)` | Argument at position `idx` as String |
| `args.int(idx, default)` | Argument at position `idx` as Int |
| `args.double(idx, default)` | Argument at position `idx` as Double |
| `args.bool(idx, default)` | Argument at position `idx` as Boolean |
| `args.size` | Number of arguments |

---

## Build and deploy

```bash
# Build the plugin JAR
GITHUB_TOKEN=<token> ./gradlew jar

# The JAR contains only your classes + META-INF/services — no bundled dependencies
ls build/libs/
```

Deploy the JAR to the ETL plugins directory as configured in your WebRobot installation.

---

## Software factory (CLI)

The [WebRobot CLI](https://github.com/WebRobot-Ltd/WebRobot-CLI) includes a `plugin` command group that scaffolds all boilerplate:

```bash
webrobot plugin new <name> [--group <groupId>] [--output <dir>]
webrobot plugin add stage    <Name> [--type transform|filter|aggregate]
webrobot plugin add resolver <Name>
webrobot plugin add action   <Name>
```

Each `add` command generates the Scala source file and updates the corresponding `META-INF/services` entry automatically.

---

## Example plugin

A complete working example is available at:
**[github.com/WebRobot-Ltd/webrobot-example-plugin](https://github.com/WebRobot-Ltd/webrobot-example-plugin)**
