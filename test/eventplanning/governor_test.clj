(ns eventplanning.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [eventplanning.store :as store]
            [eventplanning.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Events"})
    (store/register-event! st {:event-id "E-1" :client-id "client-1"
                               :name "event-042"
                               :budget-ceiling 50000
                               :venue-capacity-verified? true})
    st))

(defn- book-op [amount]
  {:op :approve-booking :effect :propose :event-id "E-1"
   :contract-amount amount :confidence 0.9 :stake :low})

(def ^:private req {:client-id "client-1"})

(deftest ok-within-budget-and-verified
  (let [st (fresh-store)
        v (governor/check req {} (book-op 25000) st)]
    (is (:ok? v))))

(deftest ok-at-exact-budget-boundary
  (testing "the budget ceiling is inclusive"
    (let [st (fresh-store)
          v (governor/check req {} (book-op 50000) st)]
      (is (:ok? v)))))

(deftest hard-on-contract-exceeds-budget
  (testing "contracting beyond the client's registered budget ceiling is unauthorized spending, not proactive planning"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (book-op 500000) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :contract-exceeds-budget (:rule %)) (:violations v))))))

(deftest hard-on-venue-capacity-not-verified
  (testing "finalizing a booking without verified venue capacity is an overcapacity risk, not efficient service"
    (let [st (store/mem-store)]
      (store/register-client! st {:client-id "client-1" :name "Kobo Events"})
      (store/register-event! st {:event-id "E-1" :client-id "client-1"
                                 :name "event-042"
                                 :budget-ceiling 50000
                                 :venue-capacity-verified? false})
      (let [v (governor/check req {} (assoc (book-op 25000) :confidence 0.99) st)]
        (is (:hard? v))
        (is (some #(= :venue-capacity-not-verified (:rule %)) (:violations v)))))))

(deftest hard-on-unknown-event
  (let [st (fresh-store)
        v (governor/check req {} (assoc (book-op 25000) :event-id "E-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-event (:rule %)) (:violations v)))))

(deftest hard-on-foreign-event
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (let [v (governor/check {:client-id "client-2"} {} (book-op 25000) st)]
      (is (:hard? v))
      (is (some #(= :event-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {} (book-op 25000) st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (book-op 25000) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest always-escalates-over-budget-vendor-contract-even-at-high-confidence
  (testing "no vendor contract above the client's registered budget ceiling without the governor gate"
    (let [st (fresh-store)
          v (governor/check req {} {:op :approve-over-budget-vendor-contract :effect :propose
                                    :event-id "E-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest always-escalates-guest-list-disclosure-even-at-high-confidence
  (testing "sharing a client's guest list with a third-party vendor always requires human sign-off"
    (let [st (fresh-store)
          v (governor/check req {} {:op :approve-guest-list-disclosure :effect :propose
                                    :event-id "E-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (book-op 25000) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
