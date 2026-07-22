(ns eventplanning.advisor
  "Event Advisor — the advisor named in this repository's README,
  proposing an event-planning operation (finalize a booking, approve
  an over-budget vendor contract, approve a guest-list disclosure)
  from an event brief, budget and venue availability. Swappable
  mock/llm; the advisor ONLY proposes — `eventplanning.governor`
  checks the budget ceiling and venue-capacity verification
  independently and always escalates over-budget-vendor-contract and
  guest-list-disclosure decisions. Modeled on cloud-itonami-isco-4311's
  advisor.

  A proposal: {:op :approve-booking|:approve-over-budget-vendor-contract|:approve-guest-list-disclosure
               :effect :propose :event-id str :contract-amount number
               :stake kw :confidence n :rationale str}. The budget-
  ceiling and venue-capacity-verification state live on the
  registered event record itself (see `eventplanning.store`), not on
  the proposal."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])))

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake event-id contract-amount] :as request}]
  {:op op
   :effect :propose
   :event-id event-id
   :contract-amount contract-amount
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for client " (:client-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are an event-planning advisor. Given a request, propose an
   :op, the :event-id and :contract-amount, an honest :confidence and
   a :stake. Never propose a contract amount beyond the event's
   registered budget ceiling, or a booking for an event whose venue
   capacity is unverified — the governor checks both against the
   registered event record. Over-budget vendor contracts and
   guest-list disclosures always require human sign-off regardless of
   confidence.")

(defn- parse-proposal [content]
  (try
    (let [p (edn/read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
