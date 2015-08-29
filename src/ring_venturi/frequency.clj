(ns ring-venturi.frequency
  (:require [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.periodic :as p]
            [ring-venturi.core :as core]
            [clojurewerkz.spyglass.client :as mc]
            [clojure.data.json :as json]))

(def ^:private format-time-to-mins
  (partial f/unparse
           (f/formatter "yyyy-MM-dd'T'hh:mm")))

(def ^:private format-time-to-sec
  (partial f/unparse
           (f/formatter "yyyy-MM-dd'T'hh:mm:ss")))

(def ^:private format-time-to-millis
  (partial f/unparse
           (f/formatter "yyyy-MM-dd'T'hh:mm:ss:S")))

(defn- parse-int [n]
  (Integer/parseInt n 10))

(defn- time-unit->seconds [time-unit]
  (case time-unit
    :hours 3600
    :minutes 60
    :seconds 1))

(defn- time-unit->time-seq [time-unit now]
  (case time-unit
    :hours   (map format-time-to-mins   (take 60 (p/periodic-seq now (t/minutes -1))))
    :minutes (map format-time-to-sec    (take 60 (p/periodic-seq now (t/seconds -1))))
    :seconds (map format-time-to-millis (take 10 (p/periodic-seq now (t/millis -100))))))

(defn- bucket-seq [prefix request-id time-unit]
  (let [time-seq (time-unit->time-seq time-unit (t/now))]
    (map #(str prefix "-" request-id "-" %1) time-seq)))

(defprotocol FrequencyStore
  (configure [this req-limit time-unit]))

(defrecord MemcachedStore [client key-prefix]
  FrequencyStore

  (configure [this req-limit time-unit]
    (assoc this :req-limit req-limit
                :time-unit time-unit))

  core/RateLimiter

  (try-make-request [this client-id]
    (let [bucket-keys (bucket-seq key-prefix client-id (:time-unit this))
          num-requests (->> (mc/get-multi client bucket-keys)
                            vals
                            (map parse-int)
                            (reduce + 0))]
      (if (>= num-requests (:req-limit this))
        false
        (do
          (mc/incr client
                   (first bucket-keys)
                   1 ; Inc by
                   1 ; Default val
                   (time-unit->seconds (:time-unit this)))
          true)))))

(defn memcached-backend
  ([client] (memcached-backend client {}))
  ([client opts]
   (MemcachedStore. client (get opts :key-prefix "rate-limiter"))))

(defn per-hour [req-limit backend]
  (.configure backend req-limit :hours))

(defn per-minute [req-limit backend]
  (.configure backend req-limit :minutes))

(defn per-second [req-limit backend]
  (.configure backend req-limit :seconds))
