#@namespace scala org.globalnames.index.thrift

struct Uuid {
  /**
   * A string representation of a UUID, in the format of:
   * <pre>
   * 550e8400-e29b-41d4-a716-446655440000
   * </pre>
   */
  1: required string uuidString
}

struct Name {
    1: Uuid uuid
    2: string value
}

enum MatchKind {
    UUIDLookup,
    ExactNameMatchByUUID,
    ExactNameMatchByString,
    ExactCanonicalNameMatchByUUID,
    ExactCanonicalNameMatchByString,
    FuzzyCanonicalMatch,
    FuzzyPartialMatch,
    ExactMatchPartialByGenus,
    ExactPartialMatch,
    Unknown
}

struct MatchType {
    1: MatchKind kind
    2: i32 editDistance
    3: i32 score
}
