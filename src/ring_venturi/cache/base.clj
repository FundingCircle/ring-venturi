(ns ring-venturi.cache.base)

(defprotocol Cache
  "Caches values"
  (get-all [this keys]
           "Retrieves all of the values for the given keys")
  (inc-or-set [this key inc-amount expire value]
              "Increment the counter if it exists, otherwise create it"))
