#@namespace scala org.globalnames.index.thrift.namefilter

include "finatra-thrift/finatra_thrift_exceptions.thrift"
include "../commondata.thrift"

const i32 nameStringsMaxCount = 1000

struct Request {
    1: string searchTerm
    2: i32 page = 0
    3: i32 perPage = nameStringsMaxCount
    4: list<i32> dataSourceIds
}

struct ResultNameStrings {
    1: commondata.Name name
    2: optional commondata.CanonicalName canonicalName
    3: list<commondata.Result> results
}

struct ResponseNameStrings {
    1: i32 page
    2: i32 perPage
    3: i32 pagesCount
    4: i32 resultsCount
    5: list<ResultNameStrings> resultNameStrings
}

struct Response {
    1: commondata.Uuid uuid
    2: list<commondata.Result> results
}
