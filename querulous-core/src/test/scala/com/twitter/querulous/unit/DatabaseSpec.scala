package com.twitter.querulous.unit

import java.sql.Connection
import org.apache.commons.dbcp2.{DelegatingConnection => DBCPConnection}
import com.mysql.jdbc.{ConnectionImpl => MySQLConnection}
import org.specs2.mock.Mockito
import scala.concurrent.duration._
import com.twitter.querulous.database._
import com.twitter.querulous.ConfiguredSpecification
import scala.concurrent.duration.{Duration => D}


class DatabaseSpec extends ConfiguredSpecification with Mockito {
  val defaultProps = Map("socketTimeout" -> "41", "connectTimeout" -> "42")

  def mysqlConn(conn: Connection) = conn match {
    case c: DBCPConnection[_] =>
      c.getInnermostDelegate.asInstanceOf[MySQLConnection]
    case c: MySQLConnection => c
  }

  def testFactory(factory: DatabaseFactory) = {
    "allow specification of default query options" in {
      val db    = factory(config.hostnames.toList, null, config.username, config.password)
      val props = mysqlConn(db.open).getProperties

      props.getProperty("connectTimeout") mustEqual "42"
      props.getProperty("socketTimeout")  mustEqual "41"
    }

    "allow override of default query options" in {
      val db    = factory(
        config.hostnames.toList,
        null,
        config.username,
        config.password,
        Map("connectTimeout" -> "43"))
      val props = mysqlConn(db.open).getProperties

      props.getProperty("connectTimeout") mustEqual "43"
      props.getProperty("socketTimeout")  mustEqual "41"
    }
  }

  "SingleConnectionDatabaseFactory" should {
    //skipIfCI {
      val factory = new SingleConnectionDatabaseFactory(defaultProps)
      testFactory(factory)
    //}
  }

  "ApachePoolingDatabaseFactory" should {
    //skipIfCI {
      val factory = new ApachePoolingDatabaseFactory(
        10, 10, D(1,SECONDS),D(10,MILLISECONDS), false, D(0,SECONDS), defaultProps
      )

      testFactory(factory)
    //}
  }
}
