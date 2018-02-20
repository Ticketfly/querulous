package com.twitter.querulous.unit

import java.sql.{SQLException, Connection, PreparedStatement, ResultSet}
import scala.collection.mutable
import com.twitter.querulous.TestEvaluator
import com.twitter.querulous.evaluator.StandardQueryEvaluator
import com.twitter.querulous.query._
import com.twitter.querulous.test.FakeDBConnectionWrapper
import com.twitter.querulous.ConfiguredSpecification
import org.specs2.mock.Mockito
import org.specs2.mutable._

class QueryEvaluatorSpec extends ConfiguredSpecification with Mockito {
  import TestEvaluator._
  sequential

  "QueryEvaluator" should {
    //skipIfCI {
      val queryEvaluator = testEvaluatorFactory(config)
      val rootQueryEvaluator = testEvaluatorFactory(config.withoutDatabase)
      val queryFactory = new SqlQueryFactory

      "connection pooling" in {

        "transactionally" >> {
          val connection = mock[Connection]
          val statement = mock[PreparedStatement]
          val rs = mock[ResultSet]

          val database = new FakeDBConnectionWrapper(connection)
          connection.prepareStatement("SELECT 1") returns statement
          statement.getResultSet returns rs

          val queryEvaluator = new StandardQueryEvaluator(database, queryFactory)

          queryEvaluator.transaction { transaction =>
            transaction.selectOne("SELECT 1") { _.getInt("1") }
          }

          got {
            one(connection).setAutoCommit(false)
            one(connection).prepareStatement("SELECT 1")
            one(connection).commit()
            one(connection).setAutoCommit(true)
          }
        }

        "nontransactionally" >> {
          val connection = mock[Connection]
          val statement = mock[PreparedStatement]
          val rs = mock[ResultSet]

          val database = new FakeDBConnectionWrapper(connection)

          connection.prepareStatement("SELECT 1") returns statement
          statement.getResultSet returns rs

          val queryEvaluator = new StandardQueryEvaluator(database, queryFactory)

          var list = new mutable.ListBuffer[Int]
          queryEvaluator.selectOne("SELECT 1") { _.getInt("1") }

          got {
            one(connection).prepareStatement("SELECT 1")
          }
        }
      }

      "select rows" in {
        var list = new mutable.ListBuffer[Int]
        queryEvaluator.select("SELECT 1 as one") { resultSet =>
          list += resultSet.getInt("one")
        }
        list.toList mustEqual List(1)
      }

      "fallback to a read slave" in {
        // should always succeed if you have the right mysql driver.
        val queryEvaluator = testEvaluatorFactory(
          "localhost:12349" :: config.hostnames.toList, config.database, config.username, config.password)
        queryEvaluator.selectOne("SELECT 1") { row => row.getInt(1) }.toList mustEqual List(1)
        queryEvaluator.execute("CREATE TABLE foo (id INT)") must throwA[SQLException]
      }

      "transaction" in {

        "when there is an exception" >> {

          rootQueryEvaluator.execute("CREATE DATABASE IF NOT EXISTS db_test")
          queryEvaluator.execute("DROP TABLE IF EXISTS querulous_unit_queryevaluatorspec")
          queryEvaluator.execute("CREATE TABLE querulous_unit_queryevaluatorspec (bar INT) ENGINE=INNODB")

          try {
            queryEvaluator.transaction { transaction =>
              transaction.execute("INSERT INTO querulous_unit_queryevaluatorspec VALUES (1)")
              throw new Exception("oh noes")
            }
          } catch {
            case _ :Throwable =>
          }

          queryEvaluator.select("SELECT * FROM foo")(_.getInt("bar")).toList mustEqual Nil
        }

        "when there is not an exception" >> {
          rootQueryEvaluator.execute("CREATE DATABASE IF NOT EXISTS db_test")
          queryEvaluator.execute("DROP TABLE IF EXISTS querulous_unit_queryevaluatorspec")
          queryEvaluator.execute("CREATE TABLE querulous_unit_queryevaluatorspec (bar VARCHAR(50), baz INT) ENGINE=INNODB")

          queryEvaluator.transaction { transaction =>
            transaction.execute("INSERT INTO querulous_unit_queryevaluatorspec VALUES (?, ?)", "one", 2)
          }

          queryEvaluator.select("SELECT * FROM querulous_unit_queryevaluatorspec") { row => (row.getString("bar"), row.getInt("baz")) }.toList mustEqual List(("one", 2))
        } 
      } 
    //}
  }
}
