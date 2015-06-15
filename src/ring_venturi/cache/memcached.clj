(ns ring-venturi.cache.memcached
  (:require [clojurewerkz.spyglass.client :as memcached-client]
            [ring-venturi.cache.base :as base]))

(deftype Memcached [client]
  base/Cache

  (get-all [this keys]
    (memcached-client/get-multi client keys))

  (inc-or-set [this key expire]
    (memcached-client/incr client key 1 1 expire)))

(defn new-cache [client]
  (Memcached. client))
