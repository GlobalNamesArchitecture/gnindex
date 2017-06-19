#@namespace scala org.globalnames.index.thrift.matcher

include "finatra-thrift/finatra_thrift_exceptions.thrift"

struct Result {
    1: string value
    2: i16 distance
}

struct Response {
    1: string input
    2: list<Result> results
}

service Service {
    list<Response> findMatches(1: list<string> words) throws (
        1: finatra_thrift_exceptions.ClientError clientError,
        2: finatra_thrift_exceptions.ServerError serverError
    )
}
