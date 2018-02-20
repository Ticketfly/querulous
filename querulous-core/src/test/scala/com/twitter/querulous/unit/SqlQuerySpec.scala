package com.twitter.querulous.unit

import java.sql._

import com.twitter.querulous.query.NullValues._
import com.twitter.querulous.query.{NullValues, SqlQuery, TooManyQueryParametersException}
import org.specs2.mock._
import org.specs2.mutable._

class SqlQuerySpec extends Specification with Mockito { // with ClassMocker {
  "SqlQuery" should {
    "typecast" in {
      "arrays" in {
        val connection = mock[Connection]
        val statement = mock[PreparedStatement]
        val rs = mock[ResultSet]

        connection.prepareStatement("SELECT * FROM foo WHERE id IN (?,?,?)") returns statement
        statement.getResultSet returns rs

        new SqlQuery(connection, "SELECT * FROM foo WHERE id IN (?)", List(1, 2, 3)).select { _ => 1 }

        there was one(connection).prepareStatement("SELECT * FROM foo WHERE id IN (?,?,?)")
        there was
          one(statement).setInt(1, 1) andThen
          one(statement).setInt(2, 2) andThen
          one(statement).setInt(3, 3) andThen
          one(statement).executeQuery() andThen
          one(statement).getResultSet

      }

      "sets" in {
        val connection = mock[Connection]
        val statement = mock[PreparedStatement]
        val rs = mock[ResultSet]

        connection.prepareStatement("SELECT * FROM foo WHERE id IN (?,?,?)") returns statement
        statement.getResultSet returns rs

        new SqlQuery(connection, "SELECT * FROM foo WHERE id IN (?)", Set(1, 2, 3)).select { _ => 1 }

        there was
          one(statement).setInt(1, 1) andThen
          one(statement).setInt(2, 2) andThen
          one(statement).setInt(3, 3) andThen
          one(statement).executeQuery() andThen
          one(statement).getResultSet
      }

      "arrays of pairs" in {
        val connection = mock[Connection]
        val statement = mock[PreparedStatement]
        val rs = mock[ResultSet]

        connection.prepareStatement("SELECT * FROM foo WHERE (id, uid) IN ((?,?),(?,?))") returns statement
        statement.getResultSet returns rs

        new SqlQuery(connection, "SELECT * FROM foo WHERE (id, uid) IN (?)", List((1, 2), (3, 4))).select { _ => 1 }

        there was
          one(statement).setInt(1, 1) andThen
          one(statement).setInt(2, 2) andThen
          one(statement).setInt(3, 3) andThen
          one(statement).setInt(4, 4) andThen
          one(statement).executeQuery() andThen
          one(statement).getResultSet
      }

    }

    "arrays of tuple3s" in {
      val connection = mock[Connection]
      val statement = mock[PreparedStatement]
      val rs = mock[ResultSet]

      connection.prepareStatement("SELECT * FROM foo WHERE (id1, id2, id3) IN ((?,?,?))") returns statement
      statement.getResultSet returns rs

      new SqlQuery(connection, "SELECT * FROM foo WHERE (id1, id2, id3) IN (?)", List((1, 2, 3))).select { _ => 1 }

      there was
        one(statement).setInt(1, 1) andThen
        one(statement).setInt(2, 2) andThen
        one(statement).setInt(3, 3) andThen
        one(statement).executeQuery() andThen
        one(statement).getResultSet


    }

    "arrays of tuple4s" in {
      val connection = mock[Connection]
      val statement = mock[PreparedStatement]
      val rs = mock[ResultSet]

      connection.prepareStatement("SELECT * FROM foo WHERE (id1, id2, id3, id4) IN ((?,?,?,?))") returns statement
      statement.getResultSet returns rs

      new SqlQuery(connection, "SELECT * FROM foo WHERE (id1, id2, id3, id4) IN (?)", List((1, 2, 3, 4))).select { _ => 1 }

      there was
        one(statement).setInt(1, 1) andThen
        one(statement).setInt(2, 2) andThen
        one(statement).setInt(3, 3) andThen
        one(statement).setInt(4, 4) andThen
        one(statement).executeQuery() andThen
        one(statement).getResultSet
    }

    "create a query string" in {
      val queryString = "INSERT INTO table (col1, col2, col3, col4, col5) VALUES (?, ?, ?, ?, ?)"
      val connection = mock[Connection]
      val statement = mock[PreparedStatement]
      val rs = mock[ResultSet]

      connection.prepareStatement(queryString) returns statement
      statement.getResultSet returns rs

      new SqlQuery(connection, queryString, "one", 2, 0x03, 4L, 5.0).execute()

      there was
        one(statement).setString(1, "one") andThen
        one(statement).setInt(2, 2) andThen
        one(statement).setInt(3, 0x03) andThen
        one(statement).setLong(4, 4) andThen
        one(statement).setDouble(5, 5.0) andThen
        one(statement).executeUpdate()

    }

    "insert nulls" in {
      val queryString = "INSERT INTO TABLE (null1, null2, null3, null4, null5, null6) VALUES (?, ?, ?, ?, ?, ?)"
      val connection = mock[Connection]
      val statement = mock[PreparedStatement]
      val rs = mock[ResultSet]

      connection.prepareStatement(queryString) returns statement //andThen
      statement.getResultSet returns rs

      new SqlQuery(connection, queryString, NullString, NullInt, NullDouble, NullBoolean, NullLong, NullValues(Types.VARBINARY)).execute()

      there was
        one(statement).setNull(1, Types.VARCHAR) andThen
        one(statement).setNull(2, Types.INTEGER) andThen
        one(statement).setNull(3, Types.DOUBLE) andThen
        one(statement).setNull(4, Types.BOOLEAN) andThen
        one(statement).setNull(5, Types.BIGINT) andThen
        one(statement).setNull(6, Types.VARBINARY) andThen
        one(statement).executeUpdate()

    }

    "handle exceptions" in {
      val queryString = "INSERT INTO TABLE (col1) VALUES (?)"
      val connection = mock[Connection]
      val statement = mock[PreparedStatement]
      val unrecognizedType = connection
      "throw illegal argument exception if type passed in is unrecognized" in {

        //expect {
        //  one(connection).prepareStatement(queryString) returns statement
        //}
        //connection.prepareStatement(queryString) returns statement 
        //connection.prepareStatement(queryString) returns statement 

        new SqlQuery(connection, queryString, unrecognizedType).execute() must throwAn[IllegalArgumentException]

        //there was one(connection).prepareStatement(queryString)
      }
    }

    "throw chained-exception" in {
      val queryString = "INSERT INTO TABLE (col1) VALUES (?)"
      val expectedCauseException = new SQLException("")
      val connection = mock[Connection]
      val statement = mock[PreparedStatement]

      connection.prepareStatement(queryString) returns statement
      statement.setString(1, "one") throws expectedCauseException

      new SqlQuery(connection, queryString, "one").execute() must throwA[TooManyQueryParametersException]

    }

    "add annotations to query" in {
      val queryString = "select * from table"
      val connection = mock[Connection]
      val statement = mock[PreparedStatement]
      val rs = mock[ResultSet]

      //expect {
      //  one(connection).prepareStatement("select * from table /*~{\"key\" : \"value2\", " +
      //    "\"key2\" : \"*\\/select 1\", \"key3\" : \"{:}\"}*/") returns statement andThen
      //  one(statement).executeQuery() andThen
      //  one(statement).getResultSet
      //}
      connection.prepareStatement("select * from table /*~{\"key\" : \"value2\", " +
        "\"key2\" : \"*\\/select 1\", \"key3\" : \"{:}\"}*/") returns statement
      statement.getResultSet returns rs


      val query = new SqlQuery(connection, queryString)
      query.addAnnotation("key", "value")
      query.addAnnotation("key", "value2") // we'll only keep this
      query.addAnnotation("key2", "*/select 1") // trying to end the comment early
      query.addAnnotation("key3", "{:}") // going all json on your ass
      query.select(result => failure("should not return any data"))

      got {
        one(connection).prepareStatement("select * from table /*~{\"key\" : \"value2\", " +
          "\"key2\" : \"*\\/select 1\", \"key3\" : \"{:}\"}*/")
        one(statement).executeQuery()
        one(statement).getResultSet
      }
    }
  }
}
