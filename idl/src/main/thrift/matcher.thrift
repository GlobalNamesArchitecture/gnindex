#@namespace scala org.globalnames.microservices.matcher.thriftscala

include "finatra-thrift/finatra_thrift_exceptions.thrift"

service MatcherService {
    list<string> findMatches(1: string word) throws (
        1: finatra_thrift_exceptions.ClientError clientError,
        2: finatra_thrift_exceptions.ServerError serverError
    )
}
