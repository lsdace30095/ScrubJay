package scrubjay.derivation

import scrubjay._
import scrubjay.datasource._
import scrubjay.meta._
import scrubjay.meta.GlobalMetaBase._
import scrubjay.units._

import org.apache.spark.rdd.RDD

/*
 * ExplodeList
 *
 * Requirements: 
 *  1. A single DataSource to derive from
 *  2. A set of user-specified columns, all of which are UnitList[_]
 *
 * Derivation:
 *  For every row with a list <a1, a2, list=[i1, i2, i3]>,
 *  creates a new row with identical attributes <a1, a2, i1>, <a1, a2, i2>, etc ...
 */

class ExplodeList(ds: DataSource,
                  columns: Seq[String],
                  val metaBase: MetaBase) extends DerivedDataSource {

  // Implementations of abstract members
  val defined: Boolean = columns.map(ds.metaSource.metaEntryMap(_)).forall(_.units.tag == UNITS_COMPOSITE_LIST.tag)
  val metaSource = ds.metaSource.withMetaEntries(
    columns.map(col => col + "_exploded" -> {
      val originalMetaEntry = ds.metaSource.metaEntryMap(col)
      originalMetaEntry.copy(units = originalMetaEntry.units.children.head.asInstanceOf[MetaUnits])
    }).toMap)

  // rdd derivation defined here
  lazy val rdd: RDD[DataRow] = {

    // For multiple expansion columns, explode into the cartesian product
    def cartesianProduct[T](xss: List[List[T]]): List[List[T]] = xss match {
      case Nil => List(Nil)
      case h :: t => for(xh <- h; xt <- cartesianProduct(t)) yield xh :: xt
    }

    // Derivation function for flatMap returns a sequence of DataRows
    def derivation(row: DataRow, cols: Seq[String]): Seq[DataRow] = {

      val vals =
        row.filter{case (k, v) => cols.contains(k)}
        .map{
          case (k, ul: UnitsList[_]) => ul.v.map{case u: Units => (k + "_exploded", u)}
          case (k, v) => throw new RuntimeException(s"Runtime type mismatch: \nexpected: UnitList[_]\nvalue: $v")
        }
        .toList

      val combinations = cartesianProduct(vals)

      for (combination <- combinations) yield {
        row ++ Map(combination:_*)
      }
    }

    // Create the derived dataset
    ds.rdd.flatMap(row =>
        derivation(row, columns))
  }
}

object ExplodeList {
  implicit class ScrubJaySession_ExplodeList(sjs: ScrubJaySession) {
    def deriveExplodedList(ds: DataSource, columns: Seq[String]): ExplodeList = {
      new ExplodeList(ds, columns, sjs.metaBase)
    }
  }
}
