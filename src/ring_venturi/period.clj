(ns ring-venturi.period
  (:require [clojure.core.cache :as cache]
            [clj-time.core :as t]
            [clj-time.coerce :as c]))

(def backoff-response {:status 429})

(defprotocol PeriodRateLimiter
  "Determines if the request is too soon. Returns true if no other requests have been made
  in the period, false otherwise."
  (try-update-key [this k]))

(deftype InMemoryLimiter [ttl-cache]
  PeriodRateLimiter
  (try-update-key [this k]
    (if (cache/has? @ttl-cache k)
      false
      (swap! ttl-cache cache/miss k true))))

(defn in-memory-limiter [ttl-millis]
  (InMemoryLimiter.
   (atom (cache/ttl-cache-factory {} :ttl ttl-millis))))

(defn limit [handler limiter id-fn]
  (fn [request]
    (if-let [id (id-fn request)]
      (if (try-update-key limiter id)
        (handler request)
        backoff-response)
      (handler request))))
