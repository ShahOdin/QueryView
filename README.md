# QueryView
[PersistentView](http://doc.akka.io/japi/akka/2.4/akka/persistence/PersistentView.html) used to be a construct offered in Akka replicating the persistent message stream of a `PersistentActor`. Implementation classes received the message stream directly from the Journal. These messages could be processed to update internal state in order to maintain an (eventual consistent) view of the state of the corresponding persistent actor. It is now deprecated in Akka 2.5. 

`QueryView` is a lightweight implementation of PersistentView, which snapshots its local cache of the data it builds up from the events related to `PersistentActor` of interest. it does not persist its data and relies only on regular snapshotting of the data it reads. 

`QueryView` will be parameterised on a class mixing in the `QueryViewData` which contains the journal event offset data as well as the data to be cached. note that a given QueryView does not have to cache the full set of data it reads from a persistent Actor and can pick and choose the data it caches internally.

The basis of using `PersistenceQuery` can be seen in `QueryInspector` which creates a stream of events from a `PersistentActor` and will receive all the following events from that actor in its inbox. That would be the starting point to look at. `AccountInspectApp` demonstrates this. 

`AccountQueryApp` fires up the persistent actor of interest as well as an account reader. The reader receives not only the events from the `Account` actor before the reader was created, but also the following events. the reader receives the persisted events from `Account` and updates its cache and responds to read queries based on its cache of the data at the time. the result will be "eventually consistent" after the reader's cache of data is in sync with the write side. (`Account`)

`AccountOperationsApp` populates the journal with some data and it can be run a few times to populate the journal with some event data.
