#@namespace scala org.globalnames.index.thrift.nameresolver

include "finatra-thrift/finatra_thrift_exceptions.thrift"
include "../commondata.thrift"

const i32 nameStringsMaxCount = 1000

struct NameInput {
    1: string value
    2: optional string suppliedId
}

struct Request {
    1: list<NameInput> names
    2: list<i32> dataSourceIds = []
    3: list<i32> preferredDataSourceIds = []
    4: i32 page = 0
    5: i32 perPage = nameStringsMaxCount
    6: bool withSurrogates = false
    7: bool withVernaculars = false
    8: bool advancedResolution
}

struct Response {
    1: i32 total
    2: optional string suppliedInput
    3: optional string suppliedId
    4: list<commondata.ResultScored> results
    5: list<commondata.ResultScored> preferredResults
}

struct Responses {
    1: list<Response> items
    2: list<commondata.Context> context
}
