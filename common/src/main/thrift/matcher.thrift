#@namespace scala org.globalnames.index.thrift.matcher

include "finatra-thrift/finatra_thrift_exceptions.thrift"
include "commondata.thrift"

struct Result {
    1: commondata.Name nameMatched
    2: commondata.MatchKind matchKind
}

struct Response {
    1: commondata.Uuid inputUuid
    2: list<Result> results
}

service Service {
    list<Response> findMatches(1: list<string> canonicalNames,
                               2: list<i32> dataSourceIds,
                               3: bool advancedResolution) throws (
        1: finatra_thrift_exceptions.ClientError clientError,
        2: finatra_thrift_exceptions.ServerError serverError
    )
}
