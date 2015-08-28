(ns ring-venturi.period
  (:require [clojure.core.cache :as cache]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [clojurewerkz.spyglass.client :as c]))

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

(declare update-expiration-time check-expiration add-expiration try-mc-request)
(def ^:private cache-expiration-secs (* 5 60))

(defn- next-request-time [mc-limitter now]
  (->> mc-limitter
       :period
       (t/plus now)
       tc/to-long))

(defn- update-expiration-time [mc-limitter client-id cas-id now]
  (let [next-req-time (next-request-time mc-limitter now)
        result (c/cas (:client mc-limitter) client-id cas-id next-req-time)]
    (case result
      :ok true
      :not-found (add-expiration mc-limitter client-id now)
      :exists (try-mc-request mc-limitter client-id now))))

(defn- check-expiration [mc-limitter client-id expiration-time cas-id now]
  (if (t/before? now expiration-time)
    false
    (update-expiration-time mc-limitter client-id cas-id now)))

(defn- add-expiration [mc-limitter client-id now]
  (let [next-req-time (next-request-time mc-limitter now)
        add-result @(c/add (:client mc-limitter)
                           client-id
                           cache-expiration-secs
                           next-req-time)]
    (if add-result
      true
      (try-mc-request mc-limitter client-id now))))

(defn- try-mc-request
  ([mc-limitter client-id] (try-mc-request mc-limitter client-id (t/now)))
  ([mc-limitter client-id now]
   (let [{expr-str :value cas-id :cas} (c/gets (:client mc-limitter) client-id)]
     (if-not (nil? expr-str)
       (check-expiration mc-limitter client-id (tc/from-long expr-str) cas-id now)
       (add-expiration mc-limitter client-id now)))))

(defrecord MemcachedLimiter [client period key-prefix]
  PeriodRateLimiter
  (try-update-key [this client-id]
    (try-mc-request this (str key-prefix client-id))))

(defn memcached-limiter
  ([client period-millis]
   (memcached-limiter client period-millis {}))
  ([client period-millis opts]
   (MemcachedLimiter. client
                      (t/millis period-millis)
                      (get opts :key-prefix ""))))
