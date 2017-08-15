#@namespace scala org.globalnames.index.thrift.matcher

include "finatra-thrift/finatra_thrift_exceptions.thrift"
include "commondata.thrift"

struct Result {
    1: commondata.Name nameMatched
    2: i32 distance
    3: commondata.MatchKind matchKind
}

struct Response {
    1: commondata.Uuid inputUuid
    2: list<Result> results
}

service Service {
    list<Response> findMatches(1: list<string> canonicalNames, list<i32> dataSourceIds) throws (
        1: finatra_thrift_exceptions.ClientError clientError,
        2: finatra_thrift_exceptions.ServerError serverError
    )
}
