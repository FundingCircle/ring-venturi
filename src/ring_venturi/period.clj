(ns ring-venturi.period
  (:require [clojure.core.cache :as cache]
            [clj-time.core :as t]
            [ring-venturi.core :as core]
            [clj-time.coerce :as tc]
            [taoensso.carmine :as car]
            [clojurewerkz.spyglass.client :as c]))

(defprotocol PeriodStore
  (configure [this period-millis]))

(defrecord MemoryStore [ttl-cache]
  PeriodStore

  (configure [this period-millis]
    (assoc this :ttl-cache (atom (cache/ttl-cache-factory {} :ttl period-millis))))

  core/RateLimiter

  (try-make-request [this client-id]
    (if (cache/has? @ttl-cache client-id)
      false
      (swap! ttl-cache cache/miss client-id true))))

(defn memory-backend []
  (MemoryStore.
   (atom (cache/ttl-cache-factory {}))))

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

(defrecord MemcachedStore [client period key-prefix]
  PeriodStore

  (configure [this period-millis]
    (assoc this :period (t/millis period-millis)))

  core/RateLimiter

  (try-make-request [this client-id]
    (try-mc-request this (str key-prefix client-id))))

(defn memcached-backend
  ([client]
   (memcached-backend client {}))
  ([client opts]
   (MemcachedStore. client
                    (t/millis 1)
                    (get opts :key-prefix ""))))

(defrecord RedisStore [redis-opts key-prefix]
  PeriodStore

  (configure [this period-millis]
    (assoc this :period-millis period-millis))

  core/RateLimiter

  (try-make-request [this client-id]
    (let [redis-key (str key-prefix ":" client-id)
          result (car/wcar redis-opts (car/set redis-key "LOCK" :PX (:period-millis this) :NX))]
      (= "OK" result))))

(defn redis-backend
  ([redis-opts]
   (redis-backend redis-opts {}))
  ([redis-opts opts]
   (RedisStore. redis-opts (get opts :key-prefix "rate-limit"))))

(defn every-millis [period-millis backend]
  (configure backend period-millis))
