#@namespace scala org.globalnames.microservices.index.thriftscala

include "finatra-thrift/finatra_thrift_exceptions.thrift"
include "index/nameresolve.thrift"

service IndexService {
    list<nameresolve.Response> nameResolve(1: list<nameresolve.Request> request) throws (
        1: finatra_thrift_exceptions.ClientError clientError,
        2: finatra_thrift_exceptions.ServerError serverError
    )
}
