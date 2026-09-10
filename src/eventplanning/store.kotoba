(ns eventplanning.store
  "SSoT for the ISCO-08 3332 independent event planning practice
  actor (itonami actor pattern, ADR-2607011000 / CLAUDE.md Actors
  section; README's 'Robotics premise' — a venue-setup and material-
  handling robot performs signage placement, seating-layout staging
  and equipment transport under this advisor/governor pair, which
  never dispatches hardware itself and never contracts a vendor above
  a client's registered budget ceiling). Modeled on
  cloud-itonami-isco-4311's bookkeeping.store.

  Domain:

    client — a registered business/individual host (:client-id, :name)
    event  — a registered event plan {:event-id :client-id :name
             :budget-ceiling number :venue-capacity-verified?
             boolean}. `:budget-ceiling` is the registered spending
             ceiling a proposed vendor contract amount must not
             exceed — contracting beyond the client's registered
             budget ceiling is unauthorized spending, not proactive
             planning. `:venue-capacity-verified?` records whether the
             venue's capacity has been verified — finalizing a booking
             without verified venue capacity is an overcapacity risk,
             not efficient service.
    record — a committed operating record (a finalized booking) —
             written ONLY via commit-record!.
    ledger — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (event [s event-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-event! [s e])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (event [_ event-id] (get-in @a [:events event-id]))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-event! [s e]
    (swap! a assoc-in [:events (:event-id e)] e) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :events {} :records [] :ledger []}
                                   seed)))))
