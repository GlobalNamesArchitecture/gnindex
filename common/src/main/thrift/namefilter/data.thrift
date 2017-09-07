#@namespace scala org.globalnames.index.thrift.namefilter

include "finatra-thrift/finatra_thrift_exceptions.thrift"
include "../commondata.thrift"

const i32 nameStringsMaxCount = 1000

struct Request {
    1: string searchTerm
    2: i32 page = 0
    3: i32 perPage = nameStringsMaxCount
}

struct NameStringUuidDataSources {
    1: commondata.Uuid nameUuid
    2: list<i32> dataSourceIds
}
