(ns ring-venturi.period-spec
  (:require [ring-venturi.period :as period]
            [speclj.core :refer :all]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [clojurewerkz.spyglass.client :as c]
            speclj.run.standard))

(describe "in-memory-limiter"
  (with handler (constantly {:status 200}))
  (with limiter (period/in-memory-limiter 1000))
  (with app (period/limit @handler @limiter :id ))
  (with request {:id 1})

  (describe "the first request"
    (it "gets through"
        (should= 200 (:status (@app @request)))))

  (describe "a second request within the backoff"
    (before (@app @request))
    (it "is blocked"
        (should= 429 (:status (@app @request)))))

  (describe "a second request after the backoff"
    (before (do
              (@app @request)
              (Thread/sleep 1000)))
    (it "gets through"
        (should= 200 (:status (@app @request)))))

  (describe "when request has no user"
    (before (@app {}))
    (it "gets through"
        (should= 200 (:status (@app {}))))))

(defn seconds-from-now [s]
  (-> (t/now)
      (t/plus (t/seconds s))
      tc/to-long))

(describe "memcached-limiter"
  (with memcached-client (c/text-connection "localhost:11211"))
  (with cache-key "my_period_key")
  (with limiter (period/memcached-limiter @memcached-client 2000))
  (with saved-expir-time (c/get @memcached-client @cache-key))

  (before
    @(c/delete @memcached-client @cache-key))

  (around [it]
    (let [now (t/now)]
      (with-redefs [t/now (constantly now)]
        (it))))

  (describe "when cache is empty"
    (it "is successful"
      (should (period/try-update-key @limiter @cache-key)))

    (it "sets the expiration time"
      (should= (seconds-from-now 2)
               (do
                 (period/try-update-key @limiter @cache-key)
                 @saved-expir-time))))

  (describe "when past the expiration time"
    (before
      @(c/set @memcached-client @cache-key 5 (tc/to-long (t/now))))

    (it "is successful"
      (should (period/try-update-key @limiter @cache-key)))

    (it "updates the expiration time"
      (should= (seconds-from-now 2)
               (do
                 (period/try-update-key @limiter @cache-key)
                 @saved-expir-time))))

  (describe "when before the expiration time"
    (with expiration-time (seconds-from-now 10))

    (before
      @(c/set @memcached-client @cache-key 5 @expiration-time))

    (it "is rejected"
      (should-not (period/try-update-key @limiter @cache-key)))

    (it "does not update the expiration time"
      (should= @expiration-time
               (do
                 (period/try-update-key @limiter @cache-key)
                 @saved-expir-time)))))

(run-specs)
