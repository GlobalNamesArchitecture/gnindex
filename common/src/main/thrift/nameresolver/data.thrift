#@namespace scala org.globalnames.index.thrift.nameresolver

include "finatra-thrift/finatra_thrift_exceptions.thrift"
include "../commondata.thrift"

const i32 nameStringsMaxCount = 1000

struct NameInput {
    1: string value
    2: optional string suppliedId
}

struct Request {
    1: list<NameInput> nameInputs
    2: list<i32> dataSourceIds = []
    3: list<i32> preferredDataSourceIds = []
    4: i32 page = 0
    5: i32 perPage = nameStringsMaxCount
    6: bool withSurrogates = false
    7: bool withVernaculars = false
    8: bool advancedResolution = true
    9: bool bestMatchOnly = false
}

struct ResultScored {
    1: commondata.Result result
    2: commondata.Score score
}

struct Response {
    1: i32 total
    2: string suppliedInput
    3: optional string suppliedId
    4: list<ResultScored> resultsScored
    5: commondata.DataSourceQuality datasourceBestQuality
    6: list<ResultScored> preferredResultsScored
    7: i32 matchedDataSources
}

struct Responses {
    1: list<Response> responses
    2: list<commondata.Context> contexts
}
