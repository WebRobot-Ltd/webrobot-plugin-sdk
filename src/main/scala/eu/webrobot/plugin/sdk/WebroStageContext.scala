package eu.webrobot.plugin.sdk

import java.sql.{Connection, PreparedStatement, ResultSet}

/**
 * Context injected by the ETL engine into stages that need external services.
 *
 * Available in: WSourceStage, WSinkStage, WPartitionStage, WGroupStage.
 * NOT available in WTransformStage / WFilterStage / WAggregateStage (stateless, serialized to workers).
 *
 * Implementations are provided by the engine at runtime — plugins never instantiate this directly.
 */
trait WebroStageContext extends Serializable {

  // ── Database ────────────────────────────────────────────────────────────────

  /**
   * Executes a SELECT query and returns rows as WRow instances.
   * Caller must not close the iterator mid-usage.
   *
   * Example:
   *   ctx.query("SELECT ean, url FROM pc_matches WHERE is_active = ?", Seq(true))
   *      .foreach(row => println(row.str("ean")))
   */
  def query(sql: String, params: Seq[Any] = Seq.empty): Iterator[WRow]

  /**
   * Executes an INSERT / UPDATE / DELETE and returns the affected row count.
   *
   * Example:
   *   ctx.execute("INSERT INTO pc_price_history(ean, price) VALUES (?, ?)", Seq(ean, price))
   */
  def execute(sql: String, params: Seq[Any] = Seq.empty): Int

  /**
   * Executes a block inside a single DB transaction.
   * Commits on success, rolls back on exception.
   *
   * Example:
   *   ctx.transaction { conn =>
   *     val ps = conn.prepareStatement("UPDATE ...")
   *     ps.setString(1, value)
   *     ps.executeUpdate()
   *   }
   */
  def transaction[T](block: Connection => T): T

  // ── Storage (MinIO / S3) ────────────────────────────────────────────────────

  /** Downloads an object and returns its raw bytes. */
  def storageGet(path: String): Array[Byte]

  /** Uploads raw bytes to the given path. */
  def storagePut(path: String, data: Array[Byte], contentType: String = "application/octet-stream"): Unit

  /** Returns true if the object exists. */
  def storageExists(path: String): Boolean

  /** Lists keys under the given prefix. */
  def storageList(prefix: String): Seq[String]

  // ── HTTP ────────────────────────────────────────────────────────────────────

  /**
   * Performs a GET request and returns the response body as String.
   * Throws on non-2xx status unless handleErrors = true.
   */
  def httpGet(url: String, headers: Map[String, String] = Map.empty, timeoutMs: Int = 10000): String

  /**
   * Performs a POST request with a String body and returns the response body.
   */
  def httpPost(url: String, body: String, headers: Map[String, String] = Map.empty, timeoutMs: Int = 10000): String

  // ── Configuration ───────────────────────────────────────────────────────────

  /** Reads a platform config value. Returns defaultValue if not found. */
  def config(key: String, defaultValue: String = ""): String

  /** Returns current build environment: "development", "staging", or "production". */
  def buildType: String

  // ── Logging ─────────────────────────────────────────────────────────────────

  /** Logs at INFO level — preferred over println in distributed stages. */
  def log(message: String): Unit

  /** Logs at WARN level. */
  def warn(message: String): Unit

  /** Logs at ERROR level. */
  def error(message: String, cause: Throwable = null): Unit
}
