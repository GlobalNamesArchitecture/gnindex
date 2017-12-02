#@namespace scala org.globalnames.index.thrift.namefilter

include "finatra-thrift/finatra_thrift_exceptions.thrift"
include "../commondata.thrift"

const i32 nameStringsMaxCount = 1000

struct Request {
    1: string searchTerm
    2: i32 page = 0
    3: i32 perPage = nameStringsMaxCount
}

struct ResultNameStrings {
    1: commondata.Name name
    2: optional commondata.CanonicalName canonicalName
    3: bool synonym
    4: commondata.MatchType matchType
    5: string taxonId
    6: optional string localId
    7: optional string url
    8: commondata.Classification classification
    9: list<commondata.DataSource> dataSources
    10: optional string acceptedTaxonId
    11: commondata.AcceptedName acceptedName
    12: optional commondata.Timestamp updatedAt
}

struct ResponseNameStrings {
    1: i32 page
    2: i32 perPage
    3: i32 pagesCount
    4: i32 resultsCount
    5: list<ResultNameStrings> results
}

struct Response {
    1: commondata.Uuid uuid
    2: list<commondata.Result> names
}
