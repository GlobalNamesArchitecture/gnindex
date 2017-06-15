#@namespace scala org.globalnames.index.thrift.matcher

include "finatra-thrift/finatra_thrift_exceptions.thrift"

struct Result {
    1: string value
    2: i16 distance
}

service Service {
    list<Result> findMatches(1: string word) throws (
        1: finatra_thrift_exceptions.ClientError clientError,
        2: finatra_thrift_exceptions.ServerError serverError
    )
}
