#@namespace scala org.globalnames.index.thrift

struct Uuid {
  1: i64 leastSignificantBits
  2: i64 mostSignificantBits
}

typedef string Timestamp

struct Name {
    1: Uuid uuid
    2: string value
}

struct CanonicalName {
    1: Uuid uuid
    2: string value
    3: string valueRanked
}

struct UuidLookup {
}

struct ExactMatch {
}

struct Unknown {
}

struct CanonicalMatch {
    1: bool partial = false
    2: i32 verbatimEditDistance = 0
    3: i32 stemEditDistance = 0
    4: bool byAbbreviation = 0
}

union MatchKind {
    1: UuidLookup uuidLookup
    2: ExactMatch exactMatch
    3: CanonicalMatch canonicalMatch
    4: Unknown unknown
}

enum DataSourceQuality {
    Curated,
    AutoCurated,
    Unknown
}

struct MatchType {
    1: MatchKind kind
    2: string kindString
    3: i32 score
}

struct AuthorScore {
    1: string authorshipInput
    2: string authorshipMatch
    3: double value
}

struct Score {
    1: optional i32 nameType
    2: AuthorScore authorScore
    3: i32 parsingQuality
    4: optional double value
    5: optional string message
}

struct Classification {
    1: optional string path
    2: optional string pathIds
    3: optional string pathRanks
}

struct DataSource {
    1: i32 id
    2: string title
    3: optional string description
    4: optional string logoUrl
    5: optional string webSiteUrl
    6: optional string dataUrl
    7: optional i32 refreshPeriodDays
    8: optional i32 uniqueNamesCount
    9: optional string createdAt
    10: optional string updatedAt
    11: optional string dataHash
    12: DataSourceQuality quality
    13: i32 recordCount
}

struct AcceptedName {
    1: Name name
    2: optional CanonicalName canonicalName
    3: string taxonId
    4: i32 dataSourceId
}

struct Context {
    1: DataSource dataSource
    2: string clade
}

struct Vernacular {
    1: Uuid id
    2: string name
    3: i32 dataSourceId
}

struct Result {
    1: Name name
    2: optional CanonicalName canonicalName
    3: bool synonym
    4: MatchType matchType
    5: string taxonId
    6: optional string localId
    7: optional string url
    8: Classification classification
    9: DataSource dataSource
    10: optional string acceptedTaxonId
    11: AcceptedName acceptedName
    12: optional Timestamp updatedAt
    13: list<Vernacular> vernaculars
}
