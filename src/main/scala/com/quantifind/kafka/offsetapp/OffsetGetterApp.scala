package com.quantifind.kafka.offsetapp

import java.text.NumberFormat

import com.quantifind.kafka.OffsetGetter.OffsetInfo

import scala.concurrent.duration._

import com.quantifind.sumac.{ ArgMain, FieldArgs }
import com.quantifind.sumac.validation.Required
import kafka.utils.ZKStringSerializer
import org.I0Itec.zkclient.ZkClient
import com.quantifind.kafka.OffsetGetter

import scala.sys.process._

class OffsetGetterArgsWGT extends OffsetGetterArgs {
  @Required
  var prefix: String = _

  var group: String = _

  var topics: Seq[String] = Seq()

  var refresh: FiniteDuration = _

  var graphite: String = _

  var combine: Boolean = true

  var onlyOffsets: Boolean = false

  // if you want your lag results formatted with thousands separator
  var formatLagOutput = false
}

class OffsetGetterArgs extends FieldArgs {
  @Required
  var zk: String = _

  var zkSessionTimeout: Duration = 30 seconds
  var zkConnectionTimeout: Duration = 30 seconds

}

case class Metric(prefix: String, group: String, topic: String, partition: Option[String], metric: String, value: Long, timestamp: Long) {
  def combinedKey = {
    val has_partition = partition.getOrElse("all")
    Array(prefix, group, topic, has_partition, metric).mkString(".")
  }
  override def toString = {
    s"$combinedKey $value $timestamp"
  }
  def send(host: String, port: String) = {
    println(toString)
    (s"echo $combinedKey $value $timestamp" #| s"nc -q0 $host $port")!
  }
}

/**
 * TODO DOC
 * User: pierre
 * Date: 1/22/14
 */
object OffsetGetterApp extends ArgMain[OffsetGetterArgsWGT] {

  def main(args: OffsetGetterArgsWGT) {
    val graphite_host = args.graphite.split(":")(0)
    val graphite_port = args.graphite.split(":")(1)
    var zkClient: ZkClient = null
    var og: OffsetGetter = null
    try {
      zkClient = new ZkClient(args.zk,
        args.zkSessionTimeout.toMillis.toInt,
        args.zkConnectionTimeout.toMillis.toInt,
        ZKStringSerializer)
      og = new OffsetGetter(zkClient)
      //val graphite = new Graphite(args.graphite.split(":")(0), args.graphite.split(":")(1))
      while (true) {
        val i = og.getInfo(args.group, args.topics)
        val timestamp: Long = System.currentTimeMillis / 1000
        if (i.offsets.nonEmpty) {
          if (args.combine) {
            i.offsets.groupBy(info => (info.group, info.topic))
              .flatMap {
              case (_, infoGrp) =>
                infoGrp.headOption map { head =>
                  val (offset, log, lag) = infoGrp.foldLeft((0l, 0l, 0l)) {
                    case ((offAcc, logAcc, lagAcc), info) =>
                      (offAcc + info.offset, logAcc + info.logSize, lagAcc + info.lag)
                  }
                  val fmtedLag = if (args.formatLagOutput) NumberFormat.getIntegerInstance().format(lag) else lag
                  List(
                    Metric(args.prefix, head.group, head.topic, None, "offset", offset, timestamp),
                    Metric(args.prefix, head.group, head.topic, None, "logsize", log, timestamp),
                    Metric(args.prefix, head.group, head.topic, None, "lag", lag, timestamp)
                  )
                }
            }.flatten.foreach(_.send(graphite_host, graphite_port))
          } else {
            i.offsets.map {
              info =>
                val fmtedLag = if (args.formatLagOutput) NumberFormat.getIntegerInstance().format(info.lag) else info.lag
                List(
                  Metric(args.prefix, info.group, info.topic, Some(info.partition.toString), "offset", info.offset, timestamp),
                  Metric(args.prefix, info.group, info.topic, Some(info.partition.toString), "logsize", info.logSize, timestamp),
                  Metric(args.prefix, info.group, info.topic, Some(info.partition.toString), "lag", info.lag, timestamp)
                )
            }.flatten.foreach(_.send(graphite_host, graphite_port))
          }
        } else {
          System.err.println(s"no topics for group ${args.group}")
        }
        //Every n seconds
        Thread.sleep(args.refresh.toMillis)
      }
    } finally {
      if (og != null) og.close()
      if (zkClient != null)
        zkClient.close()
    }
  }

}

