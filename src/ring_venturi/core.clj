(ns ring-venturi.core
  (:require [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.periodic :as p]
            [clojure.data.json :as json]))

(def ^:private exceeded-limit-response
  {:status 429
   :body (json/write-str {:message "Exceeded limit"})})

(def ^:private format-time-to-mins
  (partial f/unparse
           (f/formatter "yyyy-MM-dd'T'hh:mm")))

(def ^:private format-time-to-sec
  (partial f/unparse
           (f/formatter "yyyy-MM-dd'T'hh:mm:ss")))

(def ^:private format-time-to-millis
  (partial f/unparse
           (f/formatter "yyyy-MM-dd'T'hh:mm:ss:S")))

(defprotocol RateLimiter
  (time-seq [this now]
            "Sequence of time strings, from newest to oldest,
            that are relevant to the given time.
            (Limiting requests by hour should be a sequence of
            60 times differing by one minute)")
  (inc-cache [this cache bucket-key]
             "Increments or sets the number of requests for a given bucket key.
             (Limiting requests by hour should expire the key after an hour)"))

(defrecord HourRateLimiter [limit]
  RateLimiter
  (time-seq [this now]
    (map format-time-to-mins (take 60
                                   (p/periodic-seq now (t/minutes -1)))))
  (inc-cache [this cache bucket-key]
    (.inc-request-count cache bucket-key 3600)))

(defrecord MinuteRateLimiter [limit]
  RateLimiter
  (time-seq [this now]
    (map format-time-to-sec (take 60
                                  (p/periodic-seq now (t/seconds -1)))))
  (inc-cache [this cache bucket-key]
    (.inc-request-count cache bucket-key 60)))

(defrecord SecondRateLimiter [limit]
  RateLimiter
  (time-seq [this now]
    (map format-time-to-millis (take 10
                                     (p/periodic-seq now (t/millis -100)))))
  (inc-cache [this cache bucket-key]
    (.inc-request-count cache bucket-key 1)))

(defn- bucket-seq [request-id rate-limiter]
  (let [time-seq (.time-seq rate-limiter (t/now))]
    (map #(str "rate-limit-" request-id "-" %1) time-seq)))

(defn- count-requests [cache bucket-keys]
  "Counts the number of requests this user has made"
  (let [request-counts (.get-request-counts cache bucket-keys)]
    (reduce + request-counts)))

(defn- exceeds-limit? [cache bucket-keys rate-limiter]
  "Determines if another request would exceed the request limit"
  (let [num-requests (count-requests cache bucket-keys)]
    (>= num-requests (:limit rate-limiter))))

(defn- limit-rate* [handler cache rate-limiter request request-id]
  (let [bucket-keys (bucket-seq request-id rate-limiter)]
    (if-not (exceeds-limit? cache bucket-keys rate-limiter)
      (do
        (.inc-cache rate-limiter cache (first bucket-keys))
        (handler request))
      exceeded-limit-response)))

(defn- new-hour-limiter [limit]
  (HourRateLimiter. limit))
(defn- new-minute-limiter [limit]
  (MinuteRateLimiter. limit))
(defn- new-second-limiter [limit]
  (SecondRateLimiter. limit))

(defn- limit-requests [limiter-builder handler cache opts]
  (let [{:keys [limit identifier-fn]} opts
        rate-limiter (limiter-builder limit)]
    (fn [request]
      (let [request-id (identifier-fn request)]
        (limit-rate* handler
                     cache
                     rate-limiter
                     request
                     request-id)))))

(def limit-requests-per-hour   (partial limit-requests new-hour-limiter))
(def limit-requests-per-minute (partial limit-requests new-minute-limiter))
(def limit-requests-per-second (partial limit-requests new-second-limiter))
