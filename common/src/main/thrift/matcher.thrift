#@namespace scala org.globalnames.index.thrift.matcher

include "finatra-thrift/finatra_thrift_exceptions.thrift"
include "data.thrift"

struct Result {
    1: data.Name nameMatched
    2: i32 distance
    3: data.MatchKind matchKind
}

struct Response {
    1: data.Name input
    2: list<Result> results
}

service Service {
    list<Response> findMatches(1: list<string> words) throws (
        1: finatra_thrift_exceptions.ClientError clientError,
        2: finatra_thrift_exceptions.ServerError serverError
    )
}
