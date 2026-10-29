(ns prf.pro-rata.deployment-scoped-claim-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [prf.pro-rata.deployment-scoped-claim :as claim]))

(defn- vector-data []
  (let [properties (java.util.Properties.)]
    (with-open [input (io/input-stream
                       "test/fixtures/deployment-scoped-entitlement-v2.candidate.properties")]
      (.load properties input))
    (let [getv #(.getProperty properties %)]
      {:status (getv "status")
       :profile (getv "profile")
       :inputs {:chain-id (Long/parseLong (getv "chain_id"))
                :chain-instance-genesis-root (getv "chain_instance_genesis_root")
                :allocator (getv "allocator") :asset (getv "asset")
                :operation-id (getv "operation_id")
                :allocation-binding-root (getv "allocation_binding_root")
                :index (Long/parseLong (getv "index"))
                :claim-identity (getv "claim_identity") :beneficiary (getv "beneficiary")
                :amount-base-units (Long/parseLong (getv "amount_base_units"))
                :policy-profile (getv "policy_profile")
                :immutable-minimum-proof-mode (Long/parseLong (getv "immutable_minimum_proof_mode"))
                :opening-salt (getv "opening_salt")}
       :expected {:entitlement-leaf (getv "expected.entitlement_leaf")
                  :entitlement-table-root (getv "expected.entitlement_table_root")
                  :allocation-claim-leaf (getv "expected.allocation_claim_leaf")
                  :allocation-claim-root (getv "expected.allocation_claim_root")
                  :two-leaf-table-node-root (getv "expected.two_leaf_table_node_root")}
       :odd-width {:count (Long/parseLong (getv "odd_width.count"))
                   :total-amount-base-units (Long/parseLong (getv "odd_width.total_amount_base_units"))
                   :openings (mapv (fn [index]
                                     {:index (Long/parseLong (getv (str "odd_width.opening." index ".index")))
                                      :claim-identity (getv (str "odd_width.opening." index ".claim_identity"))
                                      :beneficiary (getv (str "odd_width.opening." index ".beneficiary"))
                                      :amount-base-units (Long/parseLong
                                                          (getv (str "odd_width.opening." index ".amount_base_units")))
                                      :opening-salt (getv (str "odd_width.opening." index ".opening_salt"))})
                                   (range 3))
                   :expected {:table-leaves (mapv #(getv (str "expected.odd_width.entitlement_leaf." %))
                                                  (range 3))
                              :table-pair (getv "expected.odd_width.entitlement_pair.01")
                              :table-duplicate-last-node (getv "expected.odd_width.entitlement_duplicate_last_node")
                              :table-members-root (getv "expected.odd_width.entitlement_members_root")
                              :table-root (getv "expected.odd_width.entitlement_table_root")
                              :claim-leaves (mapv #(getv (str "expected.odd_width.allocation_claim_leaf." %))
                                                  (range 3))
                              :claim-pair (getv "expected.odd_width.allocation_claim_pair.01")
                              :claim-duplicate-last-node (getv "expected.odd_width.allocation_claim_duplicate_last_node")
                              :claim-members-root (getv "expected.odd_width.allocation_claim_members_root")
                              :claim-root (getv "expected.odd_width.allocation_claim_root")
                              :table-index-2-siblings (mapv #(getv (str "expected.odd_width.index_2_entitlement_sibling." %))
                                                            (range 2))
                              :claim-index-2-siblings (mapv #(getv (str "expected.odd_width.index_2_allocation_claim_sibling." %))
                                                            (range 2))}}})))

(defn- failure-reason [f]
  (try (f)
       nil
       (catch clojure.lang.ExceptionInfo error
         (:reason (ex-data error)))))

(deftest candidate-profile-matches-shared-candidate-vector
  (let [{:keys [status profile inputs expected]} (vector-data)
        context (select-keys inputs [:chain-id :chain-instance-genesis-root :allocator :asset
                                     :operation-id :allocation-binding-root :policy-profile
                                     :immutable-minimum-proof-mode])
        opening {:index (:index inputs)
                 :claim-identity (:claim-identity inputs)
                 :beneficiary (:beneficiary inputs)
                 :amount-base-units (:amount-base-units inputs)
                 :opening-salt (:opening-salt inputs)}
        table-leaf (claim/entitlement-table-leaf-v2 context opening)
        claim-leaf (claim/allocation-claim-leaf-v2 context opening)]
    (is (= "candidate-not-adopted" status))
    (is (= "prf.pro-rata-entitlement.deployment-scoped.v2" profile))
    (is (= (:entitlement-leaf expected) (claim/bytes->hex table-leaf)))
    (is (= (:entitlement-table-root expected)
           (claim/bytes->hex (claim/entitlement-table-root-v2 table-leaf 1 10))))
    (is (= (:allocation-claim-leaf expected) (claim/bytes->hex claim-leaf)))
    (is (= (:allocation-claim-root expected)
           (claim/bytes->hex (claim/allocation-claim-root-v2 claim-leaf 1 10))))))

(deftest deployment-context-and-proof-profile-are-bound
  (let [{:keys [inputs]} (vector-data)
        context (select-keys inputs [:chain-id :chain-instance-genesis-root :allocator :asset
                                     :operation-id :allocation-binding-root :policy-profile
                                     :immutable-minimum-proof-mode])
        opening {:index (:index inputs) :claim-identity (:claim-identity inputs)
                 :beneficiary (:beneficiary inputs) :amount-base-units (:amount-base-units inputs)
                 :opening-salt (:opening-salt inputs)}
        root (claim/entitlement-table-leaf-v2 context opening)]
    (doseq [[field value] [[:chain-id 31338]
                           [:chain-instance-genesis-root "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"]
                           [:allocator "0x0000000000000000000000000000000000009999"]
                           [:asset "0x0000000000000000000000000000000000009999"]
                           [:operation-id "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"]
                           [:allocation-binding-root "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"]
                           [:policy-profile "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"]
                           [:immutable-minimum-proof-mode 0]]]
      (is (not= (claim/bytes->hex root)
                (claim/bytes->hex
                 (claim/entitlement-table-leaf-v2 (assoc context field value) opening)))
           (str "changed " field " changes commitment")))))

(deftest entitlement-fields-and-salt-have-the-declared-commitment-scope
  (let [{:keys [inputs]} (vector-data)
        context (select-keys inputs [:chain-id :chain-instance-genesis-root :allocator :asset
                                     :operation-id :allocation-binding-root :policy-profile
                                     :immutable-minimum-proof-mode])
        opening {:index (:index inputs) :claim-identity (:claim-identity inputs)
                 :beneficiary (:beneficiary inputs) :amount-base-units (:amount-base-units inputs)
                 :opening-salt (:opening-salt inputs)}
        changed-opening (assoc opening
                               :index 1
                               :claim-identity "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                               :beneficiary "0x0000000000000000000000000000000000009999"
                               :amount-base-units 11)]
    (is (not= (claim/bytes->hex (claim/entitlement-table-leaf-v2 context opening))
              (claim/bytes->hex (claim/entitlement-table-leaf-v2 context changed-opening))))
    (is (not= (claim/bytes->hex (claim/allocation-claim-leaf-v2 context opening))
              (claim/bytes->hex (claim/allocation-claim-leaf-v2 context changed-opening))))
    (is (not= (claim/bytes->hex (claim/entitlement-table-leaf-v2 context opening))
              (claim/bytes->hex (claim/entitlement-table-leaf-v2
                                 context (assoc opening :opening-salt
                                                "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))))
         "table membership binds the opening salt")
    (is (= (claim/bytes->hex (claim/allocation-claim-leaf-v2 context opening))
           (claim/bytes->hex (claim/allocation-claim-leaf-v2
                              context (assoc opening :opening-salt
                                             "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))))
        "allocation-claim identity intentionally excludes the opening salt")))

(deftest malformed-profile-inputs-fail-closed
  (let [{:keys [inputs]} (vector-data)
        context (select-keys inputs [:chain-id :chain-instance-genesis-root :allocator :asset
                                     :operation-id :allocation-binding-root :policy-profile
                                     :immutable-minimum-proof-mode])
        opening {:index (:index inputs) :claim-identity (:claim-identity inputs)
                 :beneficiary (:beneficiary inputs) :amount-base-units (:amount-base-units inputs)
                 :opening-salt (:opening-salt inputs)}]
    (testing "unknown proof mode"
      (is (= :unsupported-proof-mode
             (failure-reason #(claim/entitlement-table-leaf-v2
                               (assoc context :immutable-minimum-proof-mode 2) opening)))))
    (testing "uint256 overflow"
      (is (= :uint256-out-of-range
             (failure-reason #(claim/entitlement-table-leaf-v2
                               (assoc context :chain-id (.shiftLeft java.math.BigInteger/ONE 256)) opening)))))
    (testing "empty tree and zero count"
      (is (= :empty-tree
             (failure-reason #(claim/ordered-tree-root-v2 [] claim/table-node-domain))))
      (is (= :zero-entitlement-count
             (failure-reason #(claim/entitlement-table-root-v2 (:opening-salt inputs) 0 10)))))))

(deftest ordered-tree-vector-is-domain-separated-and-order-sensitive
  (let [left (byte-array (concat (repeat 31 (byte 0)) [(byte 1)]))
        right (byte-array (concat (repeat 31 (byte 0)) [(byte 2)]))]
    (is (= (:two-leaf-table-node-root (:expected (vector-data)))
           (claim/bytes->hex (claim/ordered-tree-root-v2
                             [left right] claim/table-node-domain))))
    (is (not= (claim/bytes->hex (claim/ordered-tree-root-v2
                                [left right] claim/table-node-domain))
              (claim/bytes->hex (claim/ordered-tree-root-v2
                                [right left] claim/table-node-domain))))
    (is (not= (claim/bytes->hex (claim/ordered-tree-root-v2
                                [left right] claim/table-node-domain))
              (claim/bytes->hex (claim/ordered-tree-root-v2
                                [left right] claim/claim-node-domain))))))

(deftest candidate-odd-width-vector-matches-shared-golden-roots-and-proof
  (let [{:keys [inputs odd-width]} (vector-data)
        context (select-keys inputs [:chain-id :chain-instance-genesis-root :allocator :asset
                                     :operation-id :allocation-binding-root :policy-profile
                                     :immutable-minimum-proof-mode])
        openings (:openings odd-width)
        table-leaves (mapv #(claim/entitlement-table-leaf-v2 context %) openings)
        claim-leaves (mapv #(claim/allocation-claim-leaf-v2 context %) openings)
        table-pair (claim/ordered-tree-root-v2 (subvec table-leaves 0 2) claim/table-node-domain)
        table-duplicate-last (claim/ordered-tree-root-v2 [(nth table-leaves 2) (nth table-leaves 2)]
                                                         claim/table-node-domain)
        table-members-root (claim/ordered-tree-root-v2 table-leaves claim/table-node-domain)
        table-root (claim/entitlement-table-root-v2 table-members-root
                                                   (:count odd-width) (:total-amount-base-units odd-width))
        claim-pair (claim/ordered-tree-root-v2 (subvec claim-leaves 0 2) claim/claim-node-domain)
        claim-duplicate-last (claim/ordered-tree-root-v2 [(nth claim-leaves 2) (nth claim-leaves 2)]
                                                         claim/claim-node-domain)
        claim-members-root (claim/ordered-tree-root-v2 claim-leaves claim/claim-node-domain)
        claim-root (claim/allocation-claim-root-v2 claim-members-root
                                                  (:count odd-width) (:total-amount-base-units odd-width))
        expected (:expected odd-width)]
    (is (= (:table-leaves expected) (mapv claim/bytes->hex table-leaves)))
    (is (= (:claim-leaves expected) (mapv claim/bytes->hex claim-leaves)))
    (is (= (:table-pair expected) (claim/bytes->hex table-pair)))
    (is (= (:table-duplicate-last-node expected) (claim/bytes->hex table-duplicate-last)))
    (is (= (:table-members-root expected) (claim/bytes->hex table-members-root)))
    (is (= (:table-root expected) (claim/bytes->hex table-root)))
    (is (= (:claim-pair expected) (claim/bytes->hex claim-pair)))
    (is (= (:claim-duplicate-last-node expected) (claim/bytes->hex claim-duplicate-last)))
    (is (= (:claim-members-root expected) (claim/bytes->hex claim-members-root)))
    (is (= (:claim-root expected) (claim/bytes->hex claim-root)))
    (is (= (:table-index-2-siblings expected)
           [(claim/bytes->hex (nth table-leaves 2)) (claim/bytes->hex table-pair)]))
    (is (= (:claim-index-2-siblings expected)
           [(claim/bytes->hex (nth claim-leaves 2)) (claim/bytes->hex claim-pair)]))))
