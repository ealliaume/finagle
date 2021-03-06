package com.twitter.finagle.service

import java.util.concurrent.atomic.AtomicInteger

import com.twitter.util.{Future, Time, Throw}
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.finagle.{Service, ServiceFactory, SimpleFilter}

class StatsFilter[Req, Rep](statsReceiver: StatsReceiver)
  extends SimpleFilter[Req, Rep]
{
  private[this] val outstandingRequestCount = new AtomicInteger(0)
  private[this] val dispatchCount = statsReceiver.counter("requests")
  private[this] val successCount = statsReceiver.counter("success")
  private[this] val latencyStat = statsReceiver.stat("request_latency_ms")
  private[this] val outstandingRequestCountgauge =
    statsReceiver.addGauge("pending") { outstandingRequestCount.get }

  def apply(request: Req, service: Service[Req, Rep]): Future[Rep] = {
    val requestedAt = Time.now
    dispatchCount.incr()

    outstandingRequestCount.incrementAndGet()
    val result = service(request)

    result respond { response =>
      outstandingRequestCount.decrementAndGet()
      latencyStat.add(requestedAt.untilNow.inMilliseconds)
      response match {
        case Throw(e) =>
          statsReceiver.scope("failures").counter(e.getClass.getName).incr()
        case _ =>
          successCount.incr()
      }
    }

    result
  }
}
