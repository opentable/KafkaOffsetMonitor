Kafka Offset Monitor
===========

[![Build Status](https://travis-ci.org/quantifind/KafkaOffsetMonitor.svg?branch=master)](https://travis-ci.org/quantifind/KafkaOffsetMonitor)

This is an app to monitor your kafka consumers and their position (offset) in the queue.

You can see the current consumer groups, for each group the topics that they are consuming and the position of the group in each topic queue. This is useful to understand how quick you are consuming from a queue and how fast the queue is growing. It allows for debuging kafka producers and consumers or just to have an idea of what is going on in  your system.

The app keeps an history of queue position and lag of the consumers so you can have an overview of what has happened in the last days.

Here are a few screenshots:

List of Consumer Groups
-----------------------

![Consumer Groups](http://quantifind.github.io/KafkaOffsetMonitor/img/groups.png)

List of Topics for a Group
--------------------------

![Topic List](http://quantifind.github.io/KafkaOffsetMonitor/img/topics.png)

History of Topic position
-------------------------

![Position Graph](http://quantifind.github.io/KafkaOffsetMonitor/img/graph.png)

Running It
===========

If you do not want to build it manually, just download the [current jar](https://github.com/quantifind/KafkaOffsetMonitor/releases/latest).

This is a small webapp, you can run it locally or on a server, as long as you have access to the ZooKeeper nodes controlling kafka.

```
java -cp KafkaOffsetMonitor-assembly-0.2.1-SNAPSHOT.jar \
     com.quantifind.kafka.offsetapp.OffsetGetterWeb \
     --zk zk-server1,zk-server2 \
     --port 8080 \
     --refresh 10.seconds \
     --retain 2.days
```

The arguments are:

- **zk** the ZooKeeper hosts
- **port** on what port will the app be available
- **refresh** how often should the app refresh and store a point in the DB
- **retain** how long should points be kept in the DB
- **dbName** where to store the history (default 'offsetapp')

To Run it as a daemon, exporting graphite metrics
=======================================
    java -cp KafkaOffsetMonitor-assembly-0.2.1-SNAPSHOT.jar \
         com.quantifind.kafka.offsetapp.OffsetGetterApp \
         --zk zk-server1,zk-server2:2181/kafka \
         --prefix KafkaOffsetMonitor.kafka.production.dc1 \
         --group MirrorMaker \
         --topics weblogs \
         --refresh 7.seconds \
         --graphite graphite-cluster.domain.com:2003

The arguments are:

- **zk** the ZooKeeper hosts
- **prefix** an arbitrary doted prefix for your graphite metric
- **group** Kafka consumer group
- **topics** coma separated list of Kafka topics
- **refresh** how often should the app refresh and send the metrics to graphite
- **graphite** host:port combination of the graphite carbon endpoints