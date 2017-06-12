#@namespace scala org.globalnames.microservices.index.nameresolve.thriftscala

include "finatra-thrift/finatra_thrift_exceptions.thrift"

const i32 nameStringsMaxCount = 1000

struct NameRequest {
    1: string value
    2: optional string suppliedId
}

struct Request {
    1: list<NameRequest> names
    2: optional list<i32> dataSourceIds
    3: i32 page = 0
    4: i32 perPage = nameStringsMaxCount
}

struct Name {
    1: string uuid
    2: string value
}

struct Result {
    1: Name name
    2: optional Name canonicalName
}

struct Response {
    1: i32 total
    2: optional string suppliedInput
    3: optional string suppliedId
    4: list<Result> results
}
