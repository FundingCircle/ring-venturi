(ns ring-venturi.cache.base)

(defprotocol Cache
  "Caches request counts for tracking user requests."

  (get-request-counts [this keys]
                      "Retrieves all of the values for the given keys")

  (inc-request-count [this key expire]
                     "Increment the counter if it exists, otherwise create it"))
