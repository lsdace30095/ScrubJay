package scrubjay.util

import com.github.nscala_time.time.Imports._

object Util {
  def timeExpr[R](block: => R): R = {
    val t0 = System.nanoTime()
    val result = block    // call-by-name
    val t1 = System.nanoTime()
    println(s"Elapsed time: ${(t1-t0)/1000000000.0} seconds")
    result
  }
}
