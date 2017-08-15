#@namespace scala org.globalnames.index.thrift.namefilter

include "finatra-thrift/finatra_thrift_exceptions.thrift"
include "namefilter/data.thrift"
include "commondata.thrift"

service Service {
    list<commondata.ResultScored> nameString(1: data.Request request) throws (
        1: finatra_thrift_exceptions.ClientError clientError,
        2: finatra_thrift_exceptions.ServerError serverError
    )
}
