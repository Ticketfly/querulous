package com.twitter.querulous.unit

import scala.concurrent.duration.{ Duration => D }
import scala.concurrent.duration._
import org.apache.commons.pool2.ObjectPool
import org.specs2.mutable._ // Specification
import org.specs2.mock.Mockito
import org.specs2.matcher._
import org.specs2.time.TimeConversions
import com.twitter.querulous.database._
import java.sql.{SQLException, Connection}

class PooledConnectionSpec extends Specification with Mockito {
  "PooledConnectionSpec" should {
    "return to the pool" in {
      val p = mock[ObjectPool[PooledConnection]]
      val c = mock[Connection]
      val conn = new PooledConnection(c, p)

      c.isClosed() returns false 
      
      //there was one(c).isClosed() willReturn false
      //there was one(p).returnObject(conn)

      conn.close()

      got {
        one(c).isClosed()
        one(p).returnObject(conn)
      }
    }

   "eject from the pool only once" in {
      val p = mock[ObjectPool[PooledConnection]]
      val c = mock[Connection]
      val conn = new PooledConnection(c, p)


      c.isClosed() returns true
      c.isClosed() returns true

      //there was one(c).isClosed() willReturn true
      //there was one(p).invalidateObject(conn)
      //there was one(c).isClosed() willReturn true
      

      conn.close() must throwA[SQLException]
      conn.close() must throwA[SQLException]

      there was
        two(c).isClosed() andThen
        one(p).invalidateObject(conn)
    }
  }
}

class ThrottledPoolSpec extends Specification with Mockito {
  "ThrottledPoolSpec" should {
    val connection = mock[Connection]

    val repopulateInterval = D(250, MILLISECONDS )
    val idleTimeout = D(50, MILLISECONDS)
    def createPool(size: Int) = { new ThrottledPool( { () => connection }, size, D(10, MILLISECONDS), D(50,MILLISECONDS), "test") }

    "create and populate" in {
      val pool = createPool(5)

      pool.getTotal() mustEqual 5
    }

    "successfully construct if connections fail to create" in {
      val pool = new ThrottledPool( { () => throw new Exception("blah!") }, 5, D(10, MILLISECONDS), D(50, MILLISECONDS), "test")

      pool.getTotal() mustEqual 0
    }

    "checkout" in {
      val pool = createPool(5)

      pool.getTotal() mustEqual 5

      pool.borrowObject()

      pool.getNumActive() mustEqual 1
      pool.getNumIdle() mustEqual 4
    }

    "return" in {
      val pool = createPool(5)

      pool.getTotal() mustEqual 5

      val conn = pool.borrowObject()

      pool.getNumActive() mustEqual 1

      pool.returnObject(conn)

      pool.getNumActive() mustEqual 0
      pool.getNumIdle() mustEqual 5
    }

    "timeout" in {
      val pool = createPool(1)

      pool.getTotal() mustEqual 1

      pool.borrowObject()

      pool.getNumIdle() mustEqual 0
      pool.borrowObject() must throwA[PoolTimeoutException]
    }

    "fast fail when the pool is empty" in {
      val pool = createPool(0)

      pool.getTotal() mustEqual 0
      pool.borrowObject() must throwA[PoolEmptyException]
    }

    "eject idle" in {
      //expect {
        //there was atLeastOne(connection).close() // allowing(connection).close()
      //}

      val pool = createPool(5)

      pool.getTotal() mustEqual 5

      Thread.sleep(idleTimeout.toMillis + 5)
      pool.borrowObject()

      pool.getTotal() mustEqual 1
      there was atLeastOne(connection).close()
    }

    "repopulate" in {
      val pool = createPool(2)
      val conn = pool.borrowObject()

      pool.invalidateObject(conn)
      pool.getTotal() mustEqual 1

      val conn2 = pool.borrowObject()

      pool.invalidateObject(conn2)
      pool.getTotal() mustEqual 0
      new PoolWatchdogThread(pool, List(""), repopulateInterval).start()
      // 4 retries give us about 300ms to check whether the condition is finally met
      // because first check is applied immediately
      pool.getTotal() must eventually(4, TimeConversions.intToRichLong(100).millis) (be_==(1))
      pool.getTotal() must eventually(4, TimeConversions.intToRichLong(100).millis) (be_==(2))
      Thread.sleep(repopulateInterval.toMillis + 100)

      // make sure that the watchdog thread won't add more connections than the size of the pool
      pool.getTotal() must be_==(2)
    }
  }
}
