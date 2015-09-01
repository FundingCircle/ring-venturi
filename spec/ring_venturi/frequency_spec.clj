(ns ring-venturi.frequency-spec
  (:require [ring-venturi.frequency :as f]
            [clj-time.core :as t]
            [speclj.core :refer :all]
            [clojurewerkz.spyglass.client :as mc]
            [clj-time.core :as t]
            speclj.run.standard))

(describe "memcached-backend"
  (with memcached-client (mc/text-connection "localhost:11211"))
  (with rand-prefix (str (rand-int (java.lang.Integer/MAX_VALUE))))
  (with limiter (f/per-second 2 (f/memcached-backend @memcached-client {:key-prefix @rand-prefix})))
  (with memcached-req-key (str @rand-prefix "-123-2000-01-01T12:00:01:0"))
  (with client-id "123")

  (around [it]
    (with-redefs [t/now (constantly (t/date-time 2000 1 1 0 0 1))]
      (it)))

  (context "with no previous requests"
    (it "accepts the request"
      (should (.try-make-request @limiter @client-id)))

    (it "saves the request count"
      (should= "1"
               (do
                 (.try-make-request @limiter @client-id)
                 (mc/get @memcached-client
                         @memcached-req-key)))))

  (context "with existing requests below limit"
    (before
      (.try-make-request @limiter @client-id))

    (it "accepts the request"
      (should (.try-make-request @limiter @client-id)))

    (it "updates the request count"
      (should= "2"
               (do
                 (.try-make-request @limiter @client-id)
                 (mc/get @memcached-client
                         @memcached-req-key)))))

  (context "with existing requests at limit"
    (before
      (mc/set @memcached-client (str @rand-prefix "-123-2000-01-01T12:00:00:1") 1 "2"))

    (it "rejects the request"
      (should-not (.try-make-request @limiter @client-id)))

    (it "does not update the request count"
      (should-be-nil (do
                       (.try-make-request @limiter @client-id)
                       (mc/get @memcached-client
                               @memcached-req-key))))))

(run-specs)
