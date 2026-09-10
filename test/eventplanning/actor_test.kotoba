(ns eventplanning.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [eventplanning.actor :as actor]
            [eventplanning.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Events"})
    (store/register-event! st {:event-id "E-1" :client-id "client-1"
                               :name "event-042"
                               :budget-ceiling 50000
                               :venue-capacity-verified? true})
    st))

(deftest commits-a-within-budget-verified-booking
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-booking :stake :low
                 :event-id "E-1" :contract-amount 25000}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-an-over-budget-booking
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-booking :stake :low
                 :event-id "E-1" :contract-amount 500000}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-approves-over-budget-vendor-contract-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-over-budget-vendor-contract :stake :low
                 :event-id "E-1"}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))
