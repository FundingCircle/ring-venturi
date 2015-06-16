(ns ring-venturi.cache.memcached
  (:require [clojurewerkz.spyglass.client :as memcached-client]
            [ring-venturi.cache.base :as base]))

(defn- parse-int [n]
  (Integer/parseInt n 10))

(deftype Memcached [client]
  base/Cache

  (get-request-counts [this keys]
    (map parse-int
         (vals (memcached-client/get-multi client keys))))

  (inc-request-count [this key expire]
    (memcached-client/incr client key 1 1 expire)))

(defn new-cache [client]
  (Memcached. client))
