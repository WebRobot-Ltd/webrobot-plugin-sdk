package eu.webrobot.plugin.sdk

/** Immutable data row passed to all plugin stages. */
case class WRow(fields: Map[String, Any]) {

  def get(name: String): Option[Any]    = fields.get(name)
  def str(name: String): Option[String] = fields.get(name).map(_.toString)

  def int(name: String): Option[Int] = fields.get(name).flatMap {
    case v: Int    => Some(v)
    case v: Long   => Some(v.toInt)
    case v: String => v.toIntOption
    case v         => v.toString.toIntOption
  }

  def double(name: String): Option[Double] = fields.get(name).flatMap {
    case v: Double => Some(v)
    case v: Float  => Some(v.toDouble)
    case v: Int    => Some(v.toDouble)
    case v: Long   => Some(v.toDouble)
    case v: String => v.toDoubleOption
    case v         => v.toString.toDoubleOption
  }

  def bool(name: String): Option[Boolean] = fields.get(name).flatMap {
    case v: Boolean => Some(v)
    case v: String  => v.toBooleanOption
    case _          => None
  }

  def set(name: String, value: Any): WRow    = copy(fields = fields + (name -> value))
  def remove(name: String): WRow             = copy(fields = fields - name)
  def hasField(name: String): Boolean        = fields.contains(name)
  def fieldNames: Set[String]                = fields.keySet
  def rename(from: String, to: String): WRow =
    fields.get(from).fold(this)(v => copy(fields = fields - from + (to -> v)))
}

object WRow {
  val empty: WRow = WRow(Map.empty)
}
