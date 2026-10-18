# PRF pro-rata allocation

This is a standalone repository candidate for the deterministic pro-rata allocation package. It contains the Clojure and Rust implementations, package-owned fixtures, the frozen portable V1 allocation contract, and pins the shared `prf-canonical` package.

The allocation candidate is source-qualified at revision `e15501efc04254defd85cdd8e5f4dc4ffd5b1805`; this does not promote release qualification. The canonical codec dependency is pinned to `prf-canonical` revision `5e2b520feca812b049ba397b7b3681963c66a48c`.

## Scope

This repository owns allocation arithmetic and its versioned request, policy, result, witness, outcome, and realized-statement projections. It does not own protocol replay, Shortfall policy, custody, capital activation, withdrawals, or deployment authority. Host-specific execution and context derivation remain adapters in consuming projects.

V1 and successor allocation profiles must keep their existing schema versions and hash domains. A successor profile does not rewrite V1 identities.

## Layout

- `clj/`: Clojure API, implementation, fixtures, and conformance runner.
- `rust/`: Rust allocation and identity crate.
- `prf-canonical`: shared Git dependency for the generic Clojure and Rust canonical codec; no implementation is vendored here.
- `SPEC.md`, `contract.edn`, and `ALLOCATION_V1.md`: frozen portable contract and allocation profile.
- `CANONICAL_HASH_SPEC_V1_BINARY_ENCODING_ABI.md`: canonical byte encoding contract.
- `PROTECTED_CAPITAL_PRO_RATA_V2_SEMANTICS.md`: upstream application semantics reference; it does not make V2 custody semantics part of this allocation package.

## Local verification commands

From the repository root:

```sh
(cd clj && clojure -M:conformance)
cargo test --workspace --locked --offline
```

The root Cargo workspace makes the Rust crate addressable as a Git dependency
at a pinned repository revision while retaining the canonical codec as an
immutable Git dependency.

The checked-in release metadata records partial overall conformance. Rust V1 allocation tests are qualified in the source candidate; additional proof-verifier and production Solidity profiles remain outside this package qualification.

## PRF integration

PRF consumes the Clojure package as a local dependency during development. Its executor budgets, progress callbacks, context/lifecycle construction, evidence binding, and compatibility APIs stay in PRF. The allocation package remains the semantic owner of the arithmetic and frozen allocation projections.
