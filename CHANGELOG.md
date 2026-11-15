# Changelog

## Candidate compatibility note

- Allocation row normalization and largest-remainder tie-breaking now follow
  the documented unsigned canonical-encoding byte order in both Clojure and
  Rust. Requests with row IDs whose typed-value order differs from encoded-byte
  order can produce different row order, tie winners, and committed roots.
  Consumers that persisted roots produced by the prior implementation must
  treat this correction as a compatibility change and qualify regenerated
  artifacts before adoption.
- The Rust result projection now omits a redistribution summary when a pass
  caps every active row and no redistribution round occurs, matching the
  Clojure allocator's V1 result semantics. This corrects affected result roots.
