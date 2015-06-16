(ns ring-venturi.frequency
  (:require [clojure.core.cache :as cache]))

(def backoff-response {:status 429
                       :body  "Rate limit execeeded"})

(defprotocol FrequencyLimiter
  "Determine if a request should be blocked, "
  (block? [this k] "Checks if the request should be blocked.")
  (backoff! [this k] "Adds an identifier for something to block"))

(deftype InMemoryLimiter [ttl-cache]
  FrequencyLimiter
  (block? [this k]
    (cache/has? @ttl-cache k))
  (backoff! [this k]
    (swap! ttl-cache cache/miss k true )))

(defn in-memory-limiter [ttl-millis]
  (InMemoryLimiter.
   (atom (cache/ttl-cache-factory {} :ttl ttl-millis))))

(defn limit [handler limiter id-fn]
  (fn [request]
    (let [id (id-fn request)]
      (if (block? limiter id)
        backoff-response
        (do
          (backoff! limiter id)
          (handler request))))))
