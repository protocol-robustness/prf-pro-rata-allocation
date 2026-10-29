//! Candidate EVM deployment-scoped entitlement commitment profile.
//!
//! This is additive and does not change any V1/V2 allocation identity. The
//! ABI/Keccak profile remains a candidate until its shared vectors are adopted
//! by the semantic owner and independently reviewed.

use num_bigint::BigUint;
use sha3::{Digest, Keccak256};

pub const PROFILE_DOMAIN: &str = "prf.pro-rata-entitlement.deployment-scoped.v2";
pub const TABLE_LEAF_DOMAIN: &str = "prf.pro-rata-entitlement.table-leaf.v2";
pub const TABLE_NODE_DOMAIN: &str = "prf.pro-rata-entitlement.table-node.v2";
pub const TABLE_ROOT_DOMAIN: &str = "prf.pro-rata-entitlement.table-root.v2";
pub const CLAIM_LEAF_DOMAIN: &str = "prf.pro-rata-entitlement.claim-leaf.v2";
pub const CLAIM_NODE_DOMAIN: &str = "prf.pro-rata-entitlement.claim-node.v2";
pub const CLAIM_ROOT_DOMAIN: &str = "prf.pro-rata-entitlement.claim-root.v2";

#[derive(Clone, Debug, PartialEq, Eq)]
pub enum DeploymentScopedClaimError {
    Uint256OutOfRange,
    UnsupportedProofMode,
    EmptyTree,
    ZeroEntitlementCount,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct DeploymentScopedClaimV2 {
    pub chain_id: BigUint,
    pub chain_instance_genesis_root: [u8; 32],
    pub allocator: [u8; 20],
    pub asset: [u8; 20],
    pub operation_id: [u8; 32],
    pub allocation_binding_root: [u8; 32],
    pub policy_profile: [u8; 32],
    /// 0 = Requested, 1 = Required.
    pub immutable_minimum_proof_mode: u8,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct EntitlementOpeningV2 {
    pub index: BigUint,
    pub claim_identity: [u8; 32],
    pub beneficiary: [u8; 20],
    pub amount_base_units: BigUint,
    pub opening_salt: [u8; 32],
}

fn keccak256(bytes: &[u8]) -> [u8; 32] {
    Keccak256::digest(bytes).into()
}

fn domain_word(domain: &str) -> [u8; 32] {
    keccak256(domain.as_bytes())
}

fn uint256_word(value: &BigUint) -> Result<[u8; 32], DeploymentScopedClaimError> {
    if value.bits() > 256 {
        return Err(DeploymentScopedClaimError::Uint256OutOfRange);
    }
    let encoded = value.to_bytes_be();
    let mut word = [0u8; 32];
    word[32 - encoded.len()..].copy_from_slice(&encoded);
    Ok(word)
}

fn address_word(address: &[u8; 20]) -> [u8; 32] {
    let mut word = [0u8; 32];
    word[12..].copy_from_slice(address);
    word
}

fn abi_encode_words(words: &[[u8; 32]]) -> Vec<u8> {
    words.iter().flat_map(|word| word.iter().copied()).collect()
}

fn context_words(
    context: &DeploymentScopedClaimV2,
    opening: &EntitlementOpeningV2,
    leaf_domain: &str,
) -> Result<Vec<[u8; 32]>, DeploymentScopedClaimError> {
    if context.immutable_minimum_proof_mode > 1 {
        return Err(DeploymentScopedClaimError::UnsupportedProofMode);
    }
    Ok(vec![
        domain_word(PROFILE_DOMAIN),
        domain_word(leaf_domain),
        uint256_word(&context.chain_id)?,
        context.chain_instance_genesis_root,
        address_word(&context.allocator),
        address_word(&context.asset),
        context.operation_id,
        context.allocation_binding_root,
        uint256_word(&opening.index)?,
        opening.claim_identity,
        address_word(&opening.beneficiary),
        uint256_word(&opening.amount_base_units)?,
        context.policy_profile,
        uint256_word(&BigUint::from(context.immutable_minimum_proof_mode))?,
    ])
}

/// Derive the table membership leaf. The opening salt is included only here.
pub fn entitlement_table_leaf_v2(
    context: &DeploymentScopedClaimV2,
    opening: &EntitlementOpeningV2,
) -> Result<[u8; 32], DeploymentScopedClaimError> {
    let mut words = context_words(context, opening, TABLE_LEAF_DOMAIN)?;
    words.push(opening.opening_salt);
    Ok(keccak256(&abi_encode_words(&words)))
}

/// Derive the allocation-claim leaf. The opening salt is not part of this leaf.
pub fn allocation_claim_leaf_v2(
    context: &DeploymentScopedClaimV2,
    opening: &EntitlementOpeningV2,
) -> Result<[u8; 32], DeploymentScopedClaimError> {
    let words = context_words(context, opening, CLAIM_LEAF_DOMAIN)?;
    Ok(keccak256(&abi_encode_words(&words)))
}

fn node_hash(domain: &str, left: &[u8; 32], right: &[u8; 32]) -> [u8; 32] {
    let mut preimage = Vec::with_capacity(96);
    preimage.extend_from_slice(&domain_word(domain));
    preimage.extend_from_slice(left);
    preimage.extend_from_slice(right);
    keccak256(&preimage)
}

/// Derive an ordered duplicate-last tree root from member leaves.
pub fn ordered_tree_root_v2(
    leaves: &[[u8; 32]],
    node_domain: &str,
) -> Result<[u8; 32], DeploymentScopedClaimError> {
    if leaves.is_empty() {
        return Err(DeploymentScopedClaimError::EmptyTree);
    }
    let mut level = leaves.to_vec();
    while level.len() > 1 {
        let mut next = Vec::with_capacity((level.len() + 1) / 2);
        for pair in level.chunks(2) {
            let right = pair.get(1).unwrap_or(&pair[0]);
            next.push(node_hash(node_domain, &pair[0], right));
        }
        level = next;
    }
    Ok(level[0])
}

fn root_envelope_v2(
    root_domain: &str,
    members_root: &[u8; 32],
    count: &BigUint,
    total_amount_base_units: &BigUint,
) -> Result<[u8; 32], DeploymentScopedClaimError> {
    if count == &BigUint::from(0u8) {
        return Err(DeploymentScopedClaimError::ZeroEntitlementCount);
    }
    let words = [
        domain_word(root_domain),
        *members_root,
        uint256_word(count)?,
        uint256_word(total_amount_base_units)?,
    ];
    Ok(keccak256(&abi_encode_words(&words)))
}

pub fn entitlement_table_root_v2(
    members_root: &[u8; 32],
    count: &BigUint,
    total_amount_base_units: &BigUint,
) -> Result<[u8; 32], DeploymentScopedClaimError> {
    root_envelope_v2(
        TABLE_ROOT_DOMAIN,
        members_root,
        count,
        total_amount_base_units,
    )
}

pub fn allocation_claim_root_v2(
    members_root: &[u8; 32],
    count: &BigUint,
    total_amount_base_units: &BigUint,
) -> Result<[u8; 32], DeploymentScopedClaimError> {
    root_envelope_v2(
        CLAIM_ROOT_DOMAIN,
        members_root,
        count,
        total_amount_base_units,
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use hex::decode;
    use std::collections::HashMap;

    fn vector_properties() -> HashMap<&'static str, &'static str> {
        include_str!(
            "../../clj/test/fixtures/deployment-scoped-entitlement-v2.candidate.properties"
        )
        .lines()
        .filter(|line| !line.trim().is_empty() && !line.trim_start().starts_with('#'))
        .filter_map(|line| line.split_once('='))
        .collect()
    }

    fn bytes32(value: &str) -> [u8; 32] {
        decode(value.trim_start_matches("0x"))
            .unwrap()
            .try_into()
            .unwrap()
    }

    fn address(value: &str) -> [u8; 20] {
        decode(value.trim_start_matches("0x"))
            .unwrap()
            .try_into()
            .unwrap()
    }

    fn candidate_vector() -> (DeploymentScopedClaimV2, EntitlementOpeningV2) {
        let properties = vector_properties();
        let value = |key| {
            properties
                .get(key)
                .copied()
                .expect("candidate vector field")
        };
        (
            DeploymentScopedClaimV2 {
                chain_id: BigUint::from(value("chain_id").parse::<u32>().unwrap()),
                chain_instance_genesis_root: bytes32(value("chain_instance_genesis_root")),
                allocator: address(value("allocator")),
                asset: address(value("asset")),
                operation_id: bytes32(value("operation_id")),
                allocation_binding_root: bytes32(value("allocation_binding_root")),
                policy_profile: bytes32(value("policy_profile")),
                immutable_minimum_proof_mode: value("immutable_minimum_proof_mode")
                    .parse()
                    .unwrap(),
            },
            EntitlementOpeningV2 {
                index: BigUint::from(value("index").parse::<u32>().unwrap()),
                claim_identity: bytes32(value("claim_identity")),
                beneficiary: address(value("beneficiary")),
                amount_base_units: BigUint::from(
                    value("amount_base_units").parse::<u32>().unwrap(),
                ),
                opening_salt: bytes32(value("opening_salt")),
            },
        )
    }

    #[test]
    fn candidate_leaf_and_root_match_solidity_cast_vector() {
        let properties = vector_properties();
        let expected = |key| {
            properties
                .get(key)
                .copied()
                .unwrap()
                .trim_start_matches("0x")
        };
        let (context, opening) = candidate_vector();
        let table_leaf = entitlement_table_leaf_v2(&context, &opening).unwrap();
        let claim_leaf = allocation_claim_leaf_v2(&context, &opening).unwrap();
        assert_eq!(
            hex::encode(table_leaf),
            expected("expected.entitlement_leaf")
        );
        assert_eq!(
            hex::encode(claim_leaf),
            expected("expected.allocation_claim_leaf")
        );
        assert_eq!(
            hex::encode(entitlement_table_root_v2(&table_leaf, &1u8.into(), &10u8.into()).unwrap()),
            expected("expected.entitlement_table_root")
        );
        assert_eq!(
            hex::encode(allocation_claim_root_v2(&claim_leaf, &1u8.into(), &10u8.into()).unwrap()),
            expected("expected.allocation_claim_root")
        );
    }

    #[test]
    fn candidate_odd_width_vector_matches_shared_golden_roots_and_proof() {
        let properties = vector_properties();
        let value = |key: String| {
            properties
                .get(key.as_str())
                .copied()
                .unwrap_or_else(|| panic!("candidate vector field {key}"))
        };
        let expected = |key: String| value(key).trim_start_matches("0x").to_owned();
        let (context, _) = candidate_vector();
        let openings: Vec<EntitlementOpeningV2> = (0..3)
            .map(|index| {
                let prefix = format!("odd_width.opening.{index}");
                EntitlementOpeningV2 {
                    index: BigUint::from(value(format!("{prefix}.index")).parse::<u32>().unwrap()),
                    claim_identity: bytes32(value(format!("{prefix}.claim_identity"))),
                    beneficiary: address(value(format!("{prefix}.beneficiary"))),
                    amount_base_units: BigUint::from(
                        value(format!("{prefix}.amount_base_units")).parse::<u32>().unwrap(),
                    ),
                    opening_salt: bytes32(value(format!("{prefix}.opening_salt"))),
                }
            })
            .collect();
        let table_leaves: Vec<[u8; 32]> = openings
            .iter()
            .map(|opening| entitlement_table_leaf_v2(&context, opening).unwrap())
            .collect();
        let claim_leaves: Vec<[u8; 32]> = openings
            .iter()
            .map(|opening| allocation_claim_leaf_v2(&context, opening).unwrap())
            .collect();
        let table_pair = ordered_tree_root_v2(&table_leaves[..2], TABLE_NODE_DOMAIN).unwrap();
        let table_duplicate_last =
            ordered_tree_root_v2(&[table_leaves[2], table_leaves[2]], TABLE_NODE_DOMAIN).unwrap();
        let table_members_root = ordered_tree_root_v2(&table_leaves, TABLE_NODE_DOMAIN).unwrap();
        let claim_pair = ordered_tree_root_v2(&claim_leaves[..2], CLAIM_NODE_DOMAIN).unwrap();
        let claim_duplicate_last =
            ordered_tree_root_v2(&[claim_leaves[2], claim_leaves[2]], CLAIM_NODE_DOMAIN).unwrap();
        let claim_members_root = ordered_tree_root_v2(&claim_leaves, CLAIM_NODE_DOMAIN).unwrap();
        let count = BigUint::from(value("odd_width.count".to_owned()).parse::<u32>().unwrap());
        let total = BigUint::from(
            value("odd_width.total_amount_base_units".to_owned())
                .parse::<u32>()
                .unwrap(),
        );

        let hexes = |values: &[[u8; 32]]| values.iter().map(hex::encode).collect::<Vec<_>>();
        assert_eq!(
            hexes(&table_leaves),
            (0..3)
                .map(|index| expected(format!("expected.odd_width.entitlement_leaf.{index}")))
                .collect::<Vec<_>>()
        );
        assert_eq!(
            hexes(&claim_leaves),
            (0..3)
                .map(|index| expected(format!("expected.odd_width.allocation_claim_leaf.{index}")))
                .collect::<Vec<_>>()
        );
        assert_eq!(
            hex::encode(table_pair),
            expected("expected.odd_width.entitlement_pair.01".to_owned())
        );
        assert_eq!(
            hex::encode(table_duplicate_last),
            expected("expected.odd_width.entitlement_duplicate_last_node".to_owned())
        );
        assert_eq!(
            hex::encode(table_members_root),
            expected("expected.odd_width.entitlement_members_root".to_owned())
        );
        assert_eq!(
            hex::encode(entitlement_table_root_v2(&table_members_root, &count, &total).unwrap()),
            expected("expected.odd_width.entitlement_table_root".to_owned())
        );
        assert_eq!(
            hex::encode(claim_pair),
            expected("expected.odd_width.allocation_claim_pair.01".to_owned())
        );
        assert_eq!(
            hex::encode(claim_duplicate_last),
            expected("expected.odd_width.allocation_claim_duplicate_last_node".to_owned())
        );
        assert_eq!(
            hex::encode(claim_members_root),
            expected("expected.odd_width.allocation_claim_members_root".to_owned())
        );
        assert_eq!(
            hex::encode(allocation_claim_root_v2(&claim_members_root, &count, &total).unwrap()),
            expected("expected.odd_width.allocation_claim_root".to_owned())
        );
        assert_eq!(
            hex::encode(table_leaves[2]),
            expected("expected.odd_width.index_2_entitlement_sibling.0".to_owned())
        );
        assert_eq!(
            hex::encode(table_pair),
            expected("expected.odd_width.index_2_entitlement_sibling.1".to_owned())
        );
        assert_eq!(
            hex::encode(claim_leaves[2]),
            expected("expected.odd_width.index_2_allocation_claim_sibling.0".to_owned())
        );
        assert_eq!(
            hex::encode(claim_pair),
            expected("expected.odd_width.index_2_allocation_claim_sibling.1".to_owned())
        );
    }

    #[test]
    fn out_of_range_uint_and_unknown_mode_fail_closed() {
        let (mut context, opening) = candidate_vector();
        context.chain_id = BigUint::from(1u8) << 256usize;
        assert_eq!(
            entitlement_table_leaf_v2(&context, &opening),
            Err(DeploymentScopedClaimError::Uint256OutOfRange)
        );
        let (mut context, opening) = candidate_vector();
        context.immutable_minimum_proof_mode = 2;
        assert_eq!(
            entitlement_table_leaf_v2(&context, &opening),
            Err(DeploymentScopedClaimError::UnsupportedProofMode)
        );
    }

    #[test]
    fn ordered_tree_uses_left_right_order_and_candidate_node_domain() {
        let expected = vector_properties()
            .get("expected.two_leaf_table_node_root")
            .copied()
            .unwrap()
            .trim_start_matches("0x");
        let left = {
            let mut value = [0u8; 32];
            value[31] = 1;
            value
        };
        let right = {
            let mut value = [0u8; 32];
            value[31] = 2;
            value
        };
        assert_eq!(
            hex::encode(ordered_tree_root_v2(&[left, right], TABLE_NODE_DOMAIN).unwrap()),
            expected
        );
        assert_ne!(
            ordered_tree_root_v2(&[left, right], TABLE_NODE_DOMAIN).unwrap(),
            ordered_tree_root_v2(&[right, left], TABLE_NODE_DOMAIN).unwrap()
        );
    }

    #[test]
    fn entitlement_fields_and_salt_have_the_declared_commitment_scope() {
        let (context, opening) = candidate_vector();
        let table_leaf = entitlement_table_leaf_v2(&context, &opening).unwrap();
        let claim_leaf = allocation_claim_leaf_v2(&context, &opening).unwrap();
        let mut changed_context = context.clone();
        changed_context.allocation_binding_root[0] ^= 1;
        assert_ne!(
            table_leaf,
            entitlement_table_leaf_v2(&changed_context, &opening).unwrap()
        );
        let mut changed_opening = opening.clone();
        changed_opening.index = BigUint::from(1u8);
        assert_ne!(
            table_leaf,
            entitlement_table_leaf_v2(&context, &changed_opening).unwrap()
        );
        assert_ne!(
            claim_leaf,
            allocation_claim_leaf_v2(&context, &changed_opening).unwrap()
        );
        changed_opening = opening.clone();
        changed_opening.opening_salt[0] ^= 1;
        assert_ne!(
            table_leaf,
            entitlement_table_leaf_v2(&context, &changed_opening).unwrap()
        );
        assert_eq!(
            claim_leaf,
            allocation_claim_leaf_v2(&context, &changed_opening).unwrap()
        );
    }
}
