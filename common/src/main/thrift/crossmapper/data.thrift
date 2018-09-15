#@namespace scala org.globalnames.index.thrift.crossmapper

struct Request {
    1: list<i32> dbSinkIds
    2: list<string> localIds
}

struct Source {
    1: i32 dbId
    2: string localId
}

struct Target {
    1: i32 dbSinkId
    2: i32 dbTargetId
    3: string localId
}

struct Result {
    1: Source source
    2: list<Target> target
}
