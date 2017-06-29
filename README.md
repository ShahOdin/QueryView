# QueryView
PersistentView is now deprecated in Akka. QueryView is a lightweight implementation of PersistentView, which snapshots its local cache of the data it builds up from the events related to `PersistentActor` of interest. it does not persist its data and relies only on regular snapshotting of the data it reads. 

`QueryView` will be parameterised on a class mixing in the `SnapshottableQuerriedData` which contains the journal event offset data as well as the data to be cached.
