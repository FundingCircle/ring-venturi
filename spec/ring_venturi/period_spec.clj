(ns ring-venturi.period-spec
  (:require [ring-venturi.period :as period]
            [speclj.core :refer :all]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [taoensso.carmine :as car]
            [clojurewerkz.spyglass.client :as c]
            speclj.run.standard))

(describe "memory-backend"
  (with limiter (.configure (period/memory-backend) 1000))

  (describe "the first request"
    (it "gets through"
        (should (.try-make-request @limiter "id"))))

  (describe "the second request"
    (before
      (.try-make-request @limiter "id"))

    (it "is rejected"
      (should-not (.try-make-request @limiter "id")))))

(defn seconds-from-now [s]
  (-> (t/now)
      (t/plus (t/seconds s))
      tc/to-long))

(describe "memcached-backend"
  (with memcached-client (c/text-connection "localhost:11211"))
  (with cache-key "my_period_key")
  (with limiter (.configure (period/memcached-backend @memcached-client) 2000))
  (with saved-expir-time (c/get @memcached-client @cache-key))

  (before
    @(c/delete @memcached-client @cache-key))

  (around [it]
    (let [now (t/now)]
      (with-redefs [t/now (constantly now)]
        (it))))

  (context "when cache is empty"
    (it "is successful"
      (should (.try-make-request @limiter @cache-key)))

    (it "sets the expiration time"
      (should= (seconds-from-now 2)
               (do
                 (.try-make-request @limiter @cache-key)
                 @saved-expir-time))))

  (context "when past the expiration time"
    (before
      @(c/set @memcached-client @cache-key 5 (tc/to-long (t/now))))

    (it "is successful"
      (should (.try-make-request @limiter @cache-key)))

    (it "updates the expiration time"
      (should= (seconds-from-now 2)
               (do
                 (.try-make-request @limiter @cache-key)
                 @saved-expir-time))))

  (context "when before the expiration time"
    (with expiration-time (seconds-from-now 10))

    (before
      @(c/set @memcached-client @cache-key 5 @expiration-time))

    (it "is rejected"
      (should-not (.try-make-request @limiter @cache-key)))

    (it "does not update the expiration time"
      (should= @expiration-time
               (do
                 (.try-make-request @limiter @cache-key)
                 @saved-expir-time)))))

(def redis-opts {:pool {} :spec {}})
(def user-id "123")

(describe "redis-backend"
  (with limiter (.configure (period/redis-backend redis-opts {:key-prefix "rate-limit-test"}) 100))

  (context "when unlocked"
    (before
      (car/wcar redis-opts
                (car/del "rate-limit-test:123")))

    (it "accepts requests"
      (should (.try-make-request @limiter user-id)))

    (it "locks the user"
      (should= "LOCK"
               (do (.try-make-request @limiter user-id)
                   (car/wcar redis-opts (car/get "rate-limit-test:123"))))))

  (context "when locked"
    (before
      (.try-make-request @limiter user-id))

    (it "should reject the request"
      (should-not (.try-make-request @limiter user-id)))

    (it "allows requests after the limit"
      (should (do (Thread/sleep 100)
                  (.try-make-request @limiter user-id))))))

(run-specs)
