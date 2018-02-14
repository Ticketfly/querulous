package com.twitter.querulous.unit

import java.sql.{DriverManager, SQLException}

import com.mysql.jdbc.exceptions.jdbc4.CommunicationsException
import com.twitter.querulous.ConfiguredSpecification
import com.twitter.querulous.database._
import com.twitter.querulous.sql.{FakeConnection, FakeContext, FakeDriver}
import org.apache.commons.dbcp2.DelegatingConnection
import org.specs2.mutable._

import scala.concurrent.duration.{Duration => D, _}

class FakeDriverSpec extends ConfiguredSpecification {
  sequential

  val host = config.hostnames.mkString(",")
  val connString = FakeDriver.DRIVER_NAME + "://" + host

  val ONE_SECOND = D(1,SECONDS)
  val TEN_MILLIS = D(10,MILLISECONDS)
  val ZERO_SECOND  = D(0,SECONDS)

  def testFactory(factory: DatabaseFactory) = {
    "the real connection should be FakeConnection" in {
      val db    = factory(config.hostnames.toList, null, config.username, config.password, Map.empty, FakeDriver.DRIVER_NAME)
      val conn = db.open() match {
        case c: DelegatingConnection[_] => c.getInnermostDelegate
        case c => c
      }

      conn.getClass.getSimpleName mustEqual "FakeConnection"
    }
  }

  "SingleConnectionDatabaseFactory" should {
    val factory = new SingleConnectionDatabaseFactory(Map.empty)
    testFactory(factory)
  }

  "ApachePoolingDatabaseFactory" should {
    val factory = new ApachePoolingDatabaseFactory(10, 10, ONE_SECOND, TEN_MILLIS, false, ZERO_SECOND,
      Map.empty)

    testFactory(factory)
  }

  "ThrottledPoolingDatabaseFactory" should {
    val factory = new ThrottledPoolingDatabaseFactory(10, ONE_SECOND, TEN_MILLIS, ONE_SECOND, Map.empty)

    testFactory(factory)
  }

  "Getting connection from FakeDriver" should {
    "return a FakeConnection" in {
      DriverManager.getConnection(connString, null) must haveClass[FakeConnection]
    } 
  }
  "Getting another conn from FakeDriver" should {
    "throw an exception when db is marked down" in {
      FakeContext.markServerDown(host)
      try {
        DriverManager.getConnection(connString, null) must throwA[CommunicationsException]
      } finally {
        FakeContext.markServerUp(host)
      }
    }
  }

  "Prepareing statement" should {
    "throw an exception when underlying connection is closed" in {
      val conn = DriverManager.getConnection(connString, null)
      conn.close()
      conn.prepareStatement("SELECT 1 FROM DUAL") must throwA[SQLException]
    }

  "throw an exception when underlying db is down" in {
      val conn = DriverManager.getConnection(connString, null)
      try {
        FakeContext.markServerDown(host)
        conn.prepareStatement("SELECT 1 FROM DUAL") must throwA[CommunicationsException]
      } finally {
        FakeContext.markServerUp(host)
      }
    }
  }

  "FakePreparedStatement" should {
    "throw an exception when trying to executeQuery while underlying connection is closed" in {
      val conn = DriverManager.getConnection(connString, null)
      val stmt = conn.prepareStatement("SELECT 1 FROM DUAL")
      conn.close()
      stmt.executeQuery() must throwA[SQLException]
    }

    "throw an exception when trying to executeQuery while underlying db is down" in {
      val conn = DriverManager.getConnection(connString, null)
      val stmt = conn.prepareStatement("SELECT 1 FROM DUAL")
      try {
        FakeContext.markServerDown(host)
        stmt.executeQuery() must throwA[CommunicationsException]
      } finally {
        FakeContext.markServerUp(host)
      }
    }
  }

  "FakeResultSet" should {
    lazy val b = new Before {
      def before =
        FakeContext.setQueryResult(host, "SELECT 1 FROM DUAL", Array(Array[java.lang.Object](1.asInstanceOf[AnyRef])))
    }

    "return result as being registered" in {
      FakeContext.setQueryResult(host, "SELECT 1 FROM DUAL", Array(Array[java.lang.Object](1.asInstanceOf[AnyRef])))
      val stmt = DriverManager.getConnection(connString, null).prepareStatement("SELECT 1 FROM DUAL")
      val rs = stmt.executeQuery()
      rs.next() must_== true
      rs.getInt(1) must_== 1
      rs.next() must_== false
    }

    "throw an exception when iterating through a closed one" in {
      FakeContext.setQueryResult(host, "SELECT 1 FROM DUAL", Array(Array[java.lang.Object](1.asInstanceOf[AnyRef])))
      val stmt = DriverManager.getConnection(connString, null).prepareStatement("SELECT 1 FROM DUAL")
      val rs = stmt.executeQuery()
      rs.close()
      rs.next() must throwA[SQLException]
    }

    "throw an exception when iterating through it while underlying connection is closed" in {
      FakeContext.setQueryResult(host, "SELECT 1 FROM DUAL", Array(Array[java.lang.Object](1.asInstanceOf[AnyRef])))
      val conn = DriverManager.getConnection(connString, null)
      val stmt = conn.prepareStatement("SELECT 1 FROM DUAL")
      val rs = stmt.executeQuery()
      conn.close()
      rs.next() must throwA[SQLException]
    }

   "throw an exception when iterating through it while underlying db is down" in {
      val stmt = DriverManager.getConnection(connString, null).prepareStatement("SELECT 1 FROM DUAL")
      val rs = stmt.executeQuery()
      try {
        FakeContext.markServerDown(host)
        rs.next() must throwA[CommunicationsException]
      } finally {
        FakeContext.markServerUp(host)
      }
    } 
  } 
}
