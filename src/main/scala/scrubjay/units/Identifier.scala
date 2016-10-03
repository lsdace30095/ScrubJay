package scrubjay.units

import scrubjay.meta._

case class Identifier(v: String) extends Units(v)

object Identifier {

  // Implement converter
  val converter = new UnitsConverter[Identifier] {
    override def convert(value: Any, metaUnits: MetaUnits): Identifier = Identifier(value.toString)
  }

}
