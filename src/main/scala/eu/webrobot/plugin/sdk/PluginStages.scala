package eu.webrobot.plugin.sdk

/**
 * Transform each row independently. Most common stage type.
 * Register via: META-INF/services/eu.webrobot.plugin.sdk.WTransformStage
 */
trait WTransformStage extends Serializable {
  def name: String
  def transform(row: WRow, args: WArgs): WRow
}

/**
 * Keep or discard rows based on a predicate.
 * Register via: META-INF/services/eu.webrobot.plugin.sdk.WFilterStage
 */
trait WFilterStage extends Serializable {
  def name: String
  def include(row: WRow, args: WArgs): Boolean
}

/**
 * Group rows by a key, then combine pairwise (like reduceByKey in Spark).
 * Register via: META-INF/services/eu.webrobot.plugin.sdk.WAggregateStage
 */
trait WAggregateStage extends Serializable {
  def name: String
  def groupBy(row: WRow): String
  def combine(left: WRow, right: WRow, args: WArgs): WRow
}

/**
 * Produce a browser action from named parameters.
 * Register via: META-INF/services/eu.webrobot.plugin.sdk.WAction
 * NOTE: hot-loading of actions is not yet supported by the ETL engine.
 */
trait WAction extends Serializable {
  def name: String
  def build(args: WActionArgs): ActionSpec
}

/**
 * Extract a string value from the text content of an HTML element.
 * Register via: META-INF/services/eu.webrobot.plugin.sdk.WResolver
 */
trait WResolver extends Serializable {
  def name: String
  def extract(text: String): Option[String]
}

/** Value type returned by a WAction, interpreted by the ETL engine. */
case class ActionSpec(actionType: String, params: Map[String, Any] = Map.empty) extends Serializable

// ── Context-aware stages ──────────────────────────────────────────────────────
// These traits receive a WebroStageContext and are NOT serialized to Spark workers.
// The engine instantiates them once per executor and injects the context.

/**
 * Produces rows from an external source (DB table, API, file, etc.).
 * Replaces load_* hardcoded stages. Called once by the engine; result is parallelized.
 *
 * Register via: META-INF/services/eu.webrobot.plugin.sdk.WSourceStage
 *
 * Pipeline YAML:
 *   - stage: load_active_matches
 *     args:
 *       - max_age_hours: 6
 *       - batch_size: 500
 */
trait WSourceStage {
  def name: String
  def produce(args: WArgs, ctx: WebroStageContext): Iterator[WRow]
}

/**
 * Writes rows to an external sink (DB, API, queue) as the final or intermediate step.
 * Rows pass through unchanged so the pipeline can continue after a sink.
 *
 * Register via: META-INF/services/eu.webrobot.plugin.sdk.WSinkStage
 *
 * Pipeline YAML:
 *   - stage: save_prices
 *     args:
 *       - price_field: "price_current_price"
 *       - currency_field: "price_currency"
 */
trait WSinkStage {
  def name: String

  /** Called per row. Return the row unchanged (or enriched with write metadata). */
  def consume(row: WRow, args: WArgs, ctx: WebroStageContext): WRow
}

/**
 * Operates on a whole partition of rows at once (Spark mapPartitions).
 * Use for batch DB writes, bulk HTTP calls, ANN index lookups, deduplication
 * across rows — anything that needs cross-row context within a partition.
 *
 * Register via: META-INF/services/eu.webrobot.plugin.sdk.WPartitionStage
 *
 * Pipeline YAML:
 *   - stage: image_ann_match
 *     args:
 *       - index_path: "s3a://bucket/faiss.index"
 *       - threshold: 0.85
 */
trait WPartitionStage {
  def name: String

  /**
   * Transforms an entire partition. The engine calls this via rdd.mapPartitions.
   * Implementations should be efficient: load heavy resources (indexes, models) once
   * outside the inner loop, then process all rows in the iterator.
   */
  def transformPartition(rows: Iterator[WRow], args: WArgs, ctx: WebroStageContext): Iterator[WRow]
}

/**
 * Groups all rows sharing the same key and reduces the full group to a single row.
 * Unlike WAggregateStage (pairwise combine), this receives the complete Iterable —
 * suitable for min/max/average across a group, or selecting the best match.
 *
 * Register via: META-INF/services/eu.webrobot.plugin.sdk.WGroupStage
 *
 * Pipeline YAML:
 *   - stage: best_price_per_ean
 *     args:
 *       - price_field: "price"
 *       - mode: "min"
 */
trait WGroupStage {
  def name: String

  /** The field whose value determines the group key. */
  def groupBy(row: WRow): String

  /**
   * Reduces a complete group to one output row.
   * The group is guaranteed non-empty.
   */
  def aggregate(rows: Iterable[WRow], args: WArgs, ctx: WebroStageContext): WRow
}
