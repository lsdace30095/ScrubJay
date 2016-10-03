package scrubjay.units

import scrubjay.meta._

import com.github.nscala_time.time.Imports._

case class DateTimeStamp(v: DateTime) extends Units(v)

object DateTimeStamp {
  val converter = new UnitsConverter[DateTimeStamp] {
    override def convert(value: Any, metaUnits: MetaUnits): Units[_] = value match {
      case s: String => DateTimeStamp(DateTime.parse(s))
      case v => throw new RuntimeException(s"Cannot convert $v to ${metaUnits.title}")
    }
  }
}
