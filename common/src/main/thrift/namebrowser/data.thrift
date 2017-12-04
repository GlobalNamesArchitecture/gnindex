#@namespace scala org.globalnames.index.thrift.namebrowser

include "finatra-thrift/finatra_thrift_exceptions.thrift"
include "../commondata.thrift"

struct Triplet {
    1: string value
    2: bool active
}
