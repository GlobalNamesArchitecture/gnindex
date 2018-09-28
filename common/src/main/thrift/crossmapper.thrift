#@namespace scala org.globalnames.index.thrift.crossmapper

include "finatra-thrift/finatra_thrift_exceptions.thrift"
include "crossmapper/data.thrift"
include "commondata.thrift"

service Service {
    list<data.Result> resolve(1: i32 dbSourceId,
                              2: i32 dbTargetId,
                              3: list<string> localIds) throws (
        1: finatra_thrift_exceptions.ClientError clientError,
        2: finatra_thrift_exceptions.ServerError serverError
    )
}
