#@namespace scala org.globalnames.index.thrift.namebrowser

include "finatra-thrift/finatra_thrift_exceptions.thrift"
include "namebrowser/data.thrift"
include "commondata.thrift"

service Service {
    list<data.Triplet> tripletsStartingWith(1: byte letter) throws (
        1: finatra_thrift_exceptions.ClientError clientError,
        2: finatra_thrift_exceptions.ServerError serverError
    )
}
