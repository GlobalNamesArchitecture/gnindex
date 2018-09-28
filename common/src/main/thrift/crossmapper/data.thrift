#@namespace scala org.globalnames.index.thrift.crossmapper

struct Source {
    1: i32 dbId
    2: string localId
}

struct Target {
    1: i32 dbTargetId
    2: string localId
}

struct Result {
    1: Source source
    2: list<Target> target
}
