(ns ring-venturi.cache.memcached
  (:require [clojurewerkz.spyglass.client :as memcached-client]
            [ring-venturi.cache.base :as base]))

(deftype Memcached [client]
  base/Cache

  (get-all [this keys]
    (memcached-client/get-multi client keys))

  (inc-or-set [this key inc-amount expire value]
    (memcached-client/incr client key inc-amount value expire)))

(defn new-cache [client]
  (Memcached. client))
