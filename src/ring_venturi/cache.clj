(ns ring-venturi.cache
  (:require [clojurewerkz.spyglass.client :as memcached-client]))

(defprotocol Cache
  "Caches request counts for tracking user requests."

  (get-request-counts [this keys]
                      "Retrieves all of the values for the given keys")

  (inc-request-count [this key expire]
                     "Increment the counter if it exists, otherwise create it"))


(defn- parse-int [n]
  (Integer/parseInt n 10))

(deftype Memcached [client]
  Cache

  (get-request-counts [this keys]
    (map parse-int
         (vals (memcached-client/get-multi client keys))))

  (inc-request-count [this key expire]
    (memcached-client/incr client key 1 1 expire)))

(defn new-memcached-cache [client]
  (Memcached. client))
