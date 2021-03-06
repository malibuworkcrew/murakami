package com.wood.murakami

import com.webtrends.harness.app.HActor
import com.wood.murakami.directory.Fields.Field
import com.wood.murakami.directory.{Fields, PathFinder}
import com.wood.murakami.executors.QueryExecutors
import com.wood.murakami.query.{Filter, Parser}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

case class Query(select: String, filter: Option[String], group: Option[String], order: Option[String])

// Main class for dealing with queries and spinning off Combiners
class QueryActor extends HActor {
  val fileName = "formatted.psv"

  override def receive: Receive = {
    case q: Query =>
      val callback = sender()
      try {
        // Parse our query
        val combiner = parseCombiner(q)
        // Find all files to read
        val paths = PathFinder.getPaths(combiner.filter, fileName)
        // Spin up threads to read each file
        val futures = Future.sequence(paths.map(path => Future {
          retry[Combiner](3) {
            val exec = new QueryExecutors(path, combiner.newInstance())
            exec.execute
          }
        }) toList)
        // Combine results into one data set
        futures.onComplete {
          case Success(s) =>
            if (s.size == 1) callback ! s.head.outputString
            else if (s.isEmpty) callback ! ""
            else {
              val combined = s.tail.foldLeft(s.head) { case (a, b) =>
                a ++= b
              }
              combined.sort
              callback ! combined.outputString
            }
          case Failure(f) => callback ! Failure(f)
        }
      } catch {
        case ex: Throwable =>
          log.error(ex, "Query Failure")
          callback ! Failure(ex)
      }
  }

  def parseCombiner(query: Query): Combiner = {
    val selects = Parser.parseSelect(query.select)
    if (!selects.successful) throw new IllegalArgumentException(selects.toString)
    val order: Option[Seq[Field]] = query.order.map { order =>
      val ord = Parser.parseOrder(order)
      if (!ord.successful) throw new IllegalArgumentException(ord.toString)
      ord.get
    }
    val filter: Option[Filter] = query.filter match {
      case Some(filt) =>
        val pFilter = Parser.parseFilter(filt)
        if (!pFilter.successful) throw new IllegalArgumentException(pFilter.toString)
        Some(pFilter.get)
      case None => None
    }
    query.group match {
      case Some(g) =>
        val group = Fields.fields.find(_.stringValue == g).get
        AggregateCombiner(selects.get, filter, group, order)
      case None => SelectionCombiner(selects.get, filter, order)
    }
  }

  // Will retry input function `n` times before throwing an error
  def retry[T](n: Int)(fn: => T): T = {
    try fn catch {
      case ex: Throwable =>
        if (n > 1) retry(n - 1)(fn)
        else throw ex
    }
  }
}
