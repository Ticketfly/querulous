package com.twitter.querulous.database

import java.sql.{Connection, SQLException}

import org.apache.commons.dbcp2.{DriverManagerConnectionFactory, PoolableConnection, PoolableConnectionFactory, PoolingDataSource}
import org.apache.commons.pool2.{ObjectPool, PooledObject, PooledObjectFactory}
import org.apache.commons.pool2.impl.{GenericObjectPool, GenericObjectPoolConfig}

import scala.collection.JavaConversions._
import concurrent.duration.Duration

class ApachePoolingDatabaseFactory(
    val minOpenConnections              :Int,
    val maxOpenConnections              :Int,
    checkConnectionHealthWhenIdleFor    :Duration,
    maxWaitForConnectionReservation     :Duration,
    checkConnectionHealthOnReservation  :Boolean,
    evictConnectionIfIdleFor            :Duration,
    defaultUrlOptions                   :Map[String, String] ) extends DatabaseFactory {

  def this(minConns: Int, maxConns: Int, checkIdle: Duration, maxWait: Duration, checkHealth: Boolean, evictTime: Duration) = {
    this(minConns, maxConns, checkIdle, maxWait, checkHealth, evictTime, Map.empty)
  }

  def apply(dbhosts: List[String], dbname: String, username: String, password: String, urlOptions: Map[String, String], driverName: String) = {
    val finalUrlOptions =
      if (urlOptions eq null) {
        defaultUrlOptions
      } else {
        defaultUrlOptions ++ urlOptions
      }

    new ApachePoolingDatabase(
      dbhosts,
      dbname,
      username,
      password,
      finalUrlOptions,
      driverName,
      minOpenConnections,
      maxOpenConnections,
      checkConnectionHealthWhenIdleFor,
      maxWaitForConnectionReservation,
      checkConnectionHealthOnReservation,
      evictConnectionIfIdleFor
    )
  }
}

class ApachePoolingDatabase(
  val hosts           :List[String],
  val name            :String,
  val username        :String,
  password            :String,
  val extraUrlOptions :Map[String, String],
  val driverName      :String,
  minOpenConnections  :Int,
  maxOpenConnections  :Int,
  checkConnectionHealthWhenIdleFor   :Duration,
  val openTimeout     :Duration,
  checkConnectionHealthOnReservation :Boolean,
  evictConnectionIfIdleFor           :Duration )
extends Database {

  Class.forName("com.mysql.jdbc.Driver")

  private val config = new GenericObjectPoolConfig
  config.setMaxTotal(maxOpenConnections)
  config.setMaxIdle(maxOpenConnections)
  config.setMinIdle(minOpenConnections)
  config.setMaxWaitMillis(openTimeout.toMillis)

  config.setTimeBetweenEvictionRunsMillis(checkConnectionHealthWhenIdleFor.toMillis)
  config.setTestWhileIdle(false)
  config.setTestOnBorrow(checkConnectionHealthOnReservation)
  config.setMinEvictableIdleTimeMillis(evictConnectionIfIdleFor.toMillis)

  config.setLifo(false)

  private val connectionFactory = new DriverManagerConnectionFactory(url(hosts, name, urlOptions), username, password)
  private val poolableConnectionFactory = new PoolableConnectionFactory(connectionFactory, null)
  private val connectionPool = new GenericObjectPool(poolableConnectionFactory, config)
  private val poolingDataSource = new PoolingDataSource(connectionPool)
  poolingDataSource.setAccessToUnderlyingConnectionAllowed(true)
  poolableConnectionFactory.setConnectionInitSql(seqAsJavaList(Seq("/* ping */ SELECT 1")))
  poolableConnectionFactory.setDefaultAutoCommit(true)

  def close(connection: Connection) {
    try {
      connection.close()
    } catch {
      case _: SQLException =>
    }
  }

  def shutdown() { connectionPool.close() }

  def open() = poolingDataSource.getConnection()

  override def toString: String = hosts.head + "_" + name
}
