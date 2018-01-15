#@namespace scala org.globalnames.index.thrift.namefilter

include "finatra-thrift/finatra_thrift_exceptions.thrift"
include "namefilter/data.thrift"
include "commondata.thrift"

service Service {
    list<data.ResponseNameStrings> nameString(1: data.Request request) throws (
        1: finatra_thrift_exceptions.ClientError clientError,
        2: finatra_thrift_exceptions.ServerError serverError
    )

    list<data.Response> nameStringByUuid(1: list<commondata.Uuid> nameUuids) throws (
        1: finatra_thrift_exceptions.ClientError clientError,
        2: finatra_thrift_exceptions.ServerError serverError
    )

    list<commondata.DataSource> dataSourceById(1: list<i32> dataSourceIds) throws (
        1: finatra_thrift_exceptions.ClientError clientError,
        2: finatra_thrift_exceptions.ServerError serverError
    )
}
