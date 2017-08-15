#@namespace scala org.globalnames.index.thrift.nameresolver

include "finatra-thrift/finatra_thrift_exceptions.thrift"
include "nameresolver/data.thrift"

service Service {
    data.Responses nameResolve(1: data.Request request) throws (
        1: finatra_thrift_exceptions.ClientError clientError,
        2: finatra_thrift_exceptions.ServerError serverError
    )
}
