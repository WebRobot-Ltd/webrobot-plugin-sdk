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
