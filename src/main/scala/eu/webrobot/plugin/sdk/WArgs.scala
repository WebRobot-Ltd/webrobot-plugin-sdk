package eu.webrobot.plugin.sdk

import scala.util.Try

/** Positional arguments passed to a stage. Index-based access. */
class WArgs(private val raw: Seq[Any]) extends Serializable {

  def size: Int        = raw.size
  def isEmpty: Boolean = raw.isEmpty
  def all: Seq[Any]    = raw

  def string(idx: Int, default: String = ""): String =
    raw.lift(idx).map(_.toString).getOrElse(default)

  def int(idx: Int, default: Int = 0): Int =
    raw.lift(idx).flatMap(v => Try(v.toString.toInt).toOption).getOrElse(default)

  def double(idx: Int, default: Double = 0.0): Double =
    raw.lift(idx).flatMap(v => Try(v.toString.toDouble).toOption).getOrElse(default)

  def bool(idx: Int, default: Boolean = false): Boolean =
    raw.lift(idx).flatMap(v => Try(v.toString.toBoolean).toOption).getOrElse(default)

  def opt(idx: Int): Option[Any] = raw.lift(idx)
}

/** Named arguments passed to an action factory. */
class WActionArgs(private val raw: Map[String, Any]) extends Serializable {

  def get(name: String): Option[Any] = raw.get(name)

  def string(name: String, default: String = ""): String =
    raw.get(name).map(_.toString).getOrElse(default)

  def requireString(name: String): String =
    raw.get(name).map(_.toString)
      .getOrElse(throw new IllegalArgumentException(s"Required argument '$name' missing"))

  def int(name: String, default: Int = 0): Int =
    raw.get(name).flatMap(v => Try(v.toString.toInt).toOption).getOrElse(default)

  def double(name: String, default: Double = 0.0): Double =
    raw.get(name).flatMap(v => Try(v.toString.toDouble).toOption).getOrElse(default)

  def bool(name: String, default: Boolean = false): Boolean =
    raw.get(name).flatMap(v => Try(v.toString.toBoolean).toOption).getOrElse(default)

  def toMap: Map[String, Any] = raw
}
