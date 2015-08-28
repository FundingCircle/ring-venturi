(ns ring-venturi.core-spec
  (:require [ring-venturi.cache :as cache]
            [ring-venturi.core :refer :all]
            [speclj.core :refer :all]
            [clj-time.core :as t]
            speclj.run.standard))

(defn- inc-in [m key]
  (let [current-value (get m key 0)]
    (assoc m key (inc current-value))))

(defrecord AtomCache [atom-cache]
  cache/Cache

  (get-request-counts [this keys]
    (map #(get @atom-cache %1 0) keys))

  (inc-request-count [this key expire]
    (swap! atom-cache inc-in key)))

(describe "ring-venturi.rate-limit-spec"
  (with cache (AtomCache. (atom {})))

  (around [it]
    (with-redefs [t/now (constantly (t/date-time 2015 6 14 9 31 27 456))]
      (it)))

  (describe "limit-requests-per-hour"
    (describe "when under limit"
      (with limit-fn (limit-requests-per-hour (constantly {:status 200})
                                              @cache
                                              {:limit 2
                                               :identifier-fn :id}))

      (before
        ; This request should be ignored
        (.inc-request-count @cache "rate-limit-1-2015-06-14T08:31" 3600)
        ; This request should be counted
        (.inc-request-count @cache "rate-limit-1-2015-06-14T09:30" 3600))

      (it "forwards request when under limit"
        (should= 200
                 (:status (@limit-fn {:id 1}))))

      (it "increments the request count"
        (should= 1
                 (do
                   (@limit-fn {:id 1})
                   (get (deref (:atom-cache @cache)) "rate-limit-1-2015-06-14T09:31")))))

    (describe "when over limit"
      (with limit-fn (limit-requests-per-hour (constantly {:status 200})
                                              @cache
                                              {:limit 2
                                               :identifier-fn :id}))

      (before
        (.inc-request-count @cache "rate-limit-1-2015-06-14T08:32" 3600)
        (.inc-request-count @cache "rate-limit-1-2015-06-14T09:29" 3600))

      (it "has too many requests"
        (should= 429
                 (:status (@limit-fn {:id 1}))))

      (it "does not increment the request count"
        (should-not (do
                      (@limit-fn {:id 1})
                      (get (deref (:atom-cache @cache))
                           "rate-limit-1-2015-06-14T09:31"))))))

  (describe "limit-requests-per-minute"
    (describe "when under limit"
      (with limit-fn (limit-requests-per-minute (constantly {:status 200})
                                                @cache
                                                {:limit 2
                                                 :identifier-fn :id}))

      (before
        ; This request should be ignored
        (.inc-request-count @cache "rate-limit-1-2015-06-14T09:30:27" 3600)
        ; This request should be counted
        (.inc-request-count @cache "rate-limit-1-2015-06-14T09:31:26" 3600))

      (it "forwards request when under limit"
        (should= 200
                 (:status (@limit-fn {:id 1}))))

      (it "increments the request count"
        (should= 1
                 (do
                   (@limit-fn {:id 1})
                   (get (deref (:atom-cache @cache)) "rate-limit-1-2015-06-14T09:31:27")))))

    (describe "when over limit"
      (with limit-fn (limit-requests-per-minute (constantly {:status 200})
                                                @cache
                                                {:limit 2
                                                 :identifier-fn :id}))

      (before
        (.inc-request-count @cache "rate-limit-1-2015-06-14T09:30:28" 3600)
        (.inc-request-count @cache "rate-limit-1-2015-06-14T09:31:26" 3600))

      (it "has too many requests"
        (should= 429
                 (:status (@limit-fn {:id 1}))))

      (it "does not increment the request count"
        (should-not (do
                      (@limit-fn {:id 1})
                      (get (deref (:atom-cache @cache))
                           "rate-limit-1-2015-06-14T09:31:27"))))))

  (describe "limit-requests-per-second"
    (describe "when under limit"
      (with limit-fn (limit-requests-per-second (constantly {:status 200})
                                                @cache
                                                {:limit 2
                                                 :identifier-fn :id}))

      (before
        ; This request should be ignored
        (.inc-request-count @cache "rate-limit-1-2015-06-14T09:31:26:4" 3600)
        ; This request should be counted
        (.inc-request-count @cache "rate-limit-1-2015-06-14T09:31:27:3" 3600))

      (it "forwards request when under limit"
        (should= 200
                 (:status (@limit-fn {:id 1}))))

      (it "increments the request count"
        (should= 1
                 (do
                   (@limit-fn {:id 1})
                   (get (deref (:atom-cache @cache)) "rate-limit-1-2015-06-14T09:31:27:4")))))

    (describe "when over limit"
      (with limit-fn (limit-requests-per-second (constantly {:status 200})
                                                @cache
                                                {:limit 2
                                                 :identifier-fn :id}))

      (before
        (.inc-request-count @cache "rate-limit-1-2015-06-14T09:31:26:5" 3600)
        (.inc-request-count @cache "rate-limit-1-2015-06-14T09:31:27:1" 3600))

      (it "has too many requests"
        (should= 429
                 (:status (@limit-fn {:id 1}))))

      (it "does not increment the request count"
        (should-not (do
                      (@limit-fn {:id 1})
                      (get (deref (:atom-cache @cache))
                           "rate-limit-1-2015-06-14T09:31:27:4")))))))

(run-specs)
