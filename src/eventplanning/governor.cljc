(ns eventplanning.governor
  "EventPlanningGovernor — the independent safety/traceability layer
  named in this repository's README/business-model.md, gating every
  booking an advisor may propose for an event. The governor never
  dispatches hardware itself and never contracts a vendor above a
  client's registered budget ceiling. Modeled on
  cloud-itonami-isco-4311's bookkeeping.governor. Task twist: a
  proposed vendor contract amount is an arithmetic ceiling against
  the event's registered budget ceiling, and a booking cannot be
  finalized until the venue's capacity has been verified.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance   — the business/individual host must be
                             registered.
    2. no-actuation        — proposal :effect must be :propose (the
                             governor never dispatches hardware and
                             never contracts a vendor above the
                             registered budget ceiling; it only gates
                             what the advisor may book).
    3. event basis         — a booking proposal must cite a
                             REGISTERED event belonging to this
                             client.
    4. budget ceiling       — the proposed contract amount must not
                             exceed the event's registered
                             `:budget-ceiling` (contracting beyond the
                             client's registered budget ceiling is
                             unauthorized spending, not proactive
                             planning).
    5. venue-capacity verified — the event must have
                             `:venue-capacity-verified?` true before
                             any booking can be finalized (finalizing
                             a booking without verified venue capacity
                             is an overcapacity risk, not efficient
                             service).
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off per
  business-model.md's Trust Controls — these are :high/
  :safety-critical regardless of confidence):
    6. :op :approve-over-budget-vendor-contract (no vendor contract
                             above the client's registered budget
                             ceiling without the governor gate).
    7. :op :approve-guest-list-disclosure (sharing a client's guest
                             list with a third-party vendor always
                             requires human sign-off).
    8. low confidence (< `confidence-floor`)."
  (:require [eventplanning.store :as store]))

(def confidence-floor 0.6)

(def ^:private always-escalate-ops #{:approve-over-budget-vendor-contract
                                     :approve-guest-list-disclosure})

(defn- hard-violations [{:keys [request proposal]} client-record e]
  (let [{:keys [op contract-amount]} proposal
        book? (= :approve-booking op)]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（governor は登録予算上限超過のベンダー契約を直接実行しない）"})

      (and book? (nil? e))
      (conj {:rule :unknown-event :detail "未登録 event への予約提案は不可"})

      (and book? e (not= (:client-id e) (:client-id request)))
      (conj {:rule :event-wrong-client :detail "event が別 client のもの"})

      (and book? e (number? contract-amount) (> contract-amount (:budget-ceiling e)))
      (conj {:rule :contract-exceeds-budget
             :detail (str "契約額 " contract-amount " > 登録済み予算上限 "
                          (:budget-ceiling e) "（登録予算上限を超える契約は無許可支出であって先を見越した計画ではない）")})

      (and book? e (not (:venue-capacity-verified? e)))
      (conj {:rule :venue-capacity-not-verified
             :detail "会場収容人数未確認の event の予約確定は過密リスクであって効率的サービスではない"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `eventplanning.store/Store`. Pure — never
  mutates the store, never contracts a vendor above the registered
  budget ceiling."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        e (some->> (:event-id proposal) (store/event store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record e)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        always-risky? (contains? always-escalate-ops (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
